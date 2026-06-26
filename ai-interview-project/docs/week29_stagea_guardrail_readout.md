# Week 29 Stage A Guardrail Readout

Generated: 2026-06-24T12:58:33.236393+00:00

## Status

HOLD for Stage B.

## Gates

- n_treatment >= 80: PASS (80).
- n_baseline >= 50: PASS (50).
- Session metrics decision-grade: PASS.
- User-facing latency p95 <= 3000: HOLD (4111.799999999999).
- Premature-stop rate: 0.0.
- OpenAI error rate: 0.0.
- Topic fallback rate: 0.0.
- Calibration ready: False.
- Blocking gates: user-facing latency p95 guardrail, calibration readiness.
