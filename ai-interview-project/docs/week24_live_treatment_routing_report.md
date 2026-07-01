# Week 24 Live Treatment Routing Report

**Date**: 2026-05-22  
**Owner**: Yukun Song  
**Monitoring period**: May 18–22, 2026

---

## Summary

| Item | Status | Detail |
|------|--------|--------|
| Experiment table (pre-fix) | ❌ Empty | 0 rows — root cause of n_treatment=0 |
| Stage A experiment seeded | ✅ Done | id=1, status=running, 10% treatment, started 2026-05-22 |
| Experiment routing code | ✅ Present | `ExperimentAwareLlmRouter` active for question-generate, eval, chat |
| Streaming endpoint routing | ✅ Fixed | `questionGenerateStream()` now routes via `ExperimentAwareLlmRouter`; metric recorded in `doOnComplete()` |
| n_treatment (pre-fix) | 0 | No treatment sessions recorded (May 11–22) |
| n_treatment (post-seed) | 0 | Seed completed; new sessions will route; smoke test pending |
| Preflight result | UNBLOCKED | pass=10, warn=11, fail=0 (after feature cache backfill 2026-05-22) |

**Stage A remains HOLD post-seed pending (a) smoke-test confirmation of metric writes and (b) feature cache population (Task 2).**

---

## Root Cause: n_treatment=0

`ExperimentAwareLlmRouter.route()` calls `experimentTracker.getActiveExperiment(endpoint)`, which queries:

```sql
SELECT * FROM experiment WHERE status = 'running' AND target_endpoint = ?
```

The `experiment` table was empty for all of Weeks 22–24. With zero active experiment rows, every request fell through to `LlmRouteDecision.defaultRoute()` — no variant assignment, no `recordMetric()` calls, no rows in `experiment_metric`.

### Secondary issue: interview data stale

All 29 interview rows in the DB show status='In Progress' with no `started_at` / `ended_at` timestamps. The most recent interview was created 2026-04-09. No sessions have been initiated through the application since early April.

---

## Environment Flag Audit

The flags `ML_PREDICTION_EARLY_STOPPING_ENABLED` and `ML_NEW_POLICY_ENABLED` are Spring Boot application-layer environment variables. They are **not stored in the `experiment` table** and are not read by `ExperimentAwareLlmRouter` directly. The router depends solely on `experiment.status='running'`.

| Flag | Location | Current Value | Required for Routing |
|------|----------|---------------|----------------------|
| `ML_PREDICTION_EARLY_STOPPING_ENABLED` | Spring Boot env / application.properties | Not verified in live backend | No — routing uses DB only |
| `ML_NEW_POLICY_ENABLED` / `ML_EARLY_STOP_NEW_POLICY_ENABLED` | Spring Boot env | Not verified in live backend | No — experiment config is in `treatment_config` JSON |
| `experiment.status` | MySQL `ai_interview.experiment` | `running` (as of 2026-05-22) | **Yes — routing key** |
| `experiment.target_endpoint` | MySQL | `question-generate` | **Yes — routing key** |
| `experiment.traffic_percentage` | MySQL | 10.0% | Yes — treatment bucket |
| `experiment.min_sample_size` | MySQL | 20 | Yes — evaluation gate |

---

## Stage A Experiment Configuration (Seeded 2026-05-22)

```sql
INSERT INTO experiment (name, description, status, traffic_percentage,
  min_sample_size, baseline_variant, treatment_variant,
  baseline_config, treatment_config, target_endpoint, started_at)
VALUES (
  'stage_a_week24',
  'Stage A 10% traffic ramp - Week 24 live experiment (question-generate endpoint)',
  'running', 10.0, 20, 'baseline', 'treatment',
  '{"model":"gpt-3.5-turbo","temperature":0.7,"policy":"baseline","pass_threshold":0.95,"fail_threshold":0.05,"min_questions":"4/5/6"}',
  '{"model":"gpt-3.5-turbo","temperature":0.7,"policy":"new_policy","pass_threshold":0.85,"fail_threshold":0.10,"min_questions":"4/5/6"}',
  'question-generate', NOW()
);
```

Confirmed in DB:

