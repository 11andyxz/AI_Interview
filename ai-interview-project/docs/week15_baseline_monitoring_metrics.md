# Week 15 ML Features - Baseline Monitoring Metrics Specification

## Overview

This document defines the baseline monitoring metrics for Week 14 ML features integrated in Week 15. These metrics enable observability of ML feature performance, cost control, and early detection of quality regressions.

**Target Audience**: DevOps, SRE, ML Engineers, Product Managers

**Related Documents**:
- [Week 15 Rollout Runbook](week15_rollout_runbook.md)
- [Week 15 Rollback Strategy](week15_rollback_strategy.md)
- [Week 15 Regression Validation Report](week15_regression_validation_report.md)

---

## Metric Categories

### 1. Question Quality Metrics
### 2. Performance Metrics
### 3. Cost Metrics
### 4. Prediction Quality Metrics
### 5. System Health Metrics

---

## 1. Question Quality Metrics

### 1.1 Question Repetition Ratio

**Metric Name**: `ml.embedding.question.repetition_ratio`

**Definition**: Percentage of questions that are semantically similar to previously asked questions in the same interview session.

**Measurement**:
- Numerator: Count of questions with cosine similarity > 0.85 to any previous question
- Denominator: Total questions asked in session
- Formula: `(repeated_questions / total_questions) * 100`

**Collection Point**: `TopicCoverageTracker.evaluateRepetitionScore()`

**Baseline Target**: < 10% (lower is better)

**Alert Thresholds**:
- Warning: > 15%
- Critical: > 25%

**Rationale**: High repetition indicates embedding or clustering failure, leading to poor candidate experience.

**Sample Query (Prometheus)**:
```promql
rate(ml_embedding_question_repetition_total[5m]) / rate(ml_embedding_question_total[5m]) * 100
```

---

### 1.2 Topic Coverage Entropy

**Metric Name**: `ml.embedding.topic.coverage_entropy`

**Definition**: Shannon entropy of topic distribution across asked questions, measuring topic diversity.

**Measurement**:
- Calculate: H = -Σ(p_i * log2(p_i)) where p_i is proportion of questions in topic cluster i
- Range: 0 (all same topic) to log2(N) where N = number of topic clusters
- Normalized: entropy / log2(N) * 100 for percentage

**Collection Point**: `TopicCoverageTracker.calculateEntropy()`

**Baseline Target**: > 60% of maximum entropy (good diversity)

**Alert Thresholds**:
- Warning: < 50% (low diversity)
- Critical: < 30% (very low diversity)

**Rationale**: Low entropy indicates questions are concentrated in few topics, failing to comprehensively assess candidate.

**Sample Query (Prometheus)**:
```promql
ml_embedding_topic_coverage_entropy_percent
```

---

## 2. Performance Metrics

### 2.1 Embedding Generation Latency

**Metric Name**: `ml.embedding.generation.latency_ms`

**Definition**: Time to generate embeddings for a single text input via OpenAI API.

**Measurement**:
- Start: Before OpenAI API call in `EmbeddingService.generateEmbedding()`
- End: After receiving API response
- Unit: milliseconds

**Collection Point**: `EmbeddingService.generateEmbedding()`

**Baseline Target**: 
- p50: < 200ms
- p95: < 500ms
- p99: < 1000ms

**Alert Thresholds**:
- Warning: p95 > 1000ms (1 second)
- Critical: p95 > 3000ms (3 seconds)

**Rationale**: High latency impacts question selection speed and candidate wait time.

**Sample Query (Prometheus)**:
```promql
histogram_quantile(0.95, rate(ml_embedding_generation_latency_ms_bucket[5m]))
```

---

### 2.2 NLP Feature Extraction Latency

**Metric Name**: `ml.nlp.feature_extraction.latency_ms`

**Definition**: Time to extract NLP features from candidate response text.

