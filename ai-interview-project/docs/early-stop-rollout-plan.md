# Early-Stop Policy Rollout Plan

**Document Version**: 1.0  
**Date**: March 27, 2026  
**Status**: Ready for Rollout

---

## Executive Summary

This document specifies the controlled rollout plan for the new dual-threshold early-stopping policy (0.90/0.10, minimum 6 questions), designed to reduce average interview length while maintaining prediction accuracy within acceptable bounds (RMSE change within 1-2%).

---

## Policy Overview

### Current Policy (Baseline)

| Parameter | Value |
|-----------|-------|
| Pass Threshold | 0.95 |
| Fail Threshold | 0.05 |
| Min Questions | 5 |
| Stability Threshold | 0.2 |

**Expected Performance**:
- Average questions: 10.0
- RMSE: 9.8
- Premature stop rate: <2%

### New Policy (Week 17 P0 Task 2)

| Parameter | Current | New |
|-----------|---------|-----|
| Pass Threshold | 0.95 | **0.90** |
| Fail Threshold | 0.05 | **0.10** |
| Min Questions | 5 | **6** |
| Stability Threshold | 0.2 | 0.2 (unchanged) |

**Expected Performance** (projected):
- Average questions: ~9.0 (10-15% reduction)
- RMSE: 9.8 - 10.3 (+0-2% max)
- Premature stop rate: <3%

---

## Feature Flag Configuration

### Configuration Properties

```properties
# Feature flag for new policy (Week 17 P0 Task 2)
ml.prediction.early-stopping.new-policy.enabled=false

# New policy thresholds (0.90 / 0.10, min 6 questions)
ml.prediction.early-stopping.new-policy.pass-threshold=0.90
ml.prediction.early-stopping.new-policy.fail-threshold=0.10
ml.prediction.early-stopping.new-policy.min-questions=6
```

### Rollback Mechanism

**Fast Rollback**: Set `ml.prediction.early-stopping.new-policy.enabled=false` to immediately revert to baseline policy without code deployment.

**Deployment-Free**: Configuration changes can be applied via environment variables or config management (no application restart required with Spring Cloud Config).

---

## A/B Experiment Design

### Experiment Structure

**Type**: Bucketed experiment (session-level randomization)

**Allocation**:
- Control Group (Baseline): 50%
- Treatment Group (New Policy): 50%

**Duration**: 2 weeks minimum (target: 200+ sessions per group)

**Bucketing Strategy**:
- Hash session ID modulo 2
- Bucket 0 → Control (baseline policy)
- Bucket 1 → Treatment (new policy: 0.90/0.10)

### Success Metrics

#### Primary Metrics

| Metric | Control Target | Treatment Target | Decision Threshold |
|--------|---------------|------------------|-------------------|
| **Average Questions** | 10.0 | ~9.0 | -10% reduction minimum |
| **RMSE Change** | Baseline | +0-2% | Must not exceed +2% |
| **Premature Stop Rate** | <2% | <3% | Must not exceed 3% |

#### Secondary Metrics

| Metric | Target | Notes |
|--------|--------|-------|
| False Positive Rate | <5% | Stop fail → actual pass |
| False Negative Rate | <10% | Stop pass → actual fail |
| Candidate Satisfaction | >85% | Survey after early-stop |
| Interviewer Override Rate | <10% | Manual continue after stop |

### Statistical Requirements

- **Minimum Sample Size**: 200 sessions per group
- **Confidence Level**: 95%
- **Power**: 80%
- **Effect Size**: 10-15% question reduction

---

## Rollout Phases

### Phase 1: Development Validation (Week 17, Day 1-2)

**Goal**: Verify configuration and code correctness

**Steps**:
1. Deploy code to development environment
2. Test both policies (new-policy.enabled=true/false)
3. Verify new thresholds (0.90/0.10, min 6) applied correctly
4. Test rollback mechanism

**Exit Criteria**:
- ✅ Both policies functional
- ✅ Rollback working
- ✅ No code errors

### Phase 2: Staging A/B Test (Week 17, Day 3-5)

**Goal**: Validate experiment setup with synthetic traffic

