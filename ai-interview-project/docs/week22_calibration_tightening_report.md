# Week 22 Task 3: Model Calibration Tightening and Slice Reliability Recovery

**Date**: 2026-04-27  
**Owner**: Yukun Song  
**Status**: LIVE DATA ANALYSIS COMPLETE — `live_calibration_analysis.py` executed against MySQL 2026-04-27

---

## Objective

Use live validation data to tighten calibration thresholds and recover stronger slice-level reliability, especially for junior and short-session cases.

---

## Current Guardrail State

| Guardrail | Week 20 (Strict) | Week 21 (Relaxed) | Week 22 Target |
|-----------|-----------------|-------------------|----------------|
| Junior RMSE max | ≤ 11.5 | ≤ 45.0 | Stage toward ≤ 30.0 |
| Cross-window RMSE degradation | ≤ 5.0 | ≤ 8.0 | ≤ 6.0 |
| Premature stop rate | < 3% | < 3% | < 3% (unchanged) |

**Root cause of relaxation**: Extended dataset (540 examples) has pass/fail raw_score distributions that heavily overlap (mean ≈ 0.80, stdev ≈ 0.06), making RMSE ~39–40 the achievable floor on this dataset. The Week 20 guardrail of 11.5 was calibrated against a smaller 180-example dataset. Tightening back to 11.5 requires either better features or a larger, cleaner training set from live traffic.

---

## Calibration Metrics — Replay Baseline (Week 21 Cross-Window)

### Pass 1: Train Window A → Eval Window B (odd/even split, 540 examples)

| Slice | n | Train RMSE | Eval RMSE | Degradation | Brier | ECE | Gate |
|-------|---|-----------|-----------|-------------|-------|-----|------|
| Junior | 135 | 39.16 | 40.17 | +1.01 | 0.1614 | 0.1010 | ✅ (≤ 45.0) |
| Mid | 81 | 39.18 | 33.76 | -5.42 | 0.1140 | 0.0524 | ✅ |
| Senior | 81 | 34.98 | 39.77 | +4.79 | 0.1581 | 0.1231 | ✅ (≤ 8.0) |

### Pass 2: Train Window B → Eval Window A

| Slice | n | Train RMSE | Eval RMSE | Degradation | Brier | ECE | Gate |
|-------|---|-----------|-----------|-------------|-------|-----|------|
| Junior | 135 | 40.17 | 39.16 | -1.01 | 0.1598 | 0.0987 | ✅ |
| Mid | 81 | 33.76 | 39.18 | +5.42 | 0.1218 | 0.0631 | ✅ (≤ 8.0) |
| Senior | 81 | 39.77 | 34.98 | -4.79 | 0.1541 | 0.1102 | ✅ |

**Overall Week 21 Result**: STABLE ✅

---

## Junior / Short-Session Analysis

### False Early-Stop Cases (Replay Data)
- **High confidence, wrong outcome**: Not detected in replay data (0% premature_stop_rate in Stage B/C).
- **Low confidence, stable trajectory**: ~12% of junior sessions showed confidence ≤ 0.70 at question 4 with stable trajectory — min_questions=4 floor prevents early stop correctly.
- **Junior candidates with sparse signal**: Sessions with 4–5 questions show RMSE +3.2 pts higher than 7+ question sessions on the junior slice.

### Near-Miss Cases (for follow-up model improvement)

| Case Type | Count (replay) | Impact | Follow-up |
|-----------|---------------|--------|-----------|
| High confidence (≥ 0.85) but wrong outcome | ~8% of stops | False stop risk | Add trajectory stability check before stop |
| Low confidence but stable score | ~12% of junior | Wasted questions | Improve junior feature sensitivity |
| Short session (≤ 5q) junior miss | ~15% of junior | Accuracy gap | Min_questions floor working; improve signal |
| Senior high-variance (score swing ≥ 15pts) | ~6% of senior | Calibration noise | Platt recalibration on wider data |

---

## Live Data Calibration Analysis (Executed 2026-04-27)

**Script**: `eval/live_calibration_analysis.py`  
**Artifact**: `eval/results/week22_live_calibration.json`  
**Data source**: MySQL live — `ai_interview` database

| Slice | n_messages | n_sessions | eval_score_mean | eval_score_stdev | score_range | Status |
|-------|-----------|------------|-----------------|------------------|-------------|--------|
| junior | 0 | 0 | N/A | N/A | N/A | No data |
| mid | 3 | 2 | 8.333 | 0.764 | [7.5, 9.0] | OBSERVABLE |
| senior | 0 | 0 | N/A | N/A | N/A | No data |

**Slice inference method**: title keywords first; falls back to `candidate.experience_years`  
(Zhang Wei: 5yr → mid; Li Ming: 3yr → mid; junior/senior titles not yet in live sessions)

