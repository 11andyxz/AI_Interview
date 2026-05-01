#!/usr/bin/env python3
"""
Week 21 Task 2: Controlled Ramp Validation

Runs phased traffic policy validation for the Week 20 recommendation (0.85 threshold):
  Stage A: 10%  traffic ramp
  Stage B: 50%  traffic ramp
  Stage C: 100% traffic ramp (only if all gates pass)

Each stage reads from the existing SQLite experiment DB and evaluates metrics
against predefined guardrails. Decision: Go / Hold / Rollback.

Usage:
    # Run all stages sequentially (stops on first Hold/Rollback):
    python run_ramp_validation.py --stage all

    # Run a specific stage only:
    python run_ramp_validation.py --stage A
    python run_ramp_validation.py --stage B
    python run_ramp_validation.py --stage C

    # Re-run on fresh sessions (requires OpenAI key and DB):
    python run_ramp_validation.py --stage A --live
"""

import argparse
import json
import os
import random
import sqlite3
import time
from datetime import datetime, timezone
from pathlib import Path
from typing import Optional

import numpy as np
from scipy import stats

DB_PATH = Path(__file__).parent / "results" / "ab_experiment.db"
REGISTRY_PATH = Path(__file__).parent / "experiment_registry.csv"

# Guardrails (from Week 20 acceptance criteria)
GUARDRAILS = {
    "avg_questions_delta_pct_max": 5.0,   # treatment must not increase questions by more than 5%
    "rmse_max": 15.0,
    "premature_stop_rate_max": 0.03,       # < 3%
    "p95_latency_ms_max": 3000,
}

TREATMENT_POLICY = {
    "pass_threshold": 0.85,
    "fail_threshold": 0.10,
    "min_questions": {"junior": 4, "mid": 5, "senior": 6},
}

STAGE_SAMPLE_SIZES = {"A": 0.10, "B": 0.50, "C": 1.00}


# ---------------------------------------------------------------------------
# Data loading
# ---------------------------------------------------------------------------

def load_sessions(experiment_ids: list[str]) -> list[dict]:
    """Load sessions from SQLite for the given experiment IDs."""
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    cursor = conn.cursor()
    placeholders = ",".join("?" * len(experiment_ids))
    cursor.execute(
        f"SELECT * FROM sessions WHERE experiment_id IN ({placeholders})",
        experiment_ids,
    )
    rows = [dict(r) for r in cursor.fetchall()]
    conn.close()
    return rows


def load_questions_for_sessions(session_ids: list[int]) -> list[dict]:
    """Load question-level records for the given session IDs."""
    if not session_ids:
        return []
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    cursor = conn.cursor()
    placeholders = ",".join("?" * len(session_ids))
    cursor.execute(
        f"SELECT * FROM questions WHERE session_id IN ({placeholders})",
        session_ids,
    )
    rows = [dict(r) for r in cursor.fetchall()]
    conn.close()
    return rows


def _infer_slice_from_title(title: str) -> str:
    """Infer experience-level slice from interview title."""
    t = (title or "").lower()
    if "junior" in t:
        return "junior"
    if "senior" in t or "lead" in t or "staff" in t or "principal" in t:
        return "senior"
    return "mid"


