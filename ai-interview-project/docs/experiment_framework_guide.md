# Experiment Framework Guide

Production-grade A/B testing system for ML optimization with statistical rigor and auto-rollback.

---

## Overview

The Experiment Framework enables systematic evaluation of:
- **Prompt variants**: Test different prompt engineering approaches
- **Model comparisons**: Compare GPT-3.5 vs GPT-4 vs fine-tuned models
- **Parameter tuning**: Optimize temperature, max_tokens, etc.
- **RAG configurations**: Test different retrieval strategies

**Key Features:**
- ✅ Consistent traffic assignment (hash-based)
- ✅ Statistical evaluation (Welch's t-test)
- ✅ Auto-rollback on regression
- ✅ Real-time metrics collection
- ✅ Dashboard integration

---

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│               Experiment Framework Flow                  │
└─────────────────────────────────────────────────────────┘

1. Setup Phase
   ┌─────────────────┐
   │ Create          │
   │ Experiment      │──> Define baseline vs variant
   └────────┬────────┘
            │
            v
   ┌─────────────────┐
   │ Start           │──> Activate traffic split
   │ Experiment      │
   └─────────────────┘

2. Runtime Phase (Per Request)
   ┌─────────────────┐
   │ Request Arrives │
   └────────┬────────┘
            │
            v
   ┌─────────────────┐
   │ Hash RequestId  │──> Consistent assignment
   └────────┬────────┘
            │
            ├─────> 50% Baseline Config
            │
            └─────> 50% Variant Config
            │
            v
   ┌─────────────────┐
   │ Call LLM with   │
   │ Assigned Config │
   └────────┬────────┘
            │
            v
   ┌─────────────────┐
   │ Collect Metrics │──> quality, latency, cost
   └────────┬────────┘
            │
            v
   ┌─────────────────┐
   │ Log to DB       │
   └─────────────────┘

3. Evaluation Phase
   ┌─────────────────┐
   │ Collect N       │──> Wait for min_sample_size
   │ Samples         │
   └────────┬────────┘
            │
            v
   ┌─────────────────┐
   │ Compute         │──> Mean, variance per variant
   │ Statistics      │
   └────────┬────────┘
            │
            v
   ┌─────────────────┐
   │ T-Test          │──> p-value < 0.05?
   └────────┬────────┘
            │
            ├─────> Significant → Winner
            │
            └─────> Not Significant → Continue
            │
            v
   ┌─────────────────┐
   │ Recommendation  │
   │ - Rollout       │──> Deploy variant
   │ - Rollback      │──> Keep baseline
   │ - Continue      │──> Need more data
   └─────────────────┘
```

---

## Core Components

### 1. Experiment Entity

```java
@Entity
@Table(name = "ml_experiments")
public class Experiment {
    String id;                    // Unique experiment ID
    String name;                  // Human-readable name
    String type;                  // prompt_variant, model_comparison, etc.
    String status;                // draft, active, completed, rolled_back
    String baselineConfig;        // JSON config for control group
    String variantConfig;         // JSON config for treatment group
    Double trafficSplit;          // 0.0-1.0 (fraction to variant)
    Integer minSampleSize;        // Samples needed per variant
    Double significanceThreshold; // p-value threshold (default 0.05)
    String primaryMetric;         // quality_score, latency_ms, cost_usd
    LocalDateTime startedAt;
    LocalDateTime completedAt;
    String winner;                // baseline, variant, or inconclusive
}
```

**Database Schema:**

```sql
CREATE TABLE ml_experiments (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    type VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL,
    baseline_config TEXT,
    variant_config TEXT,
    traffic_split DOUBLE NOT NULL,
    min_sample_size INT NOT NULL,
    significance_threshold DOUBLE NOT NULL,
    primary_metric VARCHAR(50),
    description TEXT,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    winner VARCHAR(20),
    result_summary TEXT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP
);
```

### 2. Experiment Metrics

```java
@Entity
@Table(name = "experiment_metrics")
public class ExperimentMetric {
    Long id;
    String experimentId;
    String requestId;
    String variant;              // baseline or variant
    Double qualityScore;         // 0.0-10.0
    Double latencyMs;
    Integer tokensUsed;
    Double costUsd;
    Boolean validationPass;
    LocalDateTime createdAt;
}
```

**Database Schema:**

```sql
CREATE TABLE experiment_metrics (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    experiment_id VARCHAR(64) NOT NULL,
    request_id VARCHAR(64) NOT NULL,
    variant VARCHAR(20) NOT NULL,
    quality_score DOUBLE,
    latency_ms DOUBLE,
    tokens_used INT,
    cost_usd DOUBLE,
    validation_pass BOOLEAN,
    metadata TEXT,
    created_at TIMESTAMP NOT NULL,
    INDEX idx_experiment_variant (experiment_id, variant),
    INDEX idx_created_at (created_at)
);
```

### 3. Experiment Tracker

**Core Service:**

```java
@Service
public class ExperimentTracker {
    
    // Assign request to variant
    ExperimentAssignment assignExperiment(String experimentId, String requestId);
    
    // Log metrics
    void logResult(String experimentId, String requestId, Map<String, Double> metrics);
    
    // Evaluate with t-test
    ExperimentResult evaluateExperiment(String experimentId);
    
    // Auto-rollback if regression detected
    boolean checkAndRollback(String experimentId);
    
    // Management
    Experiment createExperiment(Experiment experiment);
    void startExperiment(String experimentId);
    void completeExperiment(String experimentId, String winner);
    List<Experiment> getActiveExperiments();
}
```

---

## Usage Guide

### Step 1: Create Experiment

```java
Experiment experiment = Experiment.builder()
    .id("prompt_test_001")
    .name("Few-Shot vs Baseline")
    .type("prompt_variant")
    .status("draft")
    .baselineConfig("{\"prompt_version\": \"v1.0\", \"model\": \"gpt-3.5-turbo\"}")
    .variantConfig("{\"prompt_version\": \"v1.2\", \"model\": \"gpt-3.5-turbo\"}")
    .trafficSplit(0.5)              // 50/50 split
    .minSampleSize(100)             // 100 samples per variant
    .significanceThreshold(0.05)    // 95% confidence
    .primaryMetric("quality_score")
    .description("Testing if few-shot improves quality")
    .build();

experimentTracker.createExperiment(experiment);
```

**Via REST API:**

```bash
curl -X POST http://localhost:8080/api/ml/experiments \
  -H "Content-Type: application/json" \
  -d '{
    "id": "prompt_test_001",
    "name": "Few-Shot vs Baseline",
    "type": "prompt_variant",
    "baselineConfig": "{\"prompt_version\": \"v1.0\"}",
    "variantConfig": "{\"prompt_version\": \"v1.2\"}",
    "trafficSplit": 0.5,
    "minSampleSize": 100,
    "significance_threshold": 0.05,
    "primaryMetric": "quality_score"
  }'