**Measurement**:
- Start: Entry to `ResponseFeatureExtractor.extractFeatures()`
- End: Return of FeatureVector object
- Unit: milliseconds

**Collection Point**: `ResponseFeatureExtractor.extractFeatures()`

**Baseline Target**:
- p50: < 50ms
- p95: < 150ms
- p99: < 300ms

**Alert Thresholds**:
- Warning: p95 > 300ms
- Critical: p95 > 500ms

**Rationale**: Feature extraction runs synchronously during response evaluation. High latency delays feedback.

**Sample Query (Prometheus)**:
```promql
histogram_quantile(0.95, rate(ml_nlp_feature_extraction_latency_ms_bucket[5m]))
```

---

### 2.3 Outcome Prediction Latency

**Metric Name**: `ml.prediction.outcome.latency_ms`

**Definition**: Time to compute outcome prediction after each candidate response.

**Measurement**:
- Start: Entry to `InterviewOutcomePredictor.predictOutcome()`
- End: Return of PredictionResult object
- Unit: milliseconds

**Collection Point**: `InterviewOutcomePredictor.predictOutcome()`

**Baseline Target**:
- p50: < 100ms
- p95: < 250ms
- p99: < 500ms

**Alert Thresholds**:
- Warning: p95 > 500ms
- Critical: p95 > 1000ms

**Rationale**: Outcome prediction determines early stopping decisions. High latency delays interview flow.

**Sample Query (Prometheus)**:
```promql
histogram_quantile(0.95, rate(ml_prediction_outcome_latency_ms_bucket[5m]))
```

---

### 2.4 Cache Hit Ratio - Embeddings

**Metric Name**: `ml.embedding.cache.hit_ratio`

**Definition**: Percentage of embedding requests served from Redis cache vs. OpenAI API.

**Measurement**:
- Numerator: Embedding requests served from cache
- Denominator: Total embedding requests
- Formula: `(cache_hits / (cache_hits + cache_misses)) * 100`

**Collection Point**: `EmbeddingService.getOrGenerateEmbedding()`

**Baseline Target**: > 80% after warm-up period (1 hour)

**Alert Thresholds**:
- Warning: < 60% (after warm-up)
- Critical: < 40% (cache may be failing)

**Rationale**: High cache hit ratio reduces OpenAI API costs and improves latency.

**Sample Query (Prometheus)**:
```promql
rate(ml_embedding_cache_hits_total[5m]) / (rate(ml_embedding_cache_hits_total[5m]) + rate(ml_embedding_cache_misses_total[5m])) * 100
```

---

### 2.5 Cache Hit Ratio - NLP Features

**Metric Name**: `ml.nlp.feature_cache.hit_ratio`

**Definition**: Percentage of feature extraction requests served from cache.

**Measurement**:
- Numerator: Feature requests served from cache
- Denominator: Total feature requests
- Formula: `(cache_hits / (cache_hits + cache_misses)) * 100`

**Collection Point**: `ResponseFeatureExtractor.extractFeaturesWithCache()`

**Baseline Target**: > 70% (many responses are unique)

**Alert Thresholds**:
- Warning: < 50%
- Critical: < 30%

**Rationale**: Feature extraction is CPU-intensive. Caching improves performance for similar responses.

**Sample Query (Prometheus)**:
```promql
rate(ml_nlp_feature_cache_hits_total[5m]) / (rate(ml_nlp_feature_cache_hits_total[5m]) + rate(ml_nlp_feature_cache_misses_total[5m])) * 100
```

---

## 3. Cost Metrics

### 3.1 OpenAI API Token Usage

**Metric Name**: `ml.openai.tokens.consumed_total`

**Definition**: Total number of tokens consumed from OpenAI API (embeddings + chat).

**Measurement**:
- Embedding tokens: Count from API response `usage.total_tokens`
- Chat tokens: Existing metric from chat completions
- Tags: `model`, `api_type` (chat/embedding)

**Collection Point**: `EmbeddingService` and `OpenAiService`

