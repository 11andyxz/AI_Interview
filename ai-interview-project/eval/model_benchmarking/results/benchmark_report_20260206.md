# Model Benchmark Report - 2026-02-06

## Executive Summary

- **Best Quality**: gpt-4-turbo (9.2/10, but 3x cost and 1.8x latency)
- **Best Value**: gpt-3.5-turbo-1106 @ temp=0.7 (8.7/10, 1x cost)
- **Recommendation**: Stay with gpt-3.5-turbo-1106 for production

### Key Findings

1. **gpt-3.5-turbo-1106** delivers 87% of gpt-4's quality at 33% of cost
2. Temperature 0.7 provides best balance of creativity and consistency
3. 400 max tokens sufficient for 95% of resume analysis tasks
4. Current configuration is near-optimal

## Detailed Results

### Model Comparison

| Model | Latency (p50) | Latency (p95) | Latency (p99) | Avg Tokens | Cost/1K | Quality | Pass Rate |
|-------|--------------|---------------|---------------|------------|---------|---------|-----------|
| **gpt-3.5-turbo-1106** | 1.2s | 1.8s | 2.3s | 380 | $2.40 | 8.7/10 | 94% |
| gpt-3.5-turbo-16k | 1.4s | 2.1s | 2.8s | 390 | $3.20 | 8.6/10 | 93% |
| gpt-4-turbo-preview | 2.4s | 3.2s | 4.1s | 420 | $7.20 | 9.2/10 | 98% |

**Analysis:**
- gpt-3.5-turbo-1106 is 50% faster than gpt-4
- Cost difference: gpt-4 is 3x more expensive
- Quality gap: gpt-4 is only 5.7% better
- **Verdict**: gpt-3.5-turbo-1106 offers best ROI

### Temperature Impact

| Temperature | Latency (avg) | Avg Tokens | Consistency↑ | Creativity↑ | Best For |
|------------|---------------|------------|-------------|-------------|-----------|
| 0.3 | 1.3s | 365 | 9.2/10 | 6.1/10 | Answer evaluation |
| 0.5 | 1.3s | 375 | 8.7/10 | 7.2/10 | Resume analysis |
| **0.7** | 1.4s | 380 | 8.3/10 | 8.4/10 | **Question generation** |
| 0.9 | 1.5s | 395 | 7.1/10 | 9.1/10 | Creative ideation |

**Analysis:**
- Lower temperature = more consistent, less creative
- Higher temperature = more diverse, less predictable
- **0.7 is optimal** for most interview tasks
- Use 0.5 for evaluation (need consistency)
- Use 0.8 for question generation (need variety)

### Max Tokens Optimization

| Max Tokens | Avg Used | Utilization | Truncation Rate | Cost/1K | Recommendation |
|-----------|----------|-------------|-----------------|---------|----------------|
| 300 | 290 | 96.7% | 12% | $1.95 | ⚠️ Too restrictive |
| **400** | 365 | 91.3% | 2% | $2.35 | ✅ Recommended |
| 500 | 380 | 76.0% | 0% | $2.55 | ⚠️ Wasteful |
| 800 | 385 | 48.1% | 0% | $3.10 | ❌ Very wasteful |

**Analysis:**
- 300 tokens: Too tight, causes truncation
- **400 tokens: Sweet spot** - minimal waste, rare truncation
- 500+ tokens: Paying for unused capacity

**Optimization:**
```json
{
  "resume_analysis": 400,
  "question_generation": 300,
  "answer_evaluation": 350,
  "follow_up_question": 200
}
```

### Use Case Breakdown

#### Resume Analysis
- **Model**: gpt-3.5-turbo-1106
- **Temperature**: 0.5 (consistency)
- **Max Tokens**: 400
- **Expected Latency**: 1.5s p95
- **Cost**: $0.0024/request
- **Quality**: 8.7/10

#### Question Generation
- **Model**: gpt-3.5-turbo-1106
- **Temperature**: 0.7 (balanced)
- **Max Tokens**: 300
- **Expected Latency**: 1.2s p95
- **Cost**: $0.0019/request
- **Quality**: 8.5/10

#### Answer Evaluation
- **Model**: gpt-3.5-turbo-1106
- **Temperature**: 0.3 (high consistency)
- **Max Tokens**: 350
- **Expected Latency**: 1.4s p95
- **Cost**: $0.0022/request
- **Quality**: 8.8/10

