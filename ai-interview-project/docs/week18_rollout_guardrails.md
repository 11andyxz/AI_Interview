# Week 18 Rollout Guardrails & Safety Procedures

**Date**: April 9, 2026  
**Status**: Production-Ready  
**Rollback Time**: < 5 minutes  

---

## Overview

This document defines operational guardrails, monitoring thresholds, and rollback procedures for the early-stop policy A/B test and subsequent rollout.

---

## Feature Flag Configuration

### Environment Variables

All ML features controlled via environment variables (no hardcoded credentials or config):

```bash
# Core ML Services
ML_EMBEDDING_ENABLED=true
ML_NLP_ENABLED=true  
ML_PREDICTION_ENABLED=true

# Early-Stop Policies
ML_EARLY_STOP_ENABLED=true                    # Master toggle for early-stop logic
ML_EARLY_STOP_NEW_POLICY_ENABLED=true        # A/B test: false=baseline only, true=50/50 split

# Baseline Policy (Control Group)
ML_EARLY_STOP_PASS_THRESHOLD=0.95
ML_EARLY_STOP_FAIL_THRESHOLD=0.05
ML_EARLY_STOP_MIN_QUESTIONS=5

# New Policy (Treatment Group)
ML_EARLY_STOP_NEW_PASS_THRESHOLD=0.90
ML_EARLY_STOP_NEW_FAIL_THRESHOLD=0.10
ML_EARLY_STOP_NEW_MIN_QUESTIONS=6
ML_EARLY_STOP_STABILITY_THRESHOLD=0.2

# Database (secure: enforced env vars, no defaults)
DB_HOST=<mysql-host>
DB_PORT=22629
DB_NAME=ai_interview
DB_USERNAME=<username>
DB_PASSWORD=<password>
```

### Default Behavior

**All ML features default to OFF** when environment variables not set:
- `ml.embedding.enabled` → defaults to `false`
- `ml.prediction.enabled` → defaults to `false`
- `ml.prediction.early-stopping.new-policy.enabled` → defaults to `false`

**Safety**: No ML predictions run unless explicitly enabled. Ensures zero regression risk when ML toggles disabled.

---

## Monitoring Dashboard Coverage

### Grafana Dashboard: "AI Interview ML Monitoring - Week 18"

**Location**: http://localhost:3000  
**Panels** (5 total):

#### 1. Early-Stop Rate by Policy
- **Metric**: `ml_early_stop_triggered_total` (counter, tagged by `policy="baseline"` or `policy="new_policy"`)
- **Visualization**: Time series, grouped by policy  
- **Alert**: Rate >30% for 1 hour → P1 (indicates over-aggressive stopping)

#### 2. Average Questions per Session
- **Metric**: `ml_session_questions_total` / `ml_session_completed_total`
- **Visualization**: Gauge + time series
- **Baseline**: 10.0 questions
- **Target**: 8.5-9.5 (10-15% reduction)
- **Alert**: <8.0 or >11.0 for 30 min → P2 (drift from expected range)

#### 3. Premature Stop Rate
- **Metric**: `ml_premature_stop_total` (stops triggered before min questions threshold)
- **Visualization**: Percentage gauge
- **Threshold**: <3%
- **Alert**: >5% for 1 hour → P1 (safety guardrail violated)

#### 4. RMSE Trend by Experience Slice
- **Metric**: `ml_prediction_rmse` (gauge, tagged by `slice="junior"`, `slice="mid"`, `slice="senior"`)
- **Visualization**: Multi-line time series
- **Baselines**: Junior=12.4, Mid=8.9, Senior=7.2
- **Alert**: Junior RMSE >13.0 or Overall RMSE >10.2 (>2% increase) → P1

#### 5. ML Service Latency (P95)
- **Metric**: `ml_outcome_prediction_duration_seconds` (histogram, P95)
- **Visualization**: Heatmap
- **Baseline**: 1920ms (P95)
- **Alert**: >2500ms for 15 min → P2 (performance degradation)

### Additional Metrics (Prometheus)

- `ml_feature_extraction_success_rate` - Target: ≥95%
- `ml_token_usage_tokens` - OpenAI API cost monitoring
- `ml_prediction_error_total` - Catch prediction failures
- `jdbc_connections_active` - Database health
- `jvm_memory_used_bytes` - Memory leak detection

---

## Alert Rules

### P0 Alerts (Immediate Action Required)

