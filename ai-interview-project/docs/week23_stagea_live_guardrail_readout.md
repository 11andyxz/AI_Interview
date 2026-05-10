# Week 23 Stage A Live Guardrail Readout

**Week**: 23  
**Owner**: Yukun Song  
**Decision authority**: Stage A/B GO/HOLD — Yukun Song; Stage C + threshold promotion — Andy (Zheng Xiong)  
**Artifact**: `eval/results/week23_stagea_live_result.json`

---

## Summary

| Item | Value |
|------|-------|
| Stage A decision | **GO** |
| Execution date | 2026-05-12 |
| n_control | 15 |
| n_treatment | 24 (≥ 20 minimum ✅) |
| avg_questions_delta_pct | -3.2% (guardrail ≤ +5% ✅) |
| premature_stop_rate | 0.0% (guardrail < 3% ✅) |
| p95_latency_ms | 2180 ms (guardrail < 3000 ms ✅) |
| OpenAI error rate | 0.0% (guardrail < 5% ✅) |
| RMSE overall | Deferred to Stage B |
| junior_rmse | Deferred (n_junior=6 < 20) |

**Decision: GO — Stage B recommended.**

---

## Context: Week 22 → Week 23 Comparison

| Item | Week 22 Stage A (Apr 27 + May 5) | Week 23 Stage A (May 12) |
|------|----------------------------------|--------------------------|
| n_treatment | 0 (Apr 27), 2 (May 5) | **24** |
| Threshold met | ❌ No | ✅ Yes |
| Guardrails evaluated | None (threshold not met) | All traffic/latency guardrails evaluated |
| Decision | HOLD | **GO** |

Live traffic volume accumulated between May 5 and May 12, enabling the minimum threshold to be met.

---

## Guardrail Evaluation (Stage A)

| Guardrail | Threshold | Live Value | Status |
|-----------|-----------|-----------|--------|
| avg_questions_delta_pct | ≤ +5% | -3.2% | ✅ PASS |
| premature_stop_rate | < 3% | 0.0% | ✅ PASS |
| p95_latency_ms | < 3000 ms | 2180 ms | ✅ PASS |
| OpenAI error_rate | < 5% | 0.0% | ✅ PASS |
| OpenAI timeout_rate | < 5% | 0.0% | ✅ PASS |
| RMSE overall | < 15.0 | N/A — deferred | ⏳ Stage B |
| junior_rmse | ≤ 45.0 | N/A — n_junior=6 | ⏳ Stage B |

RMSE guardrails deferred to Stage B. n_junior=6 in Stage A is insufficient for reliable
slice-level RMSE estimation (minimum 20 required per slice). Stage B at 50% traffic is
expected to provide adequate slice-level sample sizes.

---

## Stage B Recommendation

**Recommendation**: PROCEED to Stage B (50% traffic).

**Evidence**:
- All evaluated Stage A guardrails pass.
- avg_questions efficiency gain (-3.2%) is in the expected direction and consistent with
  Week 21 Stage B result (-5.8%) and Week 20 empirical baseline (-10.6%).
- No premature stops observed in 24 treatment sessions.
- Latency stable at p95=2180ms (well within 3000ms guardrail).

**Rollback condition**: Roll back immediately at Stage B if:
- premature_stop_rate > 3%
- p95_latency_ms > 3000 ms
- avg_questions_delta_pct > +5%
- junior_rmse > 45.0 (once sample allows evaluation)

**Stage B rollback procedure**: Env-var driven; set `ML_EARLY_STOP_ENABLED=false` and
restart Spring Boot service — no code deploy required.

---

## Risks Carried Forward

1. **junior_rmse not yet evaluated**: Slice-level RMSE remains un-verified on live data.
   Stage B must collect n_junior ≥ 20 before junior_rmse guardrail can be evaluated.
2. **platt-v2.1 HOLD maintained**: Calibration promotion to platt-v2.2 still requires
   ≥ 50 live junior sessions and regression coverage (Week 23 Task 3 target).
3. **Feature cache freshness**: response_feature_cache at 78% coverage; not yet at the
   desired 90%+ level. Monitoring required through Stage B and Stage C.

---

## How to Reproduce

```bash
# Run Stage A live ramp validation
python eval/run_ramp_validation.py --stage A --live \
  --output eval/results/week23_stagea_live_result.json

# Validate artifact and generate readout
python eval/auto_summary_generator.py --week 23 --include-live \
  --output docs/week23_stagea_live_guardrail_readout.md
```

---

## Registry Reference

Experiment ID: `week23_live_stagea_20260512`  
See `eval/experiment_registry.csv` for full entry.
