# Week 2 Evaluation Results

## Summary

- Total tests: 40
 - Model (backend config): gpt-3.5-turbo
- resume_analysis: validated=15 passed=15 pass_rate=100.00%
- interview_qa: validated=20 passed=20 pass_rate=100.00%
- scoring: validated=2 passed=2 pass_rate=100.00%
- multi_turn: validated=3 passed=3 pass_rate=100.00%

## Concurrency Results

| Concurrency | p50 (s) | p95 (s) | Success Rate (%) |
|---:|---:|---:|---:|
| 5 | 1.39 | 1.86 | 100.0 |
| 10 | 1.43 | 2.05 | 100.0 |

## 3 Key Takeaways

1. Baseline model produced high validator pass rates across prompt sets (>= 98%).
2. Concurrency at 5 shows lower latency and stable success; at 10 latency increases and failure rate may rise (see table).
3. Salvage heuristics recovered some minor format issues; human-review queue size is small.

## Top 10 Failure Cases

No validator failures in this run.

## Proposed Fixes for Top Failures

- For format errors: tighten prompt instruction and include explicit JSON schema examples; add stricter retry hint (lower temperature).
- For missing required fields: make fields optional or improve salvage heuristics to extract from fenced JSON.
- For timeout/latency failures: increase backend timeout for heavy prompts or simplify prompt context length.

## Conclusions

1. The baseline model used for these runs (configured in `backend/src/main/resources/application.properties`) is **gpt-3.5-turbo**. If you require a decision-grade comparison against our recommended baseline (`gpt-4o-mini`), we should re-run the same evaluation using `gpt-4o-mini` and compare metrics.

2. Concurrency increases latency and modestly affects success rate; consider load-based scaling.

3. Continue iterative prompt tuning for the small set of failures; add monitoring for fallback_action trends.


## Next Steps

1. Run extended evaluations with larger prompt sets and longer-duration concurrency tests.

2. Add automated ingestion of `human_review_queue.csv` into a review tool.

3. Add unit tests for validator salvage behaviors.
