# Week 15 ML Features - Rollback Strategy

## Overview

This document provides comprehensive rollback procedures for Week 14 ML features (embedding, NLP, prediction) in case of production issues. The strategy ensures safe and rapid rollback with minimal service disruption.

**Key Principle**: ML features are designed for **zero-downtime rollback** via feature flags without database rollback.

**Target Audience**: DevOps Engineers, SRE, Incident Commanders

**Related Documents**:
- [Week 15 Rollout Runbook](week15_rollout_runbook.md)
- [Week 15 Baseline Monitoring Metrics](week15_baseline_monitoring_metrics.md)

---

## Rollback Readiness Checklist

Before production rollout, ensure rollback is tested and ready:

- [ ] Feature flags tested in staging
- [ ] Rollback procedure tested in staging environment
- [ ] All ML components use `@ConditionalOnProperty` (backward compatible)
- [ ] Database migrations are additive only (no destructive changes)
- [ ] Previous version deployment artifacts available
- [ ] Incident response team trained on rollback procedure
- [ ] Monitoring alerts configured for rollback triggers

---

## Rollback Triggers

### Automatic Rollback Triggers (P0 - Critical)

Execute immediate rollback if any of these conditions occur:

| Trigger | Threshold | Detection Time | Action |
|---------|-----------|----------------|--------|
| Production error rate | > 5% | 5 minutes | Immediate rollback via feature flags |
| OpenAI API error rate | > 10% | 5 minutes | Immediate rollback via feature flags |
| Database connection pool exhaustion | > 90% | 2 minutes | Immediate rollback via feature flags |
| Redis connection failures | > 100 in 5 min | 5 minutes | Immediate rollback via feature flags |
| Response time p95 | > 5000ms | 10 minutes | Immediate rollback via feature flags |

### Manual Rollback Triggers (P1 - High)

Evaluate and decide on rollback within specified time:

| Trigger | Threshold | Evaluation Time | Decision Timeline |
|---------|-----------|-----------------|-------------------|
| Interview completion rate drop | > 10% | 30 minutes | 1 hour |
| Prediction RMSE degradation | > 30 | 1 hour | 2 hours |
| Early stopping false positive rate | > 20% | 1 hour | 4 hours |
| Customer complaints spike | > 10 in 4 hours | 4 hours | 24 hours |
| OpenAI API cost spike | > 200% of baseline | 1 day | 24 hours |

---

## Rollback Methods

### Method 1: Feature Flag Rollback (Recommended - Fastest)

**Speed**: < 5 minutes  
**Risk**: Low  
**Scope**: Disables ML features only, keeps code deployed  
**Reversibility**: Immediate (re-enable flags)

**Use Cases**:
- ML-specific issues (high prediction error, early stopping bugs)
- OpenAI API issues (rate limiting, errors)
- Redis/cache issues
- Any P0 trigger

**Advantages**:
- ✅ Instantaneous rollback (no redeployment)
- ✅ Zero downtime
- ✅ Easy to re-enable for testing
- ✅ Gradual rollback possible (reduce from 100% → 50% → 10% → 0%)

