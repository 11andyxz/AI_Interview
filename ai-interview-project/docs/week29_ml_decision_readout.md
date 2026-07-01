# Week 29 ML Decision Readout

Generated: 2026-06-24T12:58:33.236393+00:00

## Decision

Stage B recommendation: HOLD.

## Rationale

- Post-remediation sample-size gate met: True (treatment=80, baseline=50).
- Session-level metrics decision-grade: True.
- User-facing latency p95 met: False; treatment p95=4111.799999999999.
- Topic coverage supporting signal healthy: True.
- Calibration ready: False; platt-v2.1 remains unchanged.

## Rollback

Keep Stage A at 10%; do not expand to Stage B until user-facing latency p95 guardrail, calibration readiness are resolved.
