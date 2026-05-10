#!/usr/bin/env python3
"""
Week 21 Task 1: Production Preflight Check

Validates all prerequisites before running ML experiments or rollout:
- Aiven MySQL connectivity
- OpenAI API key validity
- Required environment variables
- SQLite fallback detection (must NOT be used in production runs)

Usage:
    python preflight_check.py
    python preflight_check.py --env prod
    python preflight_check.py --output docs/week21_production_unblock_report.md
"""

import argparse
import os
import sys
import json
import time
from datetime import datetime, timezone

# --- Checklist item result ---
PASS = "PASS"
FAIL = "FAIL"
WARN = "WARN"


def check_env_vars() -> list[dict]:
    required = [
        "OPENAI_API_KEY",
        "DB_HOST",
        "DB_PORT",
        "DB_NAME",
        "DB_USERNAME",
        "DB_PASSWORD",
    ]
    optional = [
        "OPENAI_CHAT_MODEL",
        "OPENAI_EMBEDDING_MODEL",
        "ML_EARLY_STOP_PASS_THRESHOLD",
        "ML_EARLY_STOP_FAIL_THRESHOLD",
        "ML_EARLY_STOP_MIN_QUESTIONS",
    ]
    results = []

    for var in required:
        val = os.environ.get(var, "")
        if not val:
            results.append({"check": f"ENV:{var}", "status": FAIL, "detail": "not set"})
        elif var == "OPENAI_API_KEY" and val.startswith("sk-") and len(val) < 20:
            results.append({"check": f"ENV:{var}", "status": WARN, "detail": "key looks short"})
        elif var == "OPENAI_API_KEY" and not val.startswith("sk-"):
            results.append({"check": f"ENV:{var}", "status": WARN, "detail": "unexpected key format"})
        else:
            results.append({"check": f"ENV:{var}", "status": PASS, "detail": "set"})

    for var in optional:
        val = os.environ.get(var, "")
        status = PASS if val else WARN
        results.append({"check": f"ENV:{var} (optional)", "status": status,
                         "detail": val if val else "not set (will use default)"})

    return results


def check_min_questions_config() -> list[dict]:
    """Validate slice-aware min_questions env vars are set and within valid range.

    Remediation for drill report item #1: junior min_questions was not validated
    at startup, causing a premature-stop misconfiguration to go undetected.
    """
    slice_vars = {
        "ML_EARLY_STOP_NEW_MIN_QUESTIONS_JUNIOR": (3, 8),
        "ML_EARLY_STOP_NEW_MIN_QUESTIONS_MID":    (4, 10),
        "ML_EARLY_STOP_NEW_MIN_QUESTIONS_SENIOR": (5, 12),
    }
    results = []
    for var, (lo, hi) in slice_vars.items():
        val = os.environ.get(var, "")
        if not val:
            results.append({"check": f"MinQ:{var}", "status": WARN,
                             "detail": f"not set; will use default. Set to enforce slice guardrail."})
            continue
        try:
            n = int(val)
            if n < lo or n > hi:
                results.append({"check": f"MinQ:{var}", "status": FAIL,
                                 "detail": f"value {n} outside valid range [{lo},{hi}]"})
            else:
                results.append({"check": f"MinQ:{var}", "status": PASS,
                                 "detail": f"{n} (valid range {lo}-{hi})"})
        except ValueError:
            results.append({"check": f"MinQ:{var}", "status": FAIL,
                             "detail": f"non-integer value: {val!r}"})
    return results


def check_mysql() -> dict:
    try:
        import mysql.connector
    except ImportError:
        return {"check": "MySQL:connectivity", "status": FAIL,
                "detail": "mysql-connector-python not installed; run: pip install mysql-connector-python"}

    host = os.environ.get("DB_HOST", "")
    port = int(os.environ.get("DB_PORT", "3306"))
    name = os.environ.get("DB_NAME", "")
    user = os.environ.get("DB_USERNAME", "")
    pwd  = os.environ.get("DB_PASSWORD", "")

    if not all([host, name, user, pwd]):
        return {"check": "MySQL:connectivity", "status": FAIL,
                "detail": "DB env vars incomplete, skipping connection test"}

    try:
        start = time.time()
        conn = mysql.connector.connect(
            host=host, port=port, database=name,
            user=user, password=pwd,
            connection_timeout=5, ssl_disabled=False
        )
        latency_ms = int((time.time() - start) * 1000)
        conn.close()
        return {"check": "MySQL:connectivity", "status": PASS,
                "detail": f"connected to {host}:{port}/{name} in {latency_ms}ms"}
    except Exception as e:
        return {"check": "MySQL:connectivity", "status": FAIL, "detail": str(e)}


