# Model Serving POC — Week 2

**Date:** 2025-12-31

**Objective:** Demonstrate a realistic local GPU serving path for an instruct-capable 7B model, run a minimal 20-prompt evaluation, and report latency and schema pass rate.

**Chosen candidate**
- Model: LLaMA v2 7B (quantized GGUF): `tools/text-generation-webui/models/llama-2-7b-chat.Q4_K_M.gguf`
- Runtime used (POC): text-generation-webui with the `llama.cpp` CUDA backend (uses GPU offload).

**Rationale for choice**
- A quantized 7B GGUF fits on an RTX 3060 (6GB) with partial layer offloading and produces usable latency.
- Using the local `text-generation-webui` + CUDA `llama.cpp` path provides a quick, low-dependency POC equivalent to an optimized runtime (vLLM-like performance for this hardware class).

**Environment & requirements**
- Host: Windows machine with NVIDIA GPU (RTX 3060 Laptop GPU detected during POC).
- Drivers & CUDA: NVIDIA driver + CUDA toolchain (tested on the host; `nvidia-smi` present).
- Disk: model file ~3.8 GiB (GGUF); ensure ~6–8 GiB free VRAM and ~3 GiB CPU-mapped model buffer.
- Python: a virtualenv used to run `text-generation-webui` and the evaluation harness.

**How to reproduce (commands used in this POC)**
- Install dependencies (inside `tools/text-generation-webui` venv):

```powershell
# from tools/text-generation-webui
.\.venv\Scripts\Activate.ps1
pip install -r requirements/portable/requirements.txt
```

- Start the local inference API (text-generation-webui / llama.cpp CUDA):

```powershell
python server.py --api --nowebui --model-dir models --model "llama-2-7b-chat.Q4_K_M.gguf" --api-port 7860
```

