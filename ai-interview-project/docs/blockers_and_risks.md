# Blockers and Risks - Week 3

## Blockers

**Week 2 Tools PR Dependency**  
- `llama_server.py` is in Week 2 PR (under review)
- Mitigation: Eval harness works with any HTTP endpoint

## Risks

**1. OSS Model Latency (HIGH)**  
- CPU: 47.8s avg (34x slower than GPT-4o-mini)
- **GPU anomaly**: RTX 3060 with ctransformers[cuda] → 34-40s (slower than CPU!)
- Possible causes: CUDA integration overhead, driver issues, or config problems
- **Next step**: Test with vLLM or llama.cpp to isolate issue
- Current decision: Continue with GPT-4o-mini in production

**2. Coherence Gap (MEDIUM)**  
Llama-2: 68.7 vs GPT: 99.1 (-30pts).  
→ Requires fine-tuning if OSS adoption considered.

**3. Validation Coverage (LOW)**  
Only ResumeAnalysisService validated.  
→ Extend to other endpoints in future sprint.

## Next Steps

1. Merge Week 2 PR
2. Test vLLM/llama.cpp (better than ctransformers)
3. Extend validation to interview/eval endpoints
