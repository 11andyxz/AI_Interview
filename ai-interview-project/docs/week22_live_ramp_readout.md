# Week 22 Task 2: Real Traffic Ramp Execution — Live Ramp Readout

**Date**: 2026-04-27 (Stage A run executed 2026-04-27; Stages B/C pending more traffic)  
**Owner**: Yukun Song  
**Policy**: threshold=0.85, fail_threshold=0.10, slice-aware min_questions (junior=4, mid=5, senior=6)  
**Status**: Stage A EXECUTED — HOLD (insufficient treatment sessions, n_treatment=0)

---

## Objective

Execute the staged early-stop rollout on real traffic and make a defensible go/hold/rollback decision using measured production signals. Compare against Week 20 empirical and Week 21 replay baselines.

---

## Ramp Plan

| Stage | Date | Traffic % | Min Treatment Sessions | Decision Gate |
|-------|------|-----------|----------------------|---------------|
| A | May 5 | 10% | ≥ 20 | Go / Hold / Rollback |
| B | May 6 AM | 50% | ≥ 60 | Go / Hold / Rollback |
| C | May 6 PM | 100% | ≥ 120 | Final go/hold/rollback |

Stage B proceeds only if Stage A gates all pass.  
Stage C proceeds only if Stage A and Stage B gates all pass.

---

## Guardrails (all stages)

| Metric | Threshold | Rationale |
|--------|-----------|-----------|
| avg_questions_delta_pct | ≤ +5% | Treatment must not increase question burden |
| premature_stop_rate | < 3% | Min-question guardrail must be respected |
| p95_latency_ms | < 3000 ms | Based on Week 20 p95 ~2186ms + buffer |
| RMSE (overall) | < 15.0 | Week 16 baseline acceptance criterion |
| OpenAI error rate | < 5% | API reliability guardrail |
| junior_rmse | ≤ 45.0 | Week 21 recalibrated guardrail |

**Additional Stage A gate**: `n_treatment ≥ 20` before guardrail evaluation. If sample below threshold, hold and collect more traffic; do not ROLLBACK on low-power noise.

---

## Pre-Ramp Checklist (complete before May 5)

- [ ] `preflight_check.py --env staging` exits 0 (UNBLOCKED)
- [ ] OpenAI API key rotated and set in env
- [ ] Slice-aware min_questions env vars set (junior=4, mid=5, senior=6)
- [ ] Feature cache populated (`response_feature_cache` count > 0)
- [ ] Experiment registry CSV writable and backed up
- [ ] Rollback path verified: feature flag or threshold config confirmed switchable

---

## Stage A — 10% Ramp (Executed 2026-04-27)

**Command run**:
```bash
python eval/run_ramp_validation.py --stage A --live \
  --output eval/results/week22_live_stagea_result.json
```

**Data source**: MySQL live (`mysql-4c9be66-andyxiongzheng-9267.g.aivencloud.com:22629/ai_interview`)

| Metric | Result | Guardrail | Gate |
|--------|--------|-----------|------|
| n_control | 3 | — | — |
| n_treatment | **0** | ≥ 20 | HOLD |
| avg_questions_delta_pct | N/A | ≤ +5% | N/A |
| premature_stop_rate | N/A | < 3% | N/A |
| p95_latency_ms | N/A | < 3000 | N/A |
| RMSE (overall) | N/A | < 15.0 | N/A |

**Decision**: **HOLD**  
**Timestamp**: 2026-04-27T (live run)  
**Rationale**: n_treatment=0 < 20 required minimum. The 3 control sessions (all created before Apr 1) are March historical sessions. No completed sessions exist in the April treatment window. The pipeline executed end-to-end against MySQL — no SQLite fallback used. Stage A will re-run automatically once 20+ treatment sessions complete.

**Artifact**: `eval/results/week22_live_stagea_result.json`

---

## Stage B — 50% Ramp (May 6 AM, only if Stage A GO)

**Command**:
```bash
python eval/run_ramp_validation.py --stage B --live \
  --output eval/results/week22_stage_b_live.json
```

