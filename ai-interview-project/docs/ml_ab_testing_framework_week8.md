**ML A/B Testing Framework (Week 8)**

- **Goal:** Provide an end-to-end A/B testing workflow for selecting models per endpoint using production logs.

- **Router:** `backend/src/main/java/com/aiinterview/service/ModelRouterService.java` — minimal weighted routing + CSV logging. Replace CSV logging with DB persistence in production (table `model_inference_log`).

- **Evaluation harness:** `eval/advanced_eval.py` (existing) computes BLEU/ROUGE/SBERT where available. New helpers:
  - `eval/merge_metrics.py` — merges inference logs + per-example ML metrics.
  - `eval/backfill_validator_pass.py` — infers missing `validator_pass` (median-based) for demo/backfill.

- **Reporting:** `eval/generate_weekly_ml_report.py` — aggregates last 7 days of logs (`eval/model_inference_log*.csv` or `eval/ml_merged_metrics*.csv`) and writes `eval/weekly_ml_report.md` plus PNGs.

- **How to run (example):**
  - Combine historical runs: `python eval/combine_runs_for_ab.py --a <a.csv> --model_a gpt-3.5-turbo --b <b.csv> --model_b gpt-4o-mini --out eval/model_inference_log_ab.csv`
  - Merge metrics: `python eval/merge_metrics.py --logs eval/model_inference_log_ab.csv --ml eval/ml_metrics.csv --output eval/ml_merged_metrics_ab.csv`
  - Backfill validator (optional demo): `python eval/backfill_validator_pass.py --merged eval/ml_merged_metrics_ab.csv --out eval/ml_merged_metrics_ab_filled.csv`
  - Decision rule: `python eval/decision_rule.py --merged eval/ml_merged_metrics_ab_filled.csv`
  - Weekly report: `python eval/generate_weekly_ml_report.py --logs eval/model_inference_log_ab.csv --merged-dir eval/ --out-dir eval/`

- **Acceptance gaps / next steps:**
  1. Persist logs to a real DB table `model_inference_log` (migration + DAO). Current CSV logging is a conservative placeholder.
  2. Add runtime instrumentation to the router for weighted live splits and feature flags (can be done via Spring config + endpoint to update weights).
  3. Integrate production validator logic (the repo currently uses a median-based backfill for missing `validator_pass`). Provide actual rules or a lightweight validator microservice.
  4. Add continuous scheduled job (cron) to run `generate_weekly_ml_report.py` and export results to a dashboard.

## ML A/B Testing Framework — Week 8 (MVP)

### Objective
Establish a production-ready A/B testing framework to systematically compare model performance across different endpoints, enabling data-driven model selection and continuous optimization.

### MVP Scope (This Week Priority)

#### 1) Model Router Service (MVP)
- `backend/src/main/java/com/aiinterview/service/ModelRouterService.java`
- Support weighted routing (e.g., 80/20), returning model keys such as `gpt-4o-mini`, `gpt-3.5-turbo`, `gpt-4-turbo`, or `local`.
- In MVP, log inference metadata (requestId, model, latency_ms, tokens_used, estimated_cost) via CSV. Future versions will persist to `model_inference_log` table.

#### 2) Eval Harness Extensions (MVP)
- `eval/run_eval.py` supports `--model-version` parameter, writing `model_version` and `estimated_cost` to CSV (`eval_results_*.csv`) for subsequent analysis.
- `eval/advanced_eval.py`: Computes BLEU/ROUGE/SBERT similarity and outputs per-example ML metrics CSV.
- `eval/generate_weekly_ml_report.py`: Aggregates inference logs from the last 7 days, generating markdown reports and optional charts (matplotlib).

#### 3) Automated Comparison Reports (MVP)
- Generate quality-cost scatter plots, latency distribution (box plots), and basic failure mode statistics.

### How to Run (Quick Start)

1. Call evaluation or production paths with `--model-version`:

```bash
python eval/run_eval.py --backend http://localhost:8080 --output eval/results_poc/run_ab_test --model-version gpt-4o-mini
```

2. Run advanced evaluation (requires optional dependencies):

```bash
python eval/advanced_eval.py --references refs.jsonl --hypotheses hyps.jsonl --output eval/ml_metrics.csv
```

3. Generate weekly report (using `model_inference_log.csv` as example):

```bash
python eval/generate_weekly_ml_report.py --logs model_inference_log.csv --output reports/week_ml_report.md
```

### Acceptance Criteria (MVP)
- Support simultaneous comparison of 2+ models (via `--model-version` labeling in logs)
- Reports include latency distribution, quality metrics (BLEU/ROUGE/SBERT), and cost estimation
- Metrics can be computed directly from logs, output CSV supports statistical significance testing

### Next Steps (Recommendations)
- Persist `ModelRouterService.logInference()` to `model_inference_log` table (propose DB schema and implement repository layer).
- Replace cost estimation with accurate per-token pricing configuration.
- Add lightweight background queue to asynchronously write logs to DB, avoiding response latency increases.

If approved, I will continue implementing DB persistence for `ModelRouterService.logInference()` and simple table schema in the next milestone.
