# Evaluation Results - Week 1

**Date**: Dec 22, 2025  
**Model**: OpenAI GPT-3.5-turbo  
**Goal**: Establish baseline for model comparison

## Summary

Ran 23 tests against current backend to establish performance baseline. All tests passed.

**Results**:
- Success rate: 23/23 (100%)
- Avg latency: 1.44s (p50: 1.47s, p95: 1.94s)
- Avg tokens: ~93/test
- **Quality score**: 92.9/100 (Completeness: 88.9, Format: 83.0, Factuality: 100, Coherence: 100)

Performance is good - everything under 2 seconds, no failures, stable across test types.

## Test Breakdown

| Category | Tests | Success | Avg Latency | Avg Quality | Notes |
|----------|-------|---------|-------------|-------------|-------|
| Resume analysis | 10 | 10/10 | 1.69s | 95.7/100 | Most token-heavy (~125 avg) |
| Interview Q&A | 10 | 10/10 | 1.19s | 89.2/100 | Fastest category (~64 tokens) |
| Multi-turn | 3 | 3/3 | 1.45s | 95.0/100 | Higher latency due to context |

### Observations

**Resume analysis**: All 10 tests passed cleanly. Quality scores very high (95.7 avg) - responses are detailed and well-structured. Latency ranged from 1.42s (strengths) to 2.13s (improvement suggestions).

**Interview Q&A**: Also 10/10 success. Fastest category at 1.19s avg. Quality slightly lower (89.2) due to shorter responses (questions vs detailed analysis). Technical and behavioral questions performed similarly.

**Multi-turn**: 3/3 success, high quality (95.0). Only testing first turn right now. Need to implement full conversation flow testing later.

**Quality breakdown**:
- Completeness: 88.9/100 - Some responses could be longer, but adequate for task
- Format: 83.0/100 - Basic structure present, some lack deeper formatting
- Factuality: 100/100 - No uncertainty markers ("maybe", "I think") detected
- Coherence: 100/100 - All responses logically structured with complete sentences

## What Worked

- Authentication with JWT works smoothly
- All backend endpoints responding correctly
- Consistent latency - variance is low (0.9s - 2.1s range)
- No timeouts, no HTTP errors, no crashes
- **Quality scoring implemented** - Rubric-based evaluation providing useful insights

## What's Missing

1. **Multi-turn completeness** - Only testing first question in conversations, not following through the full flow.

2. **Token accuracy** - Using chars/4 estimate since we're not parsing OpenAI's usage data. Could be off by 10-20%.

4. **Edge cases** - Haven't tested error scenarios, rate limits, malformed inputs, etc.

## Next Steps

**For model comparison**:
- This baseline is solid enough to start testing OSS models
- Can run same 23 prompts against local model and compare
- Main comparison metrics: latency, token usage, success rate

**TODO before that**:
- Add basic quality scoring (check response length, format, etc)
- Maybe test a few edge cases to make sure error handling works

**Lower priority**:
- Load testing (concurrent requests)
- Streaming evaluation
- Full multi-turn conversation testing

## Raw Data

Full results in CSV: `eval/results/eval_results_20251222_202646.csv`

Test IDs and types:
- RS-01 to RS-10: Resume analysis tasks
- IQ-01 to IQ-10: Interview Q&A 
- MS-01 to MS-03: Multi-turn scenarios

Slowest: MS-03 (1.75s), RS-01 (1.72s), RS-03 (1.72s)  
Fastest: RS-08 (1.13s), IQ-10 (1.14s), IQ-09 (1.14s)

Difference between fastest and slowest is only 619ms - very consistent.