**Baseline Target**: Track trend for cost forecasting

**Alert Thresholds**:
- Warning: > 500k tokens/day (unexpected spike)
- Critical: > 1M tokens/day (cost runaway)

**Rationale**: Token usage directly impacts operating costs. Monitor to prevent unexpected bills.

**Cost Calculation**:
- `text-embedding-3-small`: $0.02 / 1M tokens
- `gpt-4-turbo-preview`: $10 / 1M input tokens, $30 / 1M output tokens

**Sample Query (Prometheus)**:
```promql
sum(rate(ml_openai_tokens_consumed_total[1h])) by (model)
```

---

### 3.2 OpenAI API Request Rate

**Metric Name**: `ml.openai.requests.rate`

**Definition**: Number of API requests per minute to OpenAI.

**Measurement**:
- Counter: Increment on each API call
- Rate: requests per minute
- Tags: `api_type` (chat/embedding), `status` (success/failure)

**Collection Point**: `EmbeddingService.generateEmbedding()` and `OpenAiService.chat()`

**Baseline Target**: < 1000 requests/minute (embedding rate limit is 3000/min for tier 1)

**Alert Thresholds**:
- Warning: > 2000 requests/minute (approaching limit)
- Critical: > 2500 requests/minute (risk of rate limiting)

**Rationale**: Rate limiting causes service degradation. Monitor to prevent hitting quota.

**Sample Query (Prometheus)**:
```promql
sum(rate(ml_openai_requests_total[1m])) by (api_type) * 60
```

---

### 3.3 Embedding Cache Cost Savings

**Metric Name**: `ml.embedding.cache.cost_savings_usd`

**Definition**: Estimated cost savings from embedding cache hits vs. API calls.

**Measurement**:
- Formula: `cache_hits * avg_tokens_per_embedding * $0.02 / 1M`
- Avg tokens per embedding: ~500 tokens for text-embedding-3-small
- Cost per cache hit avoided: ~$0.00001 USD

**Collection Point**: Computed from cache hit counter

**Baseline Target**: Track to justify cache infrastructure cost

**Sample Calculation**:
- 10,000 cache hits/day
- 500 tokens per embedding
- Cost savings: 10,000 * 500 * $0.02 / 1M = $0.10/day = $3/month

**Sample Query (Prometheus)**:
```promql
rate(ml_embedding_cache_hits_total[1d]) * 500 * 0.02 / 1000000
```

---

## 4. Prediction Quality Metrics

### 4.1 Prediction Error (RMSE)

**Metric Name**: `ml.prediction.outcome.rmse`

**Definition**: Root Mean Square Error between predicted outcome score and actual final score.

**Measurement**:
- Calculated: After interview completion when final score is known
- Formula: `sqrt(mean((predicted_score - actual_score)^2))`
- Range: 0-100 (lower is better)

**Collection Point**: `InterviewOutcomePredictor.evaluateAccuracy()`

**Baseline Target**: < 15 (based on integration test: RMSE = 10.12 after 5 questions)

**Alert Thresholds**:
- Warning: > 20 (model degrading)
- Critical: > 30 (model unreliable)

**Rationale**: RMSE measures prediction accuracy. High error indicates model drift or data quality issues.

**Sample Query (Prometheus)**:
```promql
ml_prediction_outcome_rmse
```

---

### 4.2 Early Stopping False Positive Rate

**Metric Name**: `ml.prediction.early_stopping.false_positive_rate`

**Definition**: Percentage of interviews stopped early as "fail" where final score would have been "pass" (> 60).

**Measurement**:
- Numerator: Interviews stopped early with predicted score < 5%, actual final score > 60
- Denominator: Total interviews stopped early as "fail"
- Formula: `(false_fails / total_early_fails) * 100`

**Collection Point**: `EarlyStoppingService.evaluateFalseStops()` (post-interview analysis)

**Baseline Target**: < 5% (minimize unfair candidate rejections)

