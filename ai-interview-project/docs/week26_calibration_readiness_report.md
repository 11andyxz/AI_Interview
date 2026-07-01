# Week 26 Calibration Readiness Report

**Date:** 2026-06-05
**Model:** platt-v2.1
**Decision:** HOLD

---

## 1. Calibration Prerequisites

| Prerequisite | Required | Current | Status |
|---|---|---|---|
| n_treatment >= 50 | 50 | 22 | FAIL |
| p-value < 0.05 | Yes | p=0.4673 | FAIL |
| organic response_feature_cache rows | >= 1 | 10 | PASS |
| junior/mid/senior slice examples | >= 1 each | 0 each (no slice labels in smoke sessions) | FAIL |
| topic_coverage rows | >= 1 | 0 | FAIL |

---

## 2. Quality Score Distribution

| Variant | n | mean | std | metric_version |
|---|---|---|---|---|
| treatment (Week 25) | 10 | 60.00 | 0.00 | heuristic-v1.0 |
| treatment (Week 26) | 12 | 60.00 | 0.00 | llm-v1.0 |
| baseline (Week 25) | 20 | 62.00 | 4.47 | heuristic-v1.0 |
| baseline (Week 26) | 24 | 60.00 | 0.00 | llm-v1.0 |

Zero variance in treatment quality persists across both metric versions for these controlled smoke sessions. The LLM scorer upgrade did not introduce variance because all sessions share the same no-history context and generate the same generic question.

---

## 3. Slice Coverage

No junior, mid, or senior slice labels are available from controlled smoke sessions. All sessions used the default `level=mid` parameter, but no slice-disaggregated evaluation data exists. Calibration by slice remains blocked.

---

## 4. Organic Feature Writes

`response_feature_cache` now has 10 organic-v1.0 rows from the Week 26 eval calls under `ml.nlp.enabled=true`. This confirms the write path is live and functioning. However, 10 rows is insufficient for calibration evidence. At least 50 organic rows with score distribution across levels are needed before a calibration update would be warranted.

---

## 5. Calibration Decision

**platt-v2.1 unchanged.**

Conditions for triggering a calibration update:
- n_treatment >= 50 (currently 22)
- p-value < 0.05 (currently 0.4673)
- Organic response_feature_cache rows >= 20 with score variance (currently 10, all score=50)
- At least 1 example per junior/mid/senior slice with real LLM evaluation scores

None of these conditions are met. No calibration update is warranted this week.

---

## 6. Risk Assessment

| Risk | Level | Note |
|---|---|---|
| Quality regression risk | Low | delta=−0.91, well above −10 threshold |
| Calibration staleness risk | Low | platt-v2.1 validated through Week 25 |
| Feature write risk | Low | organic-v1.0 writes confirmed active |
| Statistical signal risk | High | treatment std=0 limits t-test power |
