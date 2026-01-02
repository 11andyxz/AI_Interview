Local Model Integration Notes

Files added by automation:
- `backend/src/main/java/com/aiinterview/integration/LocalModelAdapter.java` - minimal Java adapter example
- `backend/src/main/resources/application-local.properties` - sample Spring Boot properties for local provider

Usage notes:
- Adjust the adapter to fit your project's DI framework (Spring, Guice, etc.) and to use your existing HTTP client/config.
- Use `LocalModelAdapter.singlePromptMessagesJson(prompt)` to build a simple messages payload for single-shot prompts; for multi-turn sessions, build `messages` JSON as your application requires.
- Ensure the model endpoint matches `application-local.properties` when testing locally.

Testing steps (to run manually):
1. Start local model runtime (text-generation-webui) and ensure it exposes `/v1/chat/completions`.
2. Start backend with `--spring.profiles.active=local` or by loading `application-local.properties`.
3. Run the evaluation harness pointing to the local endpoint to validate responses.

This file and the code are scaffolding only and were added without executing any runtime commands.
