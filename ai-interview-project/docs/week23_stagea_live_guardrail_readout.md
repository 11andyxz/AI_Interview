# Week 23 Stage A Live Guardrail Readout

**Week**: 23  
**Owner**: Yukun Song  
**Decision authority**: Stage A/B GO/HOLD — Yukun Song; Stage C + threshold promotion — Andy (Zheng Xiong)  
**Monitoring period**: May 4–8, 2026

---

## Summary

| Item | Value |
|------|-------|
| Stage A decision | **HOLD** |
| Monitoring period | May 4–8, 2026 |
| Preflight result | BLOCKED (pass=11, warn=8, fail=2) |
| n_completed_interviews | 3 (all from March 2026, pre-ramp) |
| experiment table rows | 0 — no ramp sessions recorded |
| response_feature_cache | 0 rows ❌ |
| question_embedding | 0 rows ❌ |
| Guardrails evaluated | None — feature cache gate not met |
| Stage B | Blocked |

**Decision: HOLD — preflight BLOCKED; feature caches unpopulated; no treatment sessions recorded.**

---

## Context: Week 22 → Week 23

| Item | Week 22 Stage A | Week 23 Stage A (May 4–8) |
|------|-----------------|---------------------------|
| preflight result | UNBLOCKED (pass=9, warn=9, fail=2) | BLOCKED (pass=11, warn=8, fail=2) |
| experiment rows | 0 | 0 |
| response_feature_cache | 0 rows | 0 rows |
| question_embedding | 0 rows | 0 rows |
| Guardrails evaluated | None | None |
| Decision | HOLD | **HOLD** |

The preflight check now correctly connects to `ai_interview` database. Feature cache tables
exist but remain unpopulated. No ramp treatment sessions have been recorded in the
experiment table. Stage A HOLD continues from Week 22.

---

## Guardrail Status

| Guardrail | Threshold | Status |
|-----------|-----------|--------|
| preflight: response_feature_cache | > 0 rows | ❌ FAIL — 0 rows |
| preflight: question_embedding | > 0 rows | ❌ FAIL — 0 rows |
| preflight: topic_coverage | > 0 rows | ⚠️ WARN — 0 rows |
| avg_questions_delta_pct | ≤ +5% | ⏳ Not evaluated — preflight blocked |
| premature_stop_rate | < 3% | ⏳ Not evaluated — preflight blocked |
| p95_latency_ms | < 3000 ms | ⏳ Not evaluated — preflight blocked |
| OpenAI error_rate | < 5% | ⏳ Not evaluated — preflight blocked |
| RMSE overall | < 15.0 | ⏳ Not evaluated — preflight blocked |
| junior_rmse | ≤ 45.0 | ⏳ Not evaluated — preflight blocked |

Preflight result (2026-05-08, DB: ai_interview): **BLOCKED** — pass=11, warn=8, fail=2.
Feature cache population is required before any ramp stage can be evaluated.

---

## Stage B Status

**Stage B remains blocked** until Stage A meets minimum treatment volume and all guardrails pass.

**Rollback procedure** (documented, not yet needed): Set `ML_EARLY_STOP_ENABLED=false` and restart Spring Boot service — no code deploy required.

---

## Next Steps

- Continue monitoring Stage A treatment/control accumulation from live MySQL data
- Re-run Stage A guardrail evaluation once n_treatment meets minimum threshold
- Proceed to Stage B only after all Stage A guardrails pass

---

## Registry Reference

Experiment ID: `week23_live_stagea_20260508`  
See `eval/experiment_registry.csv` for full entry.
