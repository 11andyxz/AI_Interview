# Week 30 Calibration Ground-Truth Report

Generated: 2026-07-01T01:10:18.784522+00:00

## Status

Calibration readiness: HOLD.

## Label Policy

- Label source: final_evaluation_score.
- Pass threshold: 60.0.
- Human reviewed: False.
- Exclusion rule: Rows without a ground-truth label or DB prediction probability are excluded from Brier/ECE/RMSE.

## Slice Evidence

{
  "junior": {
    "baseline": 15,
    "excluded_count": 40,
    "label_count": 40,
    "metric_gate_pass": false,
    "metrics": {
      "brier_score": null,
      "ece": null,
      "rmse": null
    },
    "prediction_count": 0,
    "sample_gate_pass": true,
    "treatment": 25,
    "usable_metric_count": 0
  },
  "mid": {
    "baseline": 15,
    "excluded_count": 40,
    "label_count": 40,
    "metric_gate_pass": false,
    "metrics": {
      "brier_score": null,
      "ece": null,
      "rmse": null
    },
    "prediction_count": 0,
    "sample_gate_pass": true,
    "treatment": 25,
    "usable_metric_count": 0
  },
  "senior": {
    "baseline": 20,
    "excluded_count": 50,
    "label_count": 50,
    "metric_gate_pass": false,
    "metrics": {
      "brier_score": null,
      "ece": null,
      "rmse": null
    },
    "prediction_count": 0,
    "sample_gate_pass": true,
    "treatment": 30,
    "usable_metric_count": 0
  }
}

## Recommendation

Keep platt-v2.1 unchanged until every slice has labels, prediction probabilities, Brier/ECE/RMSE metrics, and regression coverage.
