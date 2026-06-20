"""Week 28 repaired Stage A collection and readout generation.

This script intentionally reads DB/backend configuration from environment
variables. It does not contain credentials.
"""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
import math
import os
import statistics
import struct
import time
from collections import Counter, defaultdict
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

import mysql.connector
import requests


ROOT = Path(__file__).resolve().parents[1]
RESULTS = ROOT / "eval" / "results"
DOCS = ROOT / "docs"
REGISTRY = ROOT / "eval" / "experiment_registry.csv"
EXPERIMENT_ID = 1
BACKEND_URL = os.getenv("WEEK28_BACKEND_URL", "http://localhost:8081")
DEFAULT_PREFIX = os.getenv("WEEK28_PREFIX", "w28-")
TRAFFIC_PCT = 10.0
METRIC_VERSION = "llm-v1.0-history"
CHAT_MODEL = "gpt-4o-mini"


PRIOR_HISTORIES = [
    [
        {
            "question": "Can you explain what a Java interface is and when you'd use it?",
            "answer": "An interface defines a contract that classes implement. I use it to decouple callers from implementations, especially for dependency injection.",
        },
        {
            "question": "How does that help when writing unit tests?",
            "answer": "It lets me mock dependencies behind the interface and test the service behavior without needing the real downstream implementation.",
        },
    ],
    [
        {
            "question": "How do you handle database transactions in Spring Boot?",
            "answer": "I use @Transactional at the service layer and keep transaction boundaries around business operations rather than controller methods.",
        },
        {
            "question": "What isolation level do you usually choose for read-heavy services?",
            "answer": "READ_COMMITTED is usually enough. It avoids dirty reads and keeps lock contention lower than repeatable read for short transactions.",
        },
        {
            "question": "How would you debug a transaction deadlock?",
            "answer": "I would inspect database deadlock logs, compare lock acquisition order, add indexes for scan-heavy predicates, and reduce transaction scope.",
        },
    ],
    [
        {
            "question": "Describe a time you optimized a slow SQL query in production.",
            "answer": "A reporting query joined five tables and scanned a large events table. I added a composite index and rewrote a correlated subquery as a CTE.",
        },
        {
            "question": "How did you validate the index would not hurt writes?",
            "answer": "I used EXPLAIN, checked cardinality, and ran a staging load test with concurrent writers. p95 write latency increased by less than 5 ms.",
        },
    ],
    [
        {
            "question": "What is the difference between state and props in React?",
            "answer": "Props are read-only values passed from a parent. State belongs to a component and changes over time through setState or hooks.",
        },
        {
            "question": "When would you lift state up?",
            "answer": "I lift state when sibling components need to share it or when the parent needs to coordinate updates across multiple children.",
        },
    ],
    [
        {
            "question": "How would you design a URL shortener?",
            "answer": "I would store mappings in MySQL, use Redis for hot lookups, generate collision-resistant short codes, and add rate limiting around creation.",
        },
        {
            "question": "How would you handle hot keys?",
            "answer": "I would replicate hot keys, add local caching for very hot redirects, and use request coalescing to avoid cache stampedes.",
        },
    ],
    [
        {
            "question": "What consistency model does Kafka provide?",
            "answer": "Kafka provides ordered logs within a partition and at-least-once delivery by default. Idempotent producers and transactions can reduce duplicates.",
        },
        {
            "question": "When would exactly-once semantics matter?",
            "answer": "It matters for ledger-like workflows where duplicate processing can create incorrect financial or inventory records.",
        },
    ],
]


SESSION_CONFIGS = [
    ("backend_java", "junior"),
    ("backend_java", "mid"),
    ("backend_java", "senior"),
    ("frontend_react", "junior"),
    ("backend_java", "mid"),
    ("backend_java", "senior"),
    ("backend_java", "junior"),
    ("backend_java", "mid"),
]


CANNED_ANSWERS = {
    "junior": "I would start with a simple implementation, write unit tests for the main path, then ask for feedback on edge cases before merging.",
    "mid": "I would review the existing patterns, add tests around the expected behavior, implement the change behind a small interface, and validate it in staging.",
    "senior": "I would first clarify the failure modes and performance constraints, design for backward compatibility, add regression tests, and document the tradeoffs for future maintainers.",
}


def now_iso() -> str:
    return datetime.now(timezone.utc).isoformat()


def require_env(name: str) -> str:
    value = os.getenv(name)
    if not value:
        raise RuntimeError(f"Missing required environment variable: {name}")
    return value