def load_live_sessions_from_mysql() -> tuple[list[dict], list[dict]]:
    """Load sessions from MySQL and split into control / treatment.

    Control  = completed sessions created before 2026-04-01 (historical baseline).
    Treatment = completed sessions created 2026-04-01+ (live ramp candidates).

    Each session dict matches the format expected by compute_metrics():
        {num_questions, early_stopped, avg_latency_ms, slice, experiment_id}

    Sessions with no messages AND no duration data are excluded (stubs).
    """
    try:
        import mysql.connector
    except ImportError:
        raise RuntimeError("mysql-connector-python not installed; run: pip install mysql-connector-python")

    host = os.environ.get("DB_HOST", "")
    port = int(os.environ.get("DB_PORT", "3306"))
    name = os.environ.get("DB_NAME", "")
    user = os.environ.get("DB_USERNAME", "")
    pwd  = os.environ.get("DB_PASSWORD", "")

    if not all([host, name, user, pwd]):
        raise RuntimeError("DB env vars incomplete — set DB_HOST, DB_PORT, DB_NAME, DB_USERNAME, DB_PASSWORD")

    conn = mysql.connector.connect(
        host=host, port=port, database=name,
        user=user, password=pwd,
        connection_timeout=10, ssl_disabled=False
    )
    cur = conn.cursor(dictionary=True)

    # Get completed (or any started/ended) interviews
    cur.execute("""
        SELECT
            i.id            AS interview_id,
            i.title,
            i.status,
            i.duration_seconds,
            i.created_at,
            COUNT(m.id)     AS num_messages,
            AVG(m.evaluation_score) AS avg_eval_score
        FROM interview i
        LEFT JOIN interview_message m ON m.interview_id = i.id
        WHERE i.status IN ('Completed', 'completed')
        GROUP BY i.id, i.title, i.status, i.duration_seconds, i.created_at
    """)
    rows = cur.fetchall()
    conn.close()

    CUTOFF = datetime(2026, 4, 1)
    control, treatment = [], []

    for r in rows:
        num_q = r["num_messages"] or 0
        duration = r["duration_seconds"] or 0
        slice_label = _infer_slice_from_title(r["title"])
        min_q = TREATMENT_POLICY["min_questions"].get(slice_label, 5)

        # Exclude sessions with zero messages and zero duration (pure stubs)
        if num_q == 0 and duration == 0:
            continue

        # Latency: average ms per message-turn
        avg_lat = (duration * 1000 / max(num_q, 1)) if duration > 0 else None

        # Premature-stop heuristic: ended too quickly relative to slice minimum
        early_stopped = 1 if (num_q < min_q and duration < min_q * 90) else 0

        session = {
            "id": r["interview_id"],
            "experiment_id": "week22_live",
            "slice": slice_label,
            "num_questions": num_q if num_q > 0 else 1,  # floor at 1 to avoid division errors
            "early_stopped": early_stopped,
            "avg_latency_ms": avg_lat,
            "eval_score": r["avg_eval_score"],
        }

        created = r["created_at"]
        if created < CUTOFF:
            control.append(session)
        else:
            treatment.append(session)

    return control, treatment


# ---------------------------------------------------------------------------
# Metric computation
# ---------------------------------------------------------------------------

def compute_metrics(control_sessions: list[dict], treatment_sessions: list[dict]) -> dict:
    ctrl_q = [s["num_questions"] for s in control_sessions]
    trt_q  = [s["num_questions"] for s in treatment_sessions]

    if not ctrl_q or not trt_q:
        return {"error": "insufficient data"}

    avg_ctrl = float(np.mean(ctrl_q))
    avg_trt  = float(np.mean(trt_q))
    delta    = avg_trt - avg_ctrl
    delta_pct = delta / avg_ctrl * 100 if avg_ctrl else 0.0

    t_stat, p_val = stats.ttest_ind(ctrl_q, trt_q, equal_var=False)
    cohens_d = delta / float(np.std(ctrl_q + trt_q, ddof=1)) if len(ctrl_q + trt_q) > 1 else 0.0

    # Premature stop: sessions that stopped before slice min_questions
    def is_premature(s: dict) -> bool:
        min_q = TREATMENT_POLICY["min_questions"].get(s.get("slice", "mid"), 5)
        return s.get("early_stopped", 0) == 1 and s["num_questions"] < min_q

    premature_count = sum(1 for s in treatment_sessions if is_premature(s))
    premature_rate  = premature_count / len(treatment_sessions) if treatment_sessions else 0.0

    # Latency
    latencies = [s["avg_latency_ms"] for s in treatment_sessions if s.get("avg_latency_ms")]
    p50_lat = float(np.percentile(latencies, 50)) if latencies else None
    p95_lat = float(np.percentile(latencies, 95)) if latencies else None

    return {
        "n_control": len(control_sessions),
        "n_treatment": len(treatment_sessions),
        "avg_questions_control": round(avg_ctrl, 2),
        "avg_questions_treatment": round(avg_trt, 2),
        "avg_questions_delta": round(delta, 2),
        "avg_questions_delta_pct": round(delta_pct, 1),
        "t_stat": round(t_stat, 3),
        "p_value": round(p_val, 4),
        "cohens_d": round(cohens_d, 3),
        "premature_stop_rate": round(premature_rate, 3),
        "p50_latency_ms": round(p50_lat, 0) if p50_lat else None,
        "p95_latency_ms": round(p95_lat, 0) if p95_lat else None,
        "rmse": None,  # Requires ground truth — filled from registry if available
    }


# ---------------------------------------------------------------------------
# Gate evaluation
# ---------------------------------------------------------------------------

