# POC Addendum — Slow / Edge Cases and Proposed Fixes

This addendum supplements `model_serving_poc_report_week2.md` with the slow/edge cases observed in the 20-prompt POC and pragmatic remediation suggestions. No code was executed; this is documentation only.

Top slow tests (POC, sorted by latency desc):

| ID | Latency (ms) |
|----|--------------:|
| IQ-14 | 52601.03 |
| IQ-20 | 47597.43 |
| IQ-11 | 28409.27 |
| IQ-19 | 26434.76 |
| IQ-18 | 25010.65 |
| IQ-01 | 24230.82 |
| IQ-12 | 23644.18 |
| IQ-13 | 20806.65 |
| IQ-05 | 17564.29 |
| IQ-02 | 17163.95 |

Notes:
- Schema pass rate for the POC was 100% (no validator failures).
- Latency numbers are from `eval/results_poc/eval_results_20251231_171407.csv`.

Proposed mitigations (ranked by likely impact):

- Tune generation parameters for latency-sensitive paths: reduce `max_tokens`, lower `temperature`, and add `stop` tokens when output length is bounded.
- Apply request-level timeouts and an early-abort policy for interactive UIs; return a short fallback answer and provide a link to fetch the full response asynchronously.
- Warm-up model on startup with a few short prompts to reduce first-request overhead.
- Enable batching of concurrent requests at the inference layer to amortize per-request overhead.
- For production/interactive workloads, prefer larger VRAM GPUs (>=16 GB) to avoid offloading and to run high-performance runtimes like `vLLM`.
- Consider converting the model to HF/safetensors and benchmarking `vLLM` or Triton on appropriate hardware to compare latency/throughput.
- Add an inference cache for templated prompts and common sessions to avoid repeated full-model runs.

Testing note: apply these mitigations incrementally and measure p50/p95 after each change to quantify improvements.
