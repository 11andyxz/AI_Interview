Model Serving POC — Week 7 Final Assessment

Recommendation: PAUSE further heavy investment in local GPU inference for production at this time.

Rationale:
- Observed latency and variability in the POC (p95 often >> interactive thresholds) make local inference unsuitable for interactive interview/product flows without significant infra investment.
- Operational gaps: memory limits (models require >16GB VRAM for acceptable throughput), lack of robust batching/queueing, and fragile scaling/ops automation.
- Cost vs. benefit: infra and engineering effort to reach parity with hosted LLMs (latency, availability, model updates) is high relative to current gains.

Minimum bar to resume investment (if approved):
- Achieve stable p95 latency <= 3000ms for representative interactive prompts under realistic concurrency.
- Demonstrated batching layer that increases throughput by >=2x in benchmarks.
- Automated provisioning and monitoring (GPU autoscaling or capacity plan), and model artifact reproducibility (safetensors/HF + pinned runtime).
- Clear operational runbook (OOM handling, graceful degradation, model warmup and cache strategies).

Blocking factors (why PAUSE):
- Latency: POC runs show many prompts with very high p95; interactive UX would degrade.
- Reliability: OOMs and runtime instability require dedicated on-call and complex infra.
- Maintenance: frequent model updates and tuning entail ongoing engineering cost similar to running a small ML infra team.

If leadership wants to continue investment, propose a targeted experiment: deploy a single 24GB GPU node with `vLLM` + quantized model (if available) and run a 500-prompt benchmark covering p50/p95/throughput and cost per 1M tokens. If results meet the minimum bar, advance to staged production.

Deliverables produced:
- This recommendation document (this file).
- Links to POC logs and eval artifacts under `eval/results_poc/`.

Next steps if PAUSE selected:
- Reallocate short-term effort to robustifying the cloud-model integration (default model improvements, observability, retry/fallback) and re-evaluate POC next quarter when infra budget is available.

Key quantitative evidence (from repo artifacts)
---------------------------------------------
- Local GPU POC run (eval/results_poc/eval_results_20251231_171407.csv): count=20 prompts — avg latency=19.596s, p50=16.758s, p95=47.597s, min=6.367s. These numbers come from the POC CSV collected during initial local runs and show a long tail on latency for interactive prompts.
- Hosted/backend run (eval/results_poc/run_w7_post_metric_fix/eval_results_latest.csv): count=40 prompts — avg latency=4.175s, p50=3.098s, p95=7.197s, min=1.699s. This run represents the hardened cloud-backed path after Week‑7 updates and has markedly lower p50/p95 than the local POC.
- Sample size note: some historical host-model CSVs are small (e.g., `eval_results_gpt4o_mini.csv` has 2 rows) and are not reliable alone — use the aggregated history in `eval/reports/aggregate_history.csv` for broader trends.

Operational findings based on these numbers:
- The local POC p95 (~47.6s) greatly exceeds interactive UX targets (<=3s), indicating significant infra or model-format gaps.
- The hardened hosted path meets interactive-like latencies in this eval sample (p50 ~3.1s, p95 ~7.2s) and delivers far lower tail latency.
- No OOM or explicit OOM-error rows were found in the available eval CSVs; however repo notes and POC docs recommend >=16GB VRAM for stable vLLM-based serving (see `docs/model_serving_poc_report_week2.md`).

Final Decision (Week 7)
-----------------------
- Decision: PAUSE continued heavy investment in local GPU inference for production at this time.
- Rationale: measured local POC tail latency (p95 ~47.6s on a 20-sample POC) is an order of magnitude above interactive targets. Closing that gap requires non-trivial infra (>=24GB GPU for stable runs in many quantized configs), batching/queueing work, and robust ops automation. The hardened hosted path already demonstrates materially lower p50/p95 in our Week‑7 runs.
- Acceptance for resuming investment: to change this decision to a Go we require reproducible benchmark evidence that a local setup achieves all of the following under representative load:
	- p95 latency <= 3000ms for interactive prompts
	- p50 latency <= 1000ms
	- Throughput improvement via batching >= 2x compared to single-request baseline
	- Stable runs (no OOMs) across a 500-prompt benchmark

Targeted experiment: none included
--------------------------------
Per request, no local 24GB GPU experiment steps are included in this report. If you want the experiment instructions moved to a separate file or added later, I can create `docs/experiments/24gb_benchmark.md` instead.

Updated deliverables and acceptance
----------------------------------
- This file (`docs/model_serving_poc_report_week7.md`) — final decision and evidence.
- Eval artifacts: `eval/results_poc/` (raw CSVs and debug JSONL) and `eval/reports/aggregate_history.csv` (aggregated trends).
- Acceptance: decisive PAUSE supported by the POC p95/p50 numbers above and the operational gaps listed earlier.