**Alert Thresholds**:
- Warning: > 10% (too many false fails)
- Critical: > 20% (unacceptable candidate experience)

**Rationale**: False positives harm candidate experience and company reputation. Must be minimized.

**Sample Query (Prometheus)**:
```promql
rate(ml_prediction_early_stopping_false_positives_total[1d]) / rate(ml_prediction_early_stopping_activated_total{outcome="fail"}[1d]) * 100
```

---

### 4.3 Early Stopping False Negative Rate

**Metric Name**: `ml.prediction.early_stopping.false_negative_rate`

**Definition**: Percentage of interviews stopped early as "pass" where final score would have been "fail" (< 60).

**Measurement**:
- Numerator: Interviews stopped early with predicted score > 95%, actual final score < 60
- Denominator: Total interviews stopped early as "pass"
- Formula: `(false_passes / total_early_passes) * 100`

**Collection Point**: `EarlyStoppingService.evaluateFalseStops()`

**Baseline Target**: < 10% (acceptable given time savings)

**Alert Thresholds**:
- Warning: > 20%
- Critical: > 30%

**Rationale**: False negatives waste interviewer time but less critical than false positives.

**Sample Query (Prometheus)**:
```promql
rate(ml_prediction_early_stopping_false_negatives_total[1d]) / rate(ml_prediction_early_stopping_activated_total{outcome="pass"}[1d]) * 100
```

---

### 4.4 Early Stopping Activation Rate

**Metric Name**: `ml.prediction.early_stopping.activation_rate`

**Definition**: Percentage of interviews that trigger early stopping decision.

**Measurement**:
- Numerator: Interviews stopped early (pass or fail)
- Denominator: Total interviews with ML prediction enabled
- Formula: `(early_stopped / total_ml_interviews) * 100`

**Collection Point**: `EarlyStoppingService.shouldStopEarly()`

**Baseline Target**: 15-25% (based on pass/fail threshold tuning)

**Alert Thresholds**:
- Warning: < 5% or > 40% (thresholds may be misconfigured)
- Critical: 0% or > 60%

**Rationale**: Activation rate indicates feature effectiveness. Too low = feature not useful, too high = thresholds too loose.

**Sample Query (Prometheus)**:
```promql
rate(ml_prediction_early_stopping_activated_total[1d]) / rate(ml_prediction_interviews_total[1d]) * 100
```

---

### 4.5 Knowledge Gap Detection Accuracy

**Metric Name**: `ml.prediction.knowledge_gap.precision`

**Definition**: Precision of detected knowledge gaps vs. actual weak areas in final assessment.

**Measurement**:
- Requires: Manual labeling or comparison with final assessment
- Precision: `true_positives / (true_positives + false_positives)`
- Ground truth: Skills marked as "weak" in final interview report

**Collection Point**: `KnowledgeGapDetector.evaluatePrecision()` (offline evaluation)

**Baseline Target**: > 70% precision

**Alert Thresholds**:
- Warning: < 60% precision
- Critical: < 50% precision

**Rationale**: Knowledge gap detection guides adaptive question selection. Low precision wastes questions on already-known topics.

**Note**: This metric requires ground truth labels and is evaluated offline, not real-time.

---

## 5. System Health Metrics

### 5.1 ML Feature Enabled Ratio

**Metric Name**: `ml.features.enabled_ratio`

**Definition**: Percentage of interviews running with ML features enabled vs. disabled.

**Measurement**:
- Numerator: Interviews with ml.embedding.enabled=true OR ml.nlp.enabled=true OR ml.prediction.enabled=true
- Denominator: Total interviews
- Formula: `(ml_enabled_interviews / total_interviews) * 100`

**Collection Point**: Interview session creation

**Baseline Target**: 
- Development: 100%
- Staging: 100%
- Production: 0% initially, ramp to 10% → 50% → 100% over weeks

**Alert Thresholds**:
- Warning: Unexpected change > 20% from target
- Critical: Unexpected change > 50% from target

