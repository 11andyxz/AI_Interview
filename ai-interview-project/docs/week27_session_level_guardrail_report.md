# Week 27 Session-Level Guardrail Report

Generated: 2026-06-13T13:55:33.309163+00:00

## Result

- Status: PARTIAL.
- candidate_skill_profile rows for repaired prefixes: 50.
- Observed early_stop_rate: 0.0.
- Observed premature_stop_rate: 0.0.

## Limitation

Seeded prior Q&A history is written before question generation, but candidate_skill_profile question_count records only backend-scored generated turns. The observed avg_questions is therefore not decision-grade for Stage B.