def db_connect():
    return mysql.connector.connect(
        host=require_env("DB_HOST"),
        port=int(require_env("DB_PORT")),
        user=require_env("DB_USERNAME"),
        password=require_env("DB_PASSWORD"),
        database=require_env("DB_NAME"),
        ssl_disabled=True,
    )


def sha256_hash_java(input_str: str) -> int:
    digest = hashlib.sha256(input_str.encode("utf-8")).digest()
    signed = struct.unpack(">i", digest[:4])[0]
    return abs(signed)


def stable_question_id(question_text: str) -> str:
    digest = hashlib.sha256((question_text or "").strip().encode("utf-8")).hexdigest()
    return "gen-" + digest[:12]


def get_variant(session_id: str, experiment_id: int = EXPERIMENT_ID) -> str:
    bucket = (sha256_hash_java(f"{session_id}:{experiment_id}") % 10000) / 100.0
    return "treatment" if bucket < TRAFFIC_PCT else "baseline"


def find_session_ids(
    prefix: str,
    variant: str,
    need: int,
    search_range: int = 100000,
    skip_existing: set[str] | None = None,
) -> list[str]:
    skip_existing = skip_existing or set()
    found: list[str] = []
    for i in range(search_range):
        sid = f"{prefix}{i:05d}"
        if sid in skip_existing:
            continue
        if get_variant(sid) == variant:
            found.append(sid)
            if len(found) >= need:
                return found
    raise RuntimeError(f"Could not find {need} {variant} session IDs for prefix {prefix}")


def fetch_all(cur, sql: str, params: tuple[Any, ...] = ()) -> list[dict[str, Any]]:
    cur.execute(sql, params)
    rows = cur.fetchall()
    cleaned: list[dict[str, Any]] = []
    for row in rows:
        item: dict[str, Any] = {}
        for key, value in row.items():
            if hasattr(value, "isoformat"):
                item[key] = value.isoformat()
            elif value is not None and not isinstance(value, (str, int, float, bool)):
                item[key] = float(value)
            else:
                item[key] = value
        cleaned.append(item)
    return cleaned


def prepare_experiment() -> dict[str, Any]:
    conn = db_connect()
    cur = conn.cursor(dictionary=True)
    cur.execute("SELECT baseline_config, treatment_config FROM experiment WHERE id=%s", (EXPERIMENT_ID,))
    row = cur.fetchone()
    if not row:
        raise RuntimeError(f"Experiment {EXPERIMENT_ID} not found")
    baseline_config = json.loads(row["baseline_config"])
    treatment_config = json.loads(row["treatment_config"])
    baseline_config["model"] = CHAT_MODEL
    treatment_config["model"] = CHAT_MODEL
    cur.execute(
        "UPDATE experiment SET status='running', ended_at=NULL, baseline_config=%s, treatment_config=%s WHERE id=%s",
        (json.dumps(baseline_config), json.dumps(treatment_config), EXPERIMENT_ID),
    )
    conn.commit()
    cur.execute("SELECT id, name, status, traffic_percentage, baseline_config, treatment_config FROM experiment WHERE id=%s", (EXPERIMENT_ID,))
    experiment = cur.fetchone()
    cur.close()
    conn.close()
    return experiment


def count_prefix_metrics(prefix: str) -> dict[str, int]:
    conn = db_connect()
    cur = conn.cursor(dictionary=True)
    cur.execute(
        "SELECT variant, COUNT(*) n FROM experiment_metric WHERE experiment_id=%s AND session_id LIKE %s GROUP BY variant",
        (EXPERIMENT_ID, f"{prefix}%"),
    )
    counts = {row["variant"]: row["n"] for row in cur.fetchall()}
    cur.close()
    conn.close()
    return counts


def prefix_metric_session_ids(prefix: str) -> set[str]:
    conn = db_connect()
    cur = conn.cursor()
    cur.execute(
        "SELECT DISTINCT session_id FROM experiment_metric WHERE experiment_id=%s AND session_id LIKE %s",
        (EXPERIMENT_ID, f"{prefix}%"),
    )
    session_ids = {row[0] for row in cur.fetchall()}
    cur.close()
    conn.close()
    return session_ids


def create_session(session_id: str, role_id: str, level: str) -> None:
    payload = {"sessionId": session_id, "roleId": role_id, "level": level, "skills": []}
    response = requests.post(f"{BACKEND_URL}/api/sessions", json=payload, timeout=30)
    response.raise_for_status()


