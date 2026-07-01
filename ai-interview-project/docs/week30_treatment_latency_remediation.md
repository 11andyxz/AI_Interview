# Week 30 Treatment Latency Remediation

Generated: 2026-07-01T01:10:18.784522+00:00

## Definition

Stage B product latency remains `user_facing_question_generation_v1`.

## Change

- Question generation now uses compact history for the OpenAI request.
- Question generation uses `openai.question-generation.max-history-messages=3`.
- Question generation uses `openai.question-generation.max-answer-chars=360`.
- Question generation uses `openai.question-generation.max-tokens=240`.
- Quality scoring, experiment metric writes, and topic mapping remain post-response audit work.

## Evidence

- Week 29 treatment user-facing p95: 4111.8 ms.
- Week 30 treatment user-facing p95: 1377.4999999999986 ms.
- Week 30 baseline user-facing p95: 1347.0499999999997 ms.
- Week 30 treatment external HTTP p95: 1469.5999999999988 ms.
- Week 30 treatment prompt input chars p95: 1513.0.
- Week 30 treatment post-response quality audit observed p95: 1657.05 ms.
- Week 30 treatment topic mapping observed p95: 5060.2499999999945 ms.
- Week 30 treatment answer evaluation p95: 3449.6499999999987 ms.
