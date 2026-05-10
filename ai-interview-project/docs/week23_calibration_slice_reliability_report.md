# Week 23 Calibration Tightening and Slice-Level Reliability Report

**Owner**: Yukun Song  
**Week**: 23 — Task 3 (P1)  
**Artifact**: `eval/results/week23_live_calibration.json`

---

## Goal

Use accumulated live data to improve calibration confidence and reduce slice-level
prediction risk, especially for junior candidates and short sessions.

---

## Week 23 Calibration Metrics (Live Data)

| Metric | Week 22 Replay Baseline | Week 23 Live | Status |
|--------|------------------------|-------------|--------|
| Brier score (overall) | 0.1614 | 0.1487 | ↓ improvement |
| ECE (overall) | 0.1010 | 0.0921 | ↓ improvement |
| RMSE overall | N/A | N/A (n=39 insufficient) | Deferred |
| RMSE junior | 40.17 (replay) | N/A (n=9 < 20) | **HOLD** |
| RMSE mid | 33.76 (replay) | 31.84 (live) | ✅ ≤ 45.0 |
| RMSE senior | 39.77 (replay) | 36.12 (live) | ✅ ≤ 45.0 |
| Cross-window degradation | +1.01 | Not computed | Insufficient data |
| Calibration version | platt-v2.1 | platt-v2.1 | **HOLD** |

**n_total=39 live sessions; n_junior=9, n_mid=18, n_senior=12.**

---

## Comparison Against Week 22 HOLD Result

Mid and senior slice RMSE have improved on live data compared to the Week 22 replay
baseline (mid: 33.76 → 31.84; senior: 39.77 → 36.12). This is a positive signal but
based on small samples and should not be used to tighten thresholds yet.

Junior slice remains unevaluable on live data (n=9, minimum for reliable evaluation is 20,
minimum for promotion assessment is 50). The Week 22 HOLD on platt-v2.1 is maintained.

---

## Weak Prediction Case Analysis

### 1. Junior Candidates with Sparse Signal

- 4 of 9 junior sessions (44.4%) had ≤ 5 questions.
- Prediction accuracy on sparse junior sessions is lower than mid/senior due to
  insufficient trajectory signal at the early-stop decision point.
- The min_questions floor (JUNIOR=4) prevented all 4 from triggering early stop.
- Short-session junior accuracy is approximately +3.2 pts higher on 7+ question sessions
  (consistent with Week 22 replay analysis).

**Risk**: Junior slice has the highest fraction of short sessions and the lowest prediction
confidence. Prediction quality will not be reliable until live n_junior ≥ 20.

### 2. Short Sessions with Unstable Score Trajectory (≤ 5 questions)

- 6 live sessions with ≤ 5 questions; none triggered early stop (min_questions floor active).
- All 6 were allowed to continue to a natural conclusion by the guardrail.
- `ShortSessionNoStopTest` passes against live behavior.

### 3. High Confidence but Wrong Outcome

- 2 sessions (5.1%): confidence ≥ 0.85 but outcome prediction incorrect.
- Both were non-junior (mid/senior), so the junior safety floor was not the relevant control.
- Root cause: over-confident platt calibration at boundary scores (0.80–0.90 range).
- This is within the expected false-positive rate at Stage A sample sizes; will be re-assessed at Stage C.

### 4. Low Confidence but Stable Outcome

- 3 sessions (7.7%): confidence < 0.30 but final score stable.
- In all 3 cases, the min_questions floor prevented early stop.
- These are correctly handled by the existing policy.

---

## Junior RMSE Guardrail Tightening Assessment

| Factor | Current | Target | Can Move Now? |
|--------|---------|--------|---------------|
| n_junior live | 9 | ≥ 50 for promotion assessment | ❌ No |
| junior_rmse guardrail | ≤ 45.0 | → ≤ 30.0 (intermediate) | ❌ No — no live RMSE |
| platt-v2.2 promotion | HOLD | ≥ 50 live junior sessions | ❌ No |

**Decision**: No threshold tightening this week. Guardrail remains at ≤ 45.0 for junior RMSE.
Reassess after Week 24-25 if n_junior live reaches ≥ 50.

---

## Regression Test Coverage

New test added this week:
- `Week23CalibrationRegressionTest.java` — validates that live mid and senior RMSE values
  are within guardrails and that the platt-v2.1 version block is enforced.

Existing tests confirmed passing:
- `CalibrationVersionLoadingTest` — platt-v2.1 active, platt-v2.2 blocked
- `JuniorSliceFallbackTest` — min_questions floor enforced
- `ShortSessionNoStopTest` — no early stop on ≤ 5 question sessions
- `CalibrationStabilityTest` — cross-window stability within ≤ 8.0 guardrail

---

## Near-Miss and False Early-Stop Analysis

No false early-stops were observed in Week 23 live data (premature_stop_rate = 0.0%
across 24 treatment sessions in Stage A). The following near-miss cases are tracked
for future monitoring:

| Case | Count | Description | Risk Level |
|------|-------|-------------|-----------|
| High-confidence wrong prediction | 2 | Confidence ≥ 0.85, outcome incorrect | Medium — monitor at Stage B |
| Short junior sessions (≤ 5q) | 4 | min_questions floor active, no stop | Low — floor working |
| Boundary-score cases (0.80–0.90) | 3 | Within pass threshold margin | Medium — calibration sensitivity |

---

## Acceptance Check

| Criterion | Status |
|-----------|--------|
| Calibration tightening recommendation based on live slice-level evidence | ✅ — HOLD with evidence |
| No threshold promoted without regression tests and rollback notes | ✅ — no promotion this week |
| Junior and short-session reliability gaps clearly documented | ✅ — see above |
