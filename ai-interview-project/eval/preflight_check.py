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
from datetime import datetime

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
    """Confirm experiment scripts are not defaulting to SQLite."""
    import pathlib
    db_host = os.environ.get("DB_HOST", "")
    if not db_host:
        return {"check": "SQLite:fallback_guard", "status": FAIL,
                "detail": "DB_HOST not set; scripts will fall back to SQLite — blocked for prod-like runs"}
    return {"check": "SQLite:fallback_guard", "status": PASS,
            "detail": "DB_HOST present; MySQL path will be used"}


def run_preflight(env: str = "local") -> dict:
    print(f"\n=== Week 21 Preflight Check (env={env}) ===\n")
    results = []

    results.extend(check_env_vars())
    results.extend(check_min_questions_config())
    results.append(check_mysql())
    results.append(check_openai())
    results.append(check_no_sqlite_fallback())

    pass_count  = sum(1 for r in results if r["status"] == PASS)
    fail_count  = sum(1 for r in results if r["status"] == FAIL)
    warn_count  = sum(1 for r in results if r["status"] == WARN)

    overall = "UNBLOCKED" if fail_count == 0 else "BLOCKED"

    for r in results:
        icon = {"PASS": "✅", "FAIL": "❌", "WARN": "⚠️"}.get(r["status"], "?")
        print(f"  {icon} [{r['status']}] {r['check']}: {r['detail']}")

    print(f"\nResult: {overall}  (pass={pass_count}, warn={warn_count}, fail={fail_count})")

    return {
        "timestamp": datetime.utcnow().isoformat() + "Z",
        "env": env,
        "overall": overall,
        "pass": pass_count,
        "warn": warn_count,
        "fail": fail_count,
        "checks": results,
    }


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
