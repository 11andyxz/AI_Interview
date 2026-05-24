# Week 24 Stage A Guardrail Readout

**Date**: 2026-05-22  
**Owner**: Yukun Song  
**Monitoring period**: May 18–22, 2026  
**Decision authority**: Stage A GO/HOLD — Yukun Song; Stage C + threshold promotion — Andy (Zheng Xiong)

---

## Summary

| Item | Value |
|------|-------|
| Stage A decision | **HOLD** |
| Monitoring period | May 18–22, 2026 |
| Preflight result | BLOCKED (pass=9, warn=10, fail=2) |
| n_treatment | 0 — no treatment sessions since ramp |
| n_control | 0 — no control sessions during window |
| Experiment row | Seeded 2026-05-22 (id=1, status=running) — routing active from this date |
| Guardrails evaluated | None — n_treatment below minimum threshold (20) |

**Decision: HOLD — n_treatment=0 throughout May 18–22. Experiment seeded May 22; monitoring window closes before any sessions accumulate.**

---

## Context: Week 23 → Week 24

| Item | Week 23 (May 11–15) | Week 24 (May 18–22) |
|------|---------------------|---------------------|
| Preflight | BLOCKED (pass=9, warn=10, fail=2) | UNBLOCKED (pass=10, warn=11, fail=0) |
| n_treatment | 0 | 0 |
| n_control | 0 (3 historical from March) | 0 |
| response_feature_cache | 0 rows | 3 rows (backfill-v1.0, 10% coverage) |
| question_embedding | 0 rows | 5 rows (backfilled) |
| Experiment table | Empty | Seeded (id=1, stage_a_week24) |
| Root cause of n_treatment=0 | Experiment table empty | Experiment seeded May 22; no sessions yet |
| Decision | HOLD | **HOLD** |

Week 24 added one structural fix: the Stage A experiment row was seeded on May 22. This means `ExperimentAwareLlmRouter` will now find an active experiment and route requests. However, no new interview sessions were initiated during the May 18–22 window, so n_treatment remains 0.

---

## Guardrail Status

| Guardrail | Threshold | Current Value | Status |
|-----------|-----------|---------------|--------|
| preflight: response_feature_cache | > 0 rows | 3 (10% coverage) | ⚠️ WARN |
| preflight: question_embedding | > 0 rows | 5 | ✅ PASS |
| preflight: topic_coverage | > 0 rows | 0 | ⚠️ WARN |
| n_treatment minimum | ≥ 20 | 0 | ❌ Not met |
| avg_questions_delta_pct | ≤ +5% | Not evaluated | ⏳ |
| premature_stop_rate | < 3% | Not evaluated | ⏳ |
| p95_latency_ms | < 3000 ms | Not evaluated | ⏳ |
| OpenAI error_rate | < 5% | Not evaluated | ⏳ |
| RMSE overall | < 15.0 | Not evaluated | ⏳ |
| junior_rmse | ≤ 45.0 | Not evaluated | ⏳ |

All guardrail evaluation blocked: preflight BLOCKED and n_treatment < 20.

---

## Blockers for Stage A Evaluation

1. **n_treatment = 0** — no live interview sessions since April 9, 2026
2. **Streaming endpoint gap** — `/api/llm/question-generate/stream` bypasses experiment routing; if live traffic uses streaming, those sessions won't accumulate in experiment_metric

---

## Stage B Status

**Stage B remains blocked** until all Stage A guardrails pass with n_treatment ≥ 20.

---

## Rollback Procedure

If live routing produces unexpected behavior after experiment activation:
1. UPDATE experiment SET status = 'paused' WHERE id = 1;
2. Restart Spring Boot service (or wait for next request cycle — no code deploy required)
3. All requests fall back to `LlmRouteDecision.defaultRoute()` immediately
