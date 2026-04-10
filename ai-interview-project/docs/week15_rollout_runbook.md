# Week 15 ML Features - Production Rollout Runbook

## Overview

This runbook provides step-by-step procedures for safely rolling out Week 14 ML features (embedding, NLP, prediction) to production environments. The rollout follows a progressive approach: dev → staging → production (10% → 50% → 100%).

**Target Audience**: DevOps Engineers, SRE, Release Managers

**Prerequisites**:
- Week 15 Task 1 (Mainline Integration) completed ✅
- Week 15 Task 2 (Regression Validation) passed ✅
- All acceptance criteria met ✅

**Related Documents**:
- [Week 15 Baseline Monitoring Metrics](week15_baseline_monitoring_metrics.md)
- [Week 15 Environment Variables Guide](week15_environment_variables_guide.md)
- [Week 15 Rollback Strategy](week15_rollback_strategy.md)

---

## Rollout Timeline

| Phase | Environment | ML Features Enabled | Duration | Go/No-Go Decision |
|-------|-------------|---------------------|----------|-------------------|
| 1 | Development | 100% | Continuous | Pass integration tests |
| 2 | Staging | 100% | 1 week | Pass validation criteria |
| 3 | Production Canary | 10% | 3 days | Zero critical issues |
| 4 | Production Ramp | 50% | 1 week | Monitor metrics stable |
| 5 | Production Full | 100% | Ongoing | Continuous monitoring |

**Total Timeline**: 2-3 weeks from dev to full production

---

## Environment Enable/Disable Matrix

### Feature Flag State by Environment

| Feature | Development | Staging | Production (Initial) | Production (Target) |
|---------|-------------|---------|----------------------|---------------------|
| `ml.embedding.enabled` | ✅ true | ✅ true | ❌ false | ✅ true |
| `ml.nlp.enabled` | ✅ true | ✅ true | ❌ false | ✅ true |
| `ml.prediction.enabled` | ✅ true | ✅ true | ❌ false | ✅ true |
| `ml.adaptive.enabled` | ✅ true | ✅ true | ❌ false | ✅ true |

### Configuration Files

- **Development**: `application-dev.properties` (all enabled)
- **Staging**: `application-staging.properties` (all enabled)
- **Production**: `application-prod.properties` (all disabled by default, override via env vars)

---

## Phase 1: Development Environment

### Objective
Validate ML features work correctly in local/dev environment before staging deployment.

### Prerequisites
- [ ] Feature branch rebased on `main`
- [ ] All integration tests passing (317/317 ✅)
- [ ] Code review approved
- [ ] Documentation updated

### Deployment Steps

```bash
# 1. Checkout feature branch
git checkout feature/ml-embedding-nlp-prediction
git pull origin feature/ml-embedding-nlp-prediction

# 2. Build application
cd ai-interview-project/backend
mvn clean package -DskipTests

# 3. Set environment variables
export SPRING_PROFILES_ACTIVE=dev
export OPENAI_API_KEY="sk-proj-dev-key"
export ML_EMBEDDING_ENABLED=true
export ML_NLP_ENABLED=true
export ML_PREDICTION_ENABLED=true

# 4. Start application
java -jar target/backend-0.0.1-SNAPSHOT.jar

# 5. Verify startup
curl http://localhost:8080/actuator/health
# Expected: {"status":"UP"}

# 6. Verify ML beans loaded
curl http://localhost:8080/actuator/beans | jq '.contexts.application.beans | keys[]' | grep -i "embedding\|nlp\|prediction"
# Expected: 10 ML beans (EmbeddingService, TopicClusteringService, etc.)
```

### Validation Criteria

- [ ] Application starts without errors
- [ ] 10 ML Spring beans loaded
- [ ] Integration tests pass: `mvn test -Dtest="*IntegrationTest"`
- [ ] Manual test: Complete 1 ML-enabled interview end-to-end
- [ ] Logs show no errors or warnings

### Go/No-Go Decision
- ✅ **GO**: All validation criteria met → Proceed to Phase 2 (Staging)
- ❌ **NO-GO**: Any test failures → Fix issues, repeat Phase 1

---

## Phase 2: Staging Environment

### Objective
Validate ML features in staging environment with production-like infrastructure and establish baseline metrics.

