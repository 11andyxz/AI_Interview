# Reliable AI Output — Hardening (Week 7)

Summary
- Goal: make model outputs reliably consumable by downstream services by applying strict output schemas, automated validators, a single deterministic retry, and observable gating for rollout.
- Acceptance criteria: validator pass >= 98% for validated prompt types; retry rates stable or decreasing; actionable logs and request-level traces for RCA.

Key components
- Prompts: explicit output schema instructions and deterministic-retry hints (low temperature) in `eval/prompts/`.
- Schemas & Validators: canonical JSON schemas in `eval/schemas/` and validators in `eval/validators/` that (a) validate, (b) perform low-risk coercions, and (c) emit `validator_error_type` and `validator_error_info`.
- Retry policy: single deterministic retry on validator failure (lower temperature). Retries are recorded per-request as `validator_retried`.

Error taxonomy (CSV field: `validator_error_type`)
- `none` — validated successfully
- `format` — malformed JSON or trailing text
- `coercion` — value type resolved via safe coercion
- `missing_required` — required field absent, not salvagable
- `invalid_value` — semantic invalid (out-of-range, contradictory)
- `internal` — validator/runner internal error

Fallback policy
- If retry produces a valid schema: mark `validator_pass=true` and `fallback_action=salvaged` (only for low-risk coercions).
- If still failing and `missing_required` or `invalid_value`: mark `validator_pass=false` and route to human review queue (`eval/results/human_review_queue.csv`).

Observability & RCA
- Per-row CSV fields: `requestId`, `endpoint`, `prompt_id`, `attempts`, `original_response`, `validator_run`, `validator_pass`, `validator_error_type`, `validator_error_info`, `validator_retried`.
- Debug dumps: `eval/debug/validator_debug_pre.jsonl` and `eval/debug/validator_debug_post.jsonl` contain full LLM outputs and post-validator payloads for failed cases.
- Alerts: configure alert when any prompt type shows `validator_pass_rate < 98%` or `retry_rate` increases > 10% week-over-week.

Rollout checklist
1. Deploy validators + retry logic to staging; run `eval/run_eval.py` against staging backend and verify `validator_pass_rate >= 98%`.
2. Smoke deploy to 1% traffic with telemetry enabled (tokens, validator pass, retry rate, p95 latency per endpoint).
3. Gradually increase to 25% → 50% → 100% if metrics remain within gating thresholds.

Quick commands
```
# Run offline eval (backend must be reachable)
python eval/run_eval.py --prompts-dir eval/prompts --output eval/results --backend http://localhost:8080
python eval/compute_validator_pass_rates.py

# Regenerate decision-grade summary (if CSV exists)
python eval/scripts/generate_eval_summary.py
```

Where to look if something breaks
- `eval/results/` and `eval/results_poc/` — CSVs and human_review_queue.csv
- `eval/debug/` — full JSONL traces for failing requests
- `logs/backend/` — requestId-linked backend logs for RCA

Notes
- This document is a living checklist; finalize specific alert thresholds and human-review SLAs before broad rollout.

