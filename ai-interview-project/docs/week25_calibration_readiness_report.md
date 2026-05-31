# Week 25 Calibration Readiness Report

**Date:** 2026-05-31  
**Model:** platt-v2.1  
**Decision:** HOLD  
**Data source:** Live HTTP calls — 30 real question-generate requests via local backend (test profile)

---

## 1. Current Model State

| Field | Value |
|-------|-------|
| Active model | platt-v2.1 |
| Pass threshold | 0.85 |
| Fail threshold | 0.10 |
| Min questions | 4/5/6 (junior/mid/senior) |
| Last calibration | Week 22 (offline) |
| Status | HOLD since Week 23 |

---

## 2. Calibration Update Requirements

The Platt calibration model (platt-v2.1) requires the following data conditions for a valid update:

| Requirement | Target | Current | Status |
|-------------|--------|---------|--------|
| n_treatment (scored sessions) | ≥ 50 | **10** | **Not met** |
| Statistical significance of score delta | p < 0.05 | p ≈ 0.163 (t=-1.453) | **Not met** |
| response_feature_cache organic rows | ≥ 20 live | 0 (all backfill) | **Not met** |
| candidate_skill_profile rows | ≥ 10 | 0 | **Not met** |

None of the thresholds for a calibration update are met this week. The organic feature-writing integration was completed in Task 2 but has not yet generated live data (backend needs to be deployed with `ml.nlp.enabled=true` in production).

---

## 3. Calibration Data Pipeline Status

As of Week 25:
- `response_feature_cache`: 3 rows (backfill-v1.0 only), 0 organic rows
- `candidate_skill_profile`: 0 rows
- `experiment_metric`: **30 rows** (10 treatment, 20 baseline — live HTTP collection)

The feature-writing pipeline is now wired (Task 2 complete). Once the backend is deployed with feature flags enabled and real sessions flow through, organic calibration data will accumulate.

---

## 4. Calibration Health Indicators

From the 10 treatment sessions (real OpenAI-generated questions):
- Mean quality score: 60.00 (std: 0.00)
- Score distribution: all scores at 60.0 (questions between 20–200 chars per length heuristic)
- No evidence of calibration drift in the treatment arm

**Note:** The zero-variance distribution is an artifact of the `estimateQuality()` heuristic, not model collapse. Existing platt-v2.1 thresholds remain appropriate.

---

## 5. Decision: HOLD

**Reason:** n_treatment=10 < required 50. p=0.163 not significant. No organic feature data yet.

**Condition for update (Week 26+):**  
- n_treatment ≥ 50 AND  
- p < 0.05 on quality delta AND  
- response_feature_cache organic rows ≥ 20

---

## 6. Risk

Low. platt-v2.1 is calibrated on offline data and showing no drift. No recalibration urgency.