```

### Step 2: Start Experiment

```java
experimentTracker.startExperiment("prompt_test_001");
```

```bash
curl -X POST http://localhost:8080/api/ml/experiments/prompt_test_001/start
```

### Step 3: Route Traffic

**In your service code:**

```java
@Service
public class InterviewService {
    
    @Autowired
    private ExperimentTracker experimentTracker;
    
    @Autowired
    private OpenAIService openAIService;
    
    public String generateQuestion(String sessionId) {
        // Get experiment assignment
        ExperimentAssignment assignment = experimentTracker.assignExperiment(
            "prompt_test_001",
            sessionId  // Use consistent ID
        );
        
        // Parse config
        Config config = parseConfig(assignment.getConfig());
        
        // Use assigned configuration
        String prompt = loadPrompt(config.getPromptVersion());
        
        long startTime = System.currentTimeMillis();
        String response = openAIService.call(
            prompt,
            config.getModel(),
            config.getTemperature()
        );
        long latency = System.currentTimeMillis() - startTime;
        
        // Evaluate quality
        double qualityScore = evaluateQuality(response);
        double cost = calculateCost(response);
        
        // Log metrics
        Map<String, Double> metrics = Map.of(
            "quality_score", qualityScore,
            "latency_ms", (double) latency,
            "cost_usd", cost,
            "validation_pass", qualityScore >= 6.0 ? 1.0 : 0.0
        );
        
        experimentTracker.logResult("prompt_test_001", sessionId, metrics);
        
        return response;
    }
}
```

### Step 4: Evaluate Results

```java
ExperimentResult result = experimentTracker.evaluateExperiment("prompt_test_001");

