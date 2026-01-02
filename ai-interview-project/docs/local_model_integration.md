# Local Model Integration — Guide (no-exec)

Purpose: describe how to integrate a local OpenAI-compatible inference endpoint (e.g., text-generation-webui at `http://localhost:7860/v1/chat/completions`) into the existing backend service interface without running code here — this file provides sample config and steps for you or CI to execute.

1) Configuration

- Add an environment variable to point the backend to the local model API (example for Spring Boot `application.properties`):

```
# application.properties
ai.model.provider=local
ai.model.local.url=http://127.0.0.1:7860/v1/chat/completions
ai.model.local.name=llama-2-7b-chat
```

- Or set env vars in your deployment start script (PowerShell example):

```powershell
$env:AI_MODEL_PROVIDER = 'local'
$env:AI_MODEL_LOCAL_URL = 'http://127.0.0.1:7860/v1/chat/completions'
$env:AI_MODEL_LOCAL_NAME = 'llama-2-7b-chat'
```

2) Backend adapter (concept)

- Implement or extend the existing API client to route requests to the configured provider. Pseudocode:

```java
// Example adapter sketch
if (config.get("ai.model.provider").equals("local")) {
  // Build chat-style payload expected by local service
  postJson(config.get("ai.model.local.url"), Map.of(
    "model", config.get("ai.model.local.name"),
    "messages", messages,
    "max_tokens", 256,
    "temperature", 0.0
  ));
} else {
  // existing backend/OpenAI flow
}
```

- Ensure the adapter uses the same schema and validator pipeline as production; responses should be validated by the same `eval/validators/*` code path.

3) Health check & readiness

- Add a lightweight health endpoint that pings the local model API (e.g., a `HEAD` or short `/v1/models` check if supported) and marks the local provider as unhealthy if the ping fails or latency > configured threshold.

4) Timeouts, retries & fallback

- Use conservative timeouts on the local model calls (e.g., 60s for heavy generations, 10s for short answers).
- Implement a single deterministic retry with reduced temperature for salvage (this behavior mirrors the evaluation harness) and propagate `fallback_action` metadata for traceability.

5) Local testing steps (to run on your machine or CI; not executed here)

```powershell
# 1) Start local model runtime (text-generation-webui) in a separate terminal
# 2) Export env vars as above or update application.properties
# 3) Start backend
# 4) Run eval harness against local endpoint:
python .\eval\run_eval.py --model-type local --local-model-url "http://127.0.0.1:7860/v1/chat/completions" --local-model-name "llama-2-7b-chat" --output eval/results_local_integration --prompts-dir eval/poc_prompts --fallback-mode salvage --allow-salvage
```

6) Observability

- Capture request/response timing, model tokens used, validator result, and fallback action in the existing evaluation CSV/MD format to enable later analysis.
- Surface the slowest requests in metrics dashboards (p50/p95) and retain sample responses for debugging.

7) Rollback plan

- If the local model provider is unhealthy or produces invalid responses, route requests back to the cloud backend (OpenAI) or return a graceful UI message indicating temporary local degradation.

If you want, I can commit a small adapter skeleton into `ai-interview-project/backend/` and a sample `application-local.properties` file (no runtime actions). Should I create those files now? 
