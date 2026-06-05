# Week 26 ML Decision Readout

**Date:** 2026-06-05
**Branch:** ml-production-readiness
**Experiment:** stage_a_week24 (id=1, traffic=10%)

---

## 1. Summary

| Area | Status |
|---|---|
| Stage A sample expansion (n_treatment >= 20) | DONE — n_treatment=22 |
| Quality metric upgrade | DONE — llm-v1.0 wired |
| Organic feature writes activated | DONE — response_feature_cache organic=10 rows |
| Stage A guardrail decision | HOLD |
| Stage B | BLOCKED |
| platt-v2.1 | UNCHANGED |

---

## 2. Experiment Configuration

| Field | Value |
|---|---|
| experiment_id | 1 |
| name | stage_a_week24 |
| status | running |
| traffic_percentage | 10.0% |
| target_endpoint | question-generate |
| baseline config | pass_threshold=0.95, fail_threshold=0.05 |
| treatment config | pass_threshold=0.85, fail_threshold=0.10 |
| model | gpt-3.5-turbo (both variants) |

---

## 3. Sample Counts

| Variant | n | Week 25 | Week 26 | Metric version |
|---|---|---|---|---|
| treatment | 22 | 10 (heuristic-v1.0) | 12 (llm-v1.0) | mixed |
| baseline | 44 | 20 (heuristic-v1.0) | 24 (llm-v1.0) | mixed |
| total | 66 | 30 | 36 | |

---

## 4. Quality Metric Upgrade

The quality metric was upgraded from `heuristic-v1.0` (3-bucket length heuristic: 30/60/80) to `llm-v1.0` (GPT-scored 0–100 via `QuestionQualityScorer`). The scorer sends a single GPT-3.5-turbo call per generated question with a structured prompt evaluating specificity, technical depth, role relevance, clarity, and non-repetitiveness. The heuristic fallback is retained if the LLM call fails.

Week 25 rows retain their original heuristic scores. Week 26 rows use llm-v1.0. The two metric versions are labeled separately in the experiment registry.

**Limitation:** All Week 26 sessions had no prior conversation history, so the backend generated the same generic question for all sessions. The LLM scorer returned 60.0 consistently for this question, producing zero variance in Week 26 quality scores. The upgrade will provide meaningful variance when sessions include real conversation history.

---

## 5. Statistical Results

| Metric | Value |
|---|---|
| t-statistic | −1.4310 |
| degrees of freedom | 43.0 |
| p-value | 0.4673 |
| quality delta | −0.91 |
| lift | −1.49% |
| treatment avg latency | 649 ms |
| baseline avg latency | 667 ms |

---

## 6. Guardrail Results

| Guardrail | Threshold | Value | Result |
|---|---|---|---|
| n_treatment >= 20 | 20 | 22 | PASS |
| quality delta >= −10 | −10.0 | −0.91 | PASS |
| p-value < 0.05 | 0.05 | 0.4673 | FAIL |
| latency < 3000 ms | 3000 ms | t=649 ms, b=667 ms | PASS |
| response_feature_cache organic >= 1 | 1 | 10 | PASS |

---

## 7. Feature Coverage

| Table | Rows | Notes |
|---|---|---|
| response_feature_cache (backfill-v1.0) | 3 | From Week 24 backfill |
| response_feature_cache (organic-v1.0) | 10 | New Week 26 organic writes |
| topic_coverage | 0 | Requires question_embedding index |
| candidate_skill_profile | 37 | 30 new from Week 25, 6 new from Week 26 eval calls, 1 pre-existing |

---

## 8. Decision

**HOLD — do not proceed to Stage B.**

All hard infrastructure guardrails pass. The only failing guardrail is statistical significance (p=0.4673). This is a statistical power issue from insufficient treatment sample size and zero variance in treatment quality scores, not an observed quality regression.

The treatment quality delta of −0.91 is well within acceptable range. No rollback is warranted.

---

## 9. Stage B Gate

| Gate | Status |
|---|---|
| Stage B approved | **NO** |
| Blocker | p-value not significant (p=0.4673 >= 0.05) |

---

## 10. Rollback Condition

Roll back to baseline-only routing if treatment quality delta drops below −10 points at any sample size, or if OpenAI error rate exceeds 5% in treatment sessions.

---

## 11. Recommended Next Steps

| Priority | Action |
|---|---|
| P0 | Continue Stage A collection targeting n_treatment >= 50 for stronger statistical power |
| P0 | Use sessions with real conversation history to produce meaningful quality score variance |
| P0 | Deploy `ml.embedding.enabled=true` in a runtime with an active question_embedding index to unlock topic_coverage writes |
| P1 | Collect junior/mid/senior labeled sessions to enable calibration readiness |
| P1 | Re-run guardrail and calibration when n_treatment >= 50 and quality variance is non-zero |
| P1 | Stage B: remain BLOCKED until sample size, quality variance, and p-value guardrails all pass |