System.out.println("=== Experiment Results ===");
System.out.println("Baseline samples: " + result.getBaselineSamples());
System.out.println("Variant samples: " + result.getVariantSamples());
System.out.println();
System.out.println("Baseline metrics: " + result.getBaselineMetrics());
System.out.println("Variant metrics: " + result.getVariantMetrics());
System.out.println();
System.out.println("P-value: " + result.getPValue());
System.out.println("Significant: " + result.getIsSignificant());
System.out.println("Winner: " + result.getWinner());
System.out.println("Improvement: " + result.getImprovement() + "%");
System.out.println();
System.out.println("Recommendation: " + result.getRecommendation());
System.out.println("Explanation: " + result.getExplanation());
```

**Sample Output:**

```
=== Experiment Results ===
Baseline samples: 120
Variant samples: 115

Baseline metrics: {quality_score=7.8, latency_ms=1450, cost_usd=0.0024}
Variant metrics: {quality_score=8.7, latency_ms=1520, cost_usd=0.0027}

P-value: 0.0032
Significant: true
Winner: variant
Improvement: 11.54%

Recommendation: rollout
Explanation: Variant performs 11.54% better on quality_score (p=0.0032). Recommend full rollout.
```

**Via REST API:**

```bash
curl http://localhost:8080/api/ml/experiments/prompt_test_001/evaluate | jq
```

### Step 5: Make Decision

```java
if ("rollout".equals(result.getRecommendation())) {
    // Variant is significantly better
    experimentTracker.completeExperiment("prompt_test_001", "variant");
    
    // Update production config
    updateProductionConfig("prompt_version", "v1.2");
    
    log.info("Rolled out variant to production");
    
} else if ("rollback".equals(result.getRecommendation())) {
    // Baseline is better (regression detected)
    experimentTracker.completeExperiment("prompt_test_001", "baseline");
    
    log.warn("Variant performed worse, keeping baseline");
    
} else {
    // Inconclusive - need more data
    log.info("Continue collecting data: {}", result.getExplanation());
}
```

---

## Statistical Methodology

### Traffic Assignment

Uses **consistent hashing** to ensure:
1. Same requestId always gets same variant
2. Traffic split is maintained across population
3. No mid-session switching

```java
SHA256(experimentId + ":" + requestId) % 1.0 < trafficSplit ? "variant" : "baseline"
```

### T-Test (Welch's)

**Null Hypothesis (H₀):** No difference between baseline and variant

**Test Statistic:**

```
t = (μ₁ - μ₂) / sqrt(s₁²/n₁ + s₂²/n₂)
```

Where:
- μ₁, μ₂ = sample means
- s₁², s₂² = sample variances
- n₁, n₂ = sample sizes

**Degrees of Freedom (Welch-Satterthwaite):**

```
df = (s₁²/n₁ + s₂²/n₂)² / ((s₁²/n₁)²/(n₁-1) + (s₂²/n₂)²/(n₂-1))
```

**P-Value:**

Two-tailed test: `p = 2 × (1 - CDF(|t|, df))`

**Decision:**
- If `p < 0.05`: Statistically significant difference
- If `p ≥ 0.05`: No significant difference (continue or inconclusive)

### Sample Size Calculation

**Formula:**

```
n = (Z² × σ² × 2) / d²
```

Where:
- Z = 1.96 (for 95% confidence)
- σ = standard deviation of metric
- d = minimum detectable effect

**Example:**
- Quality score σ = 1.5
- Want to detect d = 0.5 point improvement
- n = (1.96² × 1.5² × 2) / 0.5² = **138 samples per variant**

---

## Auto-Rollback

Automatically rollback if variant is significantly **worse** than baseline.

**Configuration:**

```java
@Scheduled(fixedDelay = 3600000) // Check every hour
public void checkActiveExperiments() {
    List<Experiment> active = experimentTracker.getActiveExperiments();
    
    for (Experiment experiment : active) {
        boolean rolledBack = experimentTracker.checkAndRollback(experiment.getId());
        
        if (rolledBack) {
            alertService.sendAlert(
                "Experiment Auto-Rollback",
                String.format("Experiment %s rolled back due to regression", experiment.getName())
            );
        }
    }
}
```

**Trigger Conditions:**
1. Variant is significantly worse (p < 0.05)
2. Recommendation is "rollback"
3. Experiment status is "active"

**Actions:**
1. Set experiment status to "rolled_back"
2. Set winner to "baseline"
3. Stop new assignments to variant
4. Log alert

---

## Dashboard Integration

### REST API Endpoints

```
GET    /api/ml/experiments              # List all experiments
GET    /api/ml/experiments/active       # List active experiments
GET    /api/ml/experiments/{id}         # Get experiment details
POST   /api/ml/experiments              # Create experiment
POST   /api/ml/experiments/{id}/start   # Start experiment
GET    /api/ml/experiments/{id}/assign  # Get assignment
POST   /api/ml/experiments/{id}/log     # Log metrics
GET    /api/ml/experiments/{id}/evaluate # Get results
POST   /api/ml/experiments/{id}/complete # Complete experiment
POST   /api/ml/experiments/{id}/check-rollback # Check for rollback
```

### Sample Dashboard Queries

**Active Experiments:**

```bash
curl http://localhost:8080/api/ml/experiments/active
```

**Evaluation Summary:**

```bash
for exp_id in $(curl -s http://localhost:8080/api/ml/experiments/active | jq -r '.[].id'); do
  echo "=== Experiment: $exp_id ==="
  curl -s http://localhost:8080/api/ml/experiments/$exp_id/evaluate | jq '{
    winner: .winner,
    improvement: .improvement,
    pValue: .pValue,
    recommendation: .recommendation
  }'
  echo
