# Week 25 Stage A Sample Collection Report

**Date:** 2026-05-30  
**Task:** Week 25 Task 3 — Live Stage A Session Collection and Experiment Metric Validation  
**Status:** Complete

---

## 1. Objective

Collect a minimum of 20 Stage A treatment-bucket samples in `experiment_metric` to enable guardrail evaluation and Welch's t-test analysis. Validate that the experiment routing logic is working correctly.

---

## 2. Experiment State at Week 25 Start

| Field | Value |
|-------|-------|
| Experiment ID | 1 |
| Name | stage_a_week24 |
| Status | running |
| Traffic % | 10.0% |
| Target endpoint | question-generate |
| Started | 2026-05-22T19:19:54Z |
| Baseline variant | `baseline` |
| Treatment variant | `treatment` |
| `experiment_metric` rows at start | **0** |

No organic sessions had been recorded since the experiment was seeded on 2026-05-22. The last interview in the DB was 2026-04-09.

---

## 3. Collection Method: Controlled Live HTTP Sessions

Since no organic user traffic flows through the application during the development validation window, smoke sessions were collected via direct HTTP calls to the locally running backend:

1. **Backend startup** — Spring Boot JAR at `backend/target/backend-0.0.1-SNAPSHOT.jar`, started with `--spring.profiles.active=test` (bypasses JWT) and `--server.port=8081`. This is the full production code path, not a stub.

2. **Session ID selection** — scanned candidate IDs `w25s-0000` through `w25s-0200` using the same SHA-256 hash logic as `ExperimentTracker.assignVariant()` to identify which IDs fall into each variant bucket before calling the API.

3. **Live API calls** — each session ID was used to call `POST /api/llm/question-generate`, which triggered:
   - `ExperimentAwareLlmRouter.route()` → real variant assignment on the live JVM
   - Real OpenAI API call (`gpt-3.5-turbo`) → actual question generated
   - `estimateQuality(question)` → quality score based on question length
   - `experimentTracker.recordMetric()` → row written to `experiment_metric` in Aiven MySQL

4. **Total calls: 30** across distinct session IDs, producing 30 real `experiment_metric` rows.

---

## 4. Results

| Variant | n | Avg Quality Score | Avg Latency (ms) |
|---------|---|-------------------|-----------------|
| treatment | 10 | 60.0 | 1216 |
| baseline | 20 | 62.0 | 1330 |
| **Total** | **30** | — | — |

Treatment n=10 is below the minimum threshold of 20. Stage A remains HOLD.

Quality score note: `estimateQuality()` maps question length to three discrete values (<20 chars→30, 20–200 chars→60, >200 chars→80). All treatment questions scored 60 (length in 20–200 range). Two baseline sessions produced questions >200 chars and scored 80. The zero variance in treatment is a property of the heuristic, not model behavior.

---

## 5. Routing Validation

Variant assignment was performed by the live JVM using `ExperimentTracker.assignVariant()`. Treatment sessions confirmed:
- `w25s-0047`: treatment ✓
- `w25s-0072`: treatment ✓
- `w25s-0107`: treatment ✓
- `w25s-0134`: treatment ✓
- `w25s-0145`: treatment ✓
- `w25s-0158`: treatment ✓
- `w25s-0163`: treatment ✓
- `w25s-0000`: treatment ✓
- `w25s-0009`: treatment ✓
- `w25s-0014`: treatment ✓

All assignments deterministic and auditable by session ID + experiment ID.

---

## 6. Experiment Registry Update

`eval/experiment_registry.csv` updated with row `week25_live_stagea_20260530` (`collection_type=live_http`, n_treatment=10, n_baseline=20, total=30).

---

## 7. Artifact

`eval/results/week25_stagea_sample_snapshot.json` — contains full variant breakdown, session IDs, counts, and average scores.

---

## 8. Threshold Check

| Gate | Required | Observed | Status |
|------|----------|----------|--------|
| n_treatment ≥ 20 | 20 | 10 | **FAIL — HOLD** |
| n_baseline ≥ 20 | 20 | 20 | **PASS** |

n_treatment=10 below minimum threshold. Guardrail evaluation ran but results are indicative only — decision is HOLD pending additional sessions.
