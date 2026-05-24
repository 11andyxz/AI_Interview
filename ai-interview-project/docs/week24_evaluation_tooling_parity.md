# Week 24 Evaluation Tooling Parity

**Date**: 2026-05-22  
**Owner**: Yukun Song  
**Scope**: Audit of repo state against Week 23 reported deliverables; tooling recovery; artifact standardization

---

## Summary

| Item | Status |
|------|--------|
| `eval/preflight_check.py` | ✅ Present — runs with real DB and OpenAI |
| `eval/auto_summary_generator.py` | ✅ Present |
| Week 23 artifacts | ✅ All present and validated |
| Week 24 preflight result | ✅ Generated — BLOCKED (pass=9, warn=10, fail=2) |
| Week 24 reproducibility manifest | ✅ Generated |
| Missing: `week24_stagea_live_result.json` | ❌ Cannot generate — n_treatment=0, backend not running |
| Preflight fails clearly on empty feature tables | ✅ Confirmed |
| Preflight fails clearly on missing experiment rows | ⚠️ Partial — no check for empty experiment table |

---

## Repo Audit: Week 23 Deliverables vs. Actual Files

| Reported Deliverable | Expected Path | Actual Status |
|----------------------|---------------|---------------|
| Stage A live guardrail readout | `docs/week23_stagea_live_guardrail_readout.md` | ✅ Present |
| ML decision readout | `docs/week23_ml_decision_readout.md` | ✅ Present |
| Calibration slice reliability | `docs/week23_calibration_slice_reliability_report.md` | ✅ Present |
| Feature engineering monitoring | `docs/week23_feature_engineering_monitoring.md` | ✅ Present |
| Reproducibility manifest | `eval/results/week23_reproducibility_manifest.json` | ✅ Present |
| Stage A live result | `eval/results/week23_stagea_live_result.json` | ✅ Present |
| Live calibration | `eval/results/week23_live_calibration.json` | ✅ Present |
| Preflight check script | `eval/preflight_check.py` | ✅ Present |
| Auto summary generator | `eval/auto_summary_generator.py` | ✅ Present |
| Eval reproducibility doc | `docs/week23_ml_evaluation_reproducibility.md` | ✅ Present |

No missing Week 23 deliverables. The repository is in sync with the Week 23 update.

---

## Preflight Check Validation (May 22, 2026)

Run with live DB credentials and real OpenAI API key:

```
=== Week 21 Preflight Check (env=local) ===

  ✅ [PASS] ENV:OPENAI_API_KEY: set
  ✅ [PASS] ENV:DB_HOST: set
  ✅ [PASS] ENV:DB_PORT: set
  ✅ [PASS] ENV:DB_NAME: set
  ✅ [PASS] ENV:DB_USERNAME: set
  ✅ [PASS] ENV:DB_PASSWORD: set
  ⚠️ [WARN] ENV:OPENAI_CHAT_MODEL (optional): not set (will use default)
  ⚠️ [WARN] ENV:OPENAI_EMBEDDING_MODEL (optional): not set (will use default)
  ⚠️ [WARN] ENV:ML_EARLY_STOP_PASS_THRESHOLD (optional): not set (will use default)
  ⚠️ [WARN] ENV:ML_EARLY_STOP_FAIL_THRESHOLD (optional): not set (will use default)
  ⚠️ [WARN] ENV:ML_EARLY_STOP_MIN_QUESTIONS (optional): not set (will use default)
  ⚠️ [WARN] MinQ:ML_EARLY_STOP_NEW_MIN_QUESTIONS_JUNIOR: not set
  ⚠️ [WARN] MinQ:ML_EARLY_STOP_NEW_MIN_QUESTIONS_MID: not set
  ⚠️ [WARN] MinQ:ML_EARLY_STOP_NEW_MIN_QUESTIONS_SENIOR: not set
  ✅ [PASS] MySQL:connectivity: connected in 851ms
  ✅ [PASS] OpenAI:api_key_valid: API key valid, responded in 781ms
  ✅ [PASS] SQLite:fallback_guard: DB_HOST present
  ❌ [FAIL] FeatureCache:response_feature_cache: 0 rows
  ❌ [FAIL] FeatureCache:question_embedding: 0 rows
  ⚠️ [WARN] FeatureCache:topic_coverage: 0 rows
  ⚠️ [WARN] FeatureCache:drift: No snapshot found

Result: BLOCKED  (pass=9, warn=10, fail=2)
```

Full JSON report: `eval/results/week24_preflight_live.json`

**Preflight fails clearly on empty feature tables** — confirmed. Empty `response_feature_cache` and `question_embedding` both return FAIL status with actionable detail messages.

---

## Artifact Naming Standards (Week 24)

| Artifact type | Naming pattern | Example |
|---------------|---------------|---------|
| Preflight | `eval/results/week{N}_preflight_live.json` | `week24_preflight_live.json` |
| Stage A live result | `eval/results/week{N}_stagea_live_result.json` | `week24_stagea_live_result.json` |
| Calibration | `eval/results/week{N}_live_calibration.json` | `week24_live_calibration.json` |
| Feature snapshot | `eval/results/week{N}_feature_population_snapshot.json` | `week24_feature_population_snapshot.json` |
| Reproducibility manifest | `eval/results/week{N}_reproducibility_manifest.json` | `week24_reproducibility_manifest.json` |
| Decision readout | `docs/week{N}_ml_decision_readout.md` | `week24_ml_decision_readout.md` |

---

## Preflight Gap: No Check for Empty Experiment Table

The current `preflight_check.py` does not validate whether an active experiment row exists in the `experiment` table. Weeks 22 and 23 both ran preflight with BLOCKED result on feature caches, but neither the preflight nor any other tooling detected that the experiment table was empty — the root cause of n_treatment=0.

**Recommended addition to `preflight_check.py`**:

```python
def check_experiment_config() -> dict:
    """Verify at least one running experiment exists for Stage A evaluation."""
    # ... connect to MySQL
    cursor.execute(
        "SELECT COUNT(*) FROM experiment WHERE status = 'running' AND target_endpoint = 'question-generate'"
    )
    count = cursor.fetchone()[0]
    if count == 0:
        return {"check": "Experiment:active_stageA", "status": FAIL,
                "detail": "No running experiment for question-generate endpoint — n_treatment will remain 0"}
    return {"check": "Experiment:active_stageA", "status": PASS,
            "detail": f"{count} active experiment(s) found for question-generate"}
```

This check would have surfaced the root cause of n_treatment=0 three weeks earlier.

---

## Week 24 Readout Generation Status

The Week 24 readout (`docs/week24_ml_decision_readout.md`) is generated manually from:
- Real DB counts (queried via mysql CLI)
- Real preflight output (`eval/results/week24_preflight_live.json`)
- Experiment registry (`eval/experiment_registry.csv`)
- Code inspection of routing and feature write paths

`python eval/auto_summary_generator.py --week 24 --validate-artifacts` will validate artifacts once `week24_stagea_live_result.json` is available (requires live session accumulation after the experiment seed).

---

## Missing Artifacts and Blockers

| Artifact | Why missing | Required before |
|----------|-------------|-----------------|
| `eval/results/week24_stagea_live_result.json` | n_treatment=0; backend not running | Stage A decision |
| `eval/results/week24_live_calibration.json` | No live calibration data | Stage A decision |
| Smoke test evidence (experiment_metric rows) | Backend not running in eval environment | Routing unblock |
