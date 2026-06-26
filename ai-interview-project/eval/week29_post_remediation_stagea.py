"""Week 29 post-remediation Stage A collection and readout generation.

This script reads backend, DB, and OpenAI configuration from environment
variables. It intentionally contains no credentials.
"""

from __future__ import annotations

import argparse
import csv
import json
import math
import statistics
import time
from collections import Counter, defaultdict
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

import requests

from week28_repaired_stagea import (
    BACKEND_URL,
    CANNED_ANSWERS,
    CHAT_MODEL,
    DEFAULT_PREFIX as WEEK28_PREFIX,
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


DEFAULT_PREFIX = "w29-"
METRIC_VERSION = "llm-v1.0-history-post-remediation"
USER_FACING_LATENCY_VERSION = "user_facing_question_generation_v1"
TARGET_TREATMENT = 80
TARGET_BASELINE = 50
USER_FACING_P95_THRESHOLD_MS = 3000
CALIBRATION_MIN_TREATMENT_PER_SLICE = 25
CALIBRATION_MIN_BASELINE_PER_SLICE = 15
SLICE_ORDER = ["junior", "mid", "senior"]
ROLE_BY_LEVEL = {
    "junior": "backend_java",
    "mid": "backend_java",
    "senior": "backend_java",
}


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


def create_session(session_id: str, role_id: str, level: str) -> None:
    response = requests.post(
        f"{BACKEND_URL}/api/sessions",
        json={"sessionId": session_id, "roleId": role_id, "level": level, "skills": []},
        timeout=30,
    )
    response.raise_for_status()


def seed_history(session_id: str, history: list[dict[str, str]]) -> None:
    for idx, turn in enumerate(history, 1):
        response = requests.post(
            f"{BACKEND_URL}/api/sessions/{session_id}/answer",
            json={
                "questionId": f"seed-{session_id}-{idx}",
                "questionText": turn["question"],
                "answerText": turn["answer"],
            },
            timeout=30,
        )
        response.raise_for_status()


def generate_question(session_id: str, role_id: str, level: str) -> tuple[dict[str, Any], int]:
    start = time.time()
    response = requests.post(
        f"{BACKEND_URL}/api/llm/question-generate",
        json={"sessionId": session_id, "roleId": role_id, "level": level},
        timeout=90,
    )
    external_latency_ms = int((time.time() - start) * 1000)
    response.raise_for_status()
    return response.json(), external_latency_ms


def evaluate_answer(session_id: str, question: str, role_id: str, level: str) -> tuple[dict[str, Any], int]:
    start = time.time()
    response = requests.post(
        f"{BACKEND_URL}/api/llm/eval",
        json={
            "sessionId": session_id,
            "question": question,
            "answer": CANNED_ANSWERS.get(level, CANNED_ANSWERS["mid"]),
            "roleId": role_id,
            "level": level,
        },
        timeout=90,
    )
    latency_ms = int((time.time() - start) * 1000)
    response.raise_for_status()
    return response.json(), latency_ms


def wait_for_experiment_metric(session_id: str, timeout_seconds: float = 45.0) -> dict[str, Any] | None:
    start = time.time()
    while time.time() - start < timeout_seconds:
        conn = db_connect()
        cur = conn.cursor(dictionary=True)
        cur.execute(
            "SELECT variant, quality_score, latency_ms, token_count, created_at "
            "FROM experiment_metric WHERE experiment_id=%s AND session_id=%s "
            "ORDER BY id DESC LIMIT 1",
            (EXPERIMENT_ID, session_id),
        )
        row = cur.fetchone()
        cur.close()
        conn.close()
        if row:
            if hasattr(row.get("created_at"), "isoformat"):
                row["created_at"] = row["created_at"].isoformat()
            row["metric_poll_latency_ms"] = int((time.time() - start) * 1000)
            return row
        time.sleep(0.5)
    return None


def wait_for_topic_coverage(session_id: str, timeout_seconds: float = 30.0) -> dict[str, Any] | None:
    start = time.time()
    while time.time() - start < timeout_seconds:
        conn = db_connect()
        cur = conn.cursor(dictionary=True)
        cur.execute(
            "SELECT session_id, role_id, total_questions, clusters_covered, total_clusters, coverage_ratio, updated_at "
            "FROM topic_coverage WHERE session_id=%s ORDER BY updated_at DESC LIMIT 1",
            (session_id,),
        )
        row = cur.fetchone()
        cur.close()
        conn.close()
        if row:
            if hasattr(row.get("updated_at"), "isoformat"):
                row["updated_at"] = row["updated_at"].isoformat()
            row["topic_coverage_poll_latency_ms"] = int((time.time() - start) * 1000)
            return row
        time.sleep(0.5)
    return None


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
                    "experiment_id": q_data.get("experimentId"),
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
            if topic_row:
                record["topic_coverage_poll_latency_ms"] = topic_row.get("topic_coverage_poll_latency_ms")
            else:
                record["topic_coverage_poll_latency_ms"] = None

            eval_data, eval_latency = evaluate_answer(session_id, question, role_id, level)
            score = eval_data.get("score")
            record.update(
                {
                    "evaluated_answer_count": 1,
                    "final_evaluation_score": score,
                    "latest_evaluation_score": score,
                    "evaluation_latency_ms": eval_latency,
                    "completion_status": "completed",
                }
            )
            print(
                f"[{idx}/{len(planned)}] {session_id} {variant} {level} "
                f"q_http={q_external_latency}ms backend_q={backend_latency}ms "
                f"audit_poll={record.get('experiment_metric_poll_latency_ms')}ms "
                f"eval={eval_latency}ms score={score}"
            )
        except Exception as exc:
            record["completion_status"] = "error"
            record["errors"].append(str(exc))
            print(f"[ERROR] {session_id}: {exc}")
        new_rows.append(record)
        if sleep_seconds > 0:
            time.sleep(sleep_seconds)

    write_manifest(prefix, manifest + new_rows)
    return new_rows


def manifest_path() -> Path:
    return RESULTS / "week29_session_manifest.json"


def load_manifest(prefix: str) -> list[dict[str, Any]]:
    path = manifest_path()
    if not path.exists():
        return []
    rows = json.loads(path.read_text(encoding="utf-8"))
    return [row for row in rows if row.get("session_id", "").startswith(prefix)]


def write_manifest(prefix: str, rows: list[dict[str, Any]]) -> None:
    path = manifest_path()
    existing_by_session: dict[str, dict[str, Any]] = {}
    if path.exists():
        existing = json.loads(path.read_text(encoding="utf-8"))
        existing_by_session = {row["session_id"]: row for row in existing if row.get("session_id")}
    for row in rows:
        existing_by_session[row["session_id"]] = row
    merged = sorted(existing_by_session.values(), key=lambda row: row.get("session_id", ""))
    RESULTS.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(merged, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def summarize_variant(rows: list[dict[str, Any]], variant: str) -> dict[str, Any]:
    subset = [row for row in rows if row.get("variant") == variant]
    completed = [row for row in subset if row.get("completion_status") == "completed"]

    def values(field: str) -> list[float]:
        return [float(row[field]) for row in completed if row.get(field) is not None]

    total_questions = values("total_questions_asked")
    backend_q = values("backend_question_generation_latency_ms")
    q_http = values("question_http_latency_ms")
    eval_latency = values("evaluation_latency_ms")
    total_turn = [q + e for q, e in zip(q_http, eval_latency)]
    return {
        "n_sessions": len(subset),
        "n_completed": len(completed),
        "avg_questions_per_session": statistics.mean(total_questions) if total_questions else None,
        "early_stop_rate": (
            len([row for row in completed if row.get("early_stop_triggered")]) / len(completed)
            if completed
            else None
        ),
        "premature_stop_rate": (
            len([row for row in completed if row.get("premature_stop")]) / len(completed)
            if completed
            else None
        ),
        "session_completion_rate": len(completed) / len(subset) if subset else None,
        "user_facing_latency_p50_ms": percentile(backend_q, 0.50),
        "user_facing_latency_p95_ms": percentile(backend_q, 0.95),
        "external_question_http_p50_ms": percentile(q_http, 0.50),
        "external_question_http_p95_ms": percentile(q_http, 0.95),
        "evaluation_latency_p50_ms": percentile(eval_latency, 0.50),
        "evaluation_latency_p95_ms": percentile(eval_latency, 0.95),
        "end_to_end_turn_latency_p95_ms": percentile(total_turn, 0.95),
        "final_score_avg": (
            statistics.mean(values("final_evaluation_score"))
            if values("final_evaluation_score")
            else None
        ),
    }


def latency_breakdown(rows: list[dict[str, Any]]) -> dict[str, Any]:
    completed = [row for row in rows if row.get("completion_status") == "completed"]

    def metric_for(subset: list[dict[str, Any]], field: str) -> dict[str, Any]:
        vals = [float(row[field]) for row in subset if row.get(field) is not None]
        return {
            "n": len(vals),
            "p50_ms": percentile(vals, 0.50),
            "p95_ms": percentile(vals, 0.95),
            "avg_ms": statistics.mean(vals) if vals else None,
        }

    def components_for(subset: list[dict[str, Any]]) -> dict[str, Any]:
        return {
            "backend_question_generation": metric_for(subset, "backend_question_generation_latency_ms"),
            "external_question_http": metric_for(subset, "question_http_latency_ms"),
            "client_request_overhead": metric_for(subset, "client_request_overhead_ms"),
            "post_response_quality_audit_observed": metric_for(subset, "experiment_metric_poll_latency_ms"),
            "generated_question_topic_mapping_observed": metric_for(subset, "topic_coverage_poll_latency_ms"),
            "answer_evaluation": metric_for(subset, "evaluation_latency_ms"),
        }

    treatment = [row for row in completed if row.get("variant") == "treatment"]
    baseline = [row for row in completed if row.get("variant") == "baseline"]
    return {
        "latency_definition": USER_FACING_LATENCY_VERSION,
        "component_metrics": components_for(completed),
        "by_variant": {
            "treatment": components_for(treatment),
            "baseline": components_for(baseline),
        },
        "notes": [
            "Stage B product latency uses backend_question_generation_latency_ms from the question-generate response.",
            "Question quality scoring, experiment_metric writes, and topic mapping run after the response and are tracked separately.",
            "answer_evaluation remains part of end-to-end interview-turn observability, not the user-facing question-generation guardrail.",
        ],
    }


def generate(prefix: str) -> dict[str, Any]:
    rows = load_manifest(prefix)
    completed = [row for row in rows if row.get("completion_status") == "completed"]
    if not completed:
        raise RuntimeError(f"No completed sessions found for prefix {prefix}")

    conn = db_connect()
    cur = conn.cursor(dictionary=True)
    params = (EXPERIMENT_ID, f"{prefix}%")
    experiment = fetch_all(cur, "SELECT * FROM experiment WHERE id=%s", (EXPERIMENT_ID,))[0]
    quality_stats = fetch_all(
        cur,
        "SELECT variant, COUNT(DISTINCT session_id) n, AVG(quality_score) avg_quality, "
        "STDDEV(quality_score) std_quality, MIN(quality_score) min_quality, MAX(quality_score) max_quality, "
        "AVG(latency_ms) avg_user_facing_latency_ms "
        "FROM experiment_metric WHERE experiment_id=%s AND session_id LIKE %s GROUP BY variant",
        params,
    )
    feature_counts = fetch_all(
        cur,
        "SELECT "
        "(SELECT COUNT(*) FROM response_feature_cache WHERE session_id LIKE %s) response_feature_cache_rows, "
        "(SELECT COUNT(*) FROM topic_coverage WHERE session_id LIKE %s) topic_coverage_rows, "
        "(SELECT COUNT(*) FROM candidate_skill_profile WHERE session_id LIKE %s) candidate_skill_profile_rows",
        (f"{prefix}%", f"{prefix}%", f"{prefix}%"),
    )[0]
    cur.close()
    conn.close()

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

    level_counts = defaultdict(Counter)
    for row in rows:
        if row.get("variant") in ("treatment", "baseline"):
            level_counts[row.get("level", "unknown")][row["variant"]] += 1
    slice_counts = {level: dict(counts) for level, counts in level_counts.items()}
    calibration_slices = {}
    for level in SLICE_ORDER:
        counts = level_counts[level]
        calibration_slices[level] = {
            "treatment": counts.get("treatment", 0),
            "baseline": counts.get("baseline", 0),
            "min_treatment": CALIBRATION_MIN_TREATMENT_PER_SLICE,
            "min_baseline": CALIBRATION_MIN_BASELINE_PER_SLICE,
            "sample_gate_pass": (
                counts.get("treatment", 0) >= CALIBRATION_MIN_TREATMENT_PER_SLICE
                and counts.get("baseline", 0) >= CALIBRATION_MIN_BASELINE_PER_SLICE
            ),
            "metric_gate_pass": False,
            "metric_gap": "No Brier/ECE/RMSE ground-truth calibration labels were produced by this collection path.",
        }
    calibration_ready = all(item["sample_gate_pass"] and item["metric_gate_pass"] for item in calibration_slices.values())

    generated_ids = [row.get("generated_question_id") for row in rows if row.get("generated_question_id")]
    unique_generated = len(set(generated_ids))
    dedup_rate = 1 - unique_generated / len(generated_ids) if generated_ids else None
    topic_snapshot = build_topic_snapshot(prefix, generated_ids, feature_counts)
    latency = latency_breakdown(rows)

    sample_gate_pass = n_treatment >= TARGET_TREATMENT and n_baseline >= TARGET_BASELINE
    user_latency_pass = (
        treatment.get("user_facing_latency_p95_ms") is not None
        and treatment["user_facing_latency_p95_ms"] <= USER_FACING_P95_THRESHOLD_MS
    )
    session_metrics_decision_grade = all(
        row.get("completion_status") == "completed"
        and row.get("generated_followup_count") == 1
        and row.get("evaluated_answer_count") == 1
        and row.get("backend_question_generation_latency_ms") is not None
        for row in rows
    )
    quality_gate_pass = quality_delta is not None and quality_delta >= -10.0
    variance_gate_pass = bool(treatment_q.get("std_quality", 0) and baseline_q.get("std_quality", 0))
    premature_gate_pass = treatment.get("premature_stop_rate") is not None and treatment["premature_stop_rate"] <= 0.03
    openai_gate_pass = openai_error_rate is not None and openai_error_rate <= 0.05
    topic_gate_pass = topic_snapshot["fallback_cluster_rate"] is not None and topic_snapshot["fallback_cluster_rate"] <= 0.20

    blocking_gates = []
    for passed, name in [
        (sample_gate_pass, "sample-size gate"),
        (session_metrics_decision_grade, "session metric completeness"),
        (user_latency_pass, "user-facing latency p95 guardrail"),
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
    generated_at = now_iso()
    sample_snapshot = {
        "generated_at": generated_at,
        "experiment_id": EXPERIMENT_ID,
        "experiment_name": experiment["name"],
        "experiment_status": experiment["status"],
        "prefix": prefix,
        "traffic_source": "post-remediation controlled production-equivalent",
        "model": CHAT_MODEL,
        "metric_version": METRIC_VERSION,
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
    calibration = {
        "generated_at": generated_at,
        "prefix": prefix,
        "status": "PASS" if calibration_ready else "HOLD",
        "active_version": "platt-v2.1",
        "candidate_version": None,
        "minimums": {
            "treatment_per_slice": CALIBRATION_MIN_TREATMENT_PER_SLICE,
            "baseline_per_slice": CALIBRATION_MIN_BASELINE_PER_SLICE,
        },
        "slices": calibration_slices,
        "recommendation": "Keep platt-v2.1 unchanged until Brier/ECE/RMSE labels and regression coverage exist for each slice.",
    }
    stagea_live = {
        "experiment_id": EXPERIMENT_ID,
        "timestamp": generated_at,
        "data_source": "live_aiven_mysql_real_openai_week29_post_remediation_prefix",
        "model_version": "embedding-v1.0+prediction-v1.0+llm-v1.0-history-post-remediation",
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
            "external_question_http_p95_ms": treatment.get("external_question_http_p95_ms"),
            "topic_coverage_rows": feature_counts["topic_coverage_rows"],
            "fallback_cluster_rate": topic_snapshot["fallback_cluster_rate"],
            "openai_error_rate": openai_error_rate,
            "calibration_ready": calibration_ready,
        },
        "blocking_gates": blocking_gates,
        "rationale": [
            f"Post-remediation sample-size gate met: {sample_gate_pass} (treatment={n_treatment}, baseline={n_baseline}).",
            f"Session-level metrics decision-grade: {session_metrics_decision_grade}.",
            f"User-facing latency p95 met: {user_latency_pass}; treatment p95={treatment.get('user_facing_latency_p95_ms')}.",
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
        "week29_latency_breakdown.json": latency,
        "week29_stagea_sample_snapshot.json": sample_snapshot,
        "week29_session_guardrails.json": session_guardrails,
        "week29_stagea_live_result.json": stagea_live,
        "week29_calibration_readiness.json": calibration,
        "week29_topic_coverage_snapshot.json": topic_snapshot,
    }
    RESULTS.mkdir(parents=True, exist_ok=True)
    for name, payload in writes.items():
        (RESULTS / name).write_text(json.dumps(payload, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    write_docs(generated_at, prefix, latency, sample_snapshot, session_guardrails, calibration, topic_snapshot, stagea_live)
    update_registry(generated_at, prefix, stagea_live)
    return stagea_live


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
        cluster = {
            "gen_embeddings_total": 0,
            "gen_clustered_total": 0,
            "gen_fallback_total": 0,
            "gen_seeded_match_total": 0,
        }
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
    dedup_rate = 1 - len(set(generated_ids)) / len(generated_ids) if generated_ids else None
    return {
        "generated_at": now_iso(),
        "prefix": prefix,
        "status": "READY_AS_SUPPORTING_SIGNAL" if fallback_rate is not None and fallback_rate <= 0.20 else "HOLD",
        "topic_coverage_rows": feature_counts["topic_coverage_rows"],
        "generated_question_dedup_rate": dedup_rate,
        "fallback_cluster_rate": fallback_rate,
        "nearest_seeded_cluster_match_rate": seeded_match_rate,
        "cluster_totals": cluster,
        "recent_topic_coverage_rows": recent_topic,
        "stage_b_gating_recommendation": "Use topic coverage as a supporting Stage B signal only while fallback rate remains within threshold.",
    }


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
    component = latency["component_metrics"]
    treatment_component = latency["by_variant"]["treatment"]
    baseline_component = latency["by_variant"]["baseline"]
    docs = {
        "week29_latency_attribution_remediation.md": f"""# Week 29 Latency Attribution and Remediation

Generated: {generated_at}

## Definition

Stage B product latency now uses `{USER_FACING_LATENCY_VERSION}`.

## Remediation

- POST `/api/llm/question-generate` returns after user-facing question generation.
- Question quality scoring, experiment metric writes, and generated-question topic mapping run after the response.
- `experiment_metric.latency_ms` records user-facing question-generation latency for the generated question.

## Component Breakdown

- overall backend question-generation p95: {component['backend_question_generation']['p95_ms']}
- treatment backend question-generation p95: {treatment_component['backend_question_generation']['p95_ms']}
- baseline backend question-generation p95: {baseline_component['backend_question_generation']['p95_ms']}
- treatment external question HTTP p95: {treatment_component['external_question_http']['p95_ms']}
- treatment post-response quality audit observed p95: {treatment_component['post_response_quality_audit_observed']['p95_ms']}
- treatment topic mapping observed p95: {treatment_component['generated_question_topic_mapping_observed']['p95_ms']}
- treatment answer evaluation p95: {treatment_component['answer_evaluation']['p95_ms']}
""",
        "week29_stagea_post_remediation_collection_report.md": f"""# Week 29 Post-Remediation Stage A Collection Report

Generated: {generated_at}

## Result

- Prefix: `{prefix}`.
- Treatment: {sample['counts']['treatment']}.
- Baseline: {sample['counts']['baseline']}.
- Model: {sample['model']}.
- Metric version: {sample['metric_version']}.
- Sample-size gate: {'PASS' if sample['counts']['treatment'] >= TARGET_TREATMENT and sample['counts']['baseline'] >= TARGET_BASELINE else 'HOLD'}.
""",
        "week29_calibration_slice_evidence_report.md": f"""# Week 29 Calibration Slice Evidence Report

Generated: {generated_at}

## Recommendation

Calibration readiness: {calibration['status']}.

## Evidence

- Slice counts: {json.dumps(sample['slice_counts'], sort_keys=True)}.
- Active calibration version: {calibration['active_version']}.
- Recommendation: {calibration['recommendation']}
""",
        "week29_stagea_guardrail_readout.md": f"""# Week 29 Stage A Guardrail Readout

Generated: {generated_at}

## Status

{live['stage_b_recommendation']} for Stage B.

## Gates

- n_treatment >= {TARGET_TREATMENT}: {'PASS' if live['metrics']['n_treatment'] >= TARGET_TREATMENT else 'HOLD'} ({live['metrics']['n_treatment']}).
- n_baseline >= {TARGET_BASELINE}: {'PASS' if live['metrics']['n_baseline'] >= TARGET_BASELINE else 'HOLD'} ({live['metrics']['n_baseline']}).
- Session metrics decision-grade: {'PASS' if session_guardrails['decision_grade'] else 'HOLD'}.
- User-facing latency p95 <= {USER_FACING_P95_THRESHOLD_MS}: {'PASS' if live['metrics']['user_facing_latency_p95_ms'] and live['metrics']['user_facing_latency_p95_ms'] <= USER_FACING_P95_THRESHOLD_MS else 'HOLD'} ({live['metrics']['user_facing_latency_p95_ms']}).
- Premature-stop rate: {live['metrics']['premature_stop_rate']}.
- OpenAI error rate: {live['metrics']['openai_error_rate']}.
- Topic fallback rate: {live['metrics']['fallback_cluster_rate']}.
- Calibration ready: {live['metrics']['calibration_ready']}.
- Blocking gates: {', '.join(live['blocking_gates']) or 'none'}.
""",
        "week29_ml_decision_readout.md": f"""# Week 29 ML Decision Readout

Generated: {generated_at}

## Decision

Stage B recommendation: {live['stage_b_recommendation']}.

## Rationale

{chr(10).join(f"- {item}" for item in live['rationale'])}

## Rollback

{live['rollback']}
""",
        "week29_stageb_gate_rollback_runbook.md": f"""# Week 29 Stage B Gate and Rollback Runbook

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
    registry_id = "week29_post_remediation_stagea_20260624"
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
        f"Week 29 post-remediation Stage A collection; Stage B {live['stage_b_recommendation']} pending {', '.join(live['blocking_gates']) or 'none'}",
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