**Rationale**: Tracks rollout progress and detects accidental feature flag changes.

**Sample Query (Prometheus)**:
```promql
rate(ml_features_enabled_interviews_total[5m]) / rate(interviews_total[5m]) * 100
```

---

### 5.2 ML Component Bean Load Status

**Metric Name**: `ml.components.beans_loaded`

**Definition**: Count of ML Spring beans successfully loaded at application startup.

**Measurement**:
- Expected beans: 10 (EmbeddingService, TopicClusteringService, TopicCoverageTracker, ResponseFeatureExtractor, TechnicalTermDictionary, TfIdfVectorizer, ResponseScoringModel, InterviewOutcomePredictor, KnowledgeGapDetector, EarlyStoppingService)
- Actual beans: Count from application context

**Collection Point**: Application startup health check

**Baseline Target**: 
- Development: 10/10 beans loaded
- Staging: 10/10 beans loaded
- Production: 0/10 beans loaded (ML disabled by default)

**Alert Thresholds**:
- Critical: Bean count mismatch from expected target for environment

**Rationale**: Detects configuration errors or missing dependencies at startup.

**Sample Query (JMX)**:
```java
mBeans.getBeansOfType(EmbeddingService.class).size()
```

---

### 5.3 Redis Cache Availability

**Metric Name**: `ml.cache.redis.availability_percent`

**Definition**: Percentage of time Redis cache is available for ML features.

**Measurement**:
- Check: Redis ping every 30 seconds
- Formula: `(successful_pings / total_pings) * 100`

**Collection Point**: Spring Boot Actuator health check

**Baseline Target**: > 99.9% uptime

**Alert Thresholds**:
- Warning: < 99% uptime
- Critical: < 95% uptime

**Rationale**: Redis failure causes cache misses, increasing OpenAI costs and latency.

**Sample Query (Prometheus)**:
```promql
avg_over_time(up{job="redis-cache"}[5m]) * 100
```

---

### 5.4 OpenAI API Error Rate

**Metric Name**: `ml.openai.errors.rate`

**Definition**: Percentage of OpenAI API requests resulting in errors (4xx, 5xx, timeout).

**Measurement**:
- Numerator: Failed API requests
- Denominator: Total API requests
- Formula: `(errors / total_requests) * 100`
- Tags: `error_type` (401_unauthorized, 429_rate_limit, 500_server_error, timeout)

**Collection Point**: `EmbeddingService` and `OpenAiService` error handling

**Baseline Target**: < 1% error rate

**Alert Thresholds**:
- Warning: > 5% error rate
- Critical: > 10% error rate

**Rationale**: High error rate indicates API key issues, rate limiting, or OpenAI outage.

**Sample Query (Prometheus)**:
```promql
rate(ml_openai_errors_total[5m]) / rate(ml_openai_requests_total[5m]) * 100
```

---

## Implementation Guide

### Instrumentation Locations

**Embedding Service** (`EmbeddingService.java`):
```java
@Timed(value = "ml.embedding.generation.latency", percentiles = {0.5, 0.95, 0.99})
public float[] generateEmbedding(String text) {
    // Implementation
}

@Counted(value = "ml.embedding.cache", extraTags = {"result", "hit"})
public float[] getFromCache(String key) {
    // Implementation
}
```

**Topic Coverage Tracker** (`TopicCoverageTracker.java`):
```java
@Gauge(name = "ml.embedding.question.repetition_ratio", description = "Question repetition percentage")
public double getRepetitionRatio() {
    return calculateRepetitionRatio();
}

@Gauge(name = "ml.embedding.topic.coverage_entropy", description = "Topic coverage entropy")
public double getCoverageEntropy() {
    return calculateEntropy();
}
```

**Response Feature Extractor** (`ResponseFeatureExtractor.java`):
```java
@Timed(value = "ml.nlp.feature_extraction.latency", percentiles = {0.5, 0.95, 0.99})
public FeatureVector extractFeatures(String responseText) {
    // Implementation
}
```