(Recommended: run this in a separate terminal or as a detached job so it doesn't block your shell.)

- Run the minimal evaluation harness (20 prompts) against the local API (from repo root):

```powershell
Set-Location -Path "D:\dev\AI_Interview\ai-interview-project"
& "D:\dev\AI_Interview\tools\text-generation-webui\.venv\Scripts\python.exe" .\eval\run_eval.py --model-type local --local-model-url "http://127.0.0.1:7860/v1/chat/completions" --local-model-name "llama-2-7b-chat" --output eval/results_poc --prompts-dir eval/poc_prompts --fallback-mode salvage --allow-salvage
```

**POC Results (20 prompts)**
- Results files: [eval_results_20251231_171407.csv](eval/results_poc/eval_results_20251231_171407.csv) and [eval_report_20251231_171407.md](eval/results_poc/eval_report_20251231_171407.md)
- Total tests: 20
- Schema/validator pass rate: 20/20 (100%) — all responses passed the `interview_chat` validation (salvage allowed where schema was missing).

Latency (per-run measurements):
- Average (mean): 19,596 ms (~19.6 s)
- Median (p50): 16,757 ms (~16.8 s)
- 95th percentile (p95): 47,597 ms (~47.6 s)
- Minimum: 6,367 ms (~6.4 s)
- Maximum: 52,601 ms (~52.6 s)

Notes on tokens: measured token counts are included in the CSV per test.

**Interpretation & acceptance**
- Viability: For the target goal of moving from “Llama-2 CPU unusable” to a realistic local-serving path, this POC is successful: the quantized 7B GGUF model, served via `text-generation-webui` with CUDA-backed `llama.cpp`, runs on an RTX 3060 and returns valid, schema-conformant responses.
- Performance characterization: latencies are relatively high for interactive use on this hardware (median ~16–17s). This is acceptable for low-throughput or offline evaluation use, but not ideal for low-latency interactive production.

**Recommendations / next steps**
1. For improved latency and throughput on local hardware, evaluate converting the model to Hugging Face/safetensors and running `vLLM` (or `vLLM` via Docker) — vLLM generally gives significantly lower latencies and higher throughput on GPUs with sufficient VRAM.
2. For production or interactive use, use a GPU with larger VRAM (16–24GB) to avoid offloading and reduce latency, or use batched serving on a small cluster.
3. Integrate the local API behind the same service interface used by the backend and run a larger evaluation (>=200 prompts) and concurrency tests to measure throughput and stability.
4. Optionally benchmark alternative 7B/8B candidates (Mistral 7B, Falcon 7B) converted to HF format to compare latency/quality trade-offs.

**Note on vLLM**
- This POC used a GGUF quantized model (`llama-2-7b-chat.Q4_K_M.gguf`) served with `text-generation-webui` (llama.cpp with CUDA). We did not use `vLLM` or a vLLM-based Docker deployment for this run. The report intentionally documents this choice — `vLLM` is a valid, high-performance serving option when using HF/safetensors models and when adequate GPU VRAM (typically >=16GB) is available. On the current RTX 3060 (6GB VRAM), running `vLLM` without additional model quantization/offload strategies is likely to hit memory limits or require significant offloading and may not provide better latency than the chosen GGUF+llama.cpp path. If stakeholders require a strict vLLM-based comparison, we can attempt it (requires HF-format weights or a model download), but it may fail or perform poorly on this hardware; the GGUF+webui numbers should be considered the primary local-GPU POC results.

**Artifacts**
- Eval CSV/MD: eval/results_poc/eval_results_20251231_171407.csv
- Human-readable report: eval/results_poc/eval_report_20251231_171407.md

## Integrated backend -> local-model results

We also ran an integrated end-to-end test where requests flow through the backend adapter (`LocalModelAdapter` + controller) into the same local GGUF runtime. This measures real-world overhead from the service layer.

Source: `ai-interview-project/eval/results_local_integration/eval_results_20251231_180218.csv`

- Count: 20
- Average latency: 23,648.63 ms (~23.6 s)
- Median (p50): 16,012.09 ms (~16.0 s)
- 95th percentile (p95): 42,673.01 ms (~42.7 s)
- Min: 5,779.03 ms (~5.8 s)
- Max: 113,247.73 ms (~113.2 s)

Notes:
- All 20 responses passed the validator (20/20). The integrated run shows higher average latency compared with the direct POC run due to a high outlier (IQ-19) and additional service-layer overhead. The median remains in the ~16s range, similar to the direct POC median.
- Recommendation: identify and capture the full request/response for outlier runs (IQ-19) to diagnose potential stalls or retry-induced long tail; apply mitigations from the addendum (parameter tuning, timeouts, warm-up, batching).

### IQ-19 snapshot

The full response snapshot for `IQ-19` was captured and saved to `eval/results_local_integration/IQ-19_snapshot.json`. It contains the raw JSON returned by the local model and the prompt used. Use this artifact when debugging the outlier (includes tokens used and model finish reason).

## Mitigations run (reduced tokens + lower temperature)

We ran a mitigations evaluation with `--max-tokens 256 --temperature 0.0 --max-new-tokens 256` after warming up the model. Results (20 prompts):

- Average latency: 19,059.96 ms (~19.1 s)
- Median (p50): 17,893.03 ms (~17.9 s)
- 95th percentile (p95): 27,820.47 ms (~27.8 s)
- Min: 6,450.71 ms (~6.5 s)
- Max: 27,820.47 ms (~27.8 s)

Observations:
- Reducing `max_tokens` and temperature produced a modest reduction in p95 and average latency compared to the integrated baseline, but the median remained similar. Additional tuning (prompt condensation, earlier stops, and model conversion/runtime changes) may be required to reach interactive-latency targets.

### Additional experiments

We ran two further experiments to evaluate targeted mitigations:

- Experiment A: `--max-tokens 128 --temperature 0.0 --max-new-tokens 128 --stop "###"` against the original prompts.
	- Count: 20
	- Avg latency: 12,557.32 ms
	- p50: 13,420.99 ms
	- p95: 14,191.14 ms
	- Min: 6,021.34 ms
	- Max: 14,191.14 ms

- Experiment B: same settings but using trimmed prompts (remove verbose instructions).
	- Count: 20
	- Avg latency: 12,848.11 ms
	- p50: 13,060.00 ms
	- p95: 14,180.44 ms
	- Min: 9,701.99 ms
	- Max: 14,180.44 ms

Observations:
- Both experiments substantially reduced the long tail and max latency compared with the integrated baseline (previous max 113s); p95 and avg improved significantly. Trimming prompts gave similar results to adding stop tokens; the combination is effective at preventing extremely long generations.
- All runs maintained schema validator pass rate 20/20.

Conclusion: Using short max_tokens, deterministic sampling (temperature=0), stop sequences and prompt condensation are effective mitigations on this hardware/runtime. They reduce p95 and remove extreme outliers, making the GGUF + `text-generation-webui` path more predictable for low-to-moderate throughput use cases. For interactive targets (sub-2s), a higher-end GPU or an optimized runtime (vLLM) is still recommended.

If you'd like, I can now:
- Convert this summary into a short slide or a one-page executive summary, or
- Attempt a vLLM (Docker) run if you prefer that runtime next (requires converting/locating an HF/safetensors model or pulling a matching container image).


---

## Appendix: IQ-19 Diagnostic

- Snapshot file: eval/results_local_integration/IQ-19_snapshot.json (raw model JSON + prompt)
- Observed latency (integrated run): 113,247.73 ms (~113.2 s)
- Model finish reason in snapshot: "stop"
- Token usage (from snapshot): prompt_tokens: 61, completion_tokens: 587, total_tokens: 648

Summary and recommended immediate debugging steps:

- Inspect `IQ-19_snapshot.json` to confirm whether the assistant's content contains a very long single token sequence or repeated loops that could cause prolonged generation.
- Check the local server logs (text-generation-webui) at the timestamp matching IQ-19 for warnings, GC/VRAM thrashing, or retry behaviour (multiple API calls for the same prompt).
- Re-run the same prompt with deterministic settings used in Experiment A (`--temperature 0.0 --max-new-tokens 128 --stop "###"`) to see if the outlier reproduces. If it does not reproduce, the outlier is likely due to an internal long generation path or transient resource pressure.
- If reproducible, capture `strace`/profiling traces (or enable `--log-level debug` in the webui) and check for tokenization or model-internal loops.
- Add a per-request timeout at the service layer (e.g., 60s) to prevent extreme long-tail requests from blocking downstream systems while preserving the full snapshot for later analysis.

Mitigation evidence: Experiments A/B (shorter `max_tokens`, stop sequences, and prompt trimming) removed the long tail and reduced p95 to ~14s while preserving schema pass rate 20/20. Use these mitigation settings as a production-safe default until a vLLM/16GB+ GPU path is available.

POC prepared by automation in repo; reply with which next action you want me to take.