def evaluate_gate(metrics: dict) -> tuple[str, list[str]]:
    """
    Returns (decision, reasons) where decision is 'GO', 'HOLD', or 'ROLLBACK'.
    """
    if "error" in metrics:
        return "HOLD", [f"Metric error: {metrics['error']}"]

    reasons = []
    decision = "GO"

    delta_pct = metrics.get("avg_questions_delta_pct", 0)
    if delta_pct > GUARDRAILS["avg_questions_delta_pct_max"]:
        reasons.append(f"avg_questions_delta_pct={delta_pct}% > {GUARDRAILS['avg_questions_delta_pct_max']}% \u2014 treatment increases questions (ROLLBACK)")
        decision = "ROLLBACK"

    premature = metrics.get("premature_stop_rate", 0)
    if premature >= GUARDRAILS["premature_stop_rate_max"]:
        reasons.append(f"premature_stop_rate={premature:.1%} >= {GUARDRAILS['premature_stop_rate_max']:.0%} (ROLLBACK)")
        decision = "ROLLBACK"

    p95 = metrics.get("p95_latency_ms")
    if p95 and p95 > GUARDRAILS["p95_latency_ms_max"]:
        msg = f"p95_latency={p95}ms > {GUARDRAILS['p95_latency_ms_max']}ms (HOLD)"
        reasons.append(msg)
        if decision == "GO":
            decision = "HOLD"

    rmse = metrics.get("rmse")
    if rmse and rmse > GUARDRAILS["rmse_max"]:
        msg = f"rmse={rmse} > {GUARDRAILS['rmse_max']} (HOLD)"
        reasons.append(msg)
        if decision == "GO":
            decision = "HOLD"

    if not reasons:
        reasons.append("All guardrails within tolerance")

    return decision, reasons


# ---------------------------------------------------------------------------
# Stage runner
# ---------------------------------------------------------------------------

def run_stage(stage: str, dry_run: bool = True, live: bool = False) -> dict:
    """Run a single ramp stage.

    When live=True: reads completed sessions from MySQL (requires DB env vars).
    When live=False (default): reads SQLite Week 20 replay data as proxy traffic.
    """
    print(f"\n{'='*60}")
    print(f"Stage {stage} ({int(STAGE_SAMPLE_SIZES[stage]*100)}% ramp)"
          + (" [LIVE / MySQL]" if live else " [DRY-RUN / SQLite]"))
    print(f"{'='*60}")

    if live:
        try:
            control, treatment = load_live_sessions_from_mysql()
        except Exception as exc:
            return {"stage": stage, "traffic_pct": int(STAGE_SAMPLE_SIZES[stage] * 100),
                    "timestamp": datetime.now(timezone.utc).isoformat() + "Z",
                    "metrics": {}, "decision": "HOLD",
                    "rationale": [f"MySQL load failed: {exc}"]}

        # Minimum sample size gate — refuse to evaluate guardrails on tiny samples
        MIN_N_TREATMENT = 20
        if len(treatment) < MIN_N_TREATMENT:
            print(f"  n_control={len(control)}, n_treatment={len(treatment)}")
            print(f"  HOLD: insufficient live treatment sessions (need >= {MIN_N_TREATMENT})")
            return {
                "stage": stage,
                "traffic_pct": int(STAGE_SAMPLE_SIZES[stage] * 100),
                "data_source": "mysql_live",
                "timestamp": datetime.now(timezone.utc).isoformat() + "Z",
                "metrics": {"n_control": len(control), "n_treatment": len(treatment)},
                "decision": "HOLD",
                "rationale": [
                    f"n_treatment={len(treatment)} < {MIN_N_TREATMENT} required for guardrail evaluation",
                    "Continue collecting live sessions. Re-run Stage A when n_treatment >= 20.",
                ],
            }

        frac = STAGE_SAMPLE_SIZES[stage]
        n_trt = max(1, int(len(treatment) * frac))
        random.seed(42 + ord(stage))
        sampled_control   = control
        sampled_treatment = random.sample(treatment, min(n_trt, len(treatment)))
    else:
        # Load base Week 20 sessions as the data source
        all_sessions = load_sessions(["week20_control_real", "week20_treatment_real"])
        control   = [s for s in all_sessions if s["experiment_id"] == "week20_control_real"]
        treatment = [s for s in all_sessions if s["experiment_id"] == "week20_treatment_real"]

        if not control or not treatment:
            return {"stage": stage, "traffic_pct": int(STAGE_SAMPLE_SIZES.get(stage, 0.1) * 100),
                    "timestamp": datetime.now(timezone.utc).isoformat() + "Z",
                    "metrics": {}, "decision": "HOLD",
                    "rationale": ["No base experiment data found. Run Week 20 experiment first."]}

        frac = STAGE_SAMPLE_SIZES[stage]
        n_ctrl = max(1, int(len(control) * frac))
        n_trt  = max(1, int(len(treatment) * frac))

        random.seed(42 + ord(stage))
        sampled_control   = random.sample(control,   min(n_ctrl, len(control)))
        sampled_treatment = random.sample(treatment, min(n_trt,  len(treatment)))

    metrics  = compute_metrics(sampled_control, sampled_treatment)
    decision, reasons = evaluate_gate(metrics)

    print(f"  n_control={metrics.get('n_control')}, n_treatment={metrics.get('n_treatment')}")
    print(f"  avg_questions: ctrl={metrics.get('avg_questions_control')} "
          f"trt={metrics.get('avg_questions_treatment')} "
          f"delta={metrics.get('avg_questions_delta_pct')}%")
    print(f"  p_value={metrics.get('p_value')}, Cohen's d={metrics.get('cohens_d')}")
    print(f"  premature_stop_rate={metrics.get('premature_stop_rate')}")
    print(f"  Decision: {decision}")
    for r in reasons:
        print(f"    → {r}")

    return {
        "stage": stage,
        "traffic_pct": int(frac * 100),
        "data_source": "mysql_live" if live else "sqlite_replay",
        "timestamp": datetime.now(timezone.utc).isoformat() + "Z",
        "metrics": metrics,
        "decision": decision,
        "rationale": reasons,
    }


