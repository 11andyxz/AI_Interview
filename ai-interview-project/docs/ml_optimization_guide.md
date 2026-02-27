# ML Performance Optimization & Cost Efficiency Guide

Complete guide to AI model optimization, prompt engineering, caching, and cost management.

---

## Table of Contents

1. [Overview](#overview)
2. [Prompt Version Control](#prompt-version-control)
3. [A/B Testing Framework](#ab-testing-framework)
4. [Intelligent Caching](#intelligent-caching)
5. [Token Optimization](#token-optimization)
6. [Cost Tracking & Budgets](#cost-tracking--budgets)
7. [Model Benchmarking](#model-benchmarking)
8. [Best Practices](#best-practices)
9. [Troubleshooting](#troubleshooting)

---

## Overview

This system provides comprehensive ML performance optimization through:

- **Prompt Version Control**: Manage and test multiple prompt variants
- **A/B Testing**: Data-driven prompt optimization
- **Intelligent Caching**: Semantic + exact match caching (25-40% cost savings)
- **Token Optimization**: Reduce token usage by 20%+
- **Cost Tracking**: Real-time budget monitoring and alerts
- **Benchmarking**: Compare models and configurations

**Expected Impact**:
- 25-30% cost reduction through caching
- 20% token usage reduction
- Improved quality through systematic experimentation
- Real-time budget visibility

---

## Prompt Version Control

### Directory Structure

```
backend/src/main/resources/prompts/versions/
├── resume_analysis/
│   ├── v1.0_baseline.json
│   ├── v1.1_structured.json
│   ├── v1.2_few_shot.json
│   └── metadata.json
├── question_generation/
│   ├── v2.0_baseline.json
│   ├── v2.1_difficulty_aware.json
│   ├── v2.2_context_enhanced.json
│   └── metadata.json
└── answer_evaluation/
    ├── v3.0_baseline.json
    ├── v3.1_rubric_based.json
    └── metadata.json
```

### Prompt Version Format

```json
{
  "version": "v1.2_few_shot",
  "description": "Enhanced with few-shot examples",
  "created_at": "2026-02-05",
  "system_prompt": "You are an expert...",
  "user_prompt_template": "Analyze: {input}",
  "parameters": {
    "temperature": 0.5,
    "max_tokens": 400,
    "top_p": 0.95
  }
}
```

### Creating New Versions

1. Copy existing version as starting point
2. Modify prompts/parameters
3. Update version number and metadata
4. Test with golden dataset
5. Document changes in metadata.json

---

## A/B Testing Framework

### Configuration

Enable experiments in `application.properties`:

```properties
ml.ab-test.enabled=true
ml.ab-test.resume-analysis.enabled=true
ml.ab-test.question-generation.enabled=false
```

### Programmatic Configuration

```java
@Bean
public PromptRouter promptRouter() {
    return PromptRouter.builder()
        .experiment("resume_analysis")
            .variant("v1.0_baseline", 0.5)    // 50% traffic
            .variant("v1.2_few_shot", 0.5)    // 50% traffic
            .metric("quality_score")
            .minimumSamples(100)
        .build();
}
```

### Experiment Workflow

1. **Define Hypothesis**: "Few-shot examples improve quality by >10%"
2. **Set Traffic Split**: Usually 50/50 for control vs treatment
3. **Collect Data**: Minimum 100 samples per variant
4. **Analyze Results**: Compare quality, cost, latency
5. **Make Decision**: Rollout, rollback, or iterate

### Routing Logic

The router uses **consistent hashing**:
- Same user always gets same variant (stable experience)
- Traffic split maintained across the user base
- No mid-session variant switching

```java
String variant = promptRouter.routeToVariant("resume_analysis", userId);
PromptVersion prompt = promptVersionLoader.getVersion("resume_analysis", variant);
```

### Metrics Collection

```java
experimentMetricsCollector.recordDataPoint(
    ExperimentDataPoint.builder()
        .scenario("resume_analysis")
        .variant("v1.2_few_shot")
        .userId(userId)
        .latencyMs(1420)
        .tokenCount(380)
        .qualityScore(8.7)
        .passed(true)
        .costEstimate(0.0024)
        .build()
);
```

### Analyzing Results

```java
ComparisonResult result = experimentMetricsCollector.compareVariants(
    "resume_analysis",
    "v1.0_baseline",     // control
    "v1.2_few_shot"      // treatment
);

// Check if statistically significant
if (result.isSignificant()) {
    log.info("Recommendation: {}", result.getRecommendation());
}
```

---

## Intelligent Caching

### Two-Level Caching Strategy

#### Level 1: Exact Match Cache (Redis)
- **Use Case**: Identical prompts
- **Hit Rate**: 15-20%
- **Latency**: ~5ms
- **TTL**: Varies by scenario (24h - 30 days)

#### Level 2: Semantic Cache (Embedding-based)
- **Use Case**: Similar prompts (cosine similarity > 0.95)
- **Hit Rate**: 10-15%
- **Latency**: ~50ms
- **TTL**: Same as exact match

### Usage

```java
@Autowired
private ResponseCacheService cacheService;

// Try to get cached response
Optional<CachedAIResponse> cached = cacheService.getCached(scenario, prompt);

if (cached.isPresent()) {
    return cached.get().getResponse();  // Cache hit!
}

// Cache miss - call AI API
String response = callOpenAI(prompt);

// Cache for future
cacheService.cache(scenario, prompt, response, metadata);
```

### Cache TTL by Scenario

```java
resume_analysis: 24 hours
question_generation: 7 days
answer_evaluation: 24 hours
resume_parsing: 30 days (PDF→text is expensive)
```

### Monitoring Cache Performance

```java
CacheStatistics stats = cacheService.getStatistics();
log.info("Cache hit rate: {:.2f}%", stats.getHitRate() * 100);
log.info("Exact match rate: {:.2f}%", stats.getExactMatchRate() * 100);
log.info("Semantic match rate: {:.2f}%", stats.getSemanticMatchRate() * 100);
```

### Cache Invalidation

```java
// Invalidate specific prompt
cacheService.invalidate(scenario, prompt);

// Invalidate entire scenario
cacheService.invalidateScenario("resume_analysis");
```

### Best Practices

1. **Don't cache user-specific data**: Cache only on prompt content
2. **Set appropriate TTL**: Balance freshness vs hit rate
3. **Monitor hit rates**: Target 30%+ overall
4. **Semantic threshold**: 0.95 is conservative, adjust based on testing

---

## Token Optimization

### Techniques

#### 1. Prompt Compression

```java
String optimized = tokenOptimizer.compressPrompt(originalPrompt);
TokenSavings savings = tokenOptimizer.calculateSavings(original, optimized);
// Expected: 15-20% reduction
```

**Removes**:
- Redundant phrases ("Please note that", "It is important to")
- Multiple spaces
- Repetitive instructions

#### 2. Context Window Management

For multi-turn conversations:

```java
String optimized = tokenOptimizer.compressPromptWithHistory(prompt, history);
```

**Strategy**:
- Keep last 5 turns in full
- Summarize older turns
- Expected: 30-40% reduction in long conversations

#### 3. Optimal Max Tokens

```java
int maxTokens = tokenOptimizer.calculateOptimalMaxTokens(RequestType.RESUME_ANALYSIS);
// Returns: 400 (based on benchmarks)
```

**Configured Limits**:
- Resume analysis: 400 tokens
- Question generation: 300 tokens
- Answer evaluation: 350 tokens
- Follow-up questions: 200 tokens

### Token Estimation

```java
int estimatedTokens = tokenOptimizer.estimateTokenCount(text);
```

**Approximation**:
- English: ~4 characters per token
- Chinese: ~1.5 characters per token

### Integration Example

```java
// Before calling API
String optimizedPrompt = tokenOptimizer.compressPromptWithHistory(prompt, history);
int maxTokens = tokenOptimizer.calculateOptimalMaxTokens(requestType, 
                    tokenOptimizer.estimateTokenCount(optimizedPrompt));

// Call OpenAI with optimized settings
ChatCompletionRequest request = ChatCompletionRequest.builder()
    .model("gpt-3.5-turbo-1106")
    .messages(buildMessages(optimizedPrompt))
    .maxTokens(maxTokens)
    .build();
```

---

## Cost Tracking & Budgets

### Recording Costs

```java
@Autowired
private CostTracker costTracker;

// After each API call
costTracker.recordCost(
    scenario,
    userId,
    model,
    inputTokens,
    outputTokens,
    fromCache
);
```

### Budget Configuration

```yaml
# ml_cost_alerts.yml
budget_allocation:
  daily_total: 100.0
  breakdown:
    resume_analysis: 30.0      # $30/day
    question_generation: 40.0  # $40/day
    answer_evaluation: 30.0    # $30/day
```

### Checking Alerts

```java
List<BudgetAlert> alerts = costTracker.checkBudgetAlerts();

for (BudgetAlert alert : alerts) {
    if (alert.getSeverity() == BudgetAlert.Severity.CRITICAL) {
        // Send notification
        alertService.notify(alert);
    }
}
```

### Alert Types

#### Critical Alerts
- **Daily Budget Exceeded**: Spent > $100/day
- **Cost Spike**: 200% of daily average
- **Monthly Budget 90%**: Approaching monthly limit

#### Warning Alerts
- **80% Budget Utilized**: Close to daily limit
- **Cost Per Request Increasing**: 20% above 7-day average
- **Low Cache Hit Rate**: < 20%
- **High GPT-4 Usage**: > 10% of requests

### Cost Analysis

```java
// Daily costs
DailyCosts today = costTracker.getTodayCosts();
log.info("Today's cost: ${:.2f} ({}% of budget)", 
         today.getTotalCost(), 
         today.getBudgetUtilization() * 100);

// Scenario costs
ScenarioCostStats stats = costTracker.getScenarioCosts("resume_analysis");
log.info("Resume analysis: {} requests, avg ${:.4f} per request", 
         stats.getRequestCount(), 
         stats.getAvgCost());

// Date range
CostStatistics monthly = costTracker.getCostStatistics(
    LocalDate.now().withDayOfMonth(1),  // Start of month
    LocalDate.now()
);
log.info("Month-to-date: ${:.2f}", monthly.getTotalCost());
```

### Cost Optimization Actions

When alerts trigger:

```yaml
actions:
  aggressive_caching:
    semantic_cache_threshold: 0.90  # Increase hit rate
    ttl_multiplier: 2.0             # Keep cache longer
    
  rate_limiting:
    max_requests_per_user_per_hour: 100
    
  model_fallback:
    fallback_sequence:
      - gpt-3.5-turbo-1106
      - gpt-3.5-turbo-16k
```

---

## Model Benchmarking

### Running Benchmarks

```bash
cd eval/model_benchmarking

# Set API key
export OPENAI_API_KEY="your-key"

# Run full benchmark
python run_benchmark.py

# Results saved to:
# - results/benchmark_report_20260206.md
# - results/benchmark_report_20260206.json
```

### What Gets Tested

1. **Model Comparison**
   - gpt-3.5-turbo-1106
   - gpt-3.5-turbo-16k
   - gpt-4-turbo-preview

2. **Temperature Variations**
   - 0.3, 0.5, 0.7, 0.9

3. **Max Tokens**
   - 300, 500, 800, 1000

### Metrics Collected

- **Latency**: p50, p95, p99
- **Token Usage**: Input + output averages
- **Cost**: Estimated cost per 1K requests
- **Quality**: Sample responses for review

### Interpreting Results

See latest report: [benchmark_report_20260206.md](../../eval/model_benchmarking/results/benchmark_report_20260206.md)

**Key Findings**:
- gpt-3.5-turbo-1106: Best value (8.7/10 quality, $2.40/1K)
- gpt-4-turbo: Best quality (9.2/10, but $7.20/1K)
- Temperature 0.7: Best balance
- 400 max_tokens: Optimal for resume analysis

---

## Best Practices

### Prompt Engineering

1. **Use Few-Shot Examples**: Improves consistency (+11% quality)
2. **Structured Output**: Reduce ambiguity
3. **Clear Instructions**: Brief but specific
4. **Test with Golden Dataset**: Validate before deployment

### A/B Testing

1. **One Change at a Time**: Isolate variables
2. **Sufficient Sample Size**: Minimum 100 per variant
3. **Statistical Significance**: Use proper tests
4. **Document Hypotheses**: Track what you're testing
5. **Gradual Rollout**: Start with 10%, then 50%, then 100%

### Caching

1. **Cache Aggressively**: TTL can be longer than you think
2. **Monitor Hit Rates**: Target 30%+
3. **Invalidate Strategically**: Balance freshness vs cost
4. **Use Semantic Cache**: Catches similar (not just identical) prompts

### Token Optimization

1. **Right-Size Max Tokens**: Don't overpay for unused capacity
2. **Compress Prompts**: Remove redundancy
3. **Manage Context**: Summarize old conversation turns
4. **Monitor Truncation**: Ensure you're not cutting off responses

### Cost Management

1. **Set Budgets**: Daily and monthly limits
2. **Monitor Alerts**: Respond to warnings promptly
3. **Track by Scenario**: Identify expensive operations
4. **Optimize Expensive Paths**: Focus on high-volume scenarios
5. **Use Cheaper Models**: Reserve GPT-4 for complex tasks

---

## Troubleshooting

### Cache Not Working

**Symptoms**: 0% hit rate

**Checks**:
1. Is Redis running? `redis-cli ping`
2. Is caching enabled? Check `ml.cache.enabled=true`
3. Are prompts identical? Small differences break exact match
4. Check logs for errors

**Solution**:
```bash
# Restart Redis
redis-server

# Check cache stats
curl http://localhost:8080/api/ml/cache/stats
```

### A/B Test Not Routing

**Symptoms**: All users getting same variant

**Checks**:
1. Is experiment enabled? Check `ml.ab-test.enabled=true`
2. Is experiment active? Check start/end dates
3. Check user ID hashing

**Solution**:
```java
// Verify routing
String variant = promptRouter.routeToVariant(scenario, userId);
log.info("User {} routed to {}", userId, variant);
```

### Cost Spike

**Symptoms**: Daily cost >> $100

**Investigation**:
1. Check cost by scenario: `costTracker.getScenarioCosts(...)`
2. Look for unusual traffic patterns
3. Check cache hit rate (should be 30%+)
4. Review recent code changes

**Immediate Actions**:
1. Enable aggressive caching
2. Reduce max_tokens temporarily
3. Implement rate limiting
4. Check for infinite loops

### Slow Response Times

**Symptoms**: p95 latency > 3s

**Checks**:
1. Model selection (GPT-4 is slower)
2. Prompt length (longer = slower)
3. Max tokens setting
4. OpenAI API status

**Optimization**:
1. Use gpt-3.5-turbo for most tasks
2. Compress prompts
3. Lower max_tokens
4. Enable caching for repeated queries

---

## Configuration Reference

### application.properties

```properties
# A/B Testing
ml.ab-test.enabled=true
ml.ab-test.resume-analysis.enabled=true
ml.ab-test.question-generation.enabled=false

# Caching
ml.cache.enabled=true
ml.cache.semantic.enabled=true
ml.cache.semantic.similarity-threshold=0.95
ml.cache.max-size=1000

# Cost Tracking
ml.cost.daily-budget=100.0
ml.cost.monthly-budget=2500.0
ml.cost.alert-check-interval-minutes=15

# OpenAI
openai.api.key=${OPENAI_API_KEY}
openai.api.default-model=gpt-3.5-turbo-1106
openai.api.timeout-seconds=30
```

### Model Configurations

```java
// Recommended settings per scenario
resume_analysis:
  model: gpt-3.5-turbo-1106
  temperature: 0.5
  max_tokens: 400

question_generation:
  model: gpt-3.5-turbo-1106
  temperature: 0.7
  max_tokens: 300

answer_evaluation:
  model: gpt-3.5-turbo-1106
  temperature: 0.3
  max_tokens: 350
```

---

## Maintenance

### Daily
- Check budget alerts
- Monitor cache hit rates
- Review cost by scenario

### Weekly
- Analyze A/B test results
- Review token usage trends
- Check for cost anomalies

### Monthly
- Run model benchmarks
- Review and update prompts
- Optimize based on data
- Update budget allocations

---

## Additional Resources

- [OpenAI Best Practices](https://platform.openai.com/docs/guides/best-practices)
- [Prompt Engineering Guide](https://www.promptingguide.ai/)
- [Model Benchmark Report](../../eval/model_benchmarking/results/benchmark_report_20260206.md)
- [Golden Dataset](../../eval/golden_dataset/README.md)

---

**Last Updated**: 2026-02-06  
**Version**: 1.0  
**Maintained By**: AI Performance Team
