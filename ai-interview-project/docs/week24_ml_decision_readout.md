# Week 24 ML Decision Readout

**Date**: 2026-05-22  
**Owner**: Yukun Song  
**Format**: Based on real DB state — queried 2026-05-22 via Aiven MySQL (`ai_interview` database)

---

## Executive Summary

| Status | Item |
|--------|------|
| ⚠️ | Stage A live 10% ramp — **HOLD** (n_treatment=0; pending live sessions) |
| ⛔ | Stage B live 50% ramp — blocked pending Stage A pass |
| ⛔ | Stage C live 100% ramp — blocked pending Stage B pass |
| ✅ | Stage A experiment row — **Seeded** (id=1, status=running, 2026-05-22) |
| ✅ | Preflight — **UNBLOCKED** (pass=10, warn=11, fail=0) |
| ✅ | Streaming endpoint routing — **Fixed** (`questionGenerateStream()` now routes via experiment framework) |
| ✅ | Evaluation tooling — present and validated |
| ⚠️ | platt-v2.1 calibration — HOLD (no live slice data) |
| ⚠️ | response_feature_cache — 3 rows (10% coverage; backfill-v1.0) |
| ✅ | question_embedding — 5 rows (backfilled via OpenAI text-embedding-3-small) |

---

## Live Ramp Metrics

| Stage | Monitoring Period | n_treatment | Guardrails Evaluated | Decision |
|-------|------------------|------------|---------------------|----------|
| A (10%) | May 18–22 | 0 | None — n_treatment=0 | **HOLD** |
| B (50%) | — | — | — | ⛔ Blocked |
| C (100%) | — | — | — | ⛔ Blocked |

> Experiment row seeded May 22 (id=1, stage_a_week24). No sessions were initiated during the May 18–22 window, so n_treatment=0 continues. Routing is now active for new sessions via POST `/api/llm/question-generate`.

---

## Preflight Result (Live, May 22, 2026)

| Check | Result | Detail |
|-------|--------|--------|
| ENV vars (required) | ✅ PASS (6/6) | All DB and OpenAI vars set |
| MySQL connectivity | ✅ PASS | Connected in 827ms |
| OpenAI API key | ✅ PASS | Responded in 1168ms |
| SQLite fallback guard | ✅ PASS | DB_HOST present |
| response_feature_cache | ⚠️ WARN | 3 rows / 29 interviews (10% coverage) |
| question_embedding | ✅ PASS | 5 embeddings available |
| topic_coverage | ⚠️ WARN | 0 rows |
| ML env vars (optional) | ⚠️ WARN (8 warnings) | Not set; defaults used |

**Result: UNBLOCKED — pass=10, warn=11, fail=0**

---

## Real DB State (queried May 22, 2026)

| Table | Row Count | Notes |
|-------|-----------|-------|
| experiment | 1 | stage_a_week24 seeded; status=running; traffic=10% |
| experiment_metric | 0 | No sessions through new experiment yet |
| response_feature_cache | 3 | Backfilled from interview_message (backfill-v1.0); 10% coverage |
| question_embedding | 5 | Backfilled via OpenAI text-embedding-3-small (1536 dims) |
| topic_coverage | 0 | TopicCoverageTracker not called from flow |
| candidate_skill_profile | 0 | No prediction outputs written |
| interview | 29 | Last session: 2026-04-09; all status=In Progress |
| interview_message | 6 | — |
| users | 29 | — |
| template_questions | 5 | Available for embedding backfill |
| knowledge_base | 3 | Available for embedding backfill |

---

## Calibration

| Metric | Week 23 | Week 24 | Status |
|--------|---------|---------|--------|
| platt version | platt-v2.1 | platt-v2.1 | HOLD — no promotion |
| Junior RMSE | Not computed | Not computed | Insufficient data |
| Mid RMSE | Descriptive only | Descriptive only | Insufficient data |
| Senior RMSE | Descriptive only | Descriptive only | Insufficient data |

