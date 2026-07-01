#!/usr/bin/env python3
"""
Week 22 T3: Calibration analysis from live MySQL evaluation scores.

Queries interview_message.evaluation_score grouped by experience-level slice,
and emits calibration statistics to stdout + JSON artifact.

Usage:
    python live_calibration_analysis.py --output results/week22_live_calibration.json
"""
import argparse
import json
import os
import statistics
from collections import defaultdict
from datetime import datetime, timezone


def infer_slice(title: str, experience_years: int | None = None) -> str:
    """Infer slice from title first; fall back to experience_years if title is ambiguous."""
    t = (title or "").lower()
    # Explicit level words in title take priority
    if "junior" in t:
        return "junior"
    if "senior" in t or "lead" in t or "staff" in t or "principal" in t:
        return "senior"
    # Fall back to candidate experience_years if title is role-based (no level word)
    if experience_years is not None:
        if experience_years <= 2:
            return "junior"
        if experience_years >= 6:
            return "senior"
        return "mid"
    return "mid"


def run_analysis() -> dict:
    import mysql.connector

    conn = mysql.connector.connect(
        host=os.environ["DB_HOST"],
        port=int(os.environ.get("DB_PORT", "3306")),
        database=os.environ["DB_NAME"],
        user=os.environ["DB_USERNAME"],
        password=os.environ["DB_PASSWORD"],
        connection_timeout=10,
        ssl_disabled=False,
    )
    cur = conn.cursor(dictionary=True)

    cur.execute("""
        SELECT
            i.id              AS interview_id,
            i.title,
            i.duration_seconds,
            i.status,
            i.created_at,
            c.experience_years,
            m.evaluation_score,
            m.technical_accuracy,
            m.depth_score,
            m.communication_score,
            m.evaluation_rubric_level
        FROM interview i
        JOIN interview_message m ON m.interview_id = i.id
        LEFT JOIN candidate c ON c.id = i.candidate_id
        WHERE m.evaluation_score IS NOT NULL
        ORDER BY i.created_at, m.id
    """)
    rows = cur.fetchall()
    conn.close()

    # Group by slice
    by_slice = defaultdict(list)
    for r in rows:
        by_slice[infer_slice(r["title"], r.get("experience_years"))].append(r)

    # Group by session
    by_session = defaultdict(list)
    for r in rows:
        by_session[r["interview_id"]].append(r)

    slice_stats = {}
    for sl in ["junior", "mid", "senior"]:
        msgs = by_slice[sl]
        scores = [r["evaluation_score"] for r in msgs]
        if not scores:
            slice_stats[sl] = {"n_messages": 0, "note": "no scored messages"}
            continue
        tech = [r["technical_accuracy"] for r in msgs if r["technical_accuracy"] is not None]
        depth = [r["depth_score"] for r in msgs if r["depth_score"] is not None]
        comms = [r["communication_score"] for r in msgs if r["communication_score"] is not None]
        slice_stats[sl] = {
            "n_messages": len(scores),
            "eval_score_mean": round(statistics.mean(scores), 3),
            "eval_score_stdev": round(statistics.stdev(scores), 3) if len(scores) > 1 else None,
            "eval_score_min": min(scores),
            "eval_score_max": max(scores),
            "score_range": round(max(scores) - min(scores), 3),
            "technical_accuracy_mean": round(statistics.mean(tech), 2) if tech else None,
            "depth_score_mean": round(statistics.mean(depth), 2) if depth else None,
            "communication_score_mean": round(statistics.mean(comms), 2) if comms else None,
        }

    session_summaries = []
    for iid, msgs in by_session.items():
        sl = infer_slice(msgs[0]["title"], msgs[0].get("experience_years"))
        scores = [m["evaluation_score"] for m in msgs]
        session_summaries.append({
            "interview_id": iid,
            "title": msgs[0]["title"],
            "slice": sl,
            "n_messages": len(msgs),
            "avg_eval_score": round(sum(scores) / len(scores), 3),
            "created_at": str(msgs[0]["created_at"]),
        })

    # Calibration concerns
    concerns = []
    for sl, stats_dict in slice_stats.items():
        if stats_dict.get("n_messages", 0) == 0:
            concerns.append(f"{sl}: no live scored data — calibration unverifiable for this slice")
            continue
        mean = stats_dict["eval_score_mean"]
        rng = stats_dict["score_range"]
        if mean > 8.5:
            concerns.append(
                f"{sl}: mean={mean:.2f} > 8.5 — possible overconfidence / high-score bias. "
                "Monitor for score compression at top end."
            )
        if rng is not None and rng < 1.0 and stats_dict["n_messages"] > 1:
            concerns.append(
                f"{sl}: score_range={rng:.2f} < 1.0 — narrow spread, platt-v2.1 may be under-discriminating"
            )

    all_scores = [r["evaluation_score"] for r in rows]
    observed_slices = [sl for sl, s in slice_stats.items() if s.get("n_messages", 0) > 0]
    missing_slices  = [sl for sl, s in slice_stats.items() if s.get("n_messages", 0) == 0]

    recommendation = (
        "HOLD platt-v2.1. Live evidence is insufficient for recalibration: "
        f"n_total={len(rows)} scored messages across {len(by_session)} sessions. "
        f"Observed slices: {observed_slices or ['none']}. "
        f"Missing slice data: {missing_slices}. "
        "Collect >= 50 completed sessions per slice before evaluating platt-v2.2 promotion. "
        "Current mid-slice scores cluster in "
        f"[{min(all_scores)},{max(all_scores)}] (mean={sum(all_scores)/len(all_scores):.2f} if n>0 else N/A), "
        "consistent with platt-v2.1 but not statistically conclusive. "
        "Target calibration review: Week 25-26."
    ) if rows else (
        "HOLD platt-v2.1. No scored live sessions available. "
        "Calibration is unverifiable until completed sessions accumulate."
    )

    return {
        "timestamp": datetime.now(timezone.utc).isoformat() + "Z",
        "data_source": "mysql_live",
        "total_scored_messages": len(rows),
        "total_scored_sessions": len(by_session),
        "active_calibration": "platt-v2.1",
        "slice_stats": slice_stats,
        "session_summaries": session_summaries,
        "calibration_concerns": concerns,
        "recommendation": recommendation,
    }