def check_openai() -> dict:
    api_key = os.environ.get("OPENAI_API_KEY", "")
    if not api_key:
        return {"check": "OpenAI:api_key_valid", "status": FAIL, "detail": "OPENAI_API_KEY not set"}

    try:
        from openai import OpenAI
        client = OpenAI(api_key=api_key)
        start = time.time()
        # Minimal call: list models (doesn't consume tokens)
        client.models.list()
        latency_ms = int((time.time() - start) * 1000)
        return {"check": "OpenAI:api_key_valid", "status": PASS,
                "detail": f"API key valid, models endpoint responded in {latency_ms}ms"}
    except Exception as e:
        return {"check": "OpenAI:api_key_valid", "status": FAIL, "detail": str(e)}


def check_no_sqlite_fallback() -> dict:
    """Confirm the Spring Boot backend is not defaulting to SQLite.

    Note: eval scripts (run_ramp_validation.py) read from SQLite ab_experiment.db
    for metric computation. This is separate from the backend data path.
    Live ramp execution requires the backend to be running with DB_HOST set so
    that session data is written to MySQL, not a local SQLite file.
    """
    db_host = os.environ.get("DB_HOST", "")
    if not db_host:
        return {"check": "SQLite:fallback_guard", "status": FAIL,
                "detail": "DB_HOST not set; backend will fall back to SQLite — blocked for prod-like runs"}
    return {"check": "SQLite:fallback_guard", "status": PASS,
            "detail": "DB_HOST present; backend MySQL path active. Note: eval metric scripts read SQLite ab_experiment.db independently."}


def check_feature_cache_freshness() -> list[dict]:
    """Check that ML feature caches in MySQL are populated before live ramp.

    Empty caches cause cold-start prediction fallback during live sessions.
    Run this check after MySQL connectivity is confirmed.
    """
    host = os.environ.get("DB_HOST", "")
    if not host:
        return [{"check": "FeatureCache:skipped", "status": WARN,
                 "detail": "DB_HOST not set; skipping feature cache checks"}]

    try:
        import mysql.connector
    except ImportError:
        return [{"check": "FeatureCache:skipped", "status": WARN,
                 "detail": "mysql-connector-python not installed; skipping feature cache checks"}]

    port = int(os.environ.get("DB_PORT", "3306"))
    name = os.environ.get("DB_NAME", "")
    user = os.environ.get("DB_USERNAME", "")
    pwd  = os.environ.get("DB_PASSWORD", "")

    if not all([name, user, pwd]):
        return [{"check": "FeatureCache:skipped", "status": WARN,
                 "detail": "DB credentials incomplete; skipping feature cache checks"}]

    try:
        conn = mysql.connector.connect(
            host=host, port=port, database=name,
            user=user, password=pwd,
            connection_timeout=5, ssl_disabled=False
        )
        cursor = conn.cursor()
        results = []

        # Check 1: response_feature_cache completeness (>= 50% of interview count)
        cursor.execute("SELECT COUNT(*) FROM response_feature_cache")
        cache_count = cursor.fetchone()[0]
        cursor.execute("SELECT COUNT(*) FROM interview")
        interview_count = cursor.fetchone()[0]
        coverage = (cache_count / interview_count * 100) if interview_count > 0 else 0.0
        if cache_count == 0:
            results.append({"check": "FeatureCache:response_feature_cache", "status": FAIL,
                            "detail": f"0 rows — predictions will use cold-start fallback. Populate before ramp."})
        elif coverage < 50.0:
            results.append({"check": "FeatureCache:response_feature_cache", "status": WARN,
                            "detail": f"{cache_count} rows / {interview_count} interviews ({coverage:.0f}% coverage) — below 50% threshold"})
        else:
            results.append({"check": "FeatureCache:response_feature_cache", "status": PASS,
                            "detail": f"{cache_count} rows ({coverage:.0f}% coverage)"})

        # Check 2: question_embedding availability
        cursor.execute("SELECT COUNT(*) FROM question_embedding")
        emb_count = cursor.fetchone()[0]
        if emb_count == 0:
            results.append({"check": "FeatureCache:question_embedding", "status": FAIL,
                            "detail": "0 rows — embedding similarity features unavailable. Run embedding refresh job."})
        else:
            results.append({"check": "FeatureCache:question_embedding", "status": PASS,
                            "detail": f"{emb_count} embeddings available"})

        # Check 3: topic_coverage freshness
        cursor.execute("SELECT COUNT(*) FROM topic_coverage")
        topic_count = cursor.fetchone()[0]
        if topic_count == 0:
            results.append({"check": "FeatureCache:topic_coverage", "status": WARN,
                            "detail": "0 rows — topic diversity not tracked. Run topic coverage refresh."})
        else:
            results.append({"check": "FeatureCache:topic_coverage", "status": PASS,
                            "detail": f"{topic_count} topics tracked"})

        conn.close()
        return results

    except Exception as e:
        return [{"check": "FeatureCache:error", "status": WARN,
                 "detail": f"Could not query feature cache tables: {e}"}]


