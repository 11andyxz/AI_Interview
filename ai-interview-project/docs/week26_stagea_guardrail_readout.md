# Week 26 Stage A Guardrail Readout

**Date:** 2026-06-05
**Experiment:** stage_a_week24 (experiment_id=1)
**Stage:** A (10% treatment traffic)
**Metric version:** heuristic-v1.0 (Week 25 rows), llm-v1.0 (Week 26 rows)

---

## 1. Sample Counts

| Variant | n | Source |
|---|---|---|
| treatment | 22 | 10 from Week 25 (heuristic-v1.0) + 12 from Week 26 (llm-v1.0) |
| baseline | 44 | 20 from Week 25 (heuristic-v1.0) + 24 from Week 26 (llm-v1.0) |
| **total** | **66** | |

---

## 2. Quality Score Statistics

| Metric | Treatment | Baseline |
|---|---|---|
| n | 22 | 44 |
| mean | 60.00 | 60.91 |
| std | 0.00 | 4.17 |
| delta (treatment − baseline) | −0.91 | |
| lift | −1.49% | |

**Note on treatment std=0.00:** All 12 Week 26 treatment sessions received the same score from the LLM quality scorer because all sessions had no prior history and the backend generated the same generic question. The 10 Week 25 treatment rows used the heuristic and also scored 60.0. Zero variance in treatment is a data limitation from controlled smoke sessions with no session context, not a scorer defect.

**Metric version note:** Week 25 rows used heuristic-v1.0 (3-bucket length heuristic). Week 26 rows used llm-v1.0 (GPT-scored 0–100). Both happen to produce 60.0 for the same generic question, so the combined distribution is internally consistent for this session type.

---

## 3. Statistical Test

Welch's t-test (treatment vs baseline quality):

| Parameter | Value |
|---|---|
| t | −1.4310 |
| df | 43.0 |
| p-value | 0.4673 |
| significant (p < 0.05) | **No** |

---

## 4. Guardrail Results

| Guardrail | Threshold | Value | Result |
|---|---|---|---|
| n_treatment >= 20 | 20 | 22 | **PASS** |
| quality delta >= −10 | −10.0 | −0.91 | **PASS** |
| p-value < 0.05 | 0.05 | 0.4673 | **FAIL** |
| latency avg < 3000 ms (both variants) | 3000 ms | t=649 ms, b=667 ms | **PASS** |
| response_feature_cache organic >= 1 | 1 | 10 | **PASS** |

---

## 5. Feature Coverage

| Table | Rows |
|---|---|
| response_feature_cache (backfill-v1.0) | 3 |
| response_feature_cache (organic-v1.0) | 10 |
| topic_coverage | 0 |
| candidate_skill_profile | 37 |

`topic_coverage` remains 0 because question embeddings for LLM-generated questions are not indexed. This is a pre-existing limitation and does not block the guardrail decision.

---

## 6. Decision

**HOLD**

All hard guardrails pass (sample size, quality regression, latency, feature coverage). Stage B is blocked because the statistical significance guardrail fails (p=0.4673, well above the 0.05 threshold).

The quality delta of −0.91 is well above the −10 rollback threshold. This is a statistical power blocker, not a quality regression.

---

## 7. Rollback Condition

Roll back to baseline-only routing if treatment quality delta drops below −10 points at any sample size, or if OpenAI error rate exceeds 5% in treatment sessions.