**Disadvantages**:
- ❌ Code remains deployed (if code bug exists, feature flag won't help)
- ❌ Requires monitoring to confirm flags took effect

---

### Method 2: Full Version Rollback (Fallback)

**Speed**: 10-15 minutes  
**Risk**: Medium  
**Scope**: Reverts entire application to previous stable version  
**Reversibility**: Requires redeployment

**Use Cases**:
- Code-level bugs not fixable via feature flags
- Severe performance issues in non-ML code
- Database migration issues (rare, as migrations are additive)

**Advantages**:
- ✅ Complete rollback of all changes
- ✅ Well-tested procedure (standard deployment rollback)

**Disadvantages**:
- ❌ Longer rollback time
- ❌ Brief service disruption during deployment
- ❌ Requires redeployment to fix forward

---

### Method 3: Database Migration Rollback (Emergency Only)

**Speed**: 30-60 minutes  
**Risk**: High  
**Scope**: Reverts database schema changes  
**Reversibility**: Complex, requires data backup

**Use Cases**:
- Critical database performance issues from new tables
- Data corruption (extremely rare)

**NOTE**: Week 14 migrations (V16-V19) are additive only. No rollback needed unless emergency.

**Procedure**: See "Database Rollback Emergency Procedure" section below.

---

## Rollback Procedures

### Procedure 1: Feature Flag Rollback (Primary Method)

#### Step 1: Assess Situation (2 minutes)

```bash
# Check current feature flag state
kubectl exec deployment/ai-interview-backend -- env | grep "ML_.*_ENABLED"

# Expected in production after rollout:
# ML_EMBEDDING_ENABLED=true
# ML_NLP_ENABLED=true
# ML_PREDICTION_ENABLED=true
```

#### Step 2: Announce Rollback (1 minute)

Post in incident Slack channel:
```
🚨 INCIDENT: Production ML Features Rollback Initiated
Trigger: [reason]
Action: Disabling ML features via feature flags
ETA: 5 minutes
Incident Commander: [your name]
```

#### Step 3: Execute Rollback (2 minutes)

**AWS ECS**:
```bash
# Create rollback task definition with disabled flags
cat > ecs-task-rollback.json << EOF
{
  "family": "ai-interview-backend-prod",
  "containerDefinitions": [{
    "name": "backend",
    "environment": [
      {"name": "ML_EMBEDDING_ENABLED", "value": "false"},
      {"name": "ML_NLP_ENABLED", "value": "false"},
      {"name": "ML_PREDICTION_ENABLED", "value": "false"},
      {"name": "ML_ADAPTIVE_ENABLED", "value": "false"}
    ]
  }]
}
EOF

# Register and deploy
aws ecs register-task-definition --cli-input-json file://ecs-task-rollback.json
aws ecs update-service \
  --cluster ai-interview-prod \
  --service ai-interview-backend \
  --task-definition ai-interview-backend-prod:ROLLBACK_REVISION \
  --force-new-deployment
```

**Kubernetes**:
```bash
# Update ConfigMap to disable flags
kubectl patch configmap ai-interview-config-prod -n ai-interview-prod \
  --type merge \
  -p '{"data":{"ML_EMBEDDING_ENABLED":"false","ML_NLP_ENABLED":"false","ML_PREDICTION_ENABLED":"false","ML_ADAPTIVE_ENABLED":"false"}}'

# Restart deployment to pick up changes
kubectl rollout restart deployment/ai-interview-backend -n ai-interview-prod

# Watch rollout
kubectl rollout status deployment/ai-interview-backend -n ai-interview-prod
```

**Manual Environment Variable Update (Docker/VM)**:
```bash
# Update .env file
export ML_EMBEDDING_ENABLED=false
export ML_NLP_ENABLED=false
export ML_PREDICTION_ENABLED=false
export ML_ADAPTIVE_ENABLED=false

# Restart application
systemctl restart ai-interview-backend

# Or using docker-compose
docker-compose restart backend
```

#### Step 4: Verify Rollback (2 minutes)

```bash
# Check that ML beans are NOT loaded
kubectl exec deployment/ai-interview-backend -- \
  curl -s localhost:8080/actuator/beans | \
  jq '.contexts.application.beans | keys[]' | \
  grep -c "embedding\|nlp\|prediction"

# Expected output: 0 (no ML beans)

# Check application logs
kubectl logs -f deployment/ai-interview-backend | grep "ConditionalOnProperty"
# Expected: "Did not match due to ml.embedding.enabled=false"

# Verify no OpenAI API calls in logs
kubectl logs -f deployment/ai-interview-backend --since=1m | grep "openai"
# Expected: No new API calls after rollback

# Check metrics
curl -s https://prod-backend/actuator/prometheus | grep "ml_features_enabled"
# Expected: ml_features_enabled_interviews_total should stop increasing
```

#### Step 5: Monitor Post-Rollback (15 minutes)

Monitor these metrics for 15 minutes to confirm rollback success:

- [ ] Error rate drops back to baseline (< 1%)
- [ ] Response time p95 returns to normal (< 500ms)
- [ ] OpenAI API requests stop
- [ ] No new customer complaints
- [ ] Interview completion rate returns to normal

#### Step 6: Announce Completion (1 minute)

Post in incident Slack channel:
```
✅ RESOLVED: ML Features Rollback Complete
Status: All ML features disabled via feature flags
Verification: 
- Error rate: [current] (baseline: X%)
- Response time p95: [current]ms (baseline: Xms)
- OpenAI API calls: 0 in last 5 minutes
Action: Investigating root cause
Next Steps: RCA within 24 hours
```

---

### Procedure 2: Full Version Rollback

Use this if feature flag rollback is insufficient (e.g., code-level bug in non-ML code).

#### Step 1: Identify Previous Stable Version (1 minute)

```bash
# AWS ECS: List task definition revisions
aws ecs list-task-definitions --family-prefix ai-interview-backend-prod

# Kubernetes: List deployment history
kubectl rollout history deployment/ai-interview-backend -n ai-interview-prod

# Identify last known stable revision (before ML features enabled)
STABLE_REVISION="ai-interview-backend-prod:42"  # Example
```

#### Step 2: Announce Rollback (1 minute)

Post in incident Slack channel:
```
🚨 INCIDENT: Full Version Rollback to Pre-ML Release
Trigger: [reason - feature flags insufficient]
Action: Rolling back to version [X]
ETA: 15 minutes
WARNING: Brief service interruption expected
Incident Commander: [your name]
```

#### Step 3: Execute Version Rollback (10 minutes)

**AWS ECS**:
```bash
# Rollback to previous task definition
aws ecs update-service \
  --cluster ai-interview-prod \
  --service ai-interview-backend \
  --task-definition $STABLE_REVISION \
  --force-new-deployment

# Wait for deployment
aws ecs wait services-stable \
  --cluster ai-interview-prod \
  --services ai-interview-backend
```

**Kubernetes**:
```bash
# Rollback to previous revision
kubectl rollout undo deployment/ai-interview-backend -n ai-interview-prod

# Or rollback to specific revision
kubectl rollout undo deployment/ai-interview-backend --to-revision=42 -n ai-interview-prod

# Watch rollout
kubectl rollout status deployment/ai-interview-backend -n ai-interview-prod
```

#### Step 4: Verify Rollback (5 minutes)

```bash
# Check running version
kubectl describe pod -l app=ai-interview-backend | grep "Image:"
# Expected: Previous version image tag

# Check health
curl https://prod-backend/actuator/health
# Expected: {"status":"UP"}

# Run smoke tests
./scripts/smoke-test-production.sh

# Check recent error logs
kubectl logs -f deployment/ai-interview-backend --since=5m | grep ERROR
# Expected: No errors
```

#### Step 5: Monitor and Announce (ongoing)

Same as Procedure 1 steps 5-6.

---

### Procedure 3: Partial Rollback (Gradual Disable)

Use this for less severe issues where progressive disabling is safer.

**Scenario**: Issue affects only one ML component (e.g., outcome prediction has bugs, but embedding works fine).

#### Step 1: Identify Affected Component

- Embedding issues → Disable `ML_EMBEDDING_ENABLED`
- NLP issues → Disable `ML_NLP_ENABLED`
- Prediction issues → Disable `ML_PREDICTION_ENABLED`
- Adaptive selection issues → Disable `ML_ADAPTIVE_ENABLED`

#### Step 2: Disable Specific Feature

```bash
# Example: Disable only prediction, keep embedding and NLP
kubectl patch configmap ai-interview-config-prod -n ai-interview-prod \
  --type merge \
  -p '{"data":{"ML_PREDICTION_ENABLED":"false"}}'

kubectl rollout restart deployment/ai-interview-backend -n ai-interview-prod
```

#### Step 3: Verify and Monitor

Same as Procedure 1 steps 4-6, but monitor specific feature metrics.

---

### Procedure 4: Gradual Traffic Reduction (Canary Rollback)

Use this when rollout is at partial traffic (10%, 50%) and you want to reduce rather than fully disable.

**Example**: At 50% rollout, reduce to 10% to contain issue.

```bash
# Update canary percentage
kubectl patch configmap ai-interview-config-prod -n ai-interview-prod \
  --type merge \
  -p '{"data":{"ML_CANARY_PERCENTAGE":"10"}}'

kubectl rollout restart deployment/ai-interview-backend -n ai-interview-prod
```

Monitor for 30 minutes. If issue persists, proceed to full rollback (Procedure 1).

---

## Database Rollback Emergency Procedure

**⚠️ WARNING**: Only use if database corruption or severe performance issues. Requires data backup and may cause data loss.

### Pre-Rollback Checklist

- [ ] Confirm issue is database-related (not code)
- [ ] Verify recent database backup exists
- [ ] Estimate data loss window (time since last backup)
- [ ] Get approval from Engineering Director + DBA

### Rollback Steps

#### 1. Stop All Application Instances

```bash
# Kubernetes
kubectl scale deployment/ai-interview-backend --replicas=0 -n ai-interview-prod

# ECS
aws ecs update-service --cluster ai-interview-prod --service ai-interview-backend --desired-count 0
```

#### 2. Create Database Backup (Current State)

```bash
# MySQL
mysqldump -h prod-db-host -u admin -p ai_interview > backup-before-rollback-$(date +%Y%m%d_%H%M%S).sql

# Or use cloud provider snapshot
aws rds create-db-snapshot \
  --db-instance-identifier ai-interview-prod \
  --db-snapshot-identifier ai-interview-rollback-$(date +%Y%m%d-%H%M%S)
```

#### 3. Rollback Migrations

**NOTE**: Week 14 added tables: `question_embedding`, `topic_coverage`, `response_feature_cache`, `candidate_skill_profile`

**Option A: Drop New Tables** (safest - no data loss for core features):
```sql
-- Connect to database
mysql -h prod-db-host -u admin -p ai_interview

-- Drop Week 14 tables (no foreign key constraints)
DROP TABLE IF EXISTS candidate_skill_profile;
DROP TABLE IF EXISTS response_feature_cache;
DROP TABLE IF EXISTS topic_coverage;
DROP TABLE IF EXISTS question_embedding;

-- Update Flyway version to V15 (last pre-ML migration)
UPDATE flyway_schema_history 
SET success = 0 
WHERE version IN ('16', '17', '18', '19');
```

**Option B: Keep Tables but Truncate** (if you want to retry later):
```sql
TRUNCATE TABLE candidate_skill_profile;
TRUNCATE TABLE response_feature_cache;
TRUNCATE TABLE topic_coverage;
TRUNCATE TABLE question_embedding;
```

#### 4. Restart Application (Pre-ML Version)

```bash
# Deploy pre-ML version
kubectl set image deployment/ai-interview-backend \
  backend=ai-interview-backend:pre-ml-stable \
  -n ai-interview-prod

kubectl scale deployment/ai-interview-backend --replicas=3 -n ai-interview-prod
```

#### 5. Verify Database Rollback

```bash
# Check tables exist (core tables should be intact)
mysql -h prod-db-host -u admin -p -e "SHOW TABLES FROM ai_interview;"

# Verify Flyway history
mysql -h prod-db-host -u admin -p -e "SELECT * FROM ai_interview.flyway_schema_history ORDER BY installed_rank DESC LIMIT 5;"

# Check application can connect and query
curl https://prod-backend/actuator/health
```

---

## Rollback Testing

### Staging Rollback Test (Required Before Production)

Test all rollback procedures in staging before production rollout.

#### Test 1: Feature Flag Rollback

```bash
# 1. Enable ML features in staging
kubectl patch configmap ai-interview-config-staging -n ai-interview-staging \
  --type merge \
  -p '{"data":{"ML_EMBEDDING_ENABLED":"true"}}'
kubectl rollout restart deployment/ai-interview-backend -n ai-interview-staging

# 2. Verify ML enabled
kubectl exec deployment/ai-interview-backend -n ai-interview-staging -- \
  curl -s localhost:8080/actuator/beans | jq '.contexts.application.beans | keys[]' | grep -c embedding
# Expected: > 0

# 3. Execute rollback
kubectl patch configmap ai-interview-config-staging -n ai-interview-staging \
  --type merge \
  -p '{"data":{"ML_EMBEDDING_ENABLED":"false"}}'
kubectl rollout restart deployment/ai-interview-backend -n ai-interview-staging

# 4. Verify ML disabled
kubectl exec deployment/ai-interview-backend -n ai-interview-staging -- \
  curl -s localhost:8080/actuator/beans | jq '.contexts.application.beans | keys[]' | grep -c embedding
# Expected: 0

# 5. Record rollback time
# Target: < 5 minutes
```

**Test Record**:
- Test Date: ___________
- Rollback Time: _______ minutes
- Success: ✅ / ❌
- Issues Found: ___________

#### Test 2: Full Version Rollback

```bash
# 1. Deploy ML-enabled version to staging
kubectl set image deployment/ai-interview-backend backend=ai-interview-backend:with-ml -n ai-interview-staging

# 2. Verify deployment successful
kubectl rollout status deployment/ai-interview-backend -n ai-interview-staging

# 3. Execute version rollback
kubectl rollout undo deployment/ai-interview-backend -n ai-interview-staging

# 4. Verify rollback successful
kubectl rollout status deployment/ai-interview-backend -n ai-interview-staging

# 5. Check application health
curl https://staging-backend/actuator/health

# 6. Record rollback time
# Target: < 15 minutes
```

**Test Record**:
- Test Date: ___________
- Rollback Time: _______ minutes
- Success: ✅ / ❌
- Issues Found: ___________

---

## Post-Rollback Actions

### Immediate (Within 1 Hour)

1. **Confirm Rollback Success**
   - [ ] All metrics returned to baseline
   - [ ] No new customer complaints
   - [ ] Application healthy and stable

2. **Update Status Page**
   - Post incident notice on status page
   - Communicate expected resolution timeline

3. **Start RCA Document**
   - Create incident postmortem document
   - Assign incident commander as owner

### Short Term (Within 24 Hours)

1. **Root Cause Analysis**
   - Identify what triggered rollback
   - Determine why issue wasn't caught in staging
   - Document lessons learned

2. **Fix Development**
   - Create Jira ticket for fix
   - Assign to ML team
   - Set priority based on severity

3. **Stakeholder Communication**
   - Email to engineering team with RCA summary
   - Update product team on timeline

### Medium Term (Within 1 Week)

1. **Fix Validation**
   - Develop fix in feature branch
   - Test fix in development environment
   - Validate fix in staging for 48 hours minimum

2. **Rollout Plan Update**
   - Update Week 15 Rollout Runbook with lessons learned
   - Add additional validation steps if needed
   - Update monitoring thresholds if needed

3. **Re-Rollout Decision**
   - Go/No-Go review with stakeholders
   - If approved, restart rollout from Phase 3 (10% canary)

---

## Rollback Decision Tree

```
┌─────────────────────────────┐
│   Production Issue Detected │
└──────────────┬──────────────┘
               │
               ▼
       ┌───────────────┐
       │ Is ML-related? │
       └───┬───────┬───┘
           │Yes    │No
           │       │
           │       └──────────────────────────┐
           ▼                                  ▼
    ┌──────────────┐                  ┌────────────────┐
    │ Error > 5%?  │                  │  Standard Ops  │
    │ or Critical? │                  │   Procedures   │
    └──┬───────┬───┘                  └────────────────┘
      │Yes    │No
      │       │
      │       └────────────────────────┐
      ▼                                │
┌──────────────────┐                   │
│ IMMEDIATE ROLLBACK│                   │
│ Via Feature Flags │                   │
│    (< 5 min)     │                   │
└───────────────────┘                   │
                                        ▼
                                 ┌──────────────┐
                                 │  P1 Issue?   │
                                 └──┬────────┬──┘
                                   │Yes     │No
                                   │        │
                                   │        └─────────────────┐
                                   ▼                          │
                            ┌──────────────┐                  │
                            │  Evaluate    │                  │
                            │  for Rollback│                  │
                            │ (1-4 hours)  │                  │
                            └──┬───────────┘                  │
                               │Rollback                      │
                               │Decision                      │
                               ▼                              ▼
                        ┌───────────────┐            ┌──────────────┐
                        │ Feature Flag  │            │   Monitor    │
                        │   Rollback    │            │  & Fix Fwd   │
                        └───────────────┘            └──────────────┘
```

---

## Rollback Communication Templates

### Incident Announcement Template

```
🚨 INCIDENT: [SEVERITY] - ML Features Rollback

Status: INVESTIGATING / IN PROGRESS / RESOLVED
Trigger: [Brief description of issue]
Impact: [% of users affected]
Action Taken: [Rollback method]
ETA: [Expected resolution time]
Incident Commander: @[name]
War Room: [Zoom/Slack link]

Updates will be posted every 15 minutes.
```

### Rollback Completion Template

```
✅ RESOLVED: ML Features Rollback Complete

Issue: [Brief description]
Rollback Method: [Feature flags / Full version]
Duration: [Start time] - [End time] (X minutes)
Impact: [Number of affected users/interviews]

Metrics Post-Rollback:
- Error Rate: [current] (baseline: X%)
- Response Time p95: [current]ms
- OpenAI API Calls: Stopped
- Customer Complaints: [count]

Next Steps:
1. RCA document: [Link]
2. Fix ETA: [timeline]
3. Re-rollout plan: TBD pending fix validation

Thank you for your quick response. Post-incident review scheduled for [date/time].
```

---

## Monitoring Dashboard for Rollback

Create dedicated "Rollback Status" dashboard in Grafana with these panels:

1. **ML Feature State** (gauge)
   - Shows current state of each ML feature flag
   - Green = enabled, Red = disabled

2. **Rollback Trigger Indicators** (time series)
   - Error rate (with 5% threshold line)
   - OpenAI API error rate (with 10% threshold line)
   - Response time p95 (with 500ms threshold line)

3. **Interview Metrics Comparison** (time series)
   - Completion rate (before/after rollback)
   - Average duration (before/after rollback)

4. **Cost Impact** (gauge)
   - OpenAI API token usage rate
   - Estimated hourly cost

5. **Rollback Timeline Annotation**
   - Mark rollback execution time on all graphs

---

## Rollback Metrics and SLOs

| Metric | Target | Actual (Last Test) |
|--------|--------|-------------------|
| Feature flag rollback time | < 5 min | ____ min |
| Full version rollback time | < 15 min | ____ min |
| Time to detect P0 issue | < 5 min | ____ min |
| Time to confirm rollback success | < 10 min | ____ min |
| Mean Time To Recovery (MTTR) | < 30 min | ____ min |

---

## Related Runbooks

- [Week 15 Rollout Runbook](week15_rollout_runbook.md) - Production rollout procedures
- [Week 15 Baseline Monitoring Metrics](week15_baseline_monitoring_metrics.md) - Monitoring guide
- [Week 15 Environment Variables Guide](week15_environment_variables_guide.md) - Configuration guide

---

**Document Version**: 1.0  
**Last Updated**: 2025-01-18  
**Owner**: ML Platform Team & SRE Team  
**Review Cycle**: After each rollback event or quarterly