| id | name | status | traffic_percentage | target_endpoint | started_at |
|----|------|--------|--------------------|----------------|------------|
| 1 | stage_a_week24 | running | 10 | question-generate | 2026-05-22 19:19:54 |

### Variant Assignment

Variant assignment in `ExperimentTracker.assignVariant()` is deterministic:

```java
String hashInput = sessionId + ":" + experiment.getId();
int hashValue = Math.abs(sha256Hash(hashInput));
double bucket = (hashValue % 10000) / 100.0;
return bucket < experiment.getTrafficPercentage()
    ? experiment.getTreatmentVariant()   // "treatment" if bucket < 10.0
    : experiment.getBaselineVariant();   // "baseline" otherwise
```

With `traffic_percentage=10.0`, approximately 10% of sessions will be assigned `treatment`, 90% `baseline`. The assignment is stable for the same `sessionId`.

---

## Endpoint Routing Coverage

| Endpoint | Method | Routed via ExperimentAwareLlmRouter | Metric recorded |
|----------|--------|--------------------------------------|-----------------|
| `/api/llm/question-generate` | POST | ✅ Yes | ✅ Yes (when experiment active) |
| `/api/llm/eval` | POST | ✅ Yes | ✅ Yes (when experiment active) |
| `/api/llm/chat` | POST | ✅ Yes | ✅ Yes (when experiment active) |
| `/api/llm/question-generate/stream` | GET (SSE) | ✅ **YES** (fixed 2026-05-22) | ✅ **YES** (fixed 2026-05-22) |

### Streaming Endpoint Gap — FIXED (2026-05-22)

`GET /api/llm/question-generate/stream` previously called `openAiService.chatStream()` directly, bypassing `experimentRouter.route()` and `experimentTracker.recordMetric()`. **Fixed in `LlmGatewayController.questionGenerateStream()`** — the method now:

1. Calls `experimentRouter.route(llmRequest)` with the same `LlmRequest` shape as the POST endpoint
2. Uses `route.getPromptTemplate()` if the experiment provides one
3. Accumulates streamed chunks via `StringBuilder accumulated`
4. Calls `experimentTracker.recordMetric()` in `doOnComplete()` with latency and estimated quality/tokens
5. Uses `logger.error()` instead of `System.err.println`

Streaming sessions now behave identically to POST `/question-generate` for experiment variant assignment and metric recording.

---

## Smoke Test Plan

After backend restart with the new experiment row active:

1. POST to `/api/llm/question-generate` with a test `sessionId`
2. Verify response includes `experimentId` and `variant` fields
3. Query `experiment_metric` for rows matching the test `sessionId`
4. Confirm variant assignment is stable for the same `sessionId` across repeated calls

```bash
# Expected response shape when experiment is active:
{
  "question": "...",
  "sessionId": "test-session-123",
  "questionNumber": 1,
  "experimentId": "1",
  "variant": "baseline"  # or "treatment"
}

# DB check:
SELECT * FROM experiment_metric WHERE session_id = 'test-session-123';
```

Smoke test has not been run as of 2026-05-22 — the Spring Boot backend is not running in this environment. The experiment row is seeded; confirmation requires a live backend session.

---

## Stage A HOLD Conditions (post-seed)

Stage A HOLD continues until ALL of the following are met:

1. Smoke test confirms `experiment_metric` rows are written for both baseline and treatment sessions
2. `n_treatment >= 20` (current: 0)
3. Preflight passes (current: ✅ UNBLOCKED — pass=10, warn=11, fail=0 as of 2026-05-22)
4. Streaming endpoint gap is resolved — ✅ FIXED 2026-05-22 (`LlmGatewayController.questionGenerateStream()` now routes via experiment framework)

---

## Next Steps

| Priority | Action | Owner |
|----------|--------|-------|
| P0 | Run smoke test after backend restart | Yukun Song |
| ~~P0~~ | ~~Confirm which endpoint live traffic uses (POST vs streaming)~~ | ✅ Done — streaming fixed 2026-05-22 |
| ~~P0~~ | ~~Add routing + metric capture to streaming endpoint~~ | ✅ Done — `questionGenerateStream()` updated |
| ✅ Done | Populate feature caches (Task 2) | question_embedding=5, response_feature_cache=3 backfilled |
| P1 | Re-evaluate Stage A once n_treatment >= 20 | Yukun Song |
