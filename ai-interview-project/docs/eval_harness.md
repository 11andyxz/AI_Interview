# AI Interview Evaluation Harness

## Purpose

Repeatable testing framework for comparing AI models (OpenAI vs OSS alternatives). Tracks performance metrics over time and enables regression testing.

**Location**: `ai-interview-project/eval/`

## Structure

```
eval/
├── prompts/                   # Test cases in JSONL format
│   ├── resume_analysis.jsonl # 10 resume tasks
│   ├── interview_qa.jsonl    # 10 interview questions
│   └── multi_turn.jsonl      # 3 conversation scenarios
├── results/                   # Generated reports (gitignored)
│   ├── eval_results_*.csv    # Detailed metrics
│   └── eval_report_*.md      # Summary
├── run_eval.py               # Main runner script
└── README.md                 # Usage guide
```

## How It Works

1. Loads test prompts from JSONL files
2. Authenticates with backend (JWT token)
3. Calls backend APIs with test payloads
4. Records latency, tokens, success/failure
5. Generates CSV + Markdown reports

## Prompt Format

JSONL (one JSON object per line):

```json
{
  "id": "RS-01",
  "task_type": "resume_summary",
  "difficulty": "medium",
  "prompt": "Summarize the following resume...",
  "input_context": "Resume text: 3-5 years Java, Spring Boot...",
  "expected_behaviors": ["Accurate", "Highlights key tech", "No exaggeration"],
  "evaluation_criteria": ["relevance", "completeness", "clarity"]
}
```

**Test categories**:
- `resume_analysis` (10): Resume summarization, analysis → `/api/llm/eval`
- `interview_qa` (10): Single-turn questions → `/api/llm/question-generate`
- `multi_turn` (3): Conversation flows → `/api/llm/question-generate`

**Evaluation metrics**:
- **Performance**: Latency (p50/p95), token usage, success rate
- **Quality** (Rubric-based scoring):
  - Overall quality score (0-100): Weighted average
  - Completeness (30%): Response length adequacy
  - Format compliance (20%): Basic structure checks (punctuation, capitalization)
  - Factuality (30%): Flags uncertainty markers ("maybe", "I think", etc.)
  - Coherence (20%): Sentence completeness and flow

## Current Baseline

**OpenAI GPT-3.5-turbo** (Dec 22, 2025):
- Success: 23/23 (100%)
- Avg latency: 1.44s, p95: 1.94s
- Avg tokens: 93/test
- **Quality**: 92.9/100 (Completeness: 88.9, Format: 83.0, Factuality: 100, Coherence: 100)

See [eval_results_week1.md](./eval_results_week1.md) for details.

## Usage

**Prerequisites**:
```bash
# Start backend first
cd ai-interview-project/backend
mvn spring-boot:run

# Python with requests library (Anaconda has it pre-installed)
```

**Run evaluation**:
```bash
cd ai-interview-project/eval

# Test via backend (OpenAI GPT-3.5-turbo)
python run_eval.py

# Test local OSS model (Ollama)
python run_eval.py --model-type local --local-model-name llama2
```

**With options**:
```bash
# Backend mode (default)
python run_eval.py \
  --backend http://localhost:8080 \
  --prompts-dir prompts \
  --output results \
  --username test \
  --password 123456

# Local OSS model mode
python run_eval.py \
  --model-type local \
  --local-model-url http://localhost:11434/api/generate \
  --local-model-name mistral \
  --output results_mistral
```

**Supported local models**:
- Ollama: llama2, mistral, codellama, etc. (http://localhost:11434/api/generate)
- vLLM: Any loaded model with OpenAI-compatible API
- LocalAI: GPT4All, Llama.cpp models

**Windows with Anaconda**:
```powershell
& D:\ProgramData\anaconda3\python.exe run_eval.py `
  --prompts-dir prompts --output results `
  --username test --password 123456
```

## Authentication

- Default user: `test` / `123456` 
- Endpoint: `POST /api/auth/login`
- Returns JWT token (valid ~24h)
- Token goes in `Authorization: Bearer <token>` header

## What Gets Measured

**Performance**:
- Latency (avg, p50, p95)
- Token usage
- Success/failure rate
- HTTP status codes

**Not yet implemented**:
- Quality scoring (response appropriateness)
- Multi-turn completeness (only tests first turn)
- Actual token counts (using char/4 estimate now)

## Troubleshooting

**401 errors**: Check credentials (`test`/`123456`), restart backend

**Connection refused**: Backend not running (`mvn spring-boot:run`)

**Files not found**: Run from `eval/` directory

**Timeouts**: Check OpenAI API key in database, verify network

**Encoding errors**: Script uses ASCII now, should work on Windows

## Next Steps

**Immediate**:
- Add quality scoring (rubric-based checks)
- Test against local OSS model
- Compare results side-by-side

**Future**:
- Full multi-turn conversation testing
- Load testing (concurrent requests)
- Streaming evaluation
- Cost tracking

## Design Notes

**Why Python**: Standard for ML evaluation, lightweight, separate from production code

**Why JSONL**: Git-friendly diffs, stream-friendly, easy to parse

**Why CSV + Markdown**: CSV for analysis/plotting, Markdown for human review

---

See also:
- [eval/README.md](../eval/README.md) - Detailed usage
- [eval_results_week1.md](./eval_results_week1.md) - Baseline results