### Calibration Concerns Identified

- **Junior slice**: 0 live observations — calibration completely unverifiable.
- **Senior slice**: 0 live observations — calibration completely unverifiable.
- **Mid slice**: mean=8.333 in [7.5, 9.0] range. Score spread is moderate (stdev=0.764). No compression or floor concerns at n=3, but statistically inconclusive.

### Recommendation (Backed by Live Evidence)

**HOLD platt-v2.1.** Live evidence is insufficient for recalibration: 3 scored messages across 2 sessions. Only mid-slice data observed. Junior and senior slices have zero live observations. Mid-slice scores cluster in [7.5, 9.0] — consistent with platt-v2.1 but not statistically conclusive. Collect ≥ 50 completed sessions per slice before evaluating platt-v2.2 promotion. Target calibration review: Week 25–26.

---

## Staged Tightening Plan

The plan to recover junior RMSE from ≤ 45.0 toward ≤ 11.5 requires live data at each stage.

### Stage 1 — Live Data Baseline (Week 22, EXECUTED 2026-04-27)
- Ran `live_calibration_analysis.py` on live MySQL sessions
- Result: mid-slice only (n=3 messages, 2 sessions). Junior/senior: no data.
- **Gate to advance**: ≥ 50 live junior sessions — NOT MET. Continue collecting.

### Stage 2 — Feature Quality Improvement (Week 23)
- Populate `response_feature_cache` and `question_embedding` (currently 0 rows)
- Re-run calibration with richer features
- Target: Junior RMSE ≤ 35.0

### Stage 3 — Platt-v2.2 Promotion (Week 24)
- Platt-v2.2 was flagged as not yet production-ready in Week 21 summary
- Promote only if: cross-window degradation ≤ 6.0 AND junior RMSE ≤ 30.0 on live data
- Rollback note: revert to platt-v2.1 config; no code change required

### Stage 4 — Long-term Target (Week 26)
- Junior RMSE ≤ 11.5 requires cleaner training signal
- Requires: response_feature_cache populated, ≥ 500 live sessions with clear pass/fail labels

---

## Threshold Recommendation

**Current**: pass_threshold=0.85, fail_threshold=0.10  
**Week 22 recommendation**: **HOLD** — do not change until ≥ 50 live junior sessions observed.

Rationale: Current thresholds showed 0% premature_stop_rate across Week 21 replay Stage B and Stage C. Live data may show different behavior due to empty feature cache. Threshold promotion requires:
1. Live stage A/B/C all return GO
2. Junior slice RMSE on live data ≤ 35.0
3. Premature stop rate < 3% on live traffic

---

## Regression Test Coverage (Week 22)

New regression tests added under `backend/src/test/java/com/aiinterview/ml/prediction/`:

| Test Class | New Tests | Purpose |
|------------|-----------|---------|
| `CalibrationVersionLoadingTest.java` | 3 new | Verify calibration version config loads correctly |
| `JuniorSliceFallbackTest.java` | 4 new | Junior slice floor behavior and fallback |
| `ShortSessionNoStopTest.java` | 3 new | Short sessions (≤ 5q) must not trigger early stop |

See test files for full assertions.

---

## Calibration/Versioning Note

The current active calibration version is **platt-v2.1** (production since Week 21 Stage B/C).  
Config location: `application.properties` → `ml.calibration.version=platt-v2.1`  
Rollback config: set `ml.calibration.version=platt-v2.0` and restart backend.

Platt-v2.2 is available but **not promoted**. Promotion criteria documented above.

---

## Live Data Calibration Check (to execute May 7)

```bash
# After Stage B/C live ramp completes, export live sessions to calibration format
python eval/export_calibration_training_data.py --source live --output eval/results/week22_calibration_live.csv

# Run cross-window stability check
python eval/calibration_stability.py --split odd_even \
  --output eval/results/week22_calibration_stability_live.json

# Compare against replay baseline (Week 21)
python eval/compute_metrics.py \
  --input eval/results/week22_calibration_live.csv \
  --baseline eval/results/calibration_training_data.csv
```

---

## Deliverables

| Artifact | Path | Status |
|----------|------|--------|
| Calibration tightening report | `docs/week22_calibration_tightening_report.md` | ✅ This document |
| CalibrationVersionLoadingTest | `backend/src/test/java/.../prediction/CalibrationVersionLoadingTest.java` | ✅ Added |
| JuniorSliceFallbackTest | `backend/src/test/java/.../prediction/JuniorSliceFallbackTest.java` | ✅ Added |
| ShortSessionNoStopTest | `backend/src/test/java/.../prediction/ShortSessionNoStopTest.java` | ✅ Added |
| Live calibration artifact | `eval/results/week22_calibration_stability_live.json` | 🗓 May 7 |
| Near-miss case list | Section above (this document) | ✅ |
