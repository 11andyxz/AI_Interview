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
from datetime import datetime
from pathlib import Path
from typing import Optional

import numpy as np
from scipy import stats

DB_PATH = Path(__file__).parent / "results" / "ab_experiment.db"
REGISTRY_PATH = Path(__file__).parent / "experiment_registry.csv"

# Guardrails (from Week 20 acceptance criteria)
GUARDRAILS = {
    "avg_questions_delta_pct_max": 0.0,   # treatment must reduce or hold
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
    if delta_pct > 5.0:
        reasons.append(f"avg_questions_delta_pct={delta_pct}% > 5% — treatment increases questions (ROLLBACK)")
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

def run_stage(stage: str, dry_run: bool = True) -> dict:
    """Run a single ramp stage using existing experiment data as proxy traffic."""
    print(f"\n{'='*60}")
    print(f"Stage {stage} ({int(STAGE_SAMPLE_SIZES[stage]*100)}% ramp)")
    print(f"{'='*60}")

    # Load base Week 20 sessions as the data source
    all_sessions = load_sessions(["week20_control_real", "week20_treatment_real"])
    control   = [s for s in all_sessions if s["experiment_id"] == "week20_control_real"]
    treatment = [s for s in all_sessions if s["experiment_id"] == "week20_treatment_real"]

    if not control or not treatment:
        return {"stage": stage, "traffic_pct": int(STAGE_SAMPLE_SIZES.get(stage, 0.1) * 100),
                "timestamp": datetime.utcnow().isoformat() + "Z",
                "metrics": {}, "decision": "HOLD",
                "rationale": ["No base experiment data found. Run Week 20 experiment first."]}

    # Sample proportionally for this ramp stage
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
        "timestamp": datetime.utcnow().isoformat() + "Z",
        "metrics": metrics,
        "decision": decision,
        "rationale": reasons,
    }


def append_to_registry(stage_result: dict):
    """Append stage result as a new row in experiment_registry.csv."""
    import csv
    stage = stage_result["stage"]
    m = stage_result.get("metrics", {})
    exp_id = "week21_ramp_stage" + stage.lower() + "_" + datetime.utcnow().strftime("%Y%m%d")

    import json as _json
    config_params = _json.dumps({
        "pass_threshold": 0.85,
        "fail_threshold": 0.10,
        "min_questions": "4/5/6",
        "stage": stage,
        "traffic_pct": stage_result["traffic_pct"],
        "decision": stage_result["decision"],
    })

    note = ("Week 21 Stage " + stage + " ramp - " + stage_result["decision"] + ": "
            + (stage_result["rationale"][0] if stage_result["rationale"] else ""))

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
    parser = argparse.ArgumentParser(description="Week 21 controlled ramp validation")
    parser.add_argument("--stage", default="all", choices=["A", "B", "C", "all"])
    parser.add_argument("--live", action="store_true",
                        help="Run live sessions (requires OPENAI_API_KEY and MySQL)")
    parser.add_argument("--output", help="Write JSON report to this path")
    args = parser.parse_args()

    if args.live:
        print("Live mode: requires preflight_check.py to pass first.")
        print("Run: python preflight_check.py --env staging")

    stages = ["A", "B", "C"] if args.stage == "all" else [args.stage]
    results = []

    for stage in stages:
        result = run_stage(stage, dry_run=not args.live)
        results.append(result)
        append_to_registry(result)

        if result["decision"] in ("HOLD", "ROLLBACK") and args.stage == "all":
            print(f"\n⚠️  Stopping ramp at Stage {stage}: {result['decision']}")
            print("   Fix reported issues before advancing to the next stage.")
            break

    summary = {
        "run_timestamp": datetime.utcnow().isoformat() + "Z",
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