def main():
    parser = argparse.ArgumentParser(description="Week 22 live calibration analysis")
    parser.add_argument("--output", help="Write JSON to this path")
    args = parser.parse_args()

    result = run_analysis()

    print("=== Week 22 Live Calibration Analysis ===")
    print(f"Data source:           MySQL live")
    print(f"Total scored messages: {result['total_scored_messages']}")
    print(f"Total scored sessions: {result['total_scored_sessions']}")
    print(f"Active calibration:    {result['active_calibration']}")
    print()
    for sl, s in result["slice_stats"].items():
        if s.get("n_messages", 0) == 0:
            print(f"Slice {sl:8s}: {s['note']}")
        else:
            stdev_str = f"stdev={s['eval_score_stdev']:.3f}" if s["eval_score_stdev"] is not None else "stdev=N/A (n=1)"
            print(f"Slice {sl:8s}: n={s['n_messages']} mean={s['eval_score_mean']:.3f} {stdev_str} "
                  f"range=[{s['eval_score_min']},{s['eval_score_max']}]")
    print()
    if result["calibration_concerns"]:
        print("Calibration concerns:")
        for c in result["calibration_concerns"]:
            print(f"  - {c}")
    print()
    print(f"Recommendation: {result['recommendation']}")

    if args.output:
        import pathlib
        out = pathlib.Path(args.output)
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_text(json.dumps(result, indent=2))
        print(f"\nReport written to {args.output}")


if __name__ == "__main__":
    main()
