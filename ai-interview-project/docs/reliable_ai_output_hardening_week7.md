Reliable AI Output — Week 7 Stability Hardening

Purpose
-------
This document captures the Week‑7 production hardening work for the AI output pipeline. It focuses on edge cases, operational observability, and measurable acceptance criteria so the system can run reliably in real traffic.

Scope
-----
- Endpoints covered: resume_analysis, interview_qa (question-generate), scoring/eval, multi_turn flows.
- Components: backend OpenAI gateway, per-endpoint prompt templates, validators, retry/fallback behavior, logging and telemetry.

Summary of recent code hardening (non-exhaustive)
-----------------------------------------------
- `OpenAiService`:
  - Added robust SSE chunk parsing using `ObjectMapper` with recursive search for `content` node and a safe string fallback.
  - Replaced ad-hoc System.err prints with structured SLF4J logging.
  - Streaming fallback: deterministic mock streaming when upstream fails.
- `ResumeAnalysisService`:
  - Replaced naive first/last-brace extraction with balanced-brace extractor that respects quotes and escapes.
  - `parseAnalysisResult` now extracts JSON before deserializing and surfaces errors as exceptions (caller-visible failures).

Key Issues Addressed
--------------------
- Partial or malformed JSON: robust JSON extraction salvages valid embedded JSON objects and avoids silent parse failures.
- Token-limit outputs: prompts and model parameters should be tuned to reduce near-limit responses; when encountered, validator will trigger a retry with lower `max_tokens`/temperature or invoke fallback.
- Noisy logs: structured log entries include `event`, `requestId`, `promptHash`, `attempt`, `model`, `errorType`, enabling RCA without re-running prompts.

Error taxonomy (streamlined)
----------------------------
- MALFORMED_JSON: Response cannot be parsed into JSON (no balanced object found).
- PARTIAL_JSON: Balanced JSON found but required fields missing or truncated.
- SCHEMA_MISMATCH: Parsed JSON is syntactically valid but fails schema validation.
- TOKEN_TRUNCATION: Response was cut off near token limit (detected by trailing ellipsis, incomplete JSON, or `stop` token absence).
- OPENAI_ERROR: Transport or API-level error (timeouts, 5xx, auth failures).
- VALIDATOR_INTERNAL: Validator raised an exception (internal error).

For each error type we record: `errorType`, `validator_error_info` (short), `attempt`, `fallback_action`.

Logging examples (newline-delimited JSON for log ingestion)
----------------------------------------------------------
- Normal success
{
  "ts":"2026-01-16T10:00:00Z",
  "event":"resume_analysis.success",
  "requestId":"req-123",
  "promptHash":"ph-abc",
  "attempt":1,
  "model":"gpt-4o-mini",
  "latency_ms":1250,
  "validator_pass":true
}

- Retry then success
{
  "ts":"2026-01-16T10:01:00Z",
  "event":"interview_qa.retry_success",
  "requestId":"req-456",
  "promptHash":"ph-def",
  "attempt":2,
  "model":"gpt-4o-mini",
  "latency_ms":2450,
  "validator_pass":true,
  "retry_reason":"SCHEMA_MISMATCH",
  "previous_error":"SCHEMA_MISMATCH"
}

- Fallback triggered (salvage/human_review)
{
  "ts":"2026-01-16T10:02:10Z",
  "event":"scoring.fallback",
  "requestId":"req-789",
  "promptHash":"ph-ghi",
  "attempt":2,
  "model":"gpt-4o-mini",
  "validator_pass":false,
  "fallback_action":"human_review",
  "errorType":"PARTIAL_JSON",
  "errorInfo":"Missing field 'summary'"
}

Prompt & schema refinements (targeted)
--------------------------------------
- Keep instructions explicit: require `Return ONLY valid JSON` and provide a short example JSON object in the system message.
- Reduce max_tokens for schema outputs and request a strict `max_tokens` margin: e.g., set `max_tokens` to estimated schema tokens + 100 guard.
- Add a post-processing instruction requesting the model to wrap the JSON with a unique marker (optional) to help extraction, e.g.:
  "Return the JSON object between special markers: <<<JSON>>> ... <<<END_JSON>>>"
  (Only use when allowed by prompt policy and model behavior.)
- Avoid asking for long textual summaries inside the same schema object; move long prose into a separate endpoint if needed.

Validator strategy
------------------
- For each schema-validated endpoint run: `validator_run=true` first; if `validator_pass=false` then perform a single retry with adjusted LLM params (lower temperature, hint: "Return only valid JSON, no commentary").
- If retry fails and salvage info is present (e.g., salvaged JSON fields returned by validator), and `fallback_mode==salvage`, accept salvaged response; otherwise escalate to `human_review`.

Rollout plan (high level)
-------------------------
1. Staged rollout to endpoints in this order: `resume_analysis` → `interview_qa` → `scoring` → `multi_turn`.
2. For each endpoint:
   - Deploy OpenAiService parsing improvements.
   - Deploy strict prompt templates and validator wiring.
   - Enable structured logging fields (`requestId`, `promptHash`, `attempt`, `errorType`).
   - Run decision‑grade eval (30–50 prompts) and compute validator pass rate.
   - Gate: pass-rate ≥ 98% and retry rate not increased → promote to production traffic.

Measurement & acceptance (how to compute 98% pass rate)
--------------------------------------------------------
- Use the eval harness `eval/run_eval.py` to run 30–50 representative prompts (mix resume/interview/scoring). Set `fallback_mode='salvage'` or desired mode.
- After run, compute validator pass rates using `eval/compute_validator_pass_rates.py` on the produced CSV. Target: per-prompt_type pass_rate ≥ 98%.
- Also compute retry rate: count prompts where `validator_run==true` and `validator_retried==true` divided by total validated prompts; compare to last week's baseline.

Operational playbook (short)
---------------------------
- If `MALFORMED_JSON` spike detected (>1% of traffic): enable immediate sampling for failed `requestId`s and increase `max_tokens` guard or reduce model temperature and re-run eval on sampled prompts.
- If `TOKEN_TRUNCATION` increases: lower prompt size, increase `max_tokens` guard or split the response into multiple schema calls.
- If `VALIDATOR_INTERNAL` occurs: route failures to human_review and create an incident to fix validator.

Deliverables
------------
- This document: `docs/reliable_ai_output_hardening_week7.md` (this file).
- Example log snippets (above) for ingestion into ELK/Datadog/CloudWatch.
- Prompt change rationale: `docs/prompt_changes_rationale_week2.md` (to be drafted next).

Next steps (recommended immediate actions)
----------------------------------------
1. Preserve current parsing & extractor changes and deploy to a staging environment (or run eval harness against local backend) to measure current pass rate.
2. Execute decision-grade eval with 30–50 prompts and compute pass rates.
3. If pass rate >= 98% and retry rate stable or improved, roll changes to production for the staged endpoints.

Appendix: quick commands
------------------------
Run decision-grade eval locally (example):
```bash
python eval/run_eval.py --backend http://localhost:8080 --output eval/results_poc/run_w7 --model-type backend
python eval/compute_validator_pass_rates.py
```

Run only the validator pass rate script (reads latest CSV in `eval/results`):
```bash
python eval/compute_validator_pass_rates.py
```