| Condition | Threshold | Action |
|-----------|-----------|--------|
| Application down | Health check fails 3× in 2 min | Page on-call, investigate crash/OOM |
| Database connection lost | Connection pool exhausted or timeout >10s | Check DB health, check credentials |
| ML prediction failure rate | >10% for 5 min | Disable ML features, rollback if A/B test |

### P1 Alerts (Urgent, Act Within 1 Hour)

| Condition | Threshold | Action |
|-----------|-----------|--------|
| Premature stop rate | >5% for 1 hour | Review early-stop logic, consider rollback |
| RMSE spike | >2% increase (>10.2) for 1 hour | Analyze data distribution, check calibration |
| Junior RMSE regression | >13.0 for 1 hour | Disable new policy for junior slice |
| Early-stop rate anomaly | >30% or <5% for 1 hour | Investigate threshold config or data quality |

### P2 Alerts (Monitor, Act Within 4 Hours)

| Condition | Threshold | Action |
|-----------|-----------|--------|
| Latency degradation | P95 >2500ms for 15 min | Profile slow queries, check API rate limits |
| Question drift | Avg questions <8 or >11 for 30 min | Review session distribution, check stopping logic |
| Feature extraction failures | Success rate <95% for 30 min | Check NLP service, validate input sanitization |

---

## Rollback Procedures

### Scenario 1: Disable A/B Test (Return to Baseline Only)

**Trigger**: New policy shows regression (RMSE >+2% or premature stop >5%)

**Steps**:
1. Set environment variable: `ML_EARLY_STOP_NEW_POLICY_ENABLED=false`
2. Restart application:
   ```bash
   # Stop current process (Ctrl+C in terminal, or kill PID)
   # Restart with updated env var
   cd backend
   mvn spring-boot:run
   ```
3. Verify rollback:
   ```bash
   curl http://localhost:8080/actuator/health | jq '.components.ML'
   # Should show: "early_stop_new_policy": "disabled"
   ```
4. Monitor Grafana for 15 minutes:
   - Early-stop rate should return to ~12%
   - Avg questions should return to ~10
   - RMSE should stabilize at baseline 9.8
5. Document in rollback log

**Time**: < 5 minutes (1 min config change + 2 min restart + 2 min verification)

### Scenario 2: Disable All ML Features

**Trigger**: Critical ML service failure or cascading errors

**Steps**:
1. Set environment variables:
   ```bash
   ML_EMBEDDING_ENABLED=false
   ML_NLP_ENABLED=false
   ML_PREDICTION_ENABLED=false
   ML_EARLY_STOP_ENABLED=false
   ```
2. Restart application (same as Scenario 1)
3. Verify:
   ```bash
   curl http://localhost:8080/actuator/health | jq '.components.ML'
   # Should show: "status": "DISABLED" or not present
   ```
4. Impact: Application continues without ML predictions (graceful degradation):
   - No early-stop (all interviews run to natural completion)
   - No embedding-based question selection (falls back to rule-based)
   - No outcome prediction

**Time**: < 5 minutes

### Scenario 3: Rollback to Previous Code Version

**Trigger**: New ML code introduces bugs or crashes

**Steps**:
1. Identify last known good commit:
   ```bash
   git log --oneline --all | head -20
   # Find commit before ML integration (e.g., commit before d9b6470)
   ```
2. Checkout previous version:
   ```bash
   git checkout <previous-commit-hash>
   ```
3. Rebuild and restart:
   ```bash
   cd backend
   mvn clean install -DskipTests
   mvn spring-boot:run
   ```
4. Verify health check
5. Communicate to team via Slack/email

**Time**: 10-15 minutes (5 min build + 5 min restart + 5 min verification)

---

## Rollback Decision Matrix

| Issue | Severity | Rollback Scenario | Timeline |
|-------|----------|-------------------|----------|
| Premature stop rate >5% | P1 | Scenario 1 (disable new policy) | < 5 min |
| RMSE increase >2% | P1 | Scenario 1 (disable new policy) | < 5 min |
| Junior RMSE >13.0 | P1 | Scenario 1 or slice-specific disable | < 5 min |
| ML prediction errors >10% | P0 | Scenario 2 (disable all ML) | < 5 min |
| Application crash loop | P0 | Scenario 3 (code rollback) | 10-15 min |
| Database corruption | P0 | Restore DB backup + Scenario 3 | 30-60 min |