done
```

---

## Best Practices

### 1. Consistent Request IDs

✅ **Good:** Use stable identifiers
```java
String requestId = userId;           // Same user → same variant
String requestId = sessionId;        // Same session → same variant
```

❌ **Bad:** Use random IDs
```java
String requestId = UUID.randomUUID().toString();  // Different every time!
```

### 2. Adequate Sample Sizes

- **Minimum:** 100 samples per variant
- **Recommended:** 150-200 for robust results
- **Calculate:** Use power analysis formula

### 3. Run Duration

- **Minimum:** 3 days (avoid day-of-week bias)
- **Maximum:** 2 weeks (avoid novelty effects)
- **Typical:** 5-7 days

### 4. Metric Selection

**Primary metric:** The ONE metric to optimize

**Good choices:**
- `quality_score`: Most important for ML
- `pass_rate`: For validation
- `cost_usd`: For cost optimization

**Bad choices:**
- Multiple primary metrics (use secondary instead)
- Vanity metrics (clicks without quality)

### 5. Traffic Split

- **50/50**: Standard for equal comparison
- **80/20**: Conservative (20% on risky variant)
- **90/10**: Very conservative (10% test traffic)

### 6. Avoid Peeking

Don't evaluate continuously - it inflates false positives.

**Instead:**
- Set evaluation checkpoints (e.g., every 100 samples)
- Use sequential testing methods
- Or wait until min sample size reached

---

## Troubleshooting

### Issue: No Significant Results After Many Samples

**Possible Causes:**
1. Effect size too small
2. High variance in metrics
3. Sample size insufficient

**Solutions:**
- Increase sample size
- Reduce variance (better baselines)
- Accept that effect may be negligible

### Issue: Inconsistent Assignments

**Symptom:** User reports seeing both variants

**Cause:** Non-deterministic request ID

**Fix:** Use stable ID
```java
// Before: ❌
String requestId = UUID.randomUUID().toString();

// After: ✅
String requestId = userSession.getId();
```

### Issue: One Variant Has More Samples

**Expected:** Some imbalance is normal (±5%)

**Problematic:** >20% difference

**Causes:**
1. Variant has higher error rate (failed requests not logged)
2. Variant causes users to abandon (selection bias)

**Solutions:**
- Log failures too
- Check abandonment rates

---

## Experiment Templates

See [experiment_templates.md](experiment_templates.md) for ready-to-use templates:
1. **Prompt Variant Testing**: Few-shot vs baseline
2. **Model Comparison**: GPT-4 vs GPT-3.5
3. **Temperature Tuning**: Consistency optimization

---

## References

- [A/B Testing Best Practices](https://exp-platform.com/)
- [Statistical Power Analysis](https://www.stat.ubc.ca/~rollin/stats/ssize/)
- [Welch's T-Test](https://en.wikipedia.org/wiki/Welch%27s_t-test)

---

**Last Updated:** 2026-02-13  
**Version:** 1.0  
**Maintained By:** AI Interview Team
