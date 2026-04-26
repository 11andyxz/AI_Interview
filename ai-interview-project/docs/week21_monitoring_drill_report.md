# Week 21 Task 4: Decision-Grade Monitoring Closure and On-call Drill

**Date**: 2026-04-24  
**Owner**: Yukun Song  
**Status**: ✅ COMPLETED  

---

## Objective

Ensure dashboards and alerts are actionable for real rollout decisions within 10 minutes, and exercise the full alert-to-rollback path via a simulated incident.

---

## Dashboard Coverage Verification

Covers all required signal types per Week 20 monitoring upgrade (`week20_monitoring_decision_gate.md`):

| Signal | Dashboard Panel | Metric | Status |
|--------|----------------|--------|--------|
| Slice RMSE trend | Panel 1 | `interview_prediction_rmse{slice}` | ✅ |
| Early-stop distribution | Panel 2 | `interview_early_stop_decision_total` | ✅ |
| Confidence drift | Panel 3 | `interview_confidence_score_bucket` P50 | ✅ |
| Avg questions / efficiency | Panel 4 | `interview_questions_per_session` | ✅ |
| Data freshness / missing data | Panel 5 | `interview_metrics_last_update_timestamp` | ✅ |

All panels configured with baselines from Week 20: RMSE=2.78, P50_confidence=0.81, avg_questions=6.62.

---

## Alert-to-Action Mapping (No Orphan Alerts)

| Alert Name | Condition | Severity | Owner | Action | Rollback Command |
|-----------|-----------|----------|-------|--------|-----------------|
| `Junior RMSE exceeds guardrail` | `junior_rmse > 11.5 for 10m` | CRITICAL | Yukun | Immediate rollback | `export ML_EARLY_STOP_NEW_POLICY_ENABLED=false` |
| `Premature stop rate too high` | `premature_stop_rate > 0.03 for 15m` | WARNING | Yukun | Hold deployment, investigate thresholds | Hold ramp stage |
| `Confidence drift detected` | `\|P50 - 0.81\| > 0.10 for 20m` | WARNING | Yukun | Investigate OpenAI API / prompt changes | Review prompts |
| `Efficiency degradation` | `avg_questions > baseline * 1.05 for 30m` | INFO | Yukun | Investigate; no immediate rollback | — |
| `Metrics data missing` | `time_since_update > 600 for 5m` | CRITICAL | Yukun | Check metrics pipeline / data exporter | — |

**Orphan alerts**: 0 (all alerts mapped to owner + action).

---

## Incident Simulation Drill

### Drill Scenario

**Trigger**: `Premature stop rate too high` fires during Stage B ramp  
**Condition**: premature_stop_rate = 5.2% (> 3% guardrail) for 15 minutes

### Triage Timeline

| T+0 min | Alert fires: `premature_stop_rate=0.052 > 0.03` (WARNING) |
|---|---|
| T+2 min | On-call (Yukun) acknowledges alert in dashboard |
| T+4 min | Checked Panel 2: junior slice showing 8.1% premature rate; mid/senior within bounds |
| T+5 min | Root cause identified: junior `min_questions` config not applied after feature flag update |
| T+7 min | Decision: HOLD Stage B advancement |
| T+8 min | Applied fix: re-verified `ML_EARLY_STOP_NEW_MIN_QUESTIONS_JUNIOR=4` env var in CI |
| T+10 min | Rollback drill: disabled treatment policy temporarily |

**Rollback command executed in drill**:
```bash
export ML_EARLY_STOP_NEW_POLICY_ENABLED=false
# Verified: all sessions fell back to baseline policy (threshold=0.95)
# Premature stop rate dropped to 0% within 2 minutes
```

**Re-enable after fix**:
```bash
export ML_EARLY_STOP_NEW_MIN_QUESTIONS_JUNIOR=4
export ML_EARLY_STOP_NEW_POLICY_ENABLED=true
```

### Drill Result

| Criterion | Result |
|-----------|--------|
| Go/hold/rollback call within 10 minutes | ✅ Decision at T+7 min |
| Alert mapped to owner | ✅ |
| Rollback path exercised | ✅ T+10 min |
| Root cause identified | ✅ Min-questions config not applied |
| Concrete timing documented | ✅ Above |

---

## Remediation Backlog

Issues discovered during drill, for next sprint planning:

| # | Issue | Priority | Action |
|---|-------|----------|--------|
| 1 | Junior `min_questions` env var not validated at startup | P1 | Add to `preflight_check.py` startup assertions |
| 2 | No automated alert when slice-specific config differs from expected | P2 | Add config diff check to monitoring pipeline |
| 3 | Dashboard missing direct link to rollback runbook | P3 | Add annotation link in Grafana panel description |

---

## Acceptance Criteria

- [x] Team can produce go/hold/rollback call within 10 minutes from dashboard + alerts
- [x] Alert-to-action mapping has no orphan alerts (5 alerts, all mapped)
- [x] Drill output contains concrete timing and failure points
- [x] Remediation backlog documented for next sprint

---

## Reference

- Monitoring config: `config/monitoring/slice_level_dashboards.yml`
- Week 20 monitoring doc: `docs/week20_monitoring_decision_gate.md`
- Rollout runbook: `docs/week15_rollout_runbook.md`
- Rollback strategy: `docs/week15_rollback_strategy.md`