**Interview Outcome Predictor** (`InterviewOutcomePredictor.java`):
```java
@Timed(value = "ml.prediction.outcome.latency", percentiles = {0.5, 0.95, 0.99})
public PredictionResult predictOutcome(InterviewContext context) {
    // Implementation
}

@Gauge(name = "ml.prediction.outcome.rmse", description = "Prediction RMSE")
public double getCurrentRMSE() {
    return calculateRMSE();
}
```

**Early Stopping Service** (`EarlyStoppingService.java`):
```java
@Counted(value = "ml.prediction.early_stopping.activated", extraTags = {"outcome", "#result.outcome"})
public StoppingDecision shouldStopEarly(PredictionResult prediction) {
    // Implementation
}

@Gauge(name = "ml.prediction.early_stopping.false_positive_rate")
public double getFalsePositiveRate() {
    return evaluateFalsePositiveRate();
}
```

### Required Dependencies

Add to `pom.xml`:
```xml
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-core</artifactId>
</dependency>
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

### Prometheus Scrape Configuration

Add to `prometheus.yml`:
```yaml
scrape_configs:
  - job_name: 'ai-interview-backend'
    metrics_path: '/actuator/prometheus'
    scrape_interval: 15s
    static_configs:
      - targets: ['localhost:8080']
        labels:
          environment: 'staging'
          service: 'ai-interview-backend'
```

### Grafana Dashboard Template

Import dashboard JSON from: `monitoring/grafana_ml_metrics_dashboard.json`

**Panels to include**:
1. Question Repetition Ratio (line graph)
2. Topic Coverage Entropy (gauge)
3. Embedding Generation Latency (histogram)
4. NLP Feature Extraction Latency (histogram)
5. Outcome Prediction Latency (histogram)
6. Cache Hit Ratios (stacked area)
7. OpenAI Token Usage (line graph)
8. OpenAI Request Rate (line graph)
9. Prediction RMSE (line graph)
10. Early Stopping Activation Rate (gauge)
11. False Positive/Negative Rates (bar chart)
12. OpenAI API Error Rate (line graph)

---

## Alerting Rules

### Critical Alerts (PagerDuty)

```yaml
groups:
  - name: ml_features_critical
    interval: 1m
    rules:
      - alert: MLPredictionHighErrorRate
        expr: ml_prediction_outcome_rmse > 30
        for: 10m
        labels:
          severity: critical
        annotations:
          summary: "ML prediction RMSE above critical threshold"
          description: "RMSE is {{ $value }}, expected < 15"

      - alert: MLEarlyStoppingHighFalsePositiveRate
        expr: ml_prediction_early_stopping_false_positive_rate > 20
        for: 15m
        labels:
          severity: critical
        annotations:
          summary: "Early stopping false positive rate too high"
          description: "False positive rate is {{ $value }}%, causing unfair candidate rejections"

      - alert: OpenAIAPIHighErrorRate
        expr: (rate(ml_openai_errors_total[5m]) / rate(ml_openai_requests_total[5m])) * 100 > 10
        for: 5m
        labels:
          severity: critical
        annotations:
          summary: "OpenAI API error rate above 10%"
          description: "API error rate is {{ $value }}%, check API key and rate limits"

      - alert: RedisCacheDown
        expr: up{job="redis-cache"} < 1
        for: 2m
        labels:
          severity: critical
        annotations:
          summary: "Redis cache is down"
          description: "ML features will experience high latency and increased costs"