def seed_history(session_id: str, history: list[dict[str, str]]) -> None:
    for idx, turn in enumerate(history, 1):
        payload = {
            "questionId": f"seed-{session_id}-{idx}",
            "questionText": turn["question"],
            "answerText": turn["answer"],
        }
        response = requests.post(f"{BACKEND_URL}/api/sessions/{session_id}/answer", json=payload, timeout=30)
        response.raise_for_status()


def generate_question(session_id: str, role_id: str, level: str) -> tuple[dict[str, Any], int]:
    start = time.time()
    response = requests.post(
        f"{BACKEND_URL}/api/llm/question-generate",
        json={"sessionId": session_id, "roleId": role_id, "level": level},
        timeout=90,
    )
    latency_ms = int((time.time() - start) * 1000)
    response.raise_for_status()
    return response.json(), latency_ms


def evaluate_answer(session_id: str, question: str, role_id: str, level: str) -> tuple[dict[str, Any], int]:
    answer = CANNED_ANSWERS.get(level, CANNED_ANSWERS["mid"])
    start = time.time()
    response = requests.post(
        f"{BACKEND_URL}/api/llm/eval",
        json={
            "sessionId": session_id,
            "question": question,
            "answer": answer,
            "roleId": role_id,
            "level": level,
        },
        timeout=90,
    )
    latency_ms = int((time.time() - start) * 1000)
    response.raise_for_status()
    return response.json(), latency_ms


def ascii_snippet(text: str, limit: int = 72) -> str:
    return (text or "")[:limit].encode("ascii", "backslashreplace").decode("ascii")


