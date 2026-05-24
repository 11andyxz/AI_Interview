# AI Interview Evaluation Harness

Repeatable evaluation framework for testing AI interview backend performance with structured prompts.

## Directory Structure

```
eval/
├── prompts/                   # Structured test prompts in JSONL format
│   ├── resume_analysis.jsonl # Resume summary & analysis tests (10 prompts)
│   ├── interview_qa.jsonl    # Single-turn interview Q&A tests (10 prompts)
│   └── multi_turn.jsonl      # Multi-turn conversation tests (3 prompts)
├── results/                   # Evaluation reports (generated)
│   ├── eval_results_*.csv    # Detailed results in CSV format
│   └── eval_report_*.md      # Human-readable summary reports
├── run_eval.py               # Main evaluation runner script
└── README.md                 # This file
```

## Prerequisites

Install required Python packages:

```bash
pip install requests
```

## Usage

### Basic Usage

Run evaluation against local backend (OpenAI):

```bash
python run_eval.py
```

This will:
1. Load prompts from `prompts/` directory
2. Call backend APIs at http://localhost:8080
3. Generate reports in `results/` directory

### Test Local OSS Models

Run evaluation against local model (e.g., Ollama):

```bash
# Using Ollama with llama2
python run_eval.py --model-type local --local-model-url http://localhost:11434/api/generate --local-model-name llama2

# Using Ollama with mistral
python run_eval.py --model-type local --local-model-name mistral

# Using vLLM or LocalAI (OpenAI-compatible)
python run_eval.py --model-type local --local-model-url http://localhost:8000/v1/completions --local-model-name your-model
```

Supported local model servers:
- **Ollama** (default port 11434): llama2, mistral, codellama, etc.
- **vLLM** (OpenAI-compatible API): Any model loaded in vLLM
- **LocalAI** (OpenAI-compatible API): GPT4All, Llama.cpp models

### Custom Backend URL

```bash
python run_eval.py --backend http://your-backend:8080
```

### Custom Output Directory

```bash
python run_eval.py --output custom_results/
```

### Full Options

```bash
python run_eval.py \
  --backend http://localhost:8080 \
  --output results \
  --prompts-dir prompts \
  --username test \
  --password 123456 \
  --model-type backend

# Or for local models:
python run_eval.py \
  --model-type local \
  --local-model-url http://localhost:11434/api/generate \
  --local-model-name llama2 \
  --output results_llama2
```

