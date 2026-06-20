# Week 28 ML Decision Readout

Generated: 2026-06-20T19:17:24.610725+00:00

## Decision

Stage B recommendation: HOLD.

## Rationale

- Session-level metrics decision-grade: True.
- Operational session guardrails met: False; treatment latency_p95_ms=7484.199999999998.
- Repaired sample-size gate met: True (treatment=80, baseline=50).
- Topic coverage Stage B gate ready: True.
- Calibration ready: False; platt-v2.1 remains unchanged.

## Rollback

Keep Stage A at 10%; do not expand to Stage B until latency p95 guardrail, calibration readiness are resolved.
