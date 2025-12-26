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

## Next Steps

1. **Add LLM-as-Judge**: Implement automated quality scoring
2. **Model Comparison**: Test against OSS models
3. **Streaming Tests**: Evaluate SSE endpoint performance
4. **Load Testing**: Concurrent request handling