---

## Guardrail Pass/Fail State

| Guardrail | Threshold | Week 24 Live | Status |
|-----------|-----------|-------------|--------|
| avg_questions_delta_pct | ≤ +5% | Not evaluated | ⏳ n_treatment=0 |
| premature_stop_rate | < 3% | Not evaluated | ⏳ n_treatment=0 |
| p95_latency_ms | < 3000 ms | Not evaluated | ⏳ n_treatment=0 |
| OpenAI error_rate | < 5% | Not evaluated | ⏳ n_treatment=0 |
| RMSE overall | < 15.0 | Not evaluated | ⏳ n_treatment=0 |
| junior_rmse | ≤ 45.0 | Not evaluated | ⏳ n_treatment=0 |
| response_feature_cache | > 0 rows | 0 rows | ❌ FAIL |
| question_embedding | > 0 rows | 0 rows | ❌ FAIL |

---

## Root Cause Analysis

**Why n_treatment=0 for Weeks 22–24:**

The `experiment` table in `ai_interview` was empty. `ExperimentAwareLlmRouter.getActiveExperiment()` queries `SELECT * FROM experiment WHERE status = 'running' AND target_endpoint = ?` — with 0 rows, all requests fell through to default routing and no metrics were written.

**Fix applied May 22:** Stage A experiment row inserted (id=1, status=running, target=question-generate, traffic=10%). Routing is now live for POST requests to `/api/llm/question-generate`.

**Remaining blockers:**
1. Feature caches empty — `ResponseFeatureExtractor` not connected to eval endpoint; no embedding backfill run
2. Streaming endpoint (`GET /api/llm/question-generate/stream`) bypasses experiment routing — if live traffic uses streaming, those sessions still won't accumulate
3. No new interview sessions since April 9 — user activity needed for organic metric accumulation

---

## Week 25 Recommendation

**Recommendation: HOLD — Continue Stage A data collection**

Stage A is not yet evaluable. Week 25 conditions for advancing:

| Condition | Required | Current | Path |
|-----------|----------|---------|------|
| Feature caches populated | response_feature_cache > 0, question_embedding > 0 | 0, 0 | Connect ResponseFeatureExtractor; run embedding backfill (8 questions available) |
| Preflight UNBLOCKED | Exit 0 | BLOCKED | Blocked by feature caches |
| n_treatment ≥ 20 | ≥ 20 | 0 | Requires live sessions through POST /question-generate |
| Streaming gap resolved | Confirmed non-blocking or fixed | Unknown | Audit live traffic path |

**Stage B is NOT recommended until all Stage A guardrails pass.**

**Rollback condition**: If live routing produces anomalous latency or error rates after experiment activation, set `UPDATE experiment SET status = 'paused' WHERE id = 1` and restart the backend service. No code deploy required.

---

## Deliverables Summary (Week 24)

| Deliverable | Status |
|-------------|--------|
| `docs/week24_live_treatment_routing_report.md` | ✅ |
| `eval/experiment_registry.csv` (Week 24 entry) | ✅ |
| `docs/week24_feature_writing_enablement.md` | ✅ |
| `eval/results/week24_feature_population_snapshot.json` | ✅ |
| `docs/week24_evaluation_tooling_parity.md` | ✅ |
| `eval/preflight_check.py` | ✅ (existing, validated) |
| `eval/auto_summary_generator.py` | ✅ (existing, validated) |
| `eval/results/week24_reproducibility_manifest.json` | ✅ |
| `eval/results/week24_preflight_live.json` | ✅ |
| `eval/README.md` (Week 24 sequence added) | ✅ |
| `docs/week24_stagea_guardrail_readout.md` | ✅ |
| `docs/week24_calibration_readiness_report.md` | ✅ |
| `docs/week24_ml_decision_readout.md` | ✅ |
| `eval/results/week24_stagea_live_result.json` | ❌ Cannot generate — n_treatment=0 |
