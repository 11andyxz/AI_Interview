# Model Serving POC Report

**Date**: December 20, 2025

---

## 1. Pick 1 Candidate Open-Source Model

**Llama-2-7B-Chat GGUF Q4** (~3.8GB)
- Free and open-source
- Supports fine-tuning
- Pure Python implementation (no compilation)

---

## 2. Run Small POC Behind Service Interface

**Implementation**:
- Python HTTP server (ctransformers) on port 8080
- Express proxy on port 3001 (unified for local/OpenAI/HF)
- Tested with benchmark.js and replay-conversation.js

**Status**: Working

---

## 3. Compare Output Quality + Latency vs OpenAI

**Hardware**: Intel CPU (no GPU acceleration)

| Aspect | OpenAI | Local Model |
|--------|--------|-------------|
| Latency | 1-2s | 10-40s (CPU) |
| Context Understanding | Correct | Poor |
| Multi-turn Coherence | High | Low |
| Cost | $0.002/1K tokens | Free |

**Finding**: Local model cannot replace OpenAI (quality gap too large)

**GPU Testing Note**: Attempted GPU acceleration with RTX 3060 (ctransformers[cuda]) but latency became slower (34-40s). Likely cause: ctransformers CUDA integration overhead, potential driver compatibility issues. Reverted to CPU for consistency.

---

## 4. Draft Deployment Plan

**Short-term**: Keep OpenAI (quality > cost)

**Medium-term**: GPU server + llama.cpp/vLLM (reduce latency to 2-5s)

**Long-term**: Fine-tune on collected interview data (improve quality)

**Cost Analysis**:
- OpenAI: ~$50/month (100 interviews)
- Local GPU: One-time cost + electricity
- Break-even: 500+ interviews/month

---

## Conclusion

- Technical feasibility proven (model runs)  
- Unified interface works (proxy switches backends)  
- Fine-tuning path exists

**Recommendation**: Use OpenAI now, fine-tune later with accumulated data
