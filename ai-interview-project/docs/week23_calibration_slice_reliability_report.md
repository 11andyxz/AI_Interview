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
| Brier score (overall) | Available (replay) | Not promoted — live sample insufficient | ⏳ |
| ECE (overall) | Available (replay) | Not promoted — live sample insufficient | ⏳ |
| RMSE overall | N/A | Not computed — insufficient live sample | Deferred |
| RMSE junior | Available (replay) | Not computed — n_junior below minimum | **HOLD** |
| RMSE mid | Available (replay) | Descriptive only — below reliable threshold | ⏳ |
| RMSE senior | Available (replay) | Descriptive only — below reliable threshold | ⏳ |
| Calibration version | platt-v2.1 | platt-v2.1 | **HOLD** |

Live session count through May 4–8 is insufficient for guardrail-grade slice-level evaluation.
Results are descriptive only and were not used to promote any threshold or calibration version.

---

## Comparison Against Week 22 HOLD Result

Calibration analysis was run on available Week 23 live data. Results remain descriptive
only due to limited slice-level sample size. No slice-level RMSE values meet the minimum
sample requirement for guardrail-grade conclusions. The Week 22 HOLD on platt-v2.1 is maintained.

---

## Weak Prediction Case Analysis

### 1. Junior Candidates with Sparse Signal

- A portion of junior live sessions had ≤ 5 questions, limiting trajectory signal.
- The min_questions floor (JUNIOR=4) prevented early-stop on these sessions.
- Prediction quality on sparse junior sessions is lower than mid/senior due to
  insufficient signal at the early-stop decision point.
- Junior sample count is below the minimum required for reliable RMSE evaluation.

**Risk**: Junior slice has the highest fraction of short sessions and the lowest prediction
confidence. Prediction quality will not be reliable until n_junior meets the minimum threshold.

### 2. Short Sessions with Unstable Score Trajectory (≤ 5 questions)

- 6 live sessions with ≤ 5 questions; none triggered early stop (min_questions floor active).
- All 6 were allowed to continue to a natural conclusion by the guardrail.
- `ShortSessionNoStopTest` passes against live behavior.

### 3. High Confidence but Wrong Outcome

- A small number of non-junior (mid/senior) sessions had confidence ≥ 0.85 but incorrect outcome prediction.
- Root cause: calibration sensitivity at boundary scores (0.80–0.90 range).
- Count is within the expected false-positive rate at current sample sizes; will be re-assessed once volume is sufficient.

### 4. Low Confidence but Stable Outcome

- A small number of sessions had confidence < 0.30 but stable final score.
- In all such cases, the min_questions floor prevented early stop.
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

No false early-stops were confirmed in Week 23 live data through May 4–8. The following
case categories are tracked for future monitoring as sample volume grows:

| Case | Description | Risk Level |
|------|-------------|------------|
| High-confidence wrong prediction | Confidence ≥ 0.85, outcome incorrect | Medium — monitor as volume grows |
| Short junior sessions (≤ 5q) | min_questions floor active, no stop | Low — floor working |
| Boundary-score cases (0.80–0.90) | Within pass threshold margin | Medium — calibration sensitivity |

---

## Acceptance Check

| Criterion | Status |
|-----------|--------|
| Calibration tightening recommendation based on live slice-level evidence | ✅ — HOLD with evidence |
| No threshold promoted without regression tests and rollback notes | ✅ — no promotion this week |
| Junior and short-session reliability gaps clearly documented | ✅ — see above |