### Prerequisites
- [ ] Phase 1 (Development) completed successfully
- [ ] Staging database migrated to V19 (latest schema)
- [ ] Staging Redis available
- [ ] Staging OpenAI API key configured in secrets manager
- [ ] Prometheus/Grafana monitoring configured

### Deployment Steps (Kubernetes)

```bash
# 1. Merge feature branch to staging branch
git checkout staging
git merge feature/ml-embedding-nlp-prediction
git push origin staging

# 2. Build and push Docker image
docker build -t ai-interview-backend:staging -f backend/Dockerfile.gcp .
docker tag ai-interview-backend:staging gcr.io/your-project/ai-interview-backend:staging
docker push gcr.io/your-project/ai-interview-backend:staging

# 3. Apply Kubernetes configuration
kubectl apply -f k8s/configmap-staging.yaml
kubectl apply -f k8s/secret-staging.yaml
kubectl apply -f k8s/deployment-staging.yaml

# 4. Wait for rollout to complete
kubectl rollout status deployment/ai-interview-backend -n ai-interview-staging
# Expected: deployment "ai-interview-backend" successfully rolled out

# 5. Verify health
kubectl get pods -n ai-interview-staging
kubectl logs -f deployment/ai-interview-backend -n ai-interview-staging

# 6. Check ML beans loaded
kubectl exec -it deployment/ai-interview-backend -n ai-interview-staging -- \
  curl localhost:8080/actuator/beans | jq '.contexts.application.beans | keys[]' | grep -i "embedding"
```

### Validation Criteria

#### 1. Application Health
- [ ] All pods running (2/2 ready)
- [ ] Health check passing: `/actuator/health`
- [ ] No error logs in past 10 minutes

#### 2. ML Features Availability
- [ ] 10 ML beans loaded (check actuator/beans)
- [ ] Embedding generation works (check logs for API calls)
- [ ] NLP feature extraction works
- [ ] Outcome prediction works

#### 3. Integration Tests in Staging
```bash
# Run integration tests against staging API
export API_BASE_URL=https://staging-api.yourdomain.com
mvn test -Dtest="*IntegrationTest" -Dapi.baseUrl=$API_BASE_URL
```
- [ ] 47/47 integration tests pass

#### 4. End-to-End Validation
- [ ] Complete 10 ML-enabled interviews manually
- [ ] Verify no question repetition > 10%
- [ ] Verify topic diversity (entropy > 60%)
- [ ] Verify prediction RMSE < 15
- [ ] Verify early stopping triggered 2-3 times out of 10

#### 5. Baseline Metrics Established (1 Week)
Collect metrics from Prometheus and document baselines:

| Metric | Target | Observed | Status |
|--------|--------|----------|--------|
| Question repetition ratio | < 10% | ___ % | ⏳ |
| Topic coverage entropy | > 60% | ___ % | ⏳ |
| Embedding generation p95 | < 500ms | ___ ms | ⏳ |
| NLP extraction p95 | < 150ms | ___ ms | ⏳ |
| Outcome prediction p95 | < 250ms | ___ ms | ⏳ |
| Prediction RMSE | < 15 | ___ | ⏳ |
| Early stopping false positive rate | < 5% | ___ % | ⏳ |
| Cache hit ratio (embeddings) | > 80% | ___ % | ⏳ |
| OpenAI token usage | Track trend | ___ /day | ⏳ |
| OpenAI API error rate | < 1% | ___ % | ⏳ |

#### 6. Monitoring and Alerting
- [ ] Grafana dashboard shows all 18 ML metrics
- [ ] Prometheus scraping metrics every 15s
- [ ] Alert rules configured (critical + warning)
- [ ] Alerts route to Slack #ml-monitoring channel
- [ ] PagerDuty integration tested for critical alerts

### Staging Issues Log

Document any issues found during staging validation:

| Date | Issue | Severity | Resolution | Status |
|------|-------|----------|------------|--------|
| | | | | |

### Go/No-Go Decision (After 1 Week)
- ✅ **GO**: All validation criteria met + baseline metrics stable → Proceed to Phase 3 (Production Canary)
- ❌ **NO-GO**: Any validation failure → Fix issues, repeat Phase 2

---

## Phase 3: Production Canary (10% Traffic)

### Objective
Enable ML features for 10% of production traffic to detect production-specific issues with minimal blast radius.

### Prerequisites
- [ ] Phase 2 (Staging) completed successfully
- [ ] Production database at V19 schema
- [ ] Production Redis available and tested
- [ ] Production OpenAI API key configured in AWS Secrets Manager
- [ ] Rollback plan tested in staging
- [ ] Incident response team on standby

