# Week 30 ML Decision Readout

Generated: 2026-07-01T01:10:18.784522+00:00

## Decision

Stage B recommendation: HOLD.

## Rationale

- Week 30 sample-size gate met: True (treatment=80, baseline=50).
- Session-level metrics decision-grade: True.
- Treatment user-facing latency p95 met: True; p95=1377.4999999999986.
- Topic coverage supporting signal healthy: True.
- Calibration ready: False; platt-v2.1 remains unchanged.

## Rollback

Keep Stage A at 10%; do not expand to Stage B until calibration readiness is resolved.