| Metric | Result | Guardrail | Gate |
|--------|--------|-----------|------|
| n_control | _TBD_ | — | — |
| n_treatment | _TBD_ | ≥ 60 | — |
| avg_questions_delta_pct | _TBD_ | ≤ +5% | _TBD_ |
| premature_stop_rate | _TBD_ | < 3% | _TBD_ |
| p95_latency_ms | _TBD_ | < 3000 | _TBD_ |
| OpenAI error_rate | _TBD_ | < 5% | _TBD_ |
| RMSE (overall) | _TBD_ | < 15.0 | _TBD_ |
| junior_rmse | _TBD_ | ≤ 45.0 | _TBD_ |

**Decision**: _TBD_  
**Timestamp**: _TBD_  
**Rationale**: _TBD_

---

## Stage C — 100% Ramp (May 6 PM, only if Stage A+B GO)

**Command**:
```bash
python eval/run_ramp_validation.py --stage C --live \
  --output eval/results/week22_stage_c_live.json
```

| Metric | Result | Guardrail | Gate |
|--------|--------|-----------|------|
| n_total | _TBD_ | ≥ 120 | — |
| avg_questions_delta_pct | _TBD_ | ≤ +5% | _TBD_ |
| premature_stop_rate | _TBD_ | < 3% | _TBD_ |
| p95_latency_ms | _TBD_ | < 3000 | _TBD_ |
| RMSE (overall) | _TBD_ | < 15.0 | _TBD_ |
| junior_rmse | _TBD_ | ≤ 45.0 | _TBD_ |

**Decision**: _TBD_  
**Timestamp**: _TBD_

---

## Rollback Procedure

If any stage returns ROLLBACK:

```bash
# 1. Revert threshold config to baseline (0.95/0.05, min_questions=5)
export ML_EARLY_STOP_PASS_THRESHOLD=0.95
export ML_EARLY_STOP_FAIL_THRESHOLD=0.05
export ML_EARLY_STOP_MIN_QUESTIONS=5

# 2. Restart backend with baseline config
# 3. Verify premature_stop_rate returns to 0% within 10 minutes
# 4. Record rollback timestamp and reason in this document
```

---

## Comparison to Baselines

| Metric | Week 20 Empirical | Week 21 Replay Stage B | Week 22 Live Stage A | Week 22 Live Stage B |
|--------|-------------------|----------------------|---------------------|---------------------|
| avg_questions_delta_pct | -2.8% | -5.8% | HOLD (n=0) | _TBD_ |
| premature_stop_rate | 0% | 0% | HOLD (n=0) | _TBD_ |
| p95_latency_ms | 2186 | 1983 | HOLD (n=0) | _TBD_ |
| n_treatment | 60 | 30 | 0 (need 20+) | _TBD_ |

---

## Feature Flag / Rollback Verification Note

The early-stop policy is controlled via Spring Boot environment variables:
- `ML_EARLY_STOP_PASS_THRESHOLD` — promote to pass decision
- `ML_EARLY_STOP_FAIL_THRESHOLD` — promote to fail decision
- `ML_EARLY_STOP_NEW_MIN_QUESTIONS_JUNIOR/MID/SENIOR` — slice-aware floor

**Rollback procedure** (no code change required):
```bash
# Revert to conservative baseline values:
export ML_EARLY_STOP_PASS_THRESHOLD=0.95
export ML_EARLY_STOP_FAIL_THRESHOLD=0.05
export ML_EARLY_STOP_MIN_QUESTIONS=5
# Restart backend. No deploy required — env-var driven.
```

**Verified**: `eval/preflight_check.py` `SQLite:fallback_guard` check confirms `DB_HOST` is set and MySQL path is active before any ramp stage runs. Backend Spring Boot env-var config path is the same path used by the live ramp.

---

## Deliverables

| Artifact | Path | Status |
|----------|------|--------|
| Live ramp readout | `docs/week22_live_ramp_readout.md` | ✅ This document |
| Stage A artifact | `eval/results/week22_live_stagea_result.json` | ✅ Executed 2026-04-27 (HOLD) |
| Stage B artifact | `eval/results/week22_stage_b_live.json` | Pending n_treatment >= 60 |
| Stage C artifact | `eval/results/week22_stage_c_live.json` | Pending Stage B GO |
| Registry entries | `eval/experiment_registry.csv` | ✅ Live entries present |
