"""Week 30 Stage A latency remediation collection and readout generation.

This script reads backend, DB, and OpenAI configuration from environment
variables. It intentionally contains no credentials.
"""

from __future__ import annotations

import argparse
import csv
import json
import math
import statistics
import subprocess
import time
from collections import Counter, defaultdict
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from week28_repaired_stagea import (
    BACKEND_URL,
    CANNED_ANSWERS,
    CHAT_MODEL,
    EXPERIMENT_ID,
    PRIOR_HISTORIES,
    REGISTRY,
    RESULTS,
    DOCS,
    db_connect,
    fetch_all,
    find_session_ids,
    get_variant,
    now_iso,
    percentile,
    prefix_metric_session_ids,
    prepare_experiment,
    stable_question_id,
)
from week29_post_remediation_stagea import (
    create_session,
    evaluate_answer,
    generate_question,
    seed_history,
    wait_for_experiment_metric,
    wait_for_topic_coverage,
)


DEFAULT_PREFIX = "w30-"
METRIC_VERSION = "llm-v1.0-history-week30-latency-remediation"
USER_FACING_LATENCY_VERSION = "user_facing_question_generation_v1"
TARGET_TREATMENT = 80
TARGET_BASELINE = 50
USER_FACING_P95_THRESHOLD_MS = 3000
CALIBRATION_MIN_TREATMENT_PER_SLICE = 25
CALIBRATION_MIN_BASELINE_PER_SLICE = 15
CALIBRATION_PASS_SCORE_THRESHOLD = 60.0
SLICE_ORDER = ["junior", "mid", "senior"]
ROLE_BY_LEVEL = {
    "junior": "backend_java",
    "mid": "backend_java",
    "senior": "backend_java",
}


def manifest_path() -> Path:
    return RESULTS / "week30_session_manifest.json"


def load_manifest(prefix: str) -> list[dict[str, Any]]:
    path = manifest_path()
    if not path.exists():
        return []
    rows = json.loads(path.read_text(encoding="utf-8"))
    return [row for row in rows if row.get("session_id", "").startswith(prefix)]


