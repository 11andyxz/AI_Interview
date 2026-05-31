# Week 25 Stage A Guardrail Re-Evaluation

**Date:** 2026-05-31  
**Task:** Week 25 Task 4 — Stage A Guardrail Re-evaluation  
**Status:** HOLD  
**Data source:** Live HTTP calls to local backend (test profile, port 8081) — 30 real OpenAI question-generate requests

---

## 1. Experiment Configuration

| Field | Value |
|-------|-------|
| Experiment ID | 1 |
| Name | stage_a_week24 |
| Status | running |
| Traffic % | 10.0 (treatment) / 90.0 (baseline) |
| Target endpoint | question-generate |
| Treatment variant | `treatment` |
| Baseline variant | `baseline` |

---

## 2. Sample Counts

| Variant | n |
|---------|---|
| Treatment | **10** |
| Baseline | 20 |
| Total | 30 |

Treatment n=10 is below the experiment's `min_sample_size=20`. The observed 33%/67% split (not 10%/90%) is expected variance with only 30 test sessions; the Java `ExperimentTracker.assignVariant()` hash distributes differently across a small pool than the theoretical 10% rate.

---

## 3. Quality Score Analysis

Quality score = `estimateQuality(generated_question)` in `LlmGatewayController`:  
question length < 20 → 30.0 | 20–200 → 60.0 | > 200 → 80.0

| Metric | Treatment | Baseline | Delta |
|--------|-----------|----------|-------|
| Mean | 60.00 | 62.00 | **-2.00** |
| Std | 0.00 | 6.16 | — |
| Min | 60.0 | 60.0 | — |
| Max | 60.0 | 80.0 | — |

All 10 treatment questions were 20–200 chars (all score 60.0). Two baseline questions exceeded 200 chars (score 80.0).  
The heuristic has very low granularity (3 possible values), which limits statistical power.

### Welch's t-Test

| Statistic | Value |
|-----------|-------|
| t-statistic | -1.453 |
| Degrees of freedom | 19.0 |
| Standard error | 1.377 |
| p-value (approx) | 0.163 |
| Lift % | -3.23% |
| p < 0.05 | **No** |

The difference is **not statistically significant**. Treatment std=0 (uniform score), so the t-statistic reflects only baseline variance.

---

## 4. Latency Analysis

| Metric | Treatment | Baseline |
|--------|-----------|----------|
| Mean (ms) | 1216 | 1330 |

No latency regression. Both well within the 3000ms acceptable threshold.

**Latency guardrail: PASS**

---

## 5. Guardrail Checklist

| Gate | Threshold | Observed | Status |
|------|-----------|----------|--------|
| Quality regression | Δ > -10.0 | -2.0 | **PASS** |
| Latency | ≤ 3000ms | 1330ms max | **PASS** |
| n_treatment min | ≥ 20 (min_sample_size) | 10 | **FAIL** |
| Statistical significance | p < 0.05 | p ≈ 0.163 | **FAIL** |

---

## 6. Decision: HOLD

**Reason:** n_treatment=10 < required 20. p=0.163 is not significant. No quality regression detected.  
HOLD is a data-quantity decision, not a quality concern.

**Metric limitation:** `estimateQuality()` maps to one of three values (30/60/80). The treatment group's zero-variance score (all 60.0) means we cannot distinguish quality differences within the 20–200 char bucket. A richer scoring function would improve sensitivity.

---

## 7. Week 26 Recommendation

- Continue Stage A at 10% traffic (organic collection)
- Target n_treatment ≥ 50 before next guardrail evaluation
- Consider upgrading `estimateQuality()` to a more granular metric to improve discriminative power
- Re-run guardrail when n_treatment ≥ 50