```

### Warning Alerts (Slack)

```yaml
  - name: ml_features_warning
    interval: 5m
    rules:
      - alert: MLQuestionRepetitionHigh
        expr: ml_embedding_question_repetition_ratio > 15
        for: 30m
        labels:
          severity: warning
        annotations:
          summary: "Question repetition rate above target"
          description: "Repetition ratio is {{ $value }}%, expected < 10%"

      - alert: MLEmbeddingLatencyHigh
        expr: histogram_quantile(0.95, rate(ml_embedding_generation_latency_ms_bucket[5m])) > 1000
        for: 15m
        labels:
          severity: warning
        annotations:
          summary: "Embedding generation p95 latency above 1 second"
          description: "p95 latency is {{ $value }}ms, expected < 500ms"

      - alert: MLCacheHitRatioLow
        expr: (rate(ml_embedding_cache_hits_total[5m]) / (rate(ml_embedding_cache_hits_total[5m]) + rate(ml_embedding_cache_misses_total[5m]))) * 100 < 60
        for: 1h
        labels:
          severity: warning
        annotations:
          summary: "Embedding cache hit ratio below target"
          description: "Cache hit ratio is {{ $value }}%, expected > 80%"

      - alert: OpenAITokenUsageSpike
        expr: rate(ml_openai_tokens_consumed_total[1h]) > 500000
        for: 1h
        labels:
          severity: warning
        annotations:
          summary: "OpenAI token usage spike detected"
          description: "Token usage rate is {{ $value }} tokens/hour, investigate cause"
```

---

## Testing Metrics in Staging

### Validation Checklist

Before production rollout, verify all metrics are collected correctly in staging:

- [ ] Run 10 complete ML-enabled interviews in staging
- [ ] Verify all 18 metrics appear in Prometheus `/metrics` endpoint
- [ ] Confirm Grafana dashboard renders all panels
- [ ] Trigger cache miss scenario (clear Redis) and verify cache hit ratio drops
- [ ] Trigger high latency scenario (network throttle) and verify alert fires
- [ ] Simulate OpenAI API error (invalid key) and verify error rate metric increases
- [ ] Complete interview and verify prediction RMSE is calculated
- [ ] Trigger early stopping and verify activation rate metric increments
- [ ] Export metrics for 24 hours and verify no data gaps

### Staging Environment Setup

```bash
# Enable ML features in staging
export ML_EMBEDDING_ENABLED=true
export ML_NLP_ENABLED=true
export ML_PREDICTION_ENABLED=true

# Start application with staging profile
java -jar backend.jar --spring.profiles.active=staging

# Verify metrics endpoint
curl http://staging-backend:8080/actuator/prometheus | grep "ml_"

# Expected output: 18+ metrics with ml_ prefix
```

---

## Baseline Data Collection

### Week 1: Establish Baselines

After deploying to staging, run for 1 week to establish baseline values:

1. **Collect Metrics**: Record p50/p95/p99 for all latency metrics
2. **Calculate Averages**: Compute mean/median for quality metrics
3. **Document Baselines**: Update this document with actual observed values
4. **Set Alerts**: Configure alert thresholds based on actual baseline + 2σ

### Week 2-4: Monitor Trends

- Track daily metrics in Grafana
- Identify patterns (time of day, interview type)
- Adjust alert thresholds if needed
- Document anomalies and resolutions

### Production Rollout

- Start with 10% traffic to ML features
- Compare metrics to staging baseline
- If metrics stable for 3 days, increase to 50%
- If metrics stable for 1 week, increase to 100%

---

## Retention and Storage

**Prometheus Retention**: 30 days (high-resolution data)
**Long-term Storage**: Export to S3/GCS for historical analysis
**Aggregation**: Daily summaries stored in MySQL for reporting

---

## Related Documents

- [Week 15 Rollout Runbook](week15_rollout_runbook.md) - Step-by-step rollout procedure
- [Week 15 Rollback Strategy](week15_rollback_strategy.md) - Emergency rollback instructions
- [Week 15 Regression Validation Report](week15_regression_validation_report.md) - Test results

---

**Document Version**: 1.0  
**Last Updated**: 2025-01-18  
**Owner**: ML Platform Team  
**Review Cycle**: Quarterly or after major ML feature changes