### Traffic Splitting Strategy

**Option A: User-based (Recommended)**
- Enable ML for 10% of user IDs (deterministic hash)
- Consistent experience for users
- Easy to identify ML vs. non-ML performance

**Option B: Random per-request**
- 10% probability per interview
- More even distribution
- Harder to debug user-specific issues

**Implementation**: Feature flag service (LaunchDarkly, Split.io) or custom logic in `AdaptiveQuestionSelector`

### Deployment Steps (AWS ECS)

```bash
# 1. Update ECS task definition to add feature flags as environment variables
aws ecs register-task-definition --cli-input-json file://ecs-task-definition-prod-canary.json

# Task definition changes:
# - ML_EMBEDDING_ENABLED=false (still disabled globally)
# - ML_CANARY_ENABLED=true (enable canary logic)
# - ML_CANARY_PERCENTAGE=10 (10% traffic)

# 2. Update ECS service to use new task definition
aws ecs update-service \
  --cluster ai-interview-prod \
  --service ai-interview-backend \
  --task-definition ai-interview-backend-prod:NEW_REVISION

# 3. Wait for deployment to stabilize
aws ecs wait services-stable \
  --cluster ai-interview-prod \
  --services ai-interview-backend

# 4. Verify health
aws ecs describe-services \
  --cluster ai-interview-prod \
  --services ai-interview-backend | jq '.services[0].deployments'

# Expected: PRIMARY deployment with 100% running count
```

### Canary Validation Criteria (3 Days)

#### Day 1: Initial Validation (First 4 Hours)
- [ ] No production errors in logs
- [ ] OpenAI API requests appear in logs (confirms canary is active)
- [ ] Cache hit ratio starts increasing
- [ ] No customer complaints
- [ ] No alerts fired

#### Day 2-3: Metric Comparison
Compare ML-enabled (10%) vs. ML-disabled (90%) users:

| Metric | ML Enabled (10%) | ML Disabled (90%) | Delta | Status |
|--------|------------------|-------------------|-------|--------|
| Interview completion rate | ___ % | ___ % | ___ % | ⏳ |
| Avg interview duration | ___ min | ___ min | ___ min | ⏳ |
| Question repetition ratio | ___ % | N/A | - | ⏳ |
| Candidate satisfaction NPS | ___ | ___ | ___ | ⏳ |
| System error rate | ___ % | ___ % | ___ % | ⏳ |
| Avg response time | ___ ms | ___ ms | ___ ms | ⏳ |

**Success Criteria**:
- Interview completion rate: No degradation (delta < -5%)
- System error rate: No increase (delta < +1%)
- Response time: No significant increase (delta < +200ms)
- No critical customer complaints

#### Monitoring Checklist
- [ ] Set up canary dashboard in Grafana (ML vs. non-ML split)
- [ ] Monitor 24/7 for first 24 hours
- [ ] Check alerts every 4 hours for first 3 days
- [ ] Review customer support tickets daily

### Rollback Trigger Conditions

**Immediate Rollback** (Phase 6: Emergency Rollback):
- Production error rate > 5%
- OpenAI API error rate > 10%
- Customer complaints > 5 in first 24 hours
- Any P0 incident

**Scheduled Rollback** (within 1 hour):
- Interview completion rate drops > 10%
- Avg response time increases > 500ms
- Early stopping false positive rate > 20%

### Go/No-Go Decision (After 3 Days)
- ✅ **GO**: All metrics stable, no critical issues → Proceed to Phase 4 (50% Ramp)
- ❌ **NO-GO**: Any rollback trigger hit → Execute rollback (Phase 6)

---

## Phase 4: Production Ramp (50% Traffic)

### Objective
Increase ML feature adoption to 50% of production traffic to validate scalability and continued stability.

### Prerequisites
- [ ] Phase 3 (10% Canary) completed successfully
- [ ] 3 days of stable metrics
- [ ] No production incidents related to ML features
- [ ] OpenAI API rate limits and costs reviewed

### Deployment Steps