---

## Gradual Rollout Plan (Post-Decision)

**Assumption**: A/B test shows positive results (10-15% efficiency gain, RMSE stable)

### Phase 1: Pilot (10% Traffic)
- **Duration**: 2-3 days
- **Allocation**: 10% new policy, 90% baseline
- **Monitoring**: Hourly dashboard checks
- **Gate**: <2% premature stop rate, RMSE within +1%

### Phase 2: Ramp to 25%
- **Duration**: 2-3 days
- **Allocation**: 25% new policy, 75% baseline
- **Monitoring**: Twice daily checks
- **Gate**: Premature stop <3%, RMSE within +1.5%

### Phase 3: Ramp to 50%
- **Duration**: 3-5 days  
- **Allocation**: 50% new policy, 50% baseline
- **Monitoring**: Daily checks
- **Gate**: Statistical significance confirmed (n>100/group)

### Phase 4: Ramp to 75%
- **Duration**: 2-3 days
- **Allocation**: 75% new policy, 25% baseline
- **Monitoring**: Daily checks
- **Gate**: No P1 alerts for 48 hours

### Phase 5: Full Rollout (100%)
- **Timing**: After Phase 4 success + stakeholder approval
- **Allocation**: 100% new policy
- **Monitoring**: Continue for 2 weeks
- **Final Gate**: Baseline metrics retired, new policy becomes new baseline

**Total Timeline**: 11-19 days from decision to full rollout

---

## Security Checklist

- [x] No hardcoded database credentials in code  
- [x] All secrets via environment variables  
- [x] SSL/TLS enabled for database connections (`sslMode=REQUIRED`)
- [x] No API keys in application.properties (loaded from database)
- [x] Redis auto-configuration disabled (not needed for MVP)
- [x] Feature flags tested for safe default-off behavior
- [x] Rollback procedure tested in staging (< 5 min verified)
- [x] Environment variable validation on startup (fails fast if missing)

---

## Testing Checklist

### Pre-Rollout Verification

- [x] Baseline policy behavior validated (0.95/0.05, min 5 questions)
- [x] New policy behavior validated (0.90/0.10, min 6 questions)
- [x] Feature flag toggle verified (enable/disable new policy without code change)
- [x] Prometheus metrics export confirmed (~90 metrics)
- [x] Grafana dashboard accessible and displaying data
- [x] Health endpoint returns UP status with ML components
- [x] Database connection pool healthy (10 max, 2 idle)
- [x] 15 E2E sessions executed successfully (staging validation, Apr 3)

### Post-Rollout Validation

- [ ] Collect 100+ sessions per treatment group
- [ ] Calculate paired t-test with 95% CI
- [ ] Verify premature stop rate <3%
- [ ] Confirm junior RMSE ≤11.5
- [ ] Monitor for 48 hours with no P1 alerts
- [ ] Document final metrics in experiment_registry.csv

---

## Rollback Log Template

When rollback executed, document in `docs/rollback_log.md`:

```markdown
## Rollback Event: [Date] [Time]

**Trigger**: [Premature stop rate exceeded 5% | RMSE regression | etc.]
**Scenario**: [1 | 2 | 3]
**Performed By**: [Name]
**Duration**: [X minutes]

**Metrics Before Rollback**:
- Early-stop rate: X%
- Avg questions: X
- RMSE: X

**Metrics After Rollback** (15 min post-rollback):
- Early-stop rate: X%
- Avg questions: X
- RMSE: X

**Root Cause**: [Analysis of what went wrong]
**Action Items**: [Follow-up tasks to prevent recurrence]
```

---

## Contact & Escalation

**Primary Contact**: Yukun Song (ML Engineer)  
**Backup**: Andy Zheng (Tech Lead)  
**Escalation Path**: P0 → Page on-call, P1 → Slack alert + email, P2 → Email only

**Monitoring Access**:
- Grafana: http://localhost:3000 (admin/admin)
- Prometheus: http://localhost:8080/actuator/prometheus
- Application Health: http://localhost:8080/actuator/health

---

## Version History

| Version | Date | Changes |
|---------|------|---------|
| 1.0 | Apr 3, 2026 | Initial rollout plan (A/B test launch) |
| 1.1 | Apr 9, 2026 | Added monitoring dashboard details, rollback procedures, gradual ramp plan |

**Status**: Production-ready, rollback tested < 5 min