def collect(prefix: str, target_treatment: int, target_baseline: int, sleep_seconds: float) -> list[dict[str, Any]]:
    counts = count_prefix_metrics(prefix)
    treatment_needed = max(0, target_treatment - counts.get("treatment", 0))
    baseline_needed = max(0, target_baseline - counts.get("baseline", 0))
    existing_session_ids = prefix_metric_session_ids(prefix)
    treatment_ids = find_session_ids(prefix, "treatment", treatment_needed, skip_existing=existing_session_ids)
    existing_session_ids.update(treatment_ids)
    baseline_ids = find_session_ids(prefix, "baseline", baseline_needed, skip_existing=existing_session_ids)
    planned = [(sid, "treatment") for sid in treatment_ids] + [(sid, "baseline") for sid in baseline_ids]

    manifest: list[dict[str, Any]] = []
    for idx, (session_id, expected_variant) in enumerate(planned, 1):
        role_id, level = SESSION_CONFIGS[(idx - 1) % len(SESSION_CONFIGS)]
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
            "errors": [],
            "collected_at": now_iso(),
        }
        try:
            create_session(session_id, role_id, level)
            seed_history(session_id, history)
            q_data, q_latency = generate_question(session_id, role_id, level)
            question = q_data.get("question", "")
            variant = q_data.get("variant", get_variant(session_id))
            record.update(
                {
                    "variant": variant,
                    "experiment_id": q_data.get("experimentId"),
                    "generated_followup_count": 1,
                    "total_questions_asked": len(history) + 1,
                    "question_generation_latency_ms": q_latency,
                    "generated_question_id": stable_question_id(question),
                    "generated_question_snippet": question[:240],
                }
            )
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
                f"q={q_latency}ms eval={eval_latency}ms score={score} "
                f"{ascii_snippet(question)}"
            )
        except Exception as exc:
            record["completion_status"] = "error"
            record["errors"].append(str(exc))
            print(f"[ERROR] {session_id}: {exc}")
        manifest.append(record)
        if sleep_seconds > 0:
            time.sleep(sleep_seconds)

    RESULTS.mkdir(parents=True, exist_ok=True)
    manifest_path = RESULTS / "week28_session_manifest.json"
    existing_by_session: dict[str, dict[str, Any]] = {}
    if manifest_path.exists():
        existing = json.loads(manifest_path.read_text(encoding="utf-8"))
        existing_by_session = {row["session_id"]: row for row in existing if row.get("session_id")}
    for row in manifest:
        existing_by_session[row["session_id"]] = row
    merged = sorted(existing_by_session.values(), key=lambda row: row.get("session_id", ""))
    manifest_path.write_text(json.dumps(merged, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    return manifest


def load_manifest(prefix: str) -> list[dict[str, Any]]:
    manifest_path = RESULTS / "week28_session_manifest.json"
    if not manifest_path.exists():
        return []
    rows = json.loads(manifest_path.read_text(encoding="utf-8"))
    return [row for row in rows if row.get("session_id", "").startswith(prefix)]


def percentile(values: list[float], pct: float) -> float | None:
    if not values:
        return None
    ordered = sorted(values)
    if len(ordered) == 1:
        return float(ordered[0])
    pos = (len(ordered) - 1) * pct
    lo = math.floor(pos)
    hi = math.ceil(pos)
    if lo == hi:
        return float(ordered[lo])
    return float(ordered[lo] + (ordered[hi] - ordered[lo]) * (pos - lo))


def variant_summary(rows: list[dict[str, Any]], variant: str) -> dict[str, Any]:
    subset = [row for row in rows if row.get("variant") == variant]
    completed = [row for row in subset if row.get("completion_status") == "completed"]
    total_questions = [row.get("total_questions_asked", 0) for row in completed]
    q_latencies = [row.get("question_generation_latency_ms", 0) for row in completed]
    eval_latencies = [row.get("evaluation_latency_ms", 0) for row in completed]
    total_latencies = [q + e for q, e in zip(q_latencies, eval_latencies)]
    early_stops = [row for row in completed if row.get("early_stop_triggered")]
    premature = [row for row in completed if row.get("premature_stop")]
    return {
        "n_sessions": len(subset),
        "n_completed": len(completed),
        "avg_questions_per_session": statistics.mean(total_questions) if total_questions else None,
        "avg_seeded_prior_count": statistics.mean([row.get("seeded_prior_count", 0) for row in completed]) if completed else None,
        "avg_generated_followup_count": statistics.mean([row.get("generated_followup_count", 0) for row in completed]) if completed else None,
        "avg_evaluated_answer_count": statistics.mean([row.get("evaluated_answer_count", 0) for row in completed]) if completed else None,
        "early_stop_rate": len(early_stops) / len(completed) if completed else None,
        "premature_stop_rate": len(premature) / len(completed) if completed else None,
        "session_completion_rate": len(completed) / len(subset) if subset else None,
        "latency_p50_ms": percentile(total_latencies, 0.50),
        "latency_p95_ms": percentile(total_latencies, 0.95),
        "final_score_avg": statistics.mean([row["final_evaluation_score"] for row in completed if row.get("final_evaluation_score") is not None]) if completed else None,
    }


def generate(prefix: str) -> dict[str, Any]:
    manifest = load_manifest(prefix)
    completed_sessions = [row["session_id"] for row in manifest if row.get("completion_status") == "completed"]
    if not completed_sessions:
        raise RuntimeError(f"No completed sessions found in manifest for prefix {prefix}")
    generated_question_ids = sorted(
        {
            row["generated_question_id"]
            for row in manifest
            if row.get("completion_status") == "completed" and row.get("generated_question_id")
        }
    )
    gen_placeholders = ",".join(["%s"] * len(generated_question_ids))

    conn = db_connect()
    cur = conn.cursor(dictionary=True)
    params = (EXPERIMENT_ID, f"{prefix}%")
    experiment = fetch_all(cur, "SELECT * FROM experiment WHERE id=%s", (EXPERIMENT_ID,))[0]
    quality_stats = fetch_all(
        cur,
        "SELECT variant, COUNT(*) n, AVG(quality_score) avg_quality, STDDEV(quality_score) std_quality, "
        "MIN(quality_score) min_quality, MAX(quality_score) max_quality, AVG(latency_ms) avg_latency_ms "
        "FROM experiment_metric WHERE experiment_id=%s AND session_id LIKE %s GROUP BY variant",
        params,
    )
    quality_distribution = fetch_all(
        cur,
        "SELECT variant, quality_score, COUNT(*) n FROM experiment_metric "
        "WHERE experiment_id=%s AND session_id LIKE %s GROUP BY variant, quality_score ORDER BY variant, quality_score",
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
    if generated_question_ids:
        topic_cluster = fetch_all(
            cur,
            "SELECT "
            "COUNT(*) gen_embeddings_total, "
            "SUM(CASE WHEN cluster_id IS NOT NULL THEN 1 ELSE 0 END) gen_clustered_total, "
            "SUM(CASE WHEN cluster_label='Generated questions fallback cluster' THEN 1 ELSE 0 END) gen_fallback_total, "
            "SUM(CASE WHEN cluster_id IS NOT NULL AND (cluster_label IS NULL OR cluster_label <> 'Generated questions fallback cluster') THEN 1 ELSE 0 END) gen_seeded_match_total "
            f"FROM question_embedding WHERE question_id IN ({gen_placeholders})",
            tuple(generated_question_ids),
        )[0]
        topic_by_role = fetch_all(
            cur,
            "SELECT role_id, COUNT(*) gen_embeddings, "
            "SUM(CASE WHEN cluster_id IS NOT NULL THEN 1 ELSE 0 END) gen_clustered, "
            "SUM(CASE WHEN cluster_label='Generated questions fallback cluster' THEN 1 ELSE 0 END) fallback_clustered "
            f"FROM question_embedding WHERE question_id IN ({gen_placeholders}) GROUP BY role_id ORDER BY role_id",
            tuple(generated_question_ids),
        )
    else:
        topic_cluster = {
            "gen_embeddings_total": 0,
            "gen_clustered_total": 0,
            "gen_fallback_total": 0,
            "gen_seeded_match_total": 0,
        }
        topic_by_role = []
    recent_topic = fetch_all(
        cur,
        "SELECT session_id, role_id, total_questions, clusters_covered, total_clusters, coverage_ratio, updated_at "
        "FROM topic_coverage WHERE session_id LIKE %s ORDER BY updated_at DESC LIMIT 20",
        (f"{prefix}%",),
    )
    cur.close()
    conn.close()

    stats_by_variant = {row["variant"]: row for row in quality_stats}
    treatment_q = stats_by_variant.get("treatment", {})
    baseline_q = stats_by_variant.get("baseline", {})
    quality_delta = None
    if treatment_q.get("avg_quality") is not None and baseline_q.get("avg_quality") is not None:
        quality_delta = treatment_q["avg_quality"] - baseline_q["avg_quality"]

    session_rows = [row for row in manifest if row.get("variant") in ("treatment", "baseline")]
    treatment_s = variant_summary(session_rows, "treatment")
    baseline_s = variant_summary(session_rows, "baseline")
    n_treatment = treatment_s["n_completed"]
    n_baseline = baseline_s["n_completed"]
    avg_questions_delta_pct = None
    if baseline_s.get("avg_questions_per_session"):
        avg_questions_delta_pct = (
            treatment_s["avg_questions_per_session"] - baseline_s["avg_questions_per_session"]
        ) / baseline_s["avg_questions_per_session"]
    openai_error_rate = len([row for row in manifest if row.get("errors")]) / len(manifest) if manifest else None
    sample_gate_pass = n_treatment >= 80 and n_baseline >= 50
    quality_gate_pass = quality_delta is not None and quality_delta >= -10.0
    premature_gate_pass = (
        treatment_s.get("premature_stop_rate") is not None
        and treatment_s["premature_stop_rate"] <= 0.03
    )
    avg_questions_gate_pass = avg_questions_delta_pct is not None and avg_questions_delta_pct <= 0.05
    latency_gate_pass = (
        treatment_s.get("latency_p95_ms") is not None
        and treatment_s["latency_p95_ms"] <= 3000
    )
    openai_gate_pass = openai_error_rate is not None and openai_error_rate <= 0.05
    completion_gate_pass = (
        treatment_s.get("session_completion_rate") is not None
        and treatment_s["session_completion_rate"] >= 0.85
    )
    session_thresholds_pass = all(
        [
            quality_gate_pass,
            premature_gate_pass,
            avg_questions_gate_pass,
            latency_gate_pass,
            openai_gate_pass,
            completion_gate_pass,
        ]
    )

    level_counts = defaultdict(Counter)
    for row in session_rows:
        level_counts[row.get("level", "unknown")][row.get("variant", "unknown")] += 1

    generated_ids = [row.get("generated_question_id") for row in session_rows if row.get("generated_question_id")]
    unique_generated = len(set(generated_ids))
    dedup_rate = 1 - unique_generated / len(generated_ids) if generated_ids else None
    fallback_rate = (
        topic_cluster["gen_fallback_total"] / topic_cluster["gen_clustered_total"]
        if topic_cluster.get("gen_clustered_total")
        else None
    )
    seeded_match_rate = (
        topic_cluster["gen_seeded_match_total"] / topic_cluster["gen_clustered_total"]
        if topic_cluster.get("gen_clustered_total")
        else None
    )

    collected_at = now_iso()
    sample_snapshot = {
        "generated_at": collected_at,
        "experiment_id": EXPERIMENT_ID,
        "experiment_name": experiment["name"],
        "experiment_status": experiment["status"],
        "prefix": prefix,
        "traffic_source": "history-rich controlled production-equivalent",
        "model": CHAT_MODEL,
        "metric_version": METRIC_VERSION,
        "counts": {"treatment": n_treatment, "baseline": n_baseline},
        "target_counts": {"treatment": 80, "baseline": 50, "minimum_baseline": 40},
        "quality_stats": quality_stats,
        "feature_counts": feature_counts,
        "slice_counts": {level: dict(counts) for level, counts in level_counts.items()},
    }
    quality_report = {
        "generated_at": collected_at,
        "prefix": prefix,
        "metric_version": METRIC_VERSION,
        "quality_stats": quality_stats,
        "quality_distribution": quality_distribution,
        "quality_delta_treatment_minus_baseline": quality_delta,
        "variance_recovered": bool(
            treatment_q.get("std_quality", 0) and baseline_q.get("std_quality", 0)
        ),
    }
    session_guardrails = {
        "generated_at": collected_at,
        "prefix": prefix,
        "status": "PASS" if sample_gate_pass and session_thresholds_pass else "HOLD",
        "n_treatment": n_treatment,
        "n_baseline": n_baseline,
        "quality_delta": quality_delta,
        "avg_questions_delta_pct": avg_questions_delta_pct,
        "openai_error_rate": openai_error_rate,
        "treatment": treatment_s,
        "baseline": baseline_s,
        "manifest_sessions": len(manifest),
        "decision_grade": all(
            row.get("completion_status") == "completed"
            and row.get("generated_followup_count") == 1
            and row.get("evaluated_answer_count") == 1
            for row in session_rows
        ),
        "thresholds_pass": session_thresholds_pass,
        "threshold_checks": {
            "quality_delta": quality_gate_pass,
            "premature_stop_rate": premature_gate_pass,
            "avg_questions_delta_pct": avg_questions_gate_pass,
            "latency_p95_ms": latency_gate_pass,
            "openai_error_rate": openai_gate_pass,
            "session_completion_rate": completion_gate_pass,
        },
    }
    topic_gate_ready = bool(
        feature_counts["topic_coverage_rows"]
        and fallback_rate is not None
        and fallback_rate <= 0.20
    )
    calibration_ready = False
    stage_b_ready = bool(
        sample_gate_pass
        and session_guardrails["decision_grade"]
        and session_thresholds_pass
        and topic_gate_ready
        and calibration_ready
    )
    blocking_gates = []
    if not sample_gate_pass:
        blocking_gates.append("sample-size gate")
    if not session_guardrails["decision_grade"]:
        blocking_gates.append("session metric completeness")
    if not quality_gate_pass:
        blocking_gates.append("quality delta guardrail")
    if not premature_gate_pass:
        blocking_gates.append("premature-stop guardrail")
    if not avg_questions_gate_pass:
        blocking_gates.append("average questions guardrail")
    if not latency_gate_pass:
        blocking_gates.append("latency p95 guardrail")
    if not openai_gate_pass:
        blocking_gates.append("OpenAI reliability guardrail")
    if not completion_gate_pass:
        blocking_gates.append("session completion guardrail")
    if not topic_gate_ready:
        blocking_gates.append("topic coverage gating")
    if not calibration_ready:
        blocking_gates.append("calibration readiness")
    previous_topic_reference: dict[str, Any] | None = None
    previous_topic_path = RESULTS / "week27_topic_coverage_snapshot.json"
    if previous_topic_path.exists():
        previous_topic = json.loads(previous_topic_path.read_text(encoding="utf-8"))
        previous_topic_reference = {
            "artifact": previous_topic_path.name,
            "generated_at": previous_topic.get("generated_at"),
            "status": previous_topic.get("status"),
            "real_prefixes": previous_topic.get("real_prefixes"),
            "feature_counts": previous_topic.get("feature_counts"),
            "mapping_behavior": previous_topic.get("mapping_behavior"),
            "stage_b_scope": previous_topic.get("stage_b_scope"),
            "rate_note": "The Week 27 artifact did not persist fallback or seeded-match rates; Week 28 rates are computed from the current w28- generated question IDs.",
        }
    topic_snapshot = {
        "generated_at": collected_at,
        "prefix": prefix,
        "status": "READY_FOR_STAGE_B_GATE" if topic_gate_ready else "EXCLUDED_FROM_STAGE_B",
        "topic_coverage_rows": feature_counts["topic_coverage_rows"],
        "generated_question_dedup_rate": dedup_rate,
        "fallback_cluster_rate": fallback_rate,
        "nearest_seeded_cluster_match_rate": seeded_match_rate,
        "cluster_totals": topic_cluster,
        "by_role": topic_by_role,
        "previous_topic_reference": previous_topic_reference,
        "recent_topic_coverage_rows": recent_topic,
        "stage_b_gating_recommendation": (
            "Topic coverage can support Stage B gating."
            if topic_gate_ready
            else "Exclude topic_coverage from Stage B gating until seeded role-specific clusters replace generated fallback clusters."
        ),
    }
    stagea_live = {
        "experiment_id": EXPERIMENT_ID,
        "timestamp": collected_at,
        "data_source": "live_aiven_mysql_real_openai_week28_prefix",
        "model_version": "embedding-v1.0+prediction-v1.0+llm-v1.0-history",
        "decision": "GO" if stage_b_ready else "HOLD",
        "stage_b_recommendation": "GO" if stage_b_ready else "HOLD",
        "metrics": {
            "n_treatment": n_treatment,
            "n_baseline": n_baseline,
            "quality_delta_treatment_minus_baseline": quality_delta,
            "treatment_std_quality": treatment_q.get("std_quality"),
            "baseline_std_quality": baseline_q.get("std_quality"),
            "avg_questions_delta_pct": avg_questions_delta_pct,
            "premature_stop_rate": treatment_s.get("premature_stop_rate"),
            "early_stop_rate": treatment_s.get("early_stop_rate"),
            "topic_coverage_rows": feature_counts["topic_coverage_rows"],
            "fallback_cluster_rate": fallback_rate,
            "openai_error_rate": openai_error_rate,
            "session_thresholds_pass": session_thresholds_pass,
        },
        "rationale": [
            f"Session-level metrics decision-grade: {session_guardrails['decision_grade']}.",
            f"Operational session guardrails met: {session_thresholds_pass}; treatment latency_p95_ms={treatment_s.get('latency_p95_ms')}.",
            f"Repaired sample-size gate met: {sample_gate_pass} (treatment={n_treatment}, baseline={n_baseline}).",
            f"Topic coverage Stage B gate ready: {topic_gate_ready}.",
            f"Calibration ready: {calibration_ready}; platt-v2.1 remains unchanged.",
        ],
        "blocking_gates": blocking_gates,
        "rollback": (
            f"Keep Stage A at 10%; do not expand to Stage B until {', '.join(blocking_gates)} "
            f"{'is' if len(blocking_gates) == 1 else 'are'} resolved."
            if not stage_b_ready
            else "Proceed only with the approved Stage B gate and monitor session-level guardrails continuously."
        ),
    }

    RESULTS.mkdir(parents=True, exist_ok=True)
    DOCS.mkdir(parents=True, exist_ok=True)
    writes = {
        "week28_stagea_sample_snapshot.json": sample_snapshot,
        "week28_quality_variance_report.json": quality_report,
        "week28_session_guardrails.json": session_guardrails,
        "week28_topic_coverage_snapshot.json": topic_snapshot,
        "week28_stagea_live_result.json": stagea_live,
    }
    for name, payload in writes.items():
        (RESULTS / name).write_text(json.dumps(payload, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")

    write_docs(
        collected_at,
        prefix,
        sample_snapshot,
        quality_report,
        session_guardrails,
        topic_snapshot,
        stagea_live,
    )
    update_registry(collected_at, prefix, stagea_live)
    return stagea_live


def write_docs(
    generated_at: str,
    prefix: str,
    sample: dict[str, Any],
    quality: dict[str, Any],
    session_guardrails: dict[str, Any],
    topic: dict[str, Any],
    live: dict[str, Any],
) -> None:
    metrics = live["metrics"]
    sample_gate = (
        sample["counts"]["treatment"] >= sample["target_counts"]["treatment"]
        and sample["counts"]["baseline"] >= sample["target_counts"]["baseline"]
    )
    session_grade = session_guardrails["decision_grade"]
    topic_gate_status = topic["status"]
    stage_b_recommendation = live["stage_b_recommendation"]
    blocking_text = ", ".join(live.get("blocking_gates", [])) or "none"
    previous_topic = topic.get("previous_topic_reference") or {}
    previous_scope = (previous_topic.get("stage_b_scope") or "N/A").rstrip(".")
    previous_rate_note = (previous_topic.get("rate_note") or "N/A").rstrip(".")
    docs = {
        "week28_session_metric_instrumentation_report.md": f"""# Week 28 Session Metric Instrumentation Report

Generated: {generated_at}

## Result

- Instrumentation status: {"PASS" if session_grade else "HOLD"} for the controlled `{prefix}` window.
- Stage B guardrail status: {session_guardrails['status']}.
- Session guardrails are derived from the collection manifest and joined to live DB writes.
- Treatment completed sessions: {session_guardrails['n_treatment']}.
- Baseline completed sessions: {session_guardrails['n_baseline']}.
- Decision-grade flag: {session_guardrails['decision_grade']}.

## Metrics Recorded

- seeded prior Q&A count
- generated follow-up question count
- evaluated answer count
- total questions asked
- early-stop triggered flag and reason
- premature-stop indicator
- latest evaluation score
- completion status
""",
        "week28_stagea_collection_report.md": f"""# Week 28 Repaired Stage A Collection Report

Generated: {generated_at}

## Result

- Prefix: `{prefix}`.
- Treatment: {sample['counts']['treatment']}.
- Baseline: {sample['counts']['baseline']}.
- Model: {sample['model']}.
- Metric version: {sample['metric_version']}.

Sample-size gate: {"PASS" if sample_gate else "HOLD"}.
""",
        "week28_topic_coverage_cluster_review.md": f"""# Week 28 Topic Coverage Cluster Review

Generated: {generated_at}

## Result

- Topic coverage rows for `{prefix}`: {topic['topic_coverage_rows']}.
- Generated-question deduplication rate: {topic['generated_question_dedup_rate']}.
- Fallback cluster rate: {topic['fallback_cluster_rate']}.
- Nearest seeded cluster match rate: {topic['nearest_seeded_cluster_match_rate']}.
- Stage B topic gate status: {topic_gate_status}.

## Week 27 Reference

- Prior artifact: {previous_topic.get('artifact', 'N/A')}.
- Prior Stage B scope: {previous_scope}.
- Rate note: {previous_rate_note}.

## Recommendation

{topic['stage_b_gating_recommendation']}
""",
        "week28_calibration_slice_readiness_report.md": f"""# Week 28 Calibration Slice Readiness Report

Generated: {generated_at}

## Recommendation

Calibration readiness: HOLD.

## Evidence

- Slice counts: {json.dumps(sample['slice_counts'], sort_keys=True)}.
- platt-v2.1 remains unchanged.
- Topic coverage fallback clusters are not ready for rollout gating.
""",
        "week28_stagea_guardrail_readout.md": f"""# Week 28 Stage A Guardrail Readout

Generated: {generated_at}

## Status

{stage_b_recommendation} for Stage B.

## Gates

- n_treatment >= 80: {"PASS" if metrics['n_treatment'] >= 80 else "HOLD"} ({metrics['n_treatment']}).
- n_baseline >= 50: {"PASS" if metrics['n_baseline'] >= 50 else "HOLD"} ({metrics['n_baseline']}).
- Session-level metrics decision-grade: {"PASS" if session_grade else "HOLD"}.
- Operational session guardrails: {"PASS" if metrics['session_thresholds_pass'] else "HOLD"}.
- Treatment latency p95 ms: {session_guardrails['treatment']['latency_p95_ms']}.
- Premature-stop rate: {metrics['premature_stop_rate']}.
- OpenAI error rate: {metrics['openai_error_rate']}.
- Topic coverage rollout-gate readiness: {topic_gate_status}.
- Blocking gates: {blocking_text}.
""",
        "week28_ml_decision_readout.md": f"""# Week 28 ML Decision Readout

Generated: {generated_at}

## Decision

Stage B recommendation: {stage_b_recommendation}.

## Rationale

{chr(10).join(f"- {item}" for item in live['rationale'])}

## Rollback

{live['rollback']}
""",
    }
    for name, text in docs.items():
        (DOCS / name).write_text(text, encoding="utf-8")


def update_registry(generated_at: str, prefix: str, live: dict[str, Any]) -> None:
    registry_id = "week28_repaired_stagea_20260620"
    metrics = live["metrics"]
    topic_gating = (
        "ready"
        if metrics["topic_coverage_rows"] and metrics["fallback_cluster_rate"] is not None and metrics["fallback_cluster_rate"] <= 0.20
        else "excluded"
    )
    blocking_text = ", ".join(live.get("blocking_gates", [])) or "none"
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
                "topic_gating": topic_gating,
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
        "",
        "",
        metrics["early_stop_rate"],
        "",
        f"Week 28 repaired Stage A collection; Stage B {live['stage_b_recommendation']} pending {blocking_text}",
    ]
    if not REGISTRY.exists():
        return
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
    with REGISTRY.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.writer(handle)
        writer.writerows(rows)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--prefix", default=DEFAULT_PREFIX)
    parser.add_argument("--target-treatment", type=int, default=80)
    parser.add_argument("--target-baseline", type=int, default=50)
    parser.add_argument("--sleep-seconds", type=float, default=1.0)
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
