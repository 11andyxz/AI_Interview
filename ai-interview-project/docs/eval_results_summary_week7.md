# Evaluation Results Summary — Week 7

This document summarizes the decision-grade evaluation run saved in eval/results_poc (file: eval_results_latest.csv).

## Overall Summary

- **Total prompt types**: 1

## Results by Prompt Type

| Prompt Type | Tests | Success | Avg Latency (ms) | p50 (ms) | p95 (ms) | Avg Tokens | Validated | Passed | Validator Pass Rate | Retries | |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| interview_qa | 20 | 20 | 19596.34 | 17163.95 | 47597.43 | 84 | 20 | 20 | 100% | 0 |

## Key Takeaways

- Validator pass rates meet the acceptance threshold (>= 98%) across evaluated prompt types.
- Retry behavior: single automatic retry was used where validators initially failed; retry rates are recorded per prompt type above.
- Latency: p95 latency varies by prompt type; consider adjusting timeout or concurrency strategy for high p95 endpoints.

## Next Steps

- Roll the reliable-output parsing and validator wiring to the next endpoint in the rollout plan if validator pass rates remain >= 98%.
- If validator pass rates fall below threshold for any prompt type, collect failing requestIds from logs and inspect model outputs and prompt templates.
- Track retry rate week-over-week to ensure it does not increase after rollout.
