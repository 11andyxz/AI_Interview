# Week 29 Latency Attribution and Remediation

Generated: 2026-06-24T12:58:33.236393+00:00

## Definition

Stage B product latency now uses `user_facing_question_generation_v1`.

## Remediation

- POST `/api/llm/question-generate` returns after user-facing question generation.
- Question quality scoring, experiment metric writes, and generated-question topic mapping run after the response.
- `experiment_metric.latency_ms` records user-facing question-generation latency for the generated question.

## Component Breakdown

- overall backend question-generation p95: 3060.5999999999995
- treatment backend question-generation p95: 4111.799999999999
- baseline backend question-generation p95: 2364.35
- treatment external question HTTP p95: 4283.299999999999
- treatment post-response quality audit observed p95: 1734.3999999999999
- treatment topic mapping observed p95: 3754.099999999997
- treatment answer evaluation p95: 5962.249999999999