```bash
# 1. Update canary percentage to 50%
# Update ECS task definition environment variable:
# ML_CANARY_PERCENTAGE=50

# 2. Deploy update
aws ecs register-task-definition --cli-input-json file://ecs-task-definition-prod-ramp.json
aws ecs update-service \
  --cluster ai-interview-prod \
  --service ai-interview-backend \
  --task-definition ai-interview-backend-prod:NEW_REVISION

# 3. Monitor rollout
aws ecs wait services-stable --cluster ai-interview-prod --services ai-interview-backend

# 4. Verify traffic split
# Check application logs for ML feature usage ratio
kubectl logs -f deployment/ai-interview-backend | grep "ML_ENABLED" | tail -100
```

### Validation Criteria (1 Week)

#### Scalability Validation
- [ ] OpenAI API request rate scales linearly (5x increase from 10% to 50%)
- [ ] Token usage within budget (< 1M tokens/day)
- [ ] Redis cache holds up under increased load
- [ ] No database performance degradation
- [ ] Response times remain stable

#### Cost Validation
- [ ] OpenAI API costs: ~$XX/day (expected based on 10% canary)
- [ ] No unexpected cost spikes
- [ ] Cache savings: ~$X/day from embeddings

#### Quality Validation
- [ ] RMSE remains < 15
- [ ] Topic diversity remains > 60%
- [ ] Early stopping behavior consistent with 10% canary
- [ ] No increase in false positive rate

### Daily Monitoring Checklist (Week 1)
- [ ] Day 1: Check all metrics every 4 hours
- [ ] Day 2-3: Check all metrics every 8 hours
- [ ] Day 4-7: Check all metrics daily
- [ ] Weekly: Review customer feedback and NPS scores

### Go/No-Go Decision (After 1 Week)
- ✅ **GO**: All metrics stable, scalability validated → Proceed to Phase 5 (100% Full Rollout)
- ❌ **NO-GO**: Any quality/cost issues → Consider rollback or hold at 50%

---

## Phase 5: Production Full Rollout (100% Traffic)

### Objective
Enable ML features for 100% of production users.

### Prerequisites
- [ ] Phase 4 (50% Ramp) completed successfully
- [ ] 1 week of stable metrics at 50%
- [ ] OpenAI API rate limits confirmed sufficient for 100% load
- [ ] Cost projections approved by finance
- [ ] Customer feedback positive or neutral

### Deployment Steps

```bash
# 1. Update feature flags to enable globally
# Option A: Environment variables (immediate)
aws ecs register-task-definition --cli-input-json file://ecs-task-definition-prod-full.json
# Update:
# ML_EMBEDDING_ENABLED=true
# ML_NLP_ENABLED=true
# ML_PREDICTION_ENABLED=true
# ML_CANARY_ENABLED=false (disable canary logic)

# Option B: Remove canary logic, use feature flags directly
# Update application-prod.properties defaults to true

# 2. Deploy update
aws ecs update-service \
  --cluster ai-interview-prod \
  --service ai-interview-backend \
  --task-definition ai-interview-backend-prod:NEW_REVISION \
  --force-new-deployment

# 3. Monitor rollout
aws ecs wait services-stable --cluster ai-interview-prod --services ai-interview-backend

# 4. Verify 100% adoption
# Check logs for ML feature usage = 100%
```

### Validation Criteria (Ongoing)

#### Week 1: Intensive Monitoring
- [ ] All metrics within expected ranges
- [ ] OpenAI API costs as projected
- [ ] No increase in customer complaints
- [ ] System performance stable

#### Week 2-4: Standard Monitoring
- [ ] Weekly review of all ML metrics
- [ ] Monthly cost analysis
- [ ] Quarterly customer satisfaction survey

### Continuous Monitoring

**Daily**:
- OpenAI API error rate < 1%
- Redis cache availability > 99%
- No critical alerts

**Weekly**:
- Review Grafana dashboard
- Check token usage trends
- Review customer feedback

**Monthly**:
- Cost analysis and optimization
- Model performance evaluation (RMSE, false positive rate)
- Documentation updates

---

## Phase 6: Emergency Rollback

**See [Week 15 Rollback Strategy](week15_rollback_strategy.md) for detailed procedures.**

### Quick Rollback (< 5 Minutes)

```bash
# Disable all ML features immediately via environment variables
aws ecs register-task-definition --cli-input-json file://ecs-task-definition-prod-rollback.json
# Set:
# ML_EMBEDDING_ENABLED=false
# ML_NLP_ENABLED=false
# ML_PREDICTION_ENABLED=false
# ML_ADAPTIVE_ENABLED=false

aws ecs update-service \
  --cluster ai-interview-prod \
  --service ai-interview-backend \
  --task-definition ai-interview-backend-prod:ROLLBACK_REVISION \
  --force-new-deployment
```