**Steps**:
1. Deploy to staging environment
2. Enable 50/50 A/B split
3. Run 10-20 synthetic interview sessions
4. Verify bucketing, metrics collection, and rollback

**Exit Criteria**:
- ✅ Bucketing working (50/50 split)
- ✅ Metrics logged correctly
- ✅ No P0/P1 issues

### Phase 3: Production Pilot (Week 18, Day 1-3)

**Goal**: Test with real traffic at low volume

**Steps**:
1. Deploy to production
2. Enable for 10% of traffic only
3. Monitor for 3 days
4. Analyze preliminary results

**Allocation**:
- 90% → Exclude from experiment
- 5% → Control (baseline: 0.95/0.05)
- 5% → Treatment (new policy: 0.90/0.10)

**Exit Criteria**:
- ✅ No critical bugs
- ✅ Early indicators positive
- ✅ No user complaints spike

### Phase 4: Full A/B Rollout (Week 18, Day 4-14)

**Goal**: Gather statistically significant results

**Steps**:
1. Increase to 50/50 A/B split
2. Run for 2 weeks (target: 200+ sessions each)
3. Monitor daily metrics
4. Prepare decision recommendation

**Allocation**:
- 50% → Control
- 50% → Treatment

**Exit Criteria**:
- ✅ Sample size achieved (200+ per group)
- ✅ Statistical significance confirmed
- ✅ Success metrics met

### Phase 5: Decision & Full Rollout (Week 19+)

**Option A: Rollout** (if success metrics met)
- Set `new-policy.enabled=true` for 100% traffic
- Monitor for 1 week
- Document final results

**Option B: Rollback** (if metrics fail)
- Set `new-policy.enabled=false`
- Analyze failure reasons
- Iterate on policy design

---

## Monitoring & Alerting

### Real-Time Dashboards

**Grafana Panels** (extend existing ML dashboard):

1. **Early-Stop Rate by Policy**
   ```promql
   rate(ml_early_stop_triggered_total{policy="baseline"}[1h])
   rate(ml_early_stop_triggered_total{policy="new_policy"}[1h])
   ```

2. **Average Questions by Policy**
   ```promql
   avg(ml_interview_question_count{policy="baseline"})
   avg(ml_interview_question_count{policy="new_policy"})
   ```

3. **RMSE by Policy**
   ```promql
   avg(ml_prediction_rmse{policy="baseline"})
   avg(ml_prediction_rmse{policy="new_policy"})
   ```

4. **Premature Stop Rate**
   ```promql
   rate(ml_premature_stop_total{policy="new_policy"}[1h])
   ```

### Alerts

| Alert | Condition | Severity | Action |
|-------|-----------|----------|--------|
| High Premature Stop Rate | >5% for 1 hour | P1 | Review immediately, consider rollback |
| RMSE Spike | >2% increase | P1 | Investigate, rollback if confirmed |
| Low Sample Size | <20 sessions/day | P2 | Check traffic allocation |
| Bucketing Imbalance | >60/40 split | P2 | Debug bucketing logic |

---

## Rollback Strategy

### Trigger Conditions

Rollback if ANY of the following occur:

1. **Premature stop rate** > 5% for 2 consecutive hours
2. **RMSE increase** > 2% (exceeds target tolerance)
3. **User complaints** > 10 in 24 hours
4. **False positive rate** > 8%
5. **Critical bug** detected in policy logic

### Rollback Procedure

**Step 1: Immediate Disable** (2 minutes)
```bash
# Set environment variable or update config
export ML_PREDICTION_EARLY_STOPPING_NEW_POLICY_ENABLED=false

# Or via kubectl (if using K8s)
kubectl set env deployment/ai-interview-backend \
  ML_PREDICTION_EARLY_STOPPING_NEW_POLICY_ENABLED=false
```

**Step 2: Verify Rollback** (5 minutes)
- Check logs for policy switch confirmation
- Verify Grafana shows baseline policy active
- Monitor for 5 minutes to confirm stability

**Step 3: Incident Report** (1 hour)
- Document trigger reason
- Capture metrics snapshot
- Create rollback post-mortem

**Step 4: Root Cause Analysis** (1-2 days)
- Analyze failure data
- Identify policy issues
- Plan improvements

---

## Risk Mitigation

