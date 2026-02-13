# Experiment Templates

Three ready-to-use A/B experiment templates for ML optimization.

---

## Template 1: Prompt Variant Testing

**Hypothesis:** Few-shot examples in prompts improve answer quality by >10%

**Configuration:**

```json
{
  "id": "prompt_fewshot_test_001",
  "name": "Resume Analysis - Few-Shot vs Baseline",
  "type": "prompt_variant",
  "status": "draft",
  "baseline_config": {
    "prompt_version": "v1.0_baseline",
    "model": "gpt-3.5-turbo-1106",
    "temperature": 0.5,
    "max_tokens": 400
  },
  "variant_config": {
    "prompt_version": "v1.2_few_shot",
    "model": "gpt-3.5-turbo-1106",
    "temperature": 0.5,
    "max_tokens": 400
  },
  "traffic_split": 0.5,
  "min_sample_size": 100,
  "significance_threshold": 0.05,
  "primary_metric": "quality_score",
  "description": "Testing if few-shot examples improve resume analysis quality without increasing cost or latency"
}
```

**Expected Results:**
- Quality score: +10-15%
- Latency: +5-10% (slightly slower due to longer prompt)
- Cost: +10% (more input tokens)
- **Verdict:** If quality improvement > 10%, worth the cost

**Usage:**
```bash
curl -X POST http://localhost:8080/api/ml/experiments \
  -H "Content-Type: application/json" \
  -d @experiments/template1_prompt_fewshot.json

curl -X POST http://localhost:8080/api/ml/experiments/prompt_fewshot_test_001/start
```

---

## Template 2: Model Comparison

**Hypothesis:** GPT-4 provides marginally better quality but at 3x cost - not worth it for most use cases

**Configuration:**

```json
{
  "id": "model_gpt4_test_001",
  "name": "Question Generation - GPT-4 vs GPT-3.5",
  "type": "model_comparison",
  "status": "draft",
  "baseline_config": {
    "prompt_version": "v2.1_difficulty_aware",
    "model": "gpt-3.5-turbo-1106",
    "temperature": 0.7,
    "max_tokens": 300
  },
  "variant_config": {
    "prompt_version": "v2.1_difficulty_aware",
    "model": "gpt-4-turbo-preview",
    "temperature": 0.7,
    "max_tokens": 300
  },
  "traffic_split": 0.2,
  "min_sample_size": 50,
  "significance_threshold": 0.05,
  "primary_metric": "quality_score",
  "description": "Testing if GPT-4 justifies 3x cost for question generation"
}
```

**Expected Results:**
- Quality score: +5-8%
- Latency: +80-100% (GPT-4 is much slower)
- Cost: +200% (3x price)
- **Verdict:** Use GPT-4 selectively (10% of requests) for high-stakes scenarios

**Traffic Split Note:** 0.2 = 20% GPT-4, 80% GPT-3.5 (lower risk)

**Usage:**
```bash
curl -X POST http://localhost:8080/api/ml/experiments \
  -H "Content-Type: application/json" \
  -d @experiments/template2_model_comparison.json

curl -X POST http://localhost:8080/api/ml/experiments/model_gpt4_test_001/start
```

---

## Template 3: Temperature Tuning

**Hypothesis:** Temperature 0.3 provides more consistent answer evaluation than 0.5

**Configuration:**

```json
{
  "id": "temp_tuning_eval_001",
  "name": "Answer Evaluation - Temperature 0.3 vs 0.5",
  "type": "parameter_tuning",
  "status": "draft",
  "baseline_config": {
    "prompt_version": "v3.1_rubric_based",
    "model": "gpt-3.5-turbo-1106",
    "temperature": 0.5,
    "max_tokens": 350
  },
  "variant_config": {
    "prompt_version": "v3.1_rubric_based",
    "model": "gpt-3.5-turbo-1106",
    "temperature": 0.3,
    "max_tokens": 350
  },
  "traffic_split": 0.5,
  "min_sample_size": 100,
  "significance_threshold": 0.05,
  "primary_metric": "quality_score",
  "description": "Testing if lower temperature reduces scoring variance in answer evaluation"
}
```

**Expected Results:**
- Quality score: Similar (±2%)
- Scoring variance: -20-30% (more consistent)
- Latency: Similar
- Cost: Identical
- **Verdict:** Lower temperature for evaluation tasks where consistency matters

**Metrics to Compare:**
- Mean quality score
- **Standard deviation** (consistency metric)
- Pass rate stability

**Usage:**
```bash
curl -X POST http://localhost:8080/api/ml/experiments \
  -H "Content-Type: application/json" \
  -d @experiments/template3_temperature_tuning.json

curl -X POST http://localhost:8080/api/ml/experiments/temp_tuning_eval_001/start
```

---

## Experiment Workflow

### 1. Create Experiment

```java
Experiment experiment = Experiment.builder()
    .id("my_experiment_001")
    .name("My A/B Test")
    .type("prompt_variant")
    .status("draft")
    .baselineConfig("{...}")
    .variantConfig("{...}")
    .trafficSplit(0.5)
    .minSampleSize(100)
    .significanceThreshold(0.05)
    .primaryMetric("quality_score")
    .description("Testing hypothesis...")
    .build();

experimentTracker.createExperiment(experiment);
```