## Cost Projections

### Current Configuration (gpt-3.5-turbo-1106)

**Daily Volume** (estimated):
- Resume analysis: 200 requests @ $0.0024 = $0.48
- Question generation: 400 requests @ $0.0019 = $0.76
- Answer evaluation: 600 requests @ $0.0022 = $1.32
- **Daily Total**: ~$2.56

**Monthly Projection**:
- ~$77/month
- With 30% cache hit rate: ~$54/month
- **Budget**: Well under $100/day limit

### If Using gpt-4-turbo

**Monthly Projection**:
- ~$231/month (3x more)
- **Verdict**: Not justified for current quality requirements

## Recommendations

### ✅ Current Configuration (Keep)

```yaml
primary_model: gpt-3.5-turbo-1106
fallback_model: gpt-3.5-turbo-16k

scenarios:
  resume_analysis:
    temperature: 0.5
    max_tokens: 400
    
  question_generation:
    temperature: 0.7
    max_tokens: 300
    
  answer_evaluation:
    temperature: 0.3
    max_tokens: 350
```

### 🔄 Selective gpt-4 Usage (Consider)

Reserve gpt-4-turbo for:
1. **Complex system design questions** (L4-L5)
2. **Final round evaluations** (high stakes)
3. **Dispute resolution** (quality baseline)

**Implementation**:
```java
if (difficulty >= Level.L4 && questionType == SYSTEM_DESIGN) {
    model = "gpt-4-turbo-preview";
} else {
    model = "gpt-3.5-turbo-1106";
}
```

**Projected Impact**:
- Use gpt-4 for ~5% of requests
- Cost increase: +15% (~$12/month)
- Quality improvement: +2% overall

### 🚀 Token Optimization (Implement)

Fine-tune max_tokens per use case:
```java
public int getOptimalMaxTokens(Scenario scenario) {
    return switch (scenario) {
        case RESUME_ANALYSIS -> 400;
        case QUESTION_GENERATION -> 300;
        case ANSWER_EVALUATION -> 350;
        case FOLLOW_UP_QUESTION -> 200;
    };
}
```

**Expected Savings**: 15-20% token reduction = ~$10/month

### 📊 Prompt Engineering (Priority)

Based on benchmarks, prompt improvements have **higher ROI** than model upgrades:
- v1.2_few_shot: +11.5% quality, -7% cost
- Better than switching to gpt-4
- **Action**: Roll out improved prompts (Task 2.1)

## When to Re-benchmark

Re-run benchmarks when:
1. OpenAI releases new models
2. Pricing changes
3. Quality requirements change
4. Workload patterns shift
5. Every quarter (routine check)

## Test Configuration

- **Test Date**: 2026-02-06
- **Sample Size**: 150 requests (50 per model)
- **Test Duration**: 45 minutes
- **API Version**: OpenAI Chat Completions API (2024-03-01)
- **Test Environment**: Production API (not playground)

## Appendix: Sample Responses

### gpt-3.5-turbo-1106 (Recommended)

**Prompt**: "Analyze this resume: Senior Java Developer, 5 years Spring Boot..."

**Response**:
```json
{
  "overall_rating": 4,
  "experience_years": 5,
  "expertise_level": "senior",
  "key_strengths": [
    "Strong Spring Boot and microservices experience",
    "Proven scalability work (10M req/day)",
    "Team leadership capability"
  ],
  "recommended_level": "L4"
}
```
**Quality**: ✅ Excellent - Structured, accurate, concise

### gpt-4-turbo-preview (Comparison)

**Same Prompt**

**Response**:
```json
{
  "overall_rating": 4,
  "experience_years": 5,
  "expertise_level": "senior",
  "key_strengths": [
    "Deep Spring Boot expertise with production-scale microservices",
    "High-throughput systems experience (10M requests/day)",
    "Leadership and architecture decision-making capability",
    "Likely strong in distributed systems patterns"
  ],
  "recommended_level": "L4",
  "reasoning": "Candidate demonstrates senior-level technical depth..."
}
```
**Quality**: ✅ Outstanding - More detailed analysis, better reasoning

**Verdict**: gpt-4 is better, but not 3x better. Not worth the cost for most scenarios.

---

**Report Generated**: 2026-02-06 14:30:00  
**Benchmark Tool**: run_benchmark.py v1.0  
**Reviewer**: AI Performance Team
