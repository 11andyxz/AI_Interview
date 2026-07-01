# Week 24 Calibration Readiness Report

**Date**: 2026-05-22  
**Owner**: Yukun Song  
**Current calibration model**: platt-v2.1

---

## Summary

| Item | Status |
|------|--------|
| Calibration model | platt-v2.1 (unchanged) |
| Live calibration data | None — n_treatment=0, no live sessions |
| Junior slice sample | 0 live examples |
| Mid slice sample | 0 live examples |
| Senior slice sample | 0 live examples |
| Calibration review | **HOLD** — no live slice evidence |
| Threshold promotion | **HOLD** — requires junior/mid/senior slice coverage |

---

## Live Data Availability

The DB contains 29 historical interview records (last session: April 9, 2026). None have `started_at` / `ended_at` timestamps populated, and all show status='In Progress'. The `candidate_skill_profile` table (which holds prediction confidence and calibration output) has 0 rows. No live slice data exists for any of junior, mid, or senior.

---

## Calibration Review Policy

Per Week 23 decision:
- platt-v2.1 thresholds remain at `pass_threshold=0.85`, `fail_threshold=0.10`
- No threshold update without junior/mid/senior slice evidence from live sessions
- Calibration tightening requires slice-level data with sufficient coverage for decision use

**This policy is unchanged for Week 24.** No new live calibration data is available.

---

## Re-evaluation Gate

Calibration review will proceed when:
1. `response_feature_cache` is populated (preflight PASS)
2. `question_embedding` is populated (preflight PASS)
3. `n_treatment >= 20` per slice (junior, mid, senior) — currently 0 for all slices
4. Live data exists within the same validation window as Stage A

Until these gates are met, calibration analysis is descriptive only and cannot be used to support threshold promotion.

---

## Week 25 Calibration Condition

If live sessions accumulate during Week 25 and feature caches are populated, calibration review can proceed. The review will:
- Compare live confidence distribution against platt-v2.1 Platt scaling parameters
- Check ECE (Expected Calibration Error) and Brier score on live predictions
- Require n_junior ≥ 20, n_mid ≥ 20, n_senior ≥ 20 before any slice-level threshold adjustment

platt-v2.1 is the production calibration model until a live evidence-based review supports promotion.