### Risk 1: Premature Stopping Increases

**Probability**: Medium  
**Impact**: High (poor candidate experience)

**Mitigation**:
- Looser thresholds (0.90 pass, 0.10 fail)
- Higher minimum questions (6 vs 5)
- Stability requirement (0.2) unchanged

**Detection**:
- Real-time alert if >3% premature stops
- Daily manual review of early-stopped sessions

### Risk 2: RMSE Degradation

**Probability**: Low-Medium  
**Impact**: Medium (prediction quality reduction)

**Mitigation**:
- +2% tolerance (9.8 → ~10.0 max)
- Looser thresholds reduce false failures

**Detection**:
- Daily RMSE comparison (treatment vs control)
- Alert if exceeds +5%

### Risk 3: Implementation Bugs

**Probability**: Low  
**Impact**: High (incorrect policy application)

**Mitigation**:
- Unit tests for threshold configuration
- Integration tests for policy switching
- Manual validation in staging

**Detection**:
- Code review before deployment
- Staging validation phase

---

## Success Criteria

### Go/No-Go Decision

**GO** if ALL of the following are met after 2-week A/B test:

✅ Average questions reduced by ≥10%  
✅ RMSE increase ≤2%  
✅ Premature stop rate <3%  
✅ Statistical significance (p < 0.05)  
✅ No critical bugs or rollbacks  

**NO-GO** if ANY of the following occur:

❌ RMSE increase >2%  
❌ Premature stop rate >5%  
❌ User satisfaction <75%  
❌ Multiple rollbacks required  
❌ Sample size not achieved (<150 per group)  

---

## Timeline Summary

| Week | Phase | Activities | Decision Point |
|------|-------|-----------|----------------|
| Week 17 | Dev + Staging | Code, config, validation | Continue to pilot |
| Week 18 | Pilot → Full A/B | 10% → 50% rollout | Expand or rollback |
| Week 19 | Full Rollout | 100% if successful | Final decision |

**Total Duration**: 3 weeks from code deploy to final decision

---

## Deliverables

✅ **Code**:
- EarlyStoppingConfig.java (with NewPolicyConfig)
- Updated EarlyStoppingService.java
- Unit tests for policy switching logic

✅ **Configuration**:
- application.properties with feature flags
- Environment-specific overrides (dev/staging/prod)

✅ **Documentation**:
- This rollout plan
- Monitoring runbook
- Rollback procedure

✅ **Monitoring**:
- Grafana dashboard updates (4 new panels)
- PagerDuty alerts (4 new rules)

---

## Appendix A: Configuration Examples

### Development Environment

```properties
ml.prediction.early-stopping.enabled=true
ml.prediction.early-stopping.new-policy.enabled=true
```

### Staging Environment

```properties
ml.prediction.early-stopping.enabled=true
ml.prediction.early-stopping.new-policy.enabled=true
```

### Production Environment (Phase 3 - Pilot)

```properties
ml.prediction.early-stopping.enabled=true
ml.prediction.early-stopping.new-policy.enabled=false  # Start with baseline
```

### Production Environment (Phase 4 - Full A/B)

```properties
ml.prediction.early-stopping.enabled=true
ml.prediction.early-stopping.new-policy.enabled=true  # Enable for 50% via bucketing
```

---

## Appendix B: Test Cases

### Test Case 1: New Policy Activation

**Scenario**: Set new-policy.enabled=true  
**Expected**: Use 0.90/0.10 thresholds, min 6 questions  
**Validation**: Check logs for `policy=new_policy` and threshold values

### Test Case 2: Baseline Fallback

**Scenario**: Set new-policy.enabled=false during session  
**Expected**: Immediately switch to baseline (0.95/0.05, min 5)  
**Validation**: Verify policy change within 1 request, logs show `policy=baseline`

### Test Case 3: Minimum Questions Enforcement

**Scenario**: Session with 4 questions, high confidence (0.92 pass score)  
**Expected**: No early-stop (min 6 questions not met)  
**Validation**: Session continues, no `early_stop=true` event logged

---

**Document Owner**: ML Engineering Team  
**Review Date**: April 3, 2026  
**Approval Required**: Engineering Manager, Product Manager
