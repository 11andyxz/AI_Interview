```mermaid
%% SYSTEM_FLOW_AND_LATENCY (current codebase)

flowchart LR
  %% ======================
  %% SYSTEM FLOW
  %% ======================

  FE["Frontend
Resume / Interview UI
(browser: SpeechRecognition, WebRTC)
Implemented"] -->|HTTP| BE["Backend API
(SessionController, InterviewController)
(SessionService: in-memory)"]

  BE -->|read/write| DB["MySQL
user, api_key_config
future: interview, interview_message
(user + api_key_config implemented;
interview persistence planned)"]

  BE -->|read-only| KB["Knowledge JSON
(resources)
read-only"]

  BE -->|prompt via WebClient| LLM["LLM Service
(OpenAI gpt-3.5)
resume analysis: mocked / partial"]

  LLM -->|completion| BE
  BE -->|response| FE

  BE -->|SSE streaming implemented| FE
  BE -.->|WebSocket server not implemented| FE
```


 ## Latency Sources (Current Codebase)

Latency is introduced at the following points in the current codebase:

- **Backend → OpenAI network calls**  
  LLM requests dominate end-to-end latency due to external network round-trip and model inference time.

- **Database reads/writes**  
  Remote MySQL access (e.g., fetching interview/candidate data) adds additional network and query latency.

- **Token length / prompt size**  
  Longer conversation history and higher max-token settings increase OpenAI processing time.

- **Sequential operations**  
  Some DB lookups and LLM calls are executed sequentially, compounding latency.

- **Retries / error handling**  
  Currently limited retry logic; transient failures can introduce additional delay or early termination.

> **Note**  
> SSE streaming is implemented for question generation, reducing perceived latency (time to first token),  
> but total completion time is still dominated by LLM processing.