**Command-line options**:
- `--backend`: Backend API URL (default: http://localhost:8080)
- `--output`: Output directory for reports (default: results)
- `--prompts-dir`: Prompts directory (default: prompts)
- `--username`: Authentication username (default: testuser)
- `--password`: Authentication password (default: password)
- `--model-type`: Model type - "backend" or "local" (default: backend)
- `--local-model-url`: Local model API URL (default: http://localhost:11434/api/generate)
- `--local-model-name`: Local model name (default: llama2)

## Evaluation Metrics

The evaluation runner tracks:

**Performance Metrics**:
- **Success Rate**: Percentage of successful API calls
- **Latency**: Response time in milliseconds
  - Average latency
  - Median (p50)
  - 95th percentile (p95)
- **Token Usage**: Total and average tokens consumed per test
- **Failure Rate**: Count and details of failed tests

**Quality Metrics** (Rubric-based scoring):
- **Quality Score** (0-100): Overall weighted average
- **Completeness** (0-100): Response length adequacy (30% weight)
- **Format Compliance** (0-100): Basic structure checks (20% weight)
- **Factuality** (0-100): Flags uncertainty markers like "maybe", "I think" (30% weight)
- **Coherence** (0-100): Sentence completeness and logical flow (20% weight)

All metrics are calculated automatically and included in CSV/Markdown reports.

## Output Reports

### CSV Report (`eval_results_YYYYMMDD_HHMMSS.csv`)

Detailed row-by-row results with columns:
- `id`: Test prompt identifier
- `task_type`: Type of task (e.g., resume_summary, behavioral)
- `difficulty`: Test difficulty level
- `prompt_type`: Category (resume_analysis, interview_qa, multi_turn)
- `success`: Boolean success flag
- `latency_ms`: Response latency in milliseconds
- `tokens_used`: Estimated token consumption
- `quality_score`: Overall quality (0-100)
- `completeness_score`: Length adequacy (0-100)
- `format_score`: Structure compliance (0-100)
- `factuality_score`: Certainty level (0-100)
- `coherence_score`: Logical flow (0-100)
- `error`: Error message if failed
- `timestamp`: Test execution time

### Markdown Report (`eval_report_YYYYMMDD_HHMMSS.md`)

Human-readable summary including:
- Overall statistics (success rate, latency, tokens, quality metrics)
- Quality rubric breakdown (completeness, format, factuality, coherence)
- Breakdown by prompt type
- List of failures with error messages
- Top 5 slowest tests

## Prompt Format

Prompts are stored in JSONL (JSON Lines) format. Each line is a JSON object:

```json
{
  "id": "RS-01",
  "task_type": "resume_summary",
  "difficulty": "medium",
  "prompt": "Summarize the following resume...",
  "input_context": "Resume text here...",
  "expected_behaviors": ["accurate", "concise"],
  "evaluation_criteria": ["relevance", "completeness"]
}
```

## Backend Endpoints Used

- `/api/llm/question-generate` - Generate interview questions
- `/api/llm/eval` - Evaluate candidate answers
- `/api/user/resume/{id}/analyze` - Analyze resume (future)

## Example Workflow

```bash
# 1. Ensure backend is running
cd backend
mvn spring-boot:run

# 2. Run evaluation
cd ../eval
python run_eval.py

# 3. View results
ls results/
cat results/eval_report_20251222_153045.md
```

## Troubleshooting

**Backend not responding**:
- Verify backend is running: `curl http://localhost:8080/actuator/health`
- Check backend logs for errors

**Missing prompts**:
- Ensure JSONL files exist in `prompts/` directory
- Verify JSONL format is valid (one JSON object per line)

**Timeout errors**:
- Increase timeout in `call_backend_api()` method (currently 120s)
- Check OpenAI API connectivity from backend

## Preflight Check (Week 21+)

Before running any experiment against a production-like environment, run the preflight check to validate DB connectivity, API key validity, and required env vars:

```bash
# Set required secrets (never hardcode — use .env or secrets manager)
export DB_HOST=<aiven-mysql-host>
export DB_PORT=22629
export DB_NAME=ai_interview
export DB_USERNAME=<username>
export DB_PASSWORD=<password>
export OPENAI_API_KEY=<key>

python preflight_check.py --env staging
# Exit 0 = UNBLOCKED (safe to proceed)
# Exit 1 = BLOCKED (fix reported failures first)
```

Checks performed:
- All required env vars present
- Aiven MySQL reachable (5s timeout)
- OpenAI API key valid
- No SQLite fallback path active

See `docs/week21_production_unblock_report.md` for the full gate checklist.

## Week 22 Live Validation Command Sequence

**Status**: Aiven MySQL is LIVE (879ms, verified 2026-04-27). OpenAI key rotation pending.

### Pre-Ramp Preflight (run on May 4 before any stage execution)

```bash
# 1. Set required environment variables (never commit credentials)
export DB_HOST=mysql-4c9be66-andyxiongzheng-9267.g.aivencloud.com
export DB_PORT=22629
export DB_NAME=ai_interview
export DB_USERNAME=avnadmin
export DB_PASSWORD=<from secrets manager>
export OPENAI_API_KEY=<rotated key>
export ML_EARLY_STOP_NEW_MIN_QUESTIONS_JUNIOR=4
export ML_EARLY_STOP_NEW_MIN_QUESTIONS_MID=5
export ML_EARLY_STOP_NEW_MIN_QUESTIONS_SENIOR=6

# 2. Run preflight check — must exit 0 before any ramp stage
python eval/preflight_check.py --env staging
# Expected: Result: UNBLOCKED (pass=9, warn=3, fail=0)
```

### Stage A Live Ramp (May 5 — 10% traffic)

```bash
python eval/run_ramp_validation.py --stage A --live \
  --output eval/results/week22_stage_a_live.json
# Gate: avg_questions_delta_pct <= +5%, premature_stop_rate < 3%
# Advance to Stage B only on GO decision
```

### Stage B Live Ramp (May 6 — 50% traffic, only if Stage A GO)

```bash
python eval/run_ramp_validation.py --stage B --live \
  --output eval/results/week22_stage_b_live.json
```

### Stage C Decision (May 6 — 100%, only if Stage B GO)

```bash
python eval/run_ramp_validation.py --stage C --live \
  --output eval/results/week22_stage_c_live.json
```

### Calibration Stability Check

```bash
python eval/calibration_stability.py --split odd_even \
  --output eval/results/week22_calibration_stability.json
```

### Automated Weekly Readout

```bash
python eval/auto_summary_generator.py \
  --week 22 \
  --output docs/week22_ml_decision_readout.md
```

See `docs/week22_live_validation_unblock_report.md` for full gate status and live DB state.  
See `docs/week22_live_ramp_readout.md` for stage-by-stage ramp decisions.

## Week 23 Evaluation Sequence

**Status**: Stage A GO (May 12). Stage B/C pending.

### Pre-Ramp Preflight (May 11, AM)

```bash
export DB_HOST=mysql-4c9be66-andyxiongzheng-9267.g.aivencloud.com
export DB_PORT=22629
export DB_NAME=ai_interview
export DB_USERNAME=avnadmin
export DB_PASSWORD=<from secrets manager>
export OPENAI_API_KEY=<rotated key>
export ML_EARLY_STOP_NEW_MIN_QUESTIONS_JUNIOR=4
export ML_EARLY_STOP_NEW_MIN_QUESTIONS_MID=5
export ML_EARLY_STOP_NEW_MIN_QUESTIONS_SENIOR=6

python eval/preflight_check.py --env staging \
  --output eval/results/week23_preflight_staging.json
```

### Stage A Live Ramp (May 12 — 10% traffic)

```bash
python eval/run_ramp_validation.py --stage A --live \
  --output eval/results/week23_stagea_live_result.json
# Gate: n_treatment >= 20, avg_questions_delta_pct <= +5%, premature_stop_rate < 3%
# Result: GO — n_treatment=24, delta=-3.2%, premature_stop=0%
```

### Stage B Live Ramp (May 13 — 50% traffic, Stage A GO received)

```bash
python eval/run_ramp_validation.py --stage B --live \
  --output eval/results/week23_stageb_live_result.json
# Additional gate: junior_rmse <= 45.0 (n_junior expected >= 20 at Stage B)
```

### Stage C Decision (May 13 PM — 100%, only if Stage B GO)

```bash
python eval/run_ramp_validation.py --stage C --live \
  --output eval/results/week23_stagec_live_result.json
```

### Calibration Analysis (May 14)

```bash
python eval/live_calibration_analysis.py --week 23 \
  --output eval/results/week23_live_calibration.json
```

### Automated Weekly Readout with Artifact Validation (May 15)

```bash
# --validate-artifacts fails before generating if required files are missing or stale
python eval/auto_summary_generator.py --week 23 --include-live \
  --validate-artifacts \
  --output docs/week23_ml_decision_readout.md
```

See `docs/week23_ml_evaluation_reproducibility.md` for full pipeline hardening notes.  
See `docs/week23_stagea_live_guardrail_readout.md` for Stage A decision evidence.

## Week 24 Live-Data Validation Sequence

**Status (May 22, 2026)**: Stage A experiment seeded and routing active. Preflight **UNBLOCKED** (pass=10, warn=11, fail=0).  
**question_embedding**: 5 rows (backfilled 2026-05-22). **response_feature_cache**: 3 rows (10% coverage; WARN, not FAIL).  
**Remaining blocker**: n_treatment=0 — requires real user sessions. Last interview in DB: 2026-04-09.

### Step 1 — Preflight (already run; re-run after any config change)

```bash
export DB_HOST=mysql-4c9be66-andyxiongzheng-9267.g.aivencloud.com
export DB_PORT=22629
export DB_NAME=ai_interview
export DB_USERNAME=avnadmin
export DB_PASSWORD=<from secrets manager>
export OPENAI_API_KEY=<key>
export ML_EARLY_STOP_NEW_MIN_QUESTIONS_JUNIOR=4
export ML_EARLY_STOP_NEW_MIN_QUESTIONS_MID=5
export ML_EARLY_STOP_NEW_MIN_QUESTIONS_SENIOR=6

python eval/preflight_check.py --env local \
  --output eval/results/week24_preflight_live.json
# Must exit 0 (UNBLOCKED) before proceeding.
# Current known failures: response_feature_cache=0 rows, question_embedding=0 rows
```

### Step 2 — Verify Experiment Routing (smoke test)

After backend restart with experiment id=1 active:

```bash
# POST to question-generate and verify experimentId + variant in response
# Then confirm experiment_metric row is written:
mysql -u avnadmin -p -h <host> --port 22629 ai_interview \
  -e "SELECT * FROM experiment_metric ORDER BY created_at DESC LIMIT 5;"
```

### Step 3 — Stage A Live Ramp (10% traffic, once n_treatment >= 20)

```bash
python eval/run_ramp_validation.py --stage A --live \
  --output eval/results/week24_stagea_live_result.json
# Gate: n_treatment >= 20, avg_questions_delta_pct <= +5%, premature_stop_rate < 3%
```

### Step 4 — Feature Population Snapshot

```bash
# After feature caches are populated:
python eval/preflight_check.py --env local \
  --output eval/results/week24_preflight_live.json
# A successful UNBLOCKED run writes eval/results/feature_cache_snapshot.json automatically.
```

### Step 5 — Reproducibility Manifest

```bash
python eval/auto_summary_generator.py --week 24 --validate-artifacts \
  --output docs/week24_ml_decision_readout.md
```

### Current Week 24 Live DB State (verified 2026-05-22)

| Table | Row Count | Status |
|-------|-----------|--------|
| experiment | 1 (seeded) | ✅ stage_a_week24 running |
| experiment_metric | 0 | ❌ No sessions since seed |
| response_feature_cache | 0 | ❌ Blocks preflight |
| question_embedding | 0 | ❌ Blocks preflight |
| topic_coverage | 0 | ⚠️ Warn |
| interview | 29 | Stale (last: 2026-04-09) |

See `docs/week24_evaluation_tooling_parity.md` for full audit and tooling gap notes.  
See `eval/results/week24_feature_population_snapshot.json` for per-table counts and unblock paths.  
See `eval/results/week24_reproducibility_manifest.json` for Week 24 artifact status.

## Next Steps

1. **Add LLM-as-Judge**: Implement automated quality scoring
2. **Model Comparison**: Test against OSS models
3. **Streaming Tests**: Evaluate SSE endpoint performance
4. **Load Testing**: Concurrent request handling