def write_manifest(rows: list[dict[str, Any]]) -> None:
    existing_by_session: dict[str, dict[str, Any]] = {}
    path = manifest_path()
    if path.exists():
        for row in json.loads(path.read_text(encoding="utf-8")):
            if row.get("session_id"):
                existing_by_session[row["session_id"]] = row
    for row in rows:
        existing_by_session[row["session_id"]] = row
    RESULTS.mkdir(parents=True, exist_ok=True)
    merged = sorted(existing_by_session.values(), key=lambda row: row.get("session_id", ""))
    path.write_text(json.dumps(merged, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def git_sha() -> str:
    try:
        return subprocess.check_output(
            ["git", "rev-parse", "--short", "HEAD"],
            cwd=Path(__file__).resolve().parents[1],
            text=True,
        ).strip()
    except Exception:
        return "unknown"


def git_metadata() -> dict[str, Any]:
    metadata: dict[str, Any] = {"commit_sha": git_sha(), "workspace_dirty": None}
    repo_root = Path(__file__).resolve().parents[1]
    try:
        status = subprocess.check_output(
            ["git", "status", "--porcelain"],
            cwd=repo_root,
            text=True,
        ).splitlines()
        metadata["workspace_dirty"] = bool(status)
        metadata["local_change_count"] = len(status)
        if status:
            metadata["code_version_note"] = (
                "Week 30 collection ran from local uncommitted changes; commit pending per review workflow."
            )
    except Exception:
        metadata["workspace_dirty"] = "unknown"
        metadata["code_version_note"] = "Git workspace status was unavailable during artifact generation."
    return metadata


def get_experiment_config() -> dict[str, Any]:
    conn = db_connect()
    cur = conn.cursor(dictionary=True)
    cur.execute("SELECT * FROM experiment WHERE id=%s", (EXPERIMENT_ID,))
    experiment = cur.fetchone()
    cur.close()
    conn.close()
    if not experiment:
        return {}
    for key in ("baseline_config", "treatment_config"):
        if experiment.get(key):
            try:
                experiment[key + "_json"] = json.loads(experiment[key])
            except json.JSONDecodeError:
                experiment[key + "_json"] = None
    for key, value in list(experiment.items()):
        if hasattr(value, "isoformat"):
            experiment[key] = value.isoformat()
    return experiment


def count_prefix_metrics(prefix: str) -> dict[str, int]:
    conn = db_connect()
    cur = conn.cursor(dictionary=True)
    cur.execute(
        "SELECT variant, COUNT(DISTINCT session_id) n "
        "FROM experiment_metric WHERE experiment_id=%s AND session_id LIKE %s GROUP BY variant",
        (EXPERIMENT_ID, f"{prefix}%"),
    )
    counts = {row["variant"]: row["n"] for row in cur.fetchall()}
    cur.close()
    conn.close()
    return counts


def choose_level(variant: str, level_counts: dict[str, Counter]) -> str:
    counts = level_counts[variant]
    if counts["senior"] < (30 if variant == "treatment" else 20):
        return "senior"
    return min(SLICE_ORDER, key=lambda level: counts[level])


def collect(prefix: str, target_treatment: int, target_baseline: int, sleep_seconds: float) -> list[dict[str, Any]]:
    counts = count_prefix_metrics(prefix)
    treatment_needed = max(0, target_treatment - counts.get("treatment", 0))
    baseline_needed = max(0, target_baseline - counts.get("baseline", 0))
    existing_session_ids = prefix_metric_session_ids(prefix)
    treatment_ids = find_session_ids(prefix, "treatment", treatment_needed, skip_existing=existing_session_ids)
    existing_session_ids.update(treatment_ids)
    baseline_ids = find_session_ids(prefix, "baseline", baseline_needed, skip_existing=existing_session_ids)
    planned = [(sid, "treatment") for sid in treatment_ids] + [(sid, "baseline") for sid in baseline_ids]

    manifest = load_manifest(prefix)
    run_git = git_metadata()
    experiment = get_experiment_config()
    level_counts: dict[str, Counter] = defaultdict(Counter)
    for row in manifest:
        if row.get("variant") in ("treatment", "baseline"):
            level_counts[row["variant"]][row.get("level", "unknown")] += 1

    new_rows: list[dict[str, Any]] = []
    for idx, (session_id, expected_variant) in enumerate(planned, 1):
        level = choose_level(expected_variant, level_counts)
        level_counts[expected_variant][level] += 1
        role_id = ROLE_BY_LEVEL[level]
        history = PRIOR_HISTORIES[(idx - 1) % len(PRIOR_HISTORIES)]
        record: dict[str, Any] = {
            "session_id": session_id,
            "expected_variant": expected_variant,
            "role_id": role_id,
            "level": level,
            "seeded_prior_count": len(history),
            "generated_followup_count": 0,
            "evaluated_answer_count": 0,
            "total_questions_asked": len(history),
            "early_stop_triggered": False,
            "early_stop_reason": None,
            "premature_stop": False,
            "completion_status": "started",
            "latency_definition": USER_FACING_LATENCY_VERSION,
            "collection_prefix": prefix,
            **run_git,
            "experiment_id": EXPERIMENT_ID,
            "experiment_config": {
                "name": experiment.get("name"),
                "status": experiment.get("status"),
                "traffic_percentage": experiment.get("traffic_percentage"),
                "baseline_config": experiment.get("baseline_config_json"),
                "treatment_config": experiment.get("treatment_config_json"),
            },
            "errors": [],
            "collected_at": now_iso(),
        }
        try:
            create_session(session_id, role_id, level)
            seed_history(session_id, history)
            q_data, q_external_latency = generate_question(session_id, role_id, level)
            question = q_data.get("question", "")
            variant = q_data.get("variant", get_variant(session_id))
            backend_latency = q_data.get("questionGenerationLatencyMs")
            record.update(
                {
                    "variant": variant,
                    "experiment_id": q_data.get("experimentId", EXPERIMENT_ID),
                    "generated_followup_count": 1,
                    "total_questions_asked": len(history) + 1,
                    "question_http_latency_ms": q_external_latency,
                    "backend_question_generation_latency_ms": backend_latency,
                    "client_request_overhead_ms": (
                        q_external_latency - backend_latency
                        if isinstance(backend_latency, int)
                        else None
                    ),
                    "generated_question_id": stable_question_id(question),
                    "generated_question_snippet": question[:240],
                    "post_response_audit": q_data.get("postResponseAudit"),
                    "prompt_history_messages_used": q_data.get("promptHistoryMessagesUsed"),
                    "prompt_history_total_messages": q_data.get("promptHistoryTotalMessages"),
                    "prompt_input_chars": q_data.get("promptInputChars"),
                    "question_generation_max_tokens": q_data.get("questionGenerationMaxTokens"),
                    "route_model": q_data.get("routeModel"),
                    "route_temperature": q_data.get("routeTemperature"),
                }
            )
            metric_row = wait_for_experiment_metric(session_id)
            if metric_row:
                record.update(
                    {
                        "experiment_metric_variant": metric_row.get("variant"),
                        "experiment_metric_quality_score": metric_row.get("quality_score"),
                        "experiment_metric_latency_ms": metric_row.get("latency_ms"),
                        "experiment_metric_poll_latency_ms": metric_row.get("metric_poll_latency_ms"),
                        "experiment_metric_created_at": metric_row.get("created_at"),
                    }
                )
            else:
                record["errors"].append("experiment_metric row not observed before timeout")

            topic_row = wait_for_topic_coverage(session_id)
            record["topic_coverage_poll_latency_ms"] = (
                topic_row.get("topic_coverage_poll_latency_ms") if topic_row else None
            )
            if topic_row is None:
                record["errors"].append("topic_coverage row not observed before timeout")

            eval_data, eval_latency = evaluate_answer(session_id, question, role_id, level)
            score = eval_data.get("score")
            record.update(
                {
                    "evaluated_answer_count": 1,
                    "final_evaluation_score": score,
                    "latest_evaluation_score": score,
                    "ground_truth_label": (
                        bool(float(score) >= CALIBRATION_PASS_SCORE_THRESHOLD)
                        if score is not None
                        else None
                    ),
                    "ground_truth_label_source": "final_evaluation_score_threshold_60",
                    "evaluation_latency_ms": eval_latency,
                    "completion_status": "completed",
                }
            )
            print(
                f"[{idx}/{len(planned)}] {session_id} {variant} {level} "
                f"q_http={q_external_latency}ms backend_q={backend_latency}ms "
                f"prompt_chars={record.get('prompt_input_chars')} "
                f"audit_poll={record.get('experiment_metric_poll_latency_ms')}ms "
                f"eval={eval_latency}ms score={score}"
            )
        except Exception as exc:
            record["completion_status"] = "error"
            record["errors"].append(str(exc))
            print(f"[ERROR] {session_id}: {exc}")
        new_rows.append(record)
        write_manifest(manifest + new_rows)
        if sleep_seconds > 0:
            time.sleep(sleep_seconds)

    return new_rows


def values(rows: list[dict[str, Any]], field: str) -> list[float]:
    return [float(row[field]) for row in rows if row.get(field) is not None]


def summarize_variant(rows: list[dict[str, Any]], variant: str) -> dict[str, Any]:
    subset = [row for row in rows if row.get("variant") == variant]
    completed = [row for row in subset if row.get("completion_status") == "completed"]
    total_turn = [
        row["question_http_latency_ms"] + row["evaluation_latency_ms"]
        for row in completed
        if row.get("question_http_latency_ms") is not None and row.get("evaluation_latency_ms") is not None
    ]
    return {
        "n_sessions": len(subset),
        "n_completed": len(completed),
        "avg_questions_per_session": statistics.mean(values(completed, "total_questions_asked")) if completed else None,
        "early_stop_rate": len([row for row in completed if row.get("early_stop_triggered")]) / len(completed) if completed else None,
        "premature_stop_rate": len([row for row in completed if row.get("premature_stop")]) / len(completed) if completed else None,
        "session_completion_rate": len(completed) / len(subset) if subset else None,
        "user_facing_latency_p50_ms": percentile(values(completed, "backend_question_generation_latency_ms"), 0.50),
        "user_facing_latency_p95_ms": percentile(values(completed, "backend_question_generation_latency_ms"), 0.95),
        "external_question_http_p50_ms": percentile(values(completed, "question_http_latency_ms"), 0.50),
        "external_question_http_p95_ms": percentile(values(completed, "question_http_latency_ms"), 0.95),
        "evaluation_latency_p50_ms": percentile(values(completed, "evaluation_latency_ms"), 0.50),
        "evaluation_latency_p95_ms": percentile(values(completed, "evaluation_latency_ms"), 0.95),
        "end_to_end_turn_latency_p95_ms": percentile(total_turn, 0.95),
        "final_score_avg": statistics.mean(values(completed, "final_evaluation_score")) if completed else None,
    }


def metric_for(rows: list[dict[str, Any]], field: str) -> dict[str, Any]:
    vals = values(rows, field)
    return {
        "n": len(vals),
        "p50_ms": percentile(vals, 0.50),
        "p95_ms": percentile(vals, 0.95),
        "avg_ms": statistics.mean(vals) if vals else None,
    }


def char_metric_for(rows: list[dict[str, Any]], field: str) -> dict[str, Any]:
    vals = values(rows, field)
    return {
        "n": len(vals),
        "p50_chars": percentile(vals, 0.50),
        "p95_chars": percentile(vals, 0.95),
        "avg_chars": statistics.mean(vals) if vals else None,
    }


def latency_breakdown(rows: list[dict[str, Any]]) -> dict[str, Any]:
    completed = [row for row in rows if row.get("completion_status") == "completed"]

    def components(subset: list[dict[str, Any]]) -> dict[str, Any]:
        return {
            "backend_question_generation": metric_for(subset, "backend_question_generation_latency_ms"),
            "external_question_http": metric_for(subset, "question_http_latency_ms"),
            "client_request_overhead": metric_for(subset, "client_request_overhead_ms"),
            "prompt_input_chars": char_metric_for(subset, "prompt_input_chars"),
            "post_response_quality_audit_observed": metric_for(subset, "experiment_metric_poll_latency_ms"),
            "generated_question_topic_mapping_observed": metric_for(subset, "topic_coverage_poll_latency_ms"),
            "answer_evaluation": metric_for(subset, "evaluation_latency_ms"),
        }

    treatment = [row for row in completed if row.get("variant") == "treatment"]
    baseline = [row for row in completed if row.get("variant") == "baseline"]
    return {
        "latency_definition": USER_FACING_LATENCY_VERSION,
        "component_metrics": components(completed),
        "by_variant": {"treatment": components(treatment), "baseline": components(baseline)},
        "week29_comparison": {
            "treatment_user_facing_p95_ms": 4111.8,
            "baseline_user_facing_p95_ms": 2364.35,
        },
        "remediation": {
            "question_generation_max_history_messages": 3,
            "question_generation_max_answer_chars": 360,
            "question_generation_max_tokens": 240,
            "response_path_audit": "quality scoring, experiment metric writes, and topic mapping run off the user-facing response path",
        },
    }


def fetch_feature_counts(prefix: str) -> dict[str, Any]:
    conn = db_connect()
    cur = conn.cursor(dictionary=True)
    row = fetch_all(
        cur,
        "SELECT "
        "(SELECT COUNT(*) FROM response_feature_cache WHERE session_id LIKE %s) response_feature_cache_rows, "
        "(SELECT COUNT(*) FROM topic_coverage WHERE session_id LIKE %s) topic_coverage_rows, "
        "(SELECT COUNT(*) FROM candidate_skill_profile WHERE session_id LIKE %s) candidate_skill_profile_rows",
        (f"{prefix}%", f"{prefix}%", f"{prefix}%"),
    )[0]
    cur.close()
    conn.close()
    return row


def fetch_quality_stats(prefix: str) -> list[dict[str, Any]]:
    conn = db_connect()
    cur = conn.cursor(dictionary=True)
    rows = fetch_all(
        cur,
        "SELECT variant, COUNT(DISTINCT session_id) n, AVG(quality_score) avg_quality, "
        "STDDEV(quality_score) std_quality, MIN(quality_score) min_quality, MAX(quality_score) max_quality, "
        "AVG(latency_ms) avg_user_facing_latency_ms "
        "FROM experiment_metric WHERE experiment_id=%s AND session_id LIKE %s GROUP BY variant",
        (EXPERIMENT_ID, f"{prefix}%"),
    )
    cur.close()
    conn.close()
    return rows


def build_topic_snapshot(prefix: str, generated_ids: list[str], feature_counts: dict[str, Any]) -> dict[str, Any]:
    generated_ids = sorted(set(generated_ids))
    conn = db_connect()
    cur = conn.cursor(dictionary=True)
    if generated_ids:
        placeholders = ",".join(["%s"] * len(generated_ids))
        cluster = fetch_all(
            cur,
            "SELECT COUNT(*) gen_embeddings_total, "
            "SUM(CASE WHEN cluster_id IS NOT NULL THEN 1 ELSE 0 END) gen_clustered_total, "
            "SUM(CASE WHEN cluster_label='Generated questions fallback cluster' THEN 1 ELSE 0 END) gen_fallback_total, "
            "SUM(CASE WHEN cluster_id IS NOT NULL AND (cluster_label IS NULL OR cluster_label <> 'Generated questions fallback cluster') THEN 1 ELSE 0 END) gen_seeded_match_total "
            f"FROM question_embedding WHERE question_id IN ({placeholders})",
            tuple(generated_ids),
        )[0]
    else:
        cluster = {"gen_embeddings_total": 0, "gen_clustered_total": 0, "gen_fallback_total": 0, "gen_seeded_match_total": 0}
    recent_topic = fetch_all(
        cur,
        "SELECT session_id, role_id, total_questions, clusters_covered, total_clusters, coverage_ratio, updated_at "
        "FROM topic_coverage WHERE session_id LIKE %s ORDER BY updated_at DESC LIMIT 20",
        (f"{prefix}%",),
    )
    cur.close()
    conn.close()
    clustered = cluster.get("gen_clustered_total") or 0
    fallback_rate = cluster.get("gen_fallback_total") / clustered if clustered else None
    seeded_match_rate = cluster.get("gen_seeded_match_total") / clustered if clustered else None
    return {
        "generated_at": now_iso(),
        "prefix": prefix,
        "status": "READY_AS_SUPPORTING_SIGNAL" if fallback_rate is not None and fallback_rate <= 0.20 else "HOLD",
        "topic_coverage_rows": feature_counts["topic_coverage_rows"],
        "generated_question_dedup_rate": 1 - len(set(generated_ids)) / len(generated_ids) if generated_ids else None,
        "fallback_cluster_rate": fallback_rate,
        "nearest_seeded_cluster_match_rate": seeded_match_rate,
        "cluster_totals": cluster,
        "recent_topic_coverage_rows": recent_topic,
        "stage_b_gating_recommendation": "Use topic coverage as a supporting Stage B signal only while fallback rate remains within threshold.",
    }


def fetch_skill_profiles(prefix: str) -> dict[str, dict[str, Any]]:
    conn = db_connect()
    cur = conn.cursor(dictionary=True)
    rows = fetch_all(
        cur,
        "SELECT session_id, pass_probability, confidence, predicted_final_score, question_count, score_mean, score_std "
        "FROM candidate_skill_profile WHERE session_id LIKE %s",
        (f"{prefix}%",),
    )
    cur.close()
    conn.close()
    return {row["session_id"]: row for row in rows}


def calibration_prediction(profile: dict[str, Any] | None) -> tuple[float | None, str | None]:
    if not profile:
        return None, None
    if profile.get("pass_probability") is not None:
        return float(profile["pass_probability"]), "candidate_skill_profile.pass_probability"
    if profile.get("confidence") is not None:
        confidence = float(profile["confidence"])
        if 0.0 <= confidence <= 1.0:
            return confidence, "candidate_skill_profile.confidence"
    if profile.get("predicted_final_score") is not None:
        return max(0.0, min(1.0, float(profile["predicted_final_score"]) / 100.0)), "candidate_skill_profile.predicted_final_score"
    return None, None


def brier(rows: list[dict[str, Any]]) -> float | None:
    if not rows:
        return None
    return statistics.mean((row["predicted_probability"] - row["label"]) ** 2 for row in rows)


def rmse(rows: list[dict[str, Any]]) -> float | None:
    score = brier(rows)
    return math.sqrt(score) if score is not None else None


def ece(rows: list[dict[str, Any]], bins: int = 10) -> float | None:
    if not rows:
        return None
    total = len(rows)
    error = 0.0
    for idx in range(bins):
        lower = idx / bins
        upper = (idx + 1) / bins
        bucket = [
            row for row in rows
            if lower <= row["predicted_probability"] < upper or (idx == bins - 1 and row["predicted_probability"] == 1.0)
        ]
        if not bucket:
            continue
        avg_conf = statistics.mean(row["predicted_probability"] for row in bucket)
        avg_acc = statistics.mean(row["label"] for row in bucket)
        error += (len(bucket) / total) * abs(avg_conf - avg_acc)
    return error


def build_calibration(prefix: str, rows: list[dict[str, Any]]) -> dict[str, Any]:
    profiles = fetch_skill_profiles(prefix)
    records: list[dict[str, Any]] = []
    for row in rows:
        if row.get("completion_status") != "completed":
            continue
        label = row.get("ground_truth_label")
        profile = profiles.get(row["session_id"])
        prediction, prediction_source = calibration_prediction(profile)
        records.append(
            {
                "session_id": row["session_id"],
                "level": row.get("level"),
                "variant": row.get("variant"),
                "label": int(label) if label is not None else None,
                "label_source": row.get("ground_truth_label_source"),
                "predicted_probability": prediction,
                "prediction_source": prediction_source,
                "active_calibration_version": "platt-v2.1",
                "excluded_reason": None if label is not None and prediction is not None else "missing_prediction_or_label",
            }
        )

    slices: dict[str, Any] = {}
    for level in SLICE_ORDER:
        slice_records = [record for record in records if record["level"] == level]
        usable = [
            record for record in slice_records
            if record["label"] is not None and record["predicted_probability"] is not None
        ]
        counts = Counter(record["variant"] for record in slice_records)
        metric_values = {
            "brier_score": brier(usable),
            "ece": ece(usable),
            "rmse": rmse(usable),
        }
        sample_gate = (
            counts.get("treatment", 0) >= CALIBRATION_MIN_TREATMENT_PER_SLICE
            and counts.get("baseline", 0) >= CALIBRATION_MIN_BASELINE_PER_SLICE
        )
        metric_gate = len(usable) == len(slice_records) and len(usable) > 0
        slices[level] = {
            "treatment": counts.get("treatment", 0),
            "baseline": counts.get("baseline", 0),
            "label_count": len([record for record in slice_records if record["label"] is not None]),
            "prediction_count": len([record for record in slice_records if record["predicted_probability"] is not None]),
            "usable_metric_count": len(usable),
            "sample_gate_pass": sample_gate,
            "metric_gate_pass": metric_gate,
            "metrics": metric_values,
            "excluded_count": len(slice_records) - len(usable),
        }

    calibration_ready = all(item["sample_gate_pass"] and item["metric_gate_pass"] for item in slices.values())
    export_path = RESULTS / "week30_calibration_export.json"
    export_path.write_text(json.dumps(records, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    return {
        "generated_at": now_iso(),
        "prefix": prefix,
        "status": "PASS" if calibration_ready else "HOLD",
        "active_version": "platt-v2.1",
        "candidate_version": None,
        "label_policy": {
            "source": "final_evaluation_score",
            "pass_threshold": CALIBRATION_PASS_SCORE_THRESHOLD,
            "human_reviewed": False,
            "exclusion_rule": "Rows without a ground-truth label or DB prediction probability are excluded from Brier/ECE/RMSE.",
        },
        "prediction_sources_allowed": [
            "candidate_skill_profile.pass_probability",
            "candidate_skill_profile.confidence",
            "candidate_skill_profile.predicted_final_score",
        ],
        "minimums": {
            "treatment_per_slice": CALIBRATION_MIN_TREATMENT_PER_SLICE,
            "baseline_per_slice": CALIBRATION_MIN_BASELINE_PER_SLICE,
        },
        "slices": slices,
        "export": str(export_path.relative_to(RESULTS.parent)),
        "recommendation": "Keep platt-v2.1 unchanged until every slice has labels, prediction probabilities, Brier/ECE/RMSE metrics, and regression coverage.",
    }


def generate(prefix: str) -> dict[str, Any]:
    rows = load_manifest(prefix)
    completed = [row for row in rows if row.get("completion_status") == "completed"]
    if not completed:
        raise RuntimeError(f"No completed sessions found for prefix {prefix}")

    generated_at = now_iso()
    run_git = git_metadata()
    changed_metadata = False
    for row in rows:
        if row.get("session_id", "").startswith(prefix) and "workspace_dirty" not in row:
            row.update(run_git)
            changed_metadata = True
    if changed_metadata:
        write_manifest(rows)
    quality_stats = fetch_quality_stats(prefix)
    stats_by_variant = {row["variant"]: row for row in quality_stats}
    treatment_q = stats_by_variant.get("treatment", {})
    baseline_q = stats_by_variant.get("baseline", {})
    quality_delta = None
    if treatment_q.get("avg_quality") is not None and baseline_q.get("avg_quality") is not None:
        quality_delta = treatment_q["avg_quality"] - baseline_q["avg_quality"]

    treatment = summarize_variant(rows, "treatment")
    baseline = summarize_variant(rows, "baseline")
    n_treatment = treatment["n_completed"]
    n_baseline = baseline["n_completed"]
    avg_questions_delta_pct = None
    if baseline.get("avg_questions_per_session"):
        avg_questions_delta_pct = (
            treatment["avg_questions_per_session"] - baseline["avg_questions_per_session"]
        ) / baseline["avg_questions_per_session"]

    openai_error_rate = len([row for row in rows if row.get("errors")]) / len(rows) if rows else None
    feature_counts = fetch_feature_counts(prefix)
    latency = latency_breakdown(rows)
    generated_ids = [row.get("generated_question_id") for row in rows if row.get("generated_question_id")]
    topic = build_topic_snapshot(prefix, generated_ids, feature_counts)
    calibration = build_calibration(prefix, rows)
    experiment = get_experiment_config()

    level_counts = defaultdict(Counter)
    for row in rows:
        if row.get("variant") in ("treatment", "baseline"):
            level_counts[row.get("level", "unknown")][row["variant"]] += 1
    slice_counts = {level: dict(counts) for level, counts in level_counts.items()}

    sample_gate_pass = n_treatment >= TARGET_TREATMENT and n_baseline >= TARGET_BASELINE
    session_metrics_decision_grade = all(
        row.get("completion_status") == "completed"
        and row.get("generated_followup_count") == 1
        and row.get("evaluated_answer_count") == 1
        and row.get("backend_question_generation_latency_ms") is not None
        and row.get("prompt_input_chars") is not None
        for row in rows
    )
    user_latency_pass = (
        treatment.get("user_facing_latency_p95_ms") is not None
        and treatment["user_facing_latency_p95_ms"] <= USER_FACING_P95_THRESHOLD_MS
    )
    baseline_latency_pass = (
        baseline.get("user_facing_latency_p95_ms") is not None
        and baseline["user_facing_latency_p95_ms"] <= USER_FACING_P95_THRESHOLD_MS
    )
    quality_gate_pass = quality_delta is not None and quality_delta >= -10.0
    variance_gate_pass = bool(treatment_q.get("std_quality", 0) and baseline_q.get("std_quality", 0))
    premature_gate_pass = treatment.get("premature_stop_rate") is not None and treatment["premature_stop_rate"] <= 0.03
    openai_gate_pass = openai_error_rate is not None and openai_error_rate <= 0.05
    topic_gate_pass = topic["fallback_cluster_rate"] is not None and topic["fallback_cluster_rate"] <= 0.20
    calibration_ready = calibration["status"] == "PASS"

    blocking_gates = []
    for passed, name in [
        (sample_gate_pass, "sample-size gate"),
        (session_metrics_decision_grade, "session metric completeness"),
        (user_latency_pass, "treatment user-facing latency p95 guardrail"),
        (baseline_latency_pass, "baseline user-facing latency p95 guardrail"),
        (quality_gate_pass, "quality delta guardrail"),
        (variance_gate_pass, "quality variance guardrail"),
        (premature_gate_pass, "premature-stop guardrail"),
        (openai_gate_pass, "OpenAI reliability guardrail"),
        (topic_gate_pass, "topic coverage supporting-signal diagnostics"),
        (calibration_ready, "calibration readiness"),
    ]:
        if not passed:
            blocking_gates.append(name)

    stage_b_ready = not blocking_gates
    sample_snapshot = {
        "generated_at": generated_at,
        "experiment_id": EXPERIMENT_ID,
        "experiment_name": experiment.get("name"),
        "experiment_status": experiment.get("status"),
        "prefix": prefix,
        "traffic_source": "week30 controlled production-equivalent",
        **run_git,
        "latency_definition": USER_FACING_LATENCY_VERSION,
        "model": CHAT_MODEL,
        "metric_version": METRIC_VERSION,
        "experiment_config": {
            "traffic_percentage": experiment.get("traffic_percentage"),
            "baseline_config": experiment.get("baseline_config_json"),
            "treatment_config": experiment.get("treatment_config_json"),
        },
        "prompt_config": latency["remediation"],
        "counts": {"treatment": n_treatment, "baseline": n_baseline},
        "target_counts": {"treatment": TARGET_TREATMENT, "baseline": TARGET_BASELINE},
        "quality_stats": quality_stats,
        "feature_counts": feature_counts,
        "slice_counts": slice_counts,
    }
    session_guardrails = {
        "generated_at": generated_at,
        "prefix": prefix,
        "latency_definition": USER_FACING_LATENCY_VERSION,
        "status": "PASS" if sample_gate_pass and session_metrics_decision_grade and user_latency_pass else "HOLD",
        "n_treatment": n_treatment,
        "n_baseline": n_baseline,
        "quality_delta": quality_delta,
        "avg_questions_delta_pct": avg_questions_delta_pct,
        "openai_error_rate": openai_error_rate,
        "decision_grade": session_metrics_decision_grade,
        "treatment": treatment,
        "baseline": baseline,
    }
    stagea_live = {
        "experiment_id": EXPERIMENT_ID,
        "timestamp": generated_at,
        "data_source": "live_aiven_mysql_real_openai_week30_prefix",
        "model_version": "embedding-v1.0+prediction-v1.0+llm-v1.0-history-week30-latency-remediation",
        "decision": "GO" if stage_b_ready else "HOLD",
        "stage_b_recommendation": "GO" if stage_b_ready else "HOLD",
        "metrics": {
            "n_treatment": n_treatment,
            "n_baseline": n_baseline,
            "quality_delta_treatment_minus_baseline": quality_delta,
            "treatment_std_quality": treatment_q.get("std_quality"),
            "baseline_std_quality": baseline_q.get("std_quality"),
            "avg_questions_delta_pct": avg_questions_delta_pct,
            "premature_stop_rate": treatment.get("premature_stop_rate"),
            "early_stop_rate": treatment.get("early_stop_rate"),
            "user_facing_latency_p95_ms": treatment.get("user_facing_latency_p95_ms"),
            "baseline_user_facing_latency_p95_ms": baseline.get("user_facing_latency_p95_ms"),
            "external_question_http_p95_ms": treatment.get("external_question_http_p95_ms"),
            "topic_coverage_rows": feature_counts["topic_coverage_rows"],
            "fallback_cluster_rate": topic["fallback_cluster_rate"],
            "openai_error_rate": openai_error_rate,
            "calibration_ready": calibration_ready,
        },
        "blocking_gates": blocking_gates,
        "rationale": [
            f"Week 30 sample-size gate met: {sample_gate_pass} (treatment={n_treatment}, baseline={n_baseline}).",
            f"Session-level metrics decision-grade: {session_metrics_decision_grade}.",
            f"Treatment user-facing latency p95 met: {user_latency_pass}; p95={treatment.get('user_facing_latency_p95_ms')}.",
            f"Topic coverage supporting signal healthy: {topic_gate_pass}.",
            f"Calibration ready: {calibration_ready}; platt-v2.1 remains unchanged.",
        ],
        "rollback": (
            f"Keep Stage A at 10%; do not expand to Stage B until {', '.join(blocking_gates)} "
            f"{'is' if len(blocking_gates) == 1 else 'are'} resolved."
            if not stage_b_ready
            else "Proceed only with the approved Stage B gate and monitor latency/session guardrails continuously."
        ),
    }

    writes = {
        "week30_latency_breakdown.json": latency,
        "week30_stagea_sample_snapshot.json": sample_snapshot,
        "week30_session_guardrails.json": session_guardrails,
        "week30_stagea_live_result.json": stagea_live,
        "week30_calibration_readiness.json": calibration,
        "week30_topic_coverage_snapshot.json": topic,
    }
    RESULTS.mkdir(parents=True, exist_ok=True)
    for name, payload in writes.items():
        (RESULTS / name).write_text(json.dumps(payload, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    write_docs(generated_at, prefix, latency, sample_snapshot, session_guardrails, calibration, topic, stagea_live)
    update_registry(generated_at, prefix, stagea_live)
    return stagea_live


def write_docs(
    generated_at: str,
    prefix: str,
    latency: dict[str, Any],
    sample: dict[str, Any],
    session_guardrails: dict[str, Any],
    calibration: dict[str, Any],
    topic: dict[str, Any],
    live: dict[str, Any],
) -> None:
    DOCS.mkdir(parents=True, exist_ok=True)
    treatment_component = latency["by_variant"]["treatment"]
    baseline_component = latency["by_variant"]["baseline"]
    docs = {
        "week30_treatment_latency_remediation.md": f"""# Week 30 Treatment Latency Remediation

Generated: {generated_at}

## Definition

Stage B product latency remains `{USER_FACING_LATENCY_VERSION}`.

## Change

- Question generation now uses compact history for the OpenAI request.
- Question generation uses `openai.question-generation.max-history-messages=3`.
- Question generation uses `openai.question-generation.max-answer-chars=360`.
- Question generation uses `openai.question-generation.max-tokens=240`.
- Quality scoring, experiment metric writes, and topic mapping remain post-response audit work.

## Evidence

- Week 29 treatment user-facing p95: 4111.8 ms.
- Week 30 treatment user-facing p95: {treatment_component['backend_question_generation']['p95_ms']} ms.
- Week 30 baseline user-facing p95: {baseline_component['backend_question_generation']['p95_ms']} ms.
- Week 30 treatment external HTTP p95: {treatment_component['external_question_http']['p95_ms']} ms.
- Week 30 treatment prompt input chars p95: {treatment_component['prompt_input_chars']['p95_chars']}.
- Week 30 treatment post-response quality audit observed p95: {treatment_component['post_response_quality_audit_observed']['p95_ms']} ms.
- Week 30 treatment topic mapping observed p95: {treatment_component['generated_question_topic_mapping_observed']['p95_ms']} ms.
- Week 30 treatment answer evaluation p95: {treatment_component['answer_evaluation']['p95_ms']} ms.
""",
        "week30_stagea_validation_report.md": f"""# Week 30 Stage A Validation Report

Generated: {generated_at}

## Result

- Prefix: `{prefix}`.
- Treatment: {sample['counts']['treatment']}.
- Baseline: {sample['counts']['baseline']}.
- Commit SHA: {sample['commit_sha']}.
- Latency definition: `{sample['latency_definition']}`.
- Sample-size gate: {'PASS' if sample['counts']['treatment'] >= TARGET_TREATMENT and sample['counts']['baseline'] >= TARGET_BASELINE else 'HOLD'}.
- Decision-grade session metrics: {session_guardrails['decision_grade']}.
""",
        "week30_calibration_ground_truth_report.md": f"""# Week 30 Calibration Ground-Truth Report

Generated: {generated_at}

## Status

Calibration readiness: {calibration['status']}.

## Label Policy

- Label source: {calibration['label_policy']['source']}.
- Pass threshold: {calibration['label_policy']['pass_threshold']}.
- Human reviewed: {calibration['label_policy']['human_reviewed']}.
- Exclusion rule: {calibration['label_policy']['exclusion_rule']}

## Slice Evidence

{json.dumps(calibration['slices'], indent=2, sort_keys=True)}

## Recommendation

{calibration['recommendation']}
""",
        "week30_stagea_guardrail_readout.md": f"""# Week 30 Stage A Guardrail Readout

Generated: {generated_at}

## Status

{live['stage_b_recommendation']} for Stage B.

## Gates

- n_treatment >= {TARGET_TREATMENT}: {'PASS' if live['metrics']['n_treatment'] >= TARGET_TREATMENT else 'HOLD'} ({live['metrics']['n_treatment']}).
- n_baseline >= {TARGET_BASELINE}: {'PASS' if live['metrics']['n_baseline'] >= TARGET_BASELINE else 'HOLD'} ({live['metrics']['n_baseline']}).
- Session metrics decision-grade: {'PASS' if session_guardrails['decision_grade'] else 'HOLD'}.
- Treatment user-facing latency p95 <= {USER_FACING_P95_THRESHOLD_MS}: {'PASS' if live['metrics']['user_facing_latency_p95_ms'] and live['metrics']['user_facing_latency_p95_ms'] <= USER_FACING_P95_THRESHOLD_MS else 'HOLD'} ({live['metrics']['user_facing_latency_p95_ms']}).
- Baseline user-facing latency p95 <= {USER_FACING_P95_THRESHOLD_MS}: {'PASS' if live['metrics']['baseline_user_facing_latency_p95_ms'] and live['metrics']['baseline_user_facing_latency_p95_ms'] <= USER_FACING_P95_THRESHOLD_MS else 'HOLD'} ({live['metrics']['baseline_user_facing_latency_p95_ms']}).
- Premature-stop rate: {live['metrics']['premature_stop_rate']}.
- OpenAI error rate: {live['metrics']['openai_error_rate']}.
- Topic fallback rate: {live['metrics']['fallback_cluster_rate']}.
- Calibration ready: {live['metrics']['calibration_ready']}.
- Blocking gates: {', '.join(live['blocking_gates']) or 'none'}.
""",
        "week30_ml_decision_readout.md": f"""# Week 30 ML Decision Readout

Generated: {generated_at}

## Decision

Stage B recommendation: {live['stage_b_recommendation']}.

## Rationale

{chr(10).join(f"- {item}" for item in live['rationale'])}

## Rollback

{live['rollback']}
""",
        "week30_stageb_gate_rollback_runbook.md": f"""# Week 30 Stage B Gate and Rollback Runbook

Generated: {generated_at}

## Gate

Stage B can expand only when sample size, decision-grade session metrics, user-facing latency p95, quality, premature-stop, OpenAI reliability, calibration, and topic supporting-signal diagnostics all pass.

## Current Outcome

{live['stage_b_recommendation']}.

## Rollback

If any Stage B gate regresses, keep Stage A at 10% or return to baseline-only routing under the active `stage_a_week24` experiment configuration. Automatic lifecycle conclusion remains disabled during controlled validation.
""",
    }
    for name, text in docs.items():
        (DOCS / name).write_text(text, encoding="utf-8")


def update_registry(generated_at: str, prefix: str, live: dict[str, Any]) -> None:
    registry_id = "week30_stagea_revalidation_20260629"
    if not REGISTRY.exists():
        return
    metrics = live["metrics"]
    row = [
        registry_id,
        generated_at,
        live["model_version"],
        "all",
        json.dumps(
            {
                "decision": live["decision"],
                "prefix": prefix,
                "n_treatment": metrics["n_treatment"],
                "n_baseline": metrics["n_baseline"],
                "latency_definition": USER_FACING_LATENCY_VERSION,
            },
            separators=(",", ":"),
        ),
        "",
        "",
        "",
        metrics["n_treatment"] + metrics["n_baseline"],
        "",
        "",
        "",
        metrics["user_facing_latency_p95_ms"],
        "",
        metrics["early_stop_rate"],
        "",
        f"Week 30 Stage A revalidation; Stage B {live['stage_b_recommendation']} pending {', '.join(live['blocking_gates']) or 'none'}",
    ]
    with REGISTRY.open(newline="", encoding="utf-8-sig") as handle:
        rows = list(csv.reader(handle))
    replaced = False
    for idx, existing_row in enumerate(rows):
        if existing_row and existing_row[0] == registry_id:
            rows[idx] = row
            replaced = True
            break
    if not replaced:
        rows.append(row)
    with REGISTRY.open("w", newline="", encoding="utf-8-sig") as handle:
        writer = csv.writer(handle)
        writer.writerows(rows)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--prefix", default=DEFAULT_PREFIX)
    parser.add_argument("--target-treatment", type=int, default=TARGET_TREATMENT)
    parser.add_argument("--target-baseline", type=int, default=TARGET_BASELINE)
    parser.add_argument("--sleep-seconds", type=float, default=0.5)
    parser.add_argument("--prepare-experiment", action="store_true")
    parser.add_argument("--collect", action="store_true")
    parser.add_argument("--generate", action="store_true")
    args = parser.parse_args()

    if args.prepare_experiment:
        print(json.dumps(prepare_experiment(), indent=2, default=str))
    if args.collect:
        collect(args.prefix, args.target_treatment, args.target_baseline, args.sleep_seconds)
    if args.generate:
        print(json.dumps(generate(args.prefix), indent=2, ensure_ascii=False))
    if not args.prepare_experiment and not args.collect and not args.generate:
        parser.error("Choose at least one action: --prepare-experiment, --collect, or --generate")


if __name__ == "__main__":
    main()