### 2. Start Experiment

```java
experimentTracker.startExperiment("my_experiment_001");
```

### 3. Route Traffic & Collect Metrics

```java
// In your service code
ExperimentAssignment assignment = experimentTracker.assignExperiment(
    "my_experiment_001", 
    sessionId  // Use consistent ID (userId, sessionId, etc.)
);

// Parse config and use it
Config config = parseConfig(assignment.getConfig());
String response = callAI(prompt, config);

// Log metrics
Map<String, Double> metrics = Map.of(
    "quality_score", evaluateQuality(response),
    "latency_ms", (double) latency,
    "cost_usd", calculateCost(tokens),
    "validation_pass", isValid ? 1.0 : 0.0
);

experimentTracker.logResult("my_experiment_001", sessionId, metrics);
```

### 4. Evaluate Results

```java
ExperimentResult result = experimentTracker.evaluateExperiment("my_experiment_001");

System.out.println("Baseline samples: " + result.getBaselineSamples());
System.out.println("Variant samples: " + result.getVariantSamples());
System.out.println("P-value: " + result.getPValue());
System.out.println("Significant: " + result.getIsSignificant());
System.out.println("Winner: " + result.getWinner());
System.out.println("Improvement: " + result.getImprovement() + "%");
System.out.println("Recommendation: " + result.getRecommendation());
```

### 5. Make Decision

Based on `result.getRecommendation()`:

- **"rollout"**: Variant is significantly better → Deploy to 100%
- **"rollback"**: Baseline is significantly better → Stop experiment
- **"continue"**: Not significant yet → Collect more data

```java
if ("rollout".equals(result.getRecommendation())) {
    experimentTracker.completeExperiment("my_experiment_001", "variant");
    // Deploy variant to production
} else if ("rollback".equals(result.getRecommendation())) {
    experimentTracker.completeExperiment("my_experiment_001", "baseline");
    // Keep baseline
}
```

### 6. Auto-Rollback (Optional)

For critical experiments, enable auto-rollback:

```java
// Check regularly (e.g., every hour)
boolean rolledBack = experimentTracker.checkAndRollback("my_experiment_001");

if (rolledBack) {
    logger.warn("Experiment auto-rolled back due to regression");
    alertTeam("Experiment " + experimentId + " rolled back");
}
```

---

## Best Practices

### Traffic Split

- **50/50**: Standard for equal comparison
- **90/10**: Low-risk (10% on risky variant)
- **80/20**: Moderate confidence in variant

### Sample Size

- **Minimum 100 per variant**: For statistical power
- **Calculate required: n = (Z² × σ² × 2) / d²**
  - Z = 1.96 (95% confidence)
  - σ = standard deviation
  - d = minimum detectable effect
  
**Example:**
- σ = 1.5 (quality score std dev)
- d = 0.5 (want to detect 0.5 point improvement)
- **n = (1.96² × 1.5² × 2) / 0.5² ≈ 139 samples per variant**

### Significance Threshold

- **0.05 (5%)**: Standard in industry
- **0.01 (1%)**: For critical changes
- **0.10 (10%)**: For exploratory tests

### Primary Metric

Choose one:
- `quality_score`: Most important for ML
- `cost_usd`: For cost optimization
- `latency_ms`: For performance
- `pass_rate`: For validation

### Experiment Duration

- Run until **min sample size reached**
- **Maximum 2 weeks** (avoid novelty effects)
- **Minimum 3 days** (avoid day-of-week bias)

### Multiple Testing Correction

If running multiple experiments:
- Use **Bonferroni correction**: α' = α / n
- Example: 3 experiments, use threshold 0.05/3 = 0.0167

---

## Monitoring Dashboard

View active experiments:

```bash
curl http://localhost:8080/api/ml/experiments/active
```

Evaluate all active experiments:

```bash
for exp_id in $(curl -s http://localhost:8080/api/ml/experiments/active | jq -r '.[].id'); do
  curl http://localhost:8080/api/ml/experiments/$exp_id/evaluate
done
```

---

## Troubleshooting

### No Significant Results

**Possible causes:**
1. Sample size too small
2. Effect size too small
3. High variance

**Solutions:**
- Increase sample size
- Run longer
- Reduce variance (better baseline)

### Inconsistent Assignments

**Symptom:** Same user gets different variants

**Cause:** Using non-deterministic requestId

**Solution:** Use consistent ID (userId, sessionId)

```java
// Good: Same user always gets same variant
String requestId = userId;

// Bad: Different requestId each time
String requestId = UUID.randomUUID().toString();  // ❌
```

### Metrics Not Logged

**Check:**
1. Is experiment active?
2. Is assignment recorded?
3. Are metrics valid (not null)?

```java
// Verify assignment
ExperimentAssignment assignment = experimentTracker.assignExperiment(expId, requestId);
log.info("Assigned to variant: {}", assignment.getVariant());

// Verify metrics
log.info("Metrics: {}", metrics);
```

---

**Last Updated:** 2026-02-13  
**Version:** 1.0