def check_feature_cache_drift(snapshot_path: str = "eval/results/feature_cache_snapshot.json") -> list[dict]:
    """Detect feature cache population drift by comparing current row counts against a saved snapshot.

    A drift of > 15% drop in any cache table triggers a WARN so that cache refresh
    failures are caught before they silently degrade prediction quality.
    The snapshot is written by run_preflight after a successful cache check; if no
    snapshot exists, this check is skipped with a WARN.
    """
    import pathlib

    snapshot_file = pathlib.Path(snapshot_path)
    if not snapshot_file.exists():
        return [{"check": "FeatureCache:drift", "status": WARN,
                 "detail": f"No snapshot found at {snapshot_path}; skipping drift check. "
                            "Snapshot will be written after first successful preflight."}]

    try:
        with open(snapshot_file, encoding="utf-8") as f:
            snapshot = json.load(f)
    except (json.JSONDecodeError, OSError) as e:
        return [{"check": "FeatureCache:drift", "status": WARN,
                 "detail": f"Could not read snapshot at {snapshot_path}: {e}"}]

    host = os.environ.get("DB_HOST", "")
    if not host:
        return [{"check": "FeatureCache:drift", "status": WARN,
                 "detail": "DB_HOST not set; skipping drift check"}]

    try:
        import mysql.connector
    except ImportError:
        return [{"check": "FeatureCache:drift", "status": WARN,
                 "detail": "mysql-connector-python not installed; skipping drift check"}]

    port = int(os.environ.get("DB_PORT", "3306"))
    name = os.environ.get("DB_NAME", "")
    user = os.environ.get("DB_USERNAME", "")
    pwd  = os.environ.get("DB_PASSWORD", "")

    if not all([name, user, pwd]):
        return [{"check": "FeatureCache:drift", "status": WARN,
                 "detail": "DB credentials incomplete; skipping drift check"}]

    try:
        conn = mysql.connector.connect(
            host=host, port=port, database=name,
            user=user, password=pwd,
            connection_timeout=5, ssl_disabled=False
        )
        cursor = conn.cursor()
        results = []
        tables = ["response_feature_cache", "question_embedding", "topic_coverage"]
        drift_threshold = 0.15  # 15% drop triggers WARN

        for table in tables:
            cursor.execute(f"SELECT COUNT(*) FROM {table}")
            current_count = cursor.fetchone()[0]
            snapshot_count = snapshot.get(table, 0)

            if snapshot_count == 0:
                # No baseline to compare; skip this table
                continue

            drop_pct = (snapshot_count - current_count) / snapshot_count
            if drop_pct > drift_threshold:
                results.append({
                    "check": f"FeatureCache:drift:{table}",
                    "status": WARN,
                    "detail": (
                        f"Coverage dropped {drop_pct:.0%} from snapshot "
                        f"({snapshot_count} rows -> {current_count} rows). "
                        "Run cache refresh job and investigate."
                    )
                })
            else:
                results.append({
                    "check": f"FeatureCache:drift:{table}",
                    "status": PASS,
                    "detail": f"{current_count} rows (snapshot: {snapshot_count}; drift: {drop_pct:+.0%})"
                })

        conn.close()
        return results if results else [{"check": "FeatureCache:drift", "status": PASS,
                                         "detail": "No drift detected in tracked cache tables"}]

    except Exception as e:
        return [{"check": "FeatureCache:drift", "status": WARN,
                 "detail": f"Could not check drift: {e}"}]


