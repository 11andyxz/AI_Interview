# Model Serving POC Report — Week 7

**Summary**
- POC objective: evaluate local GPU/OSS model feasibility vs hosted `gpt-4o-mini` for production interview workloads.
- Short recommendation: PAUSE local GPU migration. Continue using hosted `gpt-4o-mini` as default and iterate POC with larger GPU resources and further tuning.

Evidence (high level)
- Quality gap: hosted model output quality and multi-turn coherence remain clearly better in human-evaluated samples.
- Latency: local CPU/GPU runs show high and variable p50/p95 latencies (see `docs/eval_results_summary_week7.md`), increasing the risk for user-facing timeouts.
- Operational cost: local GPU infra introduces capital and maintenance costs; break-even requires sustained high volume.

Key metrics & decision rationale
- Validator pass rates: validated endpoints meet acceptance on hosted runs (see eval summary), but local POC validator stability and retry behavior are inconsistent.
- Latency percentiles: local p95 is substantially higher than hosted; p50 also often exceeds acceptable UX bounds.
- Retry stability: local retried requests are higher and less predictable across runs.

Actionable next steps
1. Pause broad migration; maintain hosted `gpt-4o-mini` for production.
2. Continue targeted POC experiments: test with >= A100-class GPU, vLLM/accelerated runtimes, and dedicated benchmarking for p50/p95 under load.
3. If POC experiments show clear parity in both quality and latency with acceptable cost, re-evaluate Go/No‑Go.

Where to find artifacts
- Evaluation CSVs: `eval/results_poc/` and `eval/results_poc/eval_results_latest.csv`
- Decision-grade summary: `docs/eval_results_summary_week7.md`
- Debug traces: `eval/debug/` (validator pre/post JSONL)

Conclusion
- Recommendation: PAUSE local GPU migration for now; use hosted `gpt-4o-mini`, finalize the hardening checklist (`docs/reliable_ai_output_hardening_week7.md`), then re-run scoped POC with higher-grade GPUs.