---

## Operational Runbook

### Daily Operations Checklist

**Morning Standup** (10 minutes):
- [ ] Review overnight alerts and incidents
- [ ] Check Grafana dashboard for anomalies
- [ ] Review OpenAI API usage and costs
- [ ] Check customer support tickets related to interviews

**End of Day Review** (5 minutes):
- [ ] No unresolved critical alerts
- [ ] Log any issues in runbook
- [ ] Update rollout status in Jira/Linear

### Weekly Operations Checklist

**Monday Review** (30 minutes):
- [ ] Review all ML metrics for past week
- [ ] Analyze token usage and costs
- [ ] Review customer feedback and NPS
- [ ] Plan any config tuning needed

**Friday Retrospective** (15 minutes):
- [ ] Document lessons learned
- [ ] Update runbook with new procedures
- [ ] Share weekly report with stakeholders

---

## Rollback Decision Matrix

| Condition | Severity | Action | Timeline |
|-----------|----------|--------|----------|
| Prod error rate > 5% | P0 | Immediate rollback | < 5 min |
| OpenAI API error > 10% | P0 | Immediate rollback | < 5 min |
| Interview completion drops > 10% | P1 | Rollback | < 30 min |
| RMSE > 30 | P1 | Rollback | < 1 hour |
| False positive rate > 20% | P1 | Rollback | < 1 hour |
| Avg latency > +500ms | P2 | Investigate, consider rollback | < 4 hours |
| Customer complaints > 10/day | P2 | Investigate, consider rollback | < 24 hours |

---

## Stakeholder Communication

### Rollout Announcement (Before Phase 3)

**To**: Engineering team, Product team, Customer Support
**Subject**: Production Rollout of ML Features (Week 15)

We are beginning the production rollout of Week 14 ML features:
- Embedding-based question selection
- NLP response scoring
- Outcome prediction with early stopping

**Timeline**: 
- Canary (10%): [Date] - [Date]
- Ramp (50%): [Date] - [Date]
- Full (100%): [Date]

**What to watch for**: System performance, customer feedback, interview quality

**Rollback plan**: Available in Week 15 Rollback Strategy document

---

### Rollout Status Updates (During Rollout)

**Weekly Email** to stakeholders:
- Current phase and percentage
- Key metrics (RMSE, latency, cost)
- Any issues or blockers
- Next steps and timeline

---

## Troubleshooting

### Issue: ML beans not loading in production

**Symptoms**: `/actuator/beans` shows 0 ML beans

**Diagnosis**:
```bash
kubectl logs deployment/ai-interview-backend | grep "ConditionalOnProperty"
```

**Common Causes**:
1. Feature flags set to `false` (expected if canary not enabled for this user)
2. Redis not available (check Redis health)
3. Missing configuration

**Resolution**:
```bash
# Check feature flag values
kubectl exec deployment/ai-interview-backend -- env | grep ML_

# Verify Redis connectivity
kubectl exec deployment/ai-interview-backend -- redis-cli -h $REDIS_HOST ping
```

---

### Issue: High OpenAI API costs

**Symptoms**: Token usage > 1M/day unexpectedly

**Diagnosis**:
- Check cache hit ratio in Grafana
- Review API request logs for patterns

**Resolution**:
1. Verify cache is working: `ml.embedding.cache.hit_ratio` should be > 80%
2. Check for request loops or bugs
3. Consider enabling rate limiting
4. Temporarily reduce canary percentage

---

## Post-Rollout

### Success Criteria for Rollout Completion

- [ ] 100% of production traffic on ML features for 2 weeks
- [ ] All metrics stable and within baselines
- [ ] Customer satisfaction neutral or improved
- [ ] No P0/P1 incidents related to ML features
- [ ] Cost within budget ($XXX/month)

### Handoff to Operations

Once rollout is complete:
- [ ] Update monitoring runbook for ML features
- [ ] Train support team on ML-related customer issues
- [ ] Schedule monthly ML metrics review
- [ ] Document lessons learned in engineering blog

---

**Document Version**: 1.0  
**Last Updated**: 2025-01-18  
**Owner**: ML Platform Team & DevOps Team  
**Review Cycle**: After each phase completion
