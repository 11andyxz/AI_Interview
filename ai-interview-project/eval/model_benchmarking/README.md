# Model Benchmarking Suite

Comprehensive benchmarking tools for comparing OpenAI models and configurations.

## Quick Start

```bash
# Set your OpenAI API key
export OPENAI_API_KEY="your-api-key"

# Run full benchmark
python run_benchmark.py

# Run specific benchmark
python run_benchmark.py --models-only
python run_benchmark.py --temperature-only
python run_benchmark.py --tokens-only
```

## What Gets Benchmarked

### 1. Model Comparison
- gpt-3.5-turbo-1106 (recommended)
- gpt-3.5-turbo-16k (longer context)
- gpt-4-turbo-preview (quality baseline)

**Metrics:**
- Latency (p50, p95, p99)
- Token usage (input + output)
- Cost per 1K requests
- Sample responses for quality review

### 2. Temperature Testing
Tests: 0.3, 0.5, 0.7, 0.9

**Purpose:**
- 0.3: Deterministic, consistent (good for evaluation)
- 0.5: Balanced with slight variation
- 0.7: Default, good mix of creativity and consistency
- 0.9: Creative, diverse outputs

### 3. Max Tokens Optimization
Tests: 300, 500, 800, 1000

**Purpose:**
- Find optimal token limit for each use case
- Minimize cost while avoiding truncation
- Balance completeness vs verbosity

## Test Data

The benchmark uses your golden dataset by default:
- Loads samples from `../golden_dataset/`
- Tests across multiple categories
- Falls back to default prompts if dataset unavailable

## Output

Generates two files:
1. `results/benchmark_report_YYYYMMDD.md` - Human-readable report
2. `results/benchmark_report_YYYYMMDD.json` - Raw data for analysis

## Interpreting Results

### Latency
- p50 (median): Typical request time
- p95: 95% of requests faster than this
- p99: 99% of requests faster than this

**Targets:**
- Resume analysis: <2s p95
- Question generation: <1.5s p95
- Answer evaluation: <2s p95

### Cost
Cost per 1K requests (estimated):
- gpt-3.5-turbo: $2-3
- gpt-4-turbo: $7-10

**Monthly projection:**
- 10K requests/day = $600-900/month (gpt-3.5)

### Quality
Review sample responses in report:
- Check for completeness
- Verify JSON structure
- Assess accuracy

## Customization

Edit `run_benchmark.py` to:
- Add more models
- Change test parameters
- Modify metrics tracked
- Customize report format

## Best Practices

1. **Run during low traffic** - Avoid production impact
2. **Use representative prompts** - Test with real workload
3. **Sufficient samples** - At least 30 per configuration
4. **Multiple runs** - Average results over 2-3 runs
5. **Track over time** - Compare results monthly

## Example Results

```
Model: gpt-3.5-turbo-1106
├─ Latency (p95): 1.8s
├─ Avg tokens: 380
├─ Cost per 1K: $2.40
└─ Quality: Excellent

Model: gpt-4-turbo
├─ Latency (p95): 3.2s
├─ Avg tokens: 420
├─ Cost per 1K: $7.20
└─ Quality: Outstanding

Recommendation: gpt-3.5-turbo-1106 for 95% of workload
```

## Troubleshooting

**"No test prompts available"**
- Ensure golden dataset exists
- Check path in script
- Verify JSON format

**"OpenAI API key required"**
- Set OPENAI_API_KEY environment variable
- Check API key validity

**"Rate limit exceeded"**
- Add delays between requests
- Reduce sample size
- Check OpenAI account limits
