# Rollback Runbook: Early-Stop Policy
# Week 20 Task 4: ML Monitoring Upgrade
# Purpose: Step-by-step rollback procedures for production incidents

---
# Runbook Index

1. [Junior RMSE Guardrail Breach](#runbook-1-junior-rmse-breach)
2. [Premature Stop Rate Exceeded](#runbook-2-premature-stop-exceeded)
3. [Metrics Pipeline Failure](#runbook-3-metrics-pipeline-failure)
4. [Confidence Score Drift](#runbook-4-confidence-drift)
5. [Emergency Rollback (Any Guardrail)](#runbook-5-emergency-rollback)

---
<a name="runbook-1-junior-rmse-breach"></a>
# Runbook 1: Junior RMSE Guardrail Breach

## Trigger
- **Alert**: `junior_rmse_breach`
- **Condition**: `interview_prediction_rmse{slice="junior"} > 11.5 for 10m`
- **Severity**: CRITICAL
- **Owner**: ML On-Call Engineer

## Business Impact
- Junior candidates receiving incorrect early-stop decisions
- Violates Week 20 acceptance criteria
- Risk: Premature failures or false passes

## Immediate Response (< 5 minutes)

### Step 1: Verify Alert is Real
```bash
# Check current Junior RMSE
curl -s 'http://prometheus:9090/api/v1/query?query=interview_prediction_rmse{slice="junior"}' | jq

# Expected: value > 11.5
# If value ≤ 11.5: Alert may be stale, wait 2 minutes and recheck
```

### Step 2: Execute Feature Flag Rollback
```bash
# Rollback to baseline policy (threshold=0.95/0.05)
kubectl set env deployment/ai-interview-backend \
  EARLY_STOP_POLICY=baseline \
  PASS_THRESHOLD=0.95 \
  FAIL_THRESHOLD=0.05 \
  MIN_QUESTIONS=5

# Verify deployment update
kubectl rollout status deployment/ai-interview-backend

# Expected: "deployment "ai-interview-backend" successfully rolled out"
```

### Step 3: Post Incident Notification
```bash
# Slack notification
curl -X POST https://hooks.slack.com/services/${SLACK_WEBHOOK} \
  -H 'Content-Type: application/json' \
  -d '{
    "channel": "#ml-alerts-critical",
    "text": "🚨 ROLLBACK EXECUTED: Junior RMSE breach ({{value}}), reverted to baseline policy",
    "attachments": [{
      "color": "danger",
      "fields": [
        {"title": "Alert", "value": "junior_rmse_breach", "short": true},
        {"title": "RMSE", "value": "{{current_value}}", "short": true},
        {"title": "Action", "value": "Rolled back to baseline (0.95/0.05)", "short": false}
      ]
    }]
  }'
```

## Validation (5-15 minutes)

### Step 4: Monitor RMSE Recovery
```bash
# Watch Junior RMSE for next 5 minutes
watch -n 30 'curl -s "http://prometheus:9090/api/v1/query?query=interview_prediction_rmse{slice=\"junior\"}" | jq .data.result[0].value[1]'

# Expected: RMSE drops below 11.5 within 5 minutes
# If not: Escalate to ML Team Lead
```

### Step 5: Verify No New Premature Stops
```bash
# Check premature stop rate post-rollback
curl -s 'http://prometheus:9090/api/v1/query?query=rate(interview_early_stop_premature_total[5m])/rate(interview_early_stop_total[5m])' | jq

# Expected: < 0.03 (3%)
```

## Post-Incident (< 24 hours)

### Step 6: Create Incident Ticket
```
Title: [INCIDENT] Junior RMSE Guardrail Breach - {{timestamp}}

Details:
- Alert triggered: {{alert_timestamp}}
- Peak RMSE: {{max_rmse}}
- Rollback executed: {{rollback_timestamp}}
- Recovery time: {{recovery_duration}}
- Sessions affected: {{estimated_count}}

Root Cause: TBD (requires investigation)

Action Items:
- [ ] RCA by ML Team Lead (due: 24h)
- [ ] Review calibration data freshness
- [ ] Check for model drift
- [ ] Validate recent config changes
```

### Step 7: Root Cause Analysis (RCA)
**Investigation Checklist**:
- [ ] Check Platt calibration freshness (last fitted: {{last_fit_date}})
- [ ] Review training data distribution (Beta(8,2) vs actual)
- [ ] Validate predictor (InterviewOutcomePredictor) not changed
- [ ] Check for data pipeline issues (missing features, null values)
- [ ] Review recent experiment deploys (any config drift?)

**Common Root Causes**:
1. **Stale calibration**: Platt calibration not re-fitted with recent data
   - **Fix**: Re-run `PlattCalibrationRealDataTest` with latest 180 sessions
   
2. **Confidence distribution shift**: OpenAI model updated, confidence drift
   - **Fix**: Re-fit Platt on new distribution, update thresholds
   
3. **Predictor bug**: InterviewOutcomePredictor regression
   - **Fix**: Revert to last known good version, add regression test
   
4. **Config error**: Threshold or min_questions misconfigured
   - **Fix**: Validate config against `early-stopping-v2.1.yml`

### Step 8: Fix-Forward Plan
```
Decision Tree:
├─ If stale calibration (> 30 days old):
│   └─ Re-fit Platt on last 180 sessions
│       └─ Validate Junior RMSE ≤ 11.5 in staging
│           └─ Deploy to 10% canary
│               └─ Monitor for 24h → Full rollout
│
├─ If confidence drift (> 10%):
│   └─ Analyze new distribution (Beta parameters)
│       └─ Re-tune thresholds for new distribution
│           └─ Run A/B test (7 days) → Deploy winner
│
└─ If predictor bug:
    └─ Revert code to last known good
        └─ Add regression test
            └─ Fix bug in dev → Full test suite → Deploy
```

---
<a name="runbook-2-premature-stop-exceeded"></a>
# Runbook 2: Premature Stop Rate Exceeded

## Trigger
- **Alert**: `premature_stop_high`
- **Condition**: `premature_stop_rate > 0.03 for 15m`
- **Severity**: CRITICAL
- **Owner**: ML On-Call Engineer

## Business Impact
- Candidates stopped early despite low confidence (< 0.75)
- Poor candidate experience
- Potential legal/compliance issues

## Immediate Response (< 10 minutes)

### Step 1: Halt Ongoing Rollouts
```bash
# If canary deployment in progress, pause rollout
kubectl patch deployment ai-interview-backend -p '{"spec":{"paused":true}}'

# Check current replica distribution
kubectl get deployment ai-interview-backend -o jsonpath='{.status}'

# Expected: Deployment paused, no new pods starting
```

### Step 2: Investigate Root Cause
```bash
# Check threshold configuration
kubectl get deployment ai-interview-backend -o json | jq '.spec.template.spec.containers[0].env[] | select(.name=="PASS_THRESHOLD" or .name=="FAIL_THRESHOLD" or .name=="MIN_QUESTIONS")'

# Expected:
# PASS_THRESHOLD: 0.85
# FAIL_THRESHOLD: 0.15
# MIN_QUESTIONS: 3

# If values are wrong: CONFIG ERROR → Fix immediately
```

### Step 3: Analyze Premature Stops
```bash
# Get recent premature stop examples
psql -h ${DB_HOST} -U ${DB_USER} -d aiinterview -c "
  SELECT session_id, slice, num_questions, early_stop_confidence, outcome
  FROM sessions
  WHERE early_stopped = true 
    AND early_stop_confidence < 0.75
    AND timestamp > NOW() - INTERVAL '1 hour'
  ORDER BY timestamp DESC
  LIMIT 20;
"

# Look for patterns:
# - All junior? → Slice-specific issue
# - Low question counts (< 3)? → Min questions not enforced
# - Specific time range? → Deployment/config change
```

## Decision Tree (10-30 minutes)

### Scenario A: Threshold Misconfigured
```bash
# Fix configuration
kubectl set env deployment/ai-interview-backend \
  PASS_THRESHOLD=0.85 \
  FAIL_THRESHOLD=0.15

# Resume rollout
kubectl patch deployment ai-interview-backend -p '{"spec":{"paused":false}}'

# Monitor: premature_stop_rate should drop within 10 minutes
```

### Scenario B: Min Questions Not Enforced
```bash
# Check code version (should enforce min_questions >= 3)
kubectl get deployment ai-interview-backend -o jsonpath='{.spec.template.spec.containers[0].image}'

# If wrong version deployed:
kubectl set image deployment/ai-interview-backend \
  ai-interview-backend=gcr.io/project/ai-interview-backend:v2.1-week20-correct

# Verify min_questions enforcement in logs
kubectl logs -l app=ai-interview-backend --tail=100 | grep "min_questions"
```

### Scenario C: Data Quality Issue (Confidence Scores Wrong)
```bash
# Check confidence score distribution
curl -s 'http://prometheus:9090/api/v1/query?query=histogram_quantile(0.50,interview_confidence_score_bucket)' | jq

# If P50 << 0.81: Confidence scores corrupted or distribution shifted
# Action: ROLLBACK + Investigate offline
kubectl set env deployment/ai-interview-backend EARLY_STOP_POLICY=baseline
```

### Scenario D: Unknown Cause
```bash
# Full rollback to baseline
kubectl set env deployment/ai-interview-backend \
  EARLY_STOP_POLICY=baseline \
  PASS_THRESHOLD=0.95 \
  FAIL_THRESHOLD=0.05 \
  MIN_QUESTIONS=5

# Schedule postmortem for next business day
# Investigate with full data dump offline
```

## Post-Incident

### RCA Investigation
**Key Questions**:
1. What % of stops were truly "premature" (confidence < 0.75)?
2. Were min_questions constraints violated?
3. Did confidence distribution shift suddenly?
4. Was there a recent code/config deployment?

**Data to Collect**:
```sql
-- Premature stop analysis
SELECT 
  slice,
  COUNT(*) as total_premature,
  AVG(num_questions) as avg_questions_when_stopped,
  AVG(early_stop_confidence) as avg_confidence,
  MODE() WITHIN GROUP (ORDER BY num_questions) as most_common_question_count
FROM sessions
WHERE early_stopped = true 
  AND early_stop_confidence < 0.75
  AND timestamp BETWEEN '{{incident_start}}' AND '{{incident_end}}'
GROUP BY slice;
```

---
<a name="runbook-3-metrics-pipeline-failure"></a>
# Runbook 3: Metrics Pipeline Failure

## Trigger
- **Alert**: `metrics_data_stale`
- **Condition**: `data_freshness > 30 minutes`
- **Severity**: CRITICAL (Blind monitoring!)
- **Owner**: Data Platform On-Call

## Immediate Response (< 5 minutes)

### Step 1: Check Metrics Exporter Health
```bash
# Verify metrics-exporter pod running
kubectl get pods -l app=metrics-exporter

# If CrashLoopBackOff or Error:
kubectl logs -l app=metrics-exporter --tail=100

# Common issues:
# - DB connection timeout: Check DB_HOST/DB_PORT env vars
# - OOM: Increase memory limits
# - Auth failure: Rotate DB credentials
```

### Step 2: Verify Data Sources
```bash
# Backend API health
curl http://ai-interview-backend:8080/actuator/health

# Database connection
psql -h ${DB_HOST} -U ${DB_USER} -d aiinterview -c "SELECT COUNT(*) FROM sessions WHERE timestamp > NOW() - INTERVAL '5 minutes';"

# Prometheus targets
curl -s http://prometheus:9090/api/v1/targets | jq '.data.activeTargets[] | select(.labels.job=="metrics-exporter")'
```

### Step 3: Restart Metrics Exporter
```bash
# Restart pod
kubectl rollout restart deployment/metrics-exporter

# Wait for ready
kubectl rollout status deployment/metrics-exporter

# Verify metrics flowing
curl -s http://metrics-exporter:8080/metrics | grep interview_prediction_rmse
```

## Validation (5-15 minutes)

### Step 4: Confirm Data Freshness Restored
```bash
# Check last update timestamp
curl -s 'http://prometheus:9090/api/v1/query?query=interview_metrics_last_update_timestamp' | jq .data.result[0].value[1]

# Compare to current time
date +%s

# Difference should be < 60 seconds
```

### Step 5: Backfill Missing Data (if needed)
```bash
# Identify time range of missing data
START_TIME="{{last_good_timestamp}}"
END_TIME="{{current_timestamp}}"

# Run backfill script
python scripts/backfill_metrics.py \
  --start "${START_TIME}" \
  --end "${END_TIME}" \
  --metrics "rmse,early_stop_rate,avg_questions"

# Verify backfill success
psql -c "SELECT COUNT(*) FROM metrics WHERE timestamp BETWEEN '${START_TIME}' AND '${END_TIME}';"
```

---
<a name="runbook-4-confidence-drift"></a>
# Runbook 4: Confidence Score Drift

## Trigger
- **Alert**: `confidence_drift`
- **Condition**: `|P50_confidence - 0.81| > 0.10 for 20m`
- **Severity**: WARNING
- **Owner**: ML Engineer (on-duty)

## Investigation (< 30 minutes)

### Step 1: Quantify Drift
```bash
# Get current confidence distribution
curl -s 'http://prometheus:9090/api/v1/query?query=histogram_quantile(0.50,interview_confidence_score_bucket)' | jq
curl -s 'http://prometheus:9090/api/v1/query?query=histogram_quantile(0.90,interview_confidence_score_bucket)' | jq

# Expected (baseline): P50=0.81, P90=0.94
# Compare to actual values
# Drift = (actual - expected) / expected * 100
```

### Step 2: Check OpenAI API Changes
```bash
# Review OpenAI changelog (last 7 days)
open https://platform.openai.com/docs/changelog

# Check for:
# - Model updates (gpt-4-turbo, gpt-3.5-turbo)
# - API behavior changes
# - Confidence score calculation changes
```

### Step 3: Analyze Prompt Stability
```bash
# Check for recent prompt changes
cd ai-interview-project
git log --since="1 week ago" --oneline -- backend/src/main/resources/prompts/

# If prompts changed:
git diff HEAD~1 HEAD -- backend/src/main/resources/prompts/interview_questions.json

# Review if confidence scoring instructions modified
```

## Decision Tree

### Case 1: Drift < 15% AND Sustained < 3 Days
**Action**: Monitor, no immediate changes
```bash
# Set up extended monitoring
# Daily confidence distribution report for next 7 days
*/5 * * * * python scripts/monitor_confidence_drift.py --alert-threshold 0.15
```

### Case 2: Drift > 15% OR Sustained > 3 Days
**Action**: Re-fit Platt calibration
```bash
# Export last 180 sessions
python eval/export_calibration_training_data.py --lookback 180

# Re-run calibration test
cd backend
mvn test -Dtest=PlattCalibrationRealDataTest

# Validate Junior RMSE still ≤ 11.5
# If PASS: Deploy new calibration
# If FAIL: Investigate why calibration not helping
```

### Case 3: Sudden Spike (Within 1 Hour)
**Action**: Likely bug or API issue
```bash
# Check for errors in logs
kubectl logs -l app=ai-interview-backend --since=1h | grep -i "confidence\|error"

# Check OpenAI API status
curl https://status.openai.com/api/v2/status.json | jq

# If API issue: Wait for resolution, monitor
# If bug: Rollback last deployment
```

---
<a name="runbook-5-emergency-rollback"></a>
# Runbook 5: Emergency Rollback (Any Guardrail)

## When to Use
- **Multiple guardrails failing simultaneously**
- **Unknown root cause + production impact**
- **User-reported issues (candidate complaints)**
- **Exec/product request to rollback**

## One-Command Emergency Rollback

```bash
#!/bin/bash
# emergency_rollback.sh - Full rollback to Week 16 baseline

echo "🚨 EMERGENCY ROLLBACK INITIATED"
echo "Timestamp: $(date -Iseconds)"
echo "Executed by: $(whoami)"

# Step 1: Rollback feature flags
kubectl set env deployment/ai-interview-backend \
  EARLY_STOP_POLICY=baseline \
  PASS_THRESHOLD=0.95 \
  FAIL_THRESHOLD=0.05 \
  MIN_QUESTIONS=5 \
  PLATT_CALIBRATION_ENABLED=false

# Step 2: Rollback code (if needed)
# kubectl set image deployment/ai-interview-backend \
#   ai-interview-backend=gcr.io/project/ai-interview-backend:baseline-week16

# Step 3: Verify deployment
kubectl rollout status deployment/ai-interview-backend

# Step 4: Alert stakeholders
curl -X POST ${SLACK_WEBHOOK_CRITICAL} -d '{
  "text": "🚨 EMERGENCY ROLLBACK COMPLETE",
  "attachments": [{
    "color": "danger",
    "text": "Reverted to Week 16 baseline policy. All new features disabled.",
    "fields": [
      {"title": "Threshold", "value": "0.95/0.05 (baseline)", "short": true},
      {"title": "Min Questions", "value": "5", "short": true},
      {"title": "Calibration", "value": "Disabled", "short": true}
    ]
  }]
}'

echo "✅ Rollback complete. Monitor dashboards for next 30 minutes."
```

## Validation Checklist
- [ ] Deployment rolled out successfully
- [ ] Junior RMSE drops below 11.5 within 10 minutes
- [ ] Premature stop rate drops below 3% within 15 minutes
- [ ] Confidence scores returning to Beta(8,2) within 30 minutes
- [ ] No errors in backend logs
- [ ] Stakeholders notified (Slack + PagerDuty)

## Post-Emergency Actions
1. **Incident Commander Assigned**: Engineering Manager
2. **War Room**: #incident-response Slack channel
3. **All-hands RCA**: Schedule within 24 hours
4. **Customer Communication**: Product team drafts apology/explanation
5. **Fix-Forward Plan**: ML team proposes solution within 48 hours

---
# Appendix: Useful Commands

## Monitoring
```bash
# Check all ML metrics
curl -s http://prometheus:9090/api/v1/query?query=interview_prediction_rmse | jq
curl -s http://prometheus:9090/api/v1/query?query=rate(interview_early_stop_total[5m]) | jq

# Dashboard URLs
open https://grafana.company.com/d/early-stop-quality
open https://grafana.company.com/d/decision-gate-rollout
```

## Database Queries
```sql
-- Recent sessions summary
SELECT 
  slice,
  COUNT(*) as sessions,
  AVG(num_questions) as avg_q,
  SUM(CASE WHEN early_stopped THEN 1 ELSE 0 END)::float / COUNT(*) as early_stop_pct
FROM sessions
WHERE timestamp > NOW() - INTERVAL '1 hour'
GROUP BY slice;

-- Premature stops detail
SELECT session_id, slice, num_questions, early_stop_confidence, outcome
FROM sessions
WHERE early_stopped = true AND early_stop_confidence < 0.75
ORDER BY timestamp DESC
LIMIT 20;
```

## Deployment Info
```bash
# Current config
kubectl get deployment ai-interview-backend -o json | jq '.spec.template.spec.containers[0].env'

# Rollout history
kubectl rollout history deployment/ai-interview-backend

# Rollback to previous version
kubectl rollout undo deployment/ai-interview-backend
```

---
# Emergency Contacts

**ML On-Call**: See PagerDuty schedule
**ML Team Lead**: @ml_lead (Slack), +1-XXX-XXX-XXXX
**Engineering Manager**: @eng_manager, escalation only
**Product Manager**: @product, for customer communication

**Escalation Path**: ML On-Call (5 min) → ML Lead (10 min) → Eng Manager (20 min)