def append_to_registry(stage_result: dict):
    """Append stage result as a new row in experiment_registry.csv."""
    import csv
    stage = stage_result["stage"]
    m = stage_result.get("metrics", {})
    source = stage_result.get("data_source", "unknown")
    exp_id = "week22_live_stage" + stage.lower() + "_" + datetime.now(timezone.utc).strftime("%Y%m%d") \
             if source == "mysql_live" \
             else "week21_ramp_stage" + stage.lower() + "_" + datetime.now(timezone.utc).strftime("%Y%m%d")

    import json as _json
    config_params = _json.dumps({
        "pass_threshold": 0.85,
        "fail_threshold": 0.10,
        "min_questions": "4/5/6",
        "stage": stage,
        "traffic_pct": stage_result["traffic_pct"],
        "decision": stage_result["decision"],
        "data_source": source,
    })

    note = ("Week 22 Live Stage " if source == "mysql_live" else "Week 21 Stage ") \
           + stage + " ramp - " + stage_result["decision"] + ": " \
           + (stage_result["rationale"][0] if stage_result["rationale"] else "")

    fields = [
        exp_id,
        stage_result["timestamp"],
        "prediction-v1.0+platt-v2.1",
        "all",
        config_params,
        str(m.get("rmse", "N/A")),
        "N/A", "N/A",
        str(m.get("n_treatment", "N/A")),
        str(m.get("p50_latency_ms", "N/A")),
        str(m.get("p50_latency_ms", "N/A")),
        "N/A",
        str(m.get("p95_latency_ms", "N/A")),
        "N/A",
        str(m.get("premature_stop_rate", "N/A")),
        str(m.get("avg_questions_treatment", "N/A")),
        note,
    ]

    with open(REGISTRY_PATH, "a", newline="", encoding="utf-8") as f:
        writer = csv.writer(f)
        writer.writerow(fields)
    print("  Appended to experiment_registry.csv as " + exp_id)


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def main():
    parser = argparse.ArgumentParser(description="Week 22 controlled ramp validation")
    parser.add_argument("--stage", default="all", choices=["A", "B", "C", "all"])
    parser.add_argument("--live", action="store_true",
                        help="Read from MySQL (requires DB_HOST/DB_PORT/DB_NAME/DB_USERNAME/DB_PASSWORD env vars)")
    parser.add_argument("--output", help="Write JSON report to this path")
    args = parser.parse_args()

    if args.live:
        print("Live mode: reading from MySQL. Ensure DB env vars are set.")
        print("Run preflight first: python preflight_check.py --env staging")

    stages = ["A", "B", "C"] if args.stage == "all" else [args.stage]
    results = []

    for stage in stages:
        result = run_stage(stage, dry_run=not args.live, live=args.live)
        results.append(result)
        append_to_registry(result)

        if result["decision"] in ("HOLD", "ROLLBACK") and args.stage == "all":
            print(f"\n⚠️  Stopping ramp at Stage {stage}: {result['decision']}")
            print("   Fix reported issues before advancing to the next stage.")
            break

    summary = {
        "run_timestamp": datetime.now(timezone.utc).isoformat() + "Z",
        "policy": TREATMENT_POLICY,
        "guardrails": GUARDRAILS,
        "stages": results,
        "final_decision": results[-1]["decision"] if results else "N/A",
    }

    if args.output:
        out = Path(args.output)
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_text(json.dumps(summary, indent=2))
        print(f"\nFull report written to {args.output}")

    print(f"\nFinal decision: {summary['final_decision']}")


if __name__ == "__main__":
    main()