def run_preflight(env: str = "local") -> dict:
    print(f"\n=== Week 21 Preflight Check (env={env}) ===\n")
    results = []

    results.extend(check_env_vars())
    results.extend(check_min_questions_config())
    results.append(check_mysql())
    results.append(check_openai())
    results.append(check_no_sqlite_fallback())
    cache_results = check_feature_cache_freshness()
    results.extend(cache_results)
    results.extend(check_feature_cache_drift())

    pass_count  = sum(1 for r in results if r["status"] == PASS)
    fail_count  = sum(1 for r in results if r["status"] == FAIL)
    warn_count  = sum(1 for r in results if r["status"] == WARN)

    overall = "UNBLOCKED" if fail_count == 0 else "BLOCKED"

    for r in results:
        icon = {"PASS": "✅", "FAIL": "❌", "WARN": "⚠️"}.get(r["status"], "?")
        print(f"  {icon} [{r['status']}] {r['check']}: {r['detail']}")

    print(f"\nResult: {overall}  (pass={pass_count}, warn={warn_count}, fail={fail_count})")

    report = {
        "timestamp": datetime.now(timezone.utc).isoformat() + "Z",
        "env": env,
        "overall": overall,
        "pass": pass_count,
        "warn": warn_count,
        "fail": fail_count,
        "checks": results,
    }

    # Write feature cache snapshot on successful preflight so drift checks have a baseline
    if overall == "UNBLOCKED":
        _write_feature_cache_snapshot(cache_results)

    return report


def _write_feature_cache_snapshot(cache_results: list[dict],
                                   snapshot_path: str = "eval/results/feature_cache_snapshot.json") -> None:
    """Persist current feature cache row counts as a baseline for drift detection."""
    import pathlib, re

    snapshot: dict = {}
    tables = ["response_feature_cache", "question_embedding", "topic_coverage"]
    for result in cache_results:
        check_name = result.get("check", "")
        detail = result.get("detail", "")
        for table in tables:
            if table in check_name:
                # Parse row count from detail string, e.g. "23 rows (78% coverage)"
                match = re.search(r"(\d+)\s+rows?", detail)
                if match:
                    snapshot[table] = int(match.group(1))
                break

    if snapshot:
        try:
            out = pathlib.Path(snapshot_path)
            out.parent.mkdir(parents=True, exist_ok=True)
            snapshot["written_at"] = datetime.now(timezone.utc).isoformat() + "Z"
            out.write_text(json.dumps(snapshot, indent=2))
        except OSError:
            pass  # Snapshot write failure is non-fatal


def main():
    parser = argparse.ArgumentParser(description="Week 21 production preflight check")
    parser.add_argument("--env", default="local", choices=["local", "staging", "prod"])
    parser.add_argument("--output", help="Write JSON report to this path")
    args = parser.parse_args()

    report = run_preflight(args.env)

    if args.output:
        import pathlib
        out = pathlib.Path(args.output)
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_text(json.dumps(report, indent=2))
        print(f"\nReport written to {args.output}")

    sys.exit(0 if report["overall"] == "UNBLOCKED" else 1)


if __name__ == "__main__":
    main()
