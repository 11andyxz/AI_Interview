# Week 25 ML Decision Readout

**Date:** 2026-05-31  
**Week:** 25 (Days 97–100, May 25–29, 2026)  
**Branch:** `ml-production-readiness`  
**Overall Status:** HOLD — Stage A running; Stage B gate blocked  
**Data source:** Live HTTP calls to local backend (test profile, port 8081) — 30 real OpenAI-generated questions

---

## 1. Summary Table

| Task | Status | Decision |
|------|--------|----------|
| Task 1: Code/Artifact Sync + Preflight | ✅ Complete | UNBLOCKED |
| Task 2: Organic Feature-Writing Integration | ✅ Complete | Wired, pending activation |
| Task 3: Stage A Session Collection | ✅ Complete | n_treatment=10, baseline n=20 (30 total) |
| Task 4: Stage A Guardrail + Calibration | ✅ Complete | **HOLD** |

---

## 2. Preflight

| Field | Value |
|-------|-------|
| Run time | 2026-05-30T20:42:23Z |
| Overall | **UNBLOCKED** |
| PASS / WARN / FAIL | 11 / 10 / 0 |
| Artifact | `eval/results/week25_preflight_start.json` |

All checks passed. Warnings are optional env vars for guardrail slice tuning — not blocking.

---

## 3. Stage A Results

**Endpoint:** POST /api/llm/question-generate  
**Quality metric:** `estimateQuality(question)` — length heuristic: <20→30, 20–200→60, >200→80

### Sample Counts

| Variant | n |
|---------|---|
| Treatment | 10 |
| Baseline | 20 |
| Total | 30 |

### Quality Score

| Metric | Treatment | Baseline | Delta |
|--------|-----------|----------|-------|
| Mean | 60.00 | 62.00 | **-2.00** |
| Std | 0.00 | 6.16 | — |
| Min / Max | 60 / 60 | 60 / 80 | — |

All treatment questions scored 60 (length 20–200 chars). Two baseline questions exceeded 200 chars and scored 80. The heuristic's low granularity (3 possible values) limits discriminative power.

### Latency

| Metric | Treatment | Baseline |
|--------|-----------|----------|
| Mean (ms) | 1216 | 1330 |

No latency regression. Both well within 3000ms threshold.

### Statistical Test

| Test | Result |
|------|--------|
| Welch's t | t=-1.453, df=19.0 |
| p-value (approx) | 0.163 |
| Lift | -3.23% |
| p < 0.05 | **No** |

---

## 4. Guardrail Decision: HOLD

Hard blockers:
1. n_treatment=10 < min_sample_size=20
2. p=0.163 not significant (threshold: 0.05)

No quality regression (Δ=-2.0, well above the -10 regression threshold). HOLD is a data-quantity decision, not a quality failure.

---

## 5. Calibration Decision: HOLD

- platt-v2.1 remains the active model
- No calibration update: n_treatment=10 < 50, p not significant, no organic feature data
- Feature-writing pipeline wired (Task 2) — will activate when deployed with `ml.nlp.enabled=true`

---

## 6. Organic Feature-Writing Integration (Task 2)

| Write Path | Status |
|-----------|--------|
| `response_feature_cache` via `ResponseFeatureExtractor.extractAndCache()` | **WIRED** |
| `topic_coverage` via `TopicCoverageTracker.recordQuestionAsked()` | **WIRED** |
| `candidate_skill_profile` via `updateSkillProfile()` | **WIRED** |

All paths are non-fatal (catch + log). Conditional on `ml.nlp.enabled=true`. Compile: SUCCESS.

---

## 7. Stage B Gate

| Gate | Required | Current | Status |
|------|----------|---------|--------|
| n_treatment ≥ 20 (min_sample_size) | 20 | 10 | **Blocked** |
| p < 0.05 | — | p ≈ 0.163 | **Blocked** |
| No quality regression (Δ > -10) | — | -2.0 | Ready |
| Latency acceptable (avg < 3000ms) | — | 1330ms | Ready |

Stage B is **BLOCKED**. Stage A continues at 10% traffic.

---

## 8. Week 26 Recommendation

1. **Continue Stage A** at 10% — target n_treatment ≥ 50
2. **Deploy backend** with `ml.nlp.enabled=true` to activate organic feature writes
3. **Re-run guardrail** when n_treatment reaches 50
4. **Consider upgrading** `estimateQuality()` to a richer scoring function for better signal
5. **Stage B GO** gate: promote to 25% only after all four guardrails pass

**Rollback condition:** If quality delta drops below -10 points (treatment mean < baseline mean - 10) at any sample size, immediately roll back to baseline-only routing and freeze Stage A. If OpenAI error rate exceeds 5% in treatment sessions, pause experiment and investigate before resuming.

---

## 9. Artifacts

| Artifact | Location |
|----------|----------|
| Preflight result | `eval/results/week25_preflight_start.json` |
| Feature-write smoke | `eval/results/week25_feature_write_smoke.json` |
| Stage A sample snapshot | `eval/results/week25_stagea_sample_snapshot.json` |
| Stage A live result | `eval/results/week25_stagea_live_result.json` |
| Code/artifact sync report | `docs/week25_code_artifact_sync_report.md` |
| Organic feature-writing report | `docs/week25_organic_feature_writing_report.md` |
| Stage A sample collection report | `docs/week25_stagea_sample_collection_report.md` |
| Stage A guardrail readout | `docs/week25_stagea_guardrail_readout.md` |
| Calibration readiness report | `docs/week25_calibration_readiness_report.md` |
| Experiment registry (updated) | `eval/experiment_registry.csv` |
