# Model Comparison Addendum

Task 3: Model serving POC upgrade and comparison

## Summary

| Model | Latency | Quality | Test Status | Viability |
|-------|---------|---------|-------------|-----------|
| **GPT-4o-mini** | 1.4s | 92.3/100 | ✅ 23/23 | ✅ Production |
| **Llama-2-7B (CPU)** | 47.8s | 91.5/100 | ✅ 23/23 | ❌ Not viable |

**Key Finding**: Local CPU 34x slower, coherence gap (-30pt). GPU required.

---

## 1. GPT-4o-mini Baseline

**Test**: 23 prompts (2025-12-22)

| Metric | Value |
|--------|-------|
| Success | 100% (23/23) |
| Latency | 1.4s avg, 2.1s p95 |
| Quality | 92.3/100 |
| - Completeness | 87.6/100 |
| - Format | 83.0/100 |
| - Factuality | 100.0/100 |
| - Coherence | 99.1/100 |

**By Type**: Resume 1.7s, Interview 1.1s, Multi-turn 1.4s

---

## 2. Llama-2-7B-Chat (CPU)

**Test**: Same 23 prompts (2025-12-23) | **Hardware**: Intel CPU

| Metric | Value | vs Baseline |
|--------|-------|-------------|
| Success | 100% (23/23) | ✅ Same |
| Latency | 47.8s avg, 108s p95 | ❌ 34x slower |
| Quality | 91.5/100 | ⚠️ Similar overall |
| - Completeness | 95.2/100 | ✅ +7.6 |
| - Format | 99.1/100 | ✅ +16.1 |
| - Factuality | 98.0/100 | ⚠️ -2.0 |
| - **Coherence** | **68.7/100** | ❌ **-30.4** |

**By Type**: Resume 36s, Interview **72s** (65x slower), Multi-turn 7.7s

**Critical Issues**:
- Interview Q&A: 72s average (unacceptable UX)
- Coherence: 68.7 vs 99.1 (impacts conversations)
- High variance: 7.7s - 117s

**Verdict**: ❌ Not viable on CPU. Requires GPU + fine-tuning.

---

## Recommendations

**Now**: Keep GPT-4o-mini (production ready)

**Next**: 
- GPU testing (target: 2-5s)
- Fine-tune for coherence
- Test Llama-3.1-8B, Qwen2.5-7B

---

## Detailed Results

See full reports:
- GPT-4o-mini: `eval/results/eval_report_20251222_202648.md`
- Llama-2-7B: `eval/results/eval_report_20251223_175342.md`
