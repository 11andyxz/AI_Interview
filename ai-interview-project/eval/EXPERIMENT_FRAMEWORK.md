# Experiment Tracking Framework

**Version**: 1.0  
**Date**: March 27, 2026

This framework provides standardized tools for offline model evaluation, experiment tracking, and baseline/candidate comparison for the AI Interview system.

---

## Quick Start

### Run a Baseline Experiment

**PowerShell (Windows)**:
```powershell
cd eval
.\run_experiments.ps1 -ExperimentType baseline -ModelVersion "v1.0" -ConfigParams '{"pass_threshold":0.95,"fail_threshold":0.05,"min_questions":5}'
```

**Bash (Linux/macOS)**:
```bash
cd eval
./run_experiments.sh baseline v1.0 '{"pass_threshold":0.95,"fail_threshold":0.05,"min_questions":5}'
```

### Run a Candidate Experiment with Comparison

**PowerShell**:
```powershell
.\run_experiments.ps1 `
    -ExperimentType candidate `
    -ModelVersion "v1.1-role-specific" `
    -ConfigParams '{"pass_threshold":0.92,"fail_threshold":0.08,"min_questions":4,"role":"junior"}' `
    -CompareTo baseline_20260327_100000
```

**Bash**:
```bash
./run_experiments.sh candidate v1.1-role-specific \
    '{"pass_threshold":0.92,"fail_threshold":0.08,"min_questions":4,"role":"junior"}' \
    --compare-to baseline_20260327_100000
```

---

## Framework Components

### 1. Experiment Registry (`experiment_registry.csv`)

Central log of all experiments with key metrics:

| Column | Description |
|--------|-------------|
| `experiment_id` | Unique identifier (e.g., baseline_20260327_100000) |
| `timestamp` | ISO 8601 timestamp |
| `model_version` | Model/system version identifier |
| `slice` | Data slice (all, junior, mid, senior, etc.) |
| `config_params` | JSON configuration used |
| `rmse` | Root Mean Squared Error |
| `mae` | Mean Absolute Error |
| `brier_score` | Brier score for pass/fail prediction |
| `sample_size` | Number of test cases |
| `avg_latency_ms` | Average latency in milliseconds |
| `p50_latency_ms` | Median latency |
| `early_stop_rate` | Fraction of sessions stopped early |
| `avg_questions` | Average number of questions per session |
| `notes` | Free-text notes |

**Example**:
```csv
baseline_20260327_001,2026-03-27T10:00:00,embedding-v1.0,all,"{""pass_threshold"":0.95}",9.8,7.2,0.082,250,1340,1280,0.12,10.0,Week 16 baseline
candidate_20260327_002,2026-03-27T11:30:00,embedding-v1.1,junior,"{""pass_threshold"":0.92}",10.1,7.5,0.085,240,1290,1230,0.18,8.7,Role-specific policy test
```

### 2. Metrics Computation (`compute_metrics.py`)

Python module for standardized metric calculation:

**Metrics Provided**:
- **Regression**: RMSE, MAE
- **Classification**: Brier score, Expected Calibration Error (ECE)
- **Latency**: Mean, P50/P90/P95/P99
- **Early-Stop**: Early-stop rate, average questions (stopped vs full)
- **Comparison**: Absolute and relative deltas between experiments

**Usage**:
```bash
python compute_metrics.py \
    --input results/eval_results_20260327_100000.csv \
    --output metrics_baseline.json \
    --model-version "v1.0" \
    --config '{"pass_threshold":0.95}'

# With baseline comparison
python compute_metrics.py \
    --input results/eval_results_20260327_110000.csv \
    --output metrics_candidate.json \
    --model-version "v1.1" \
    --config '{"pass_threshold":0.92}' \
    --baseline metrics_baseline.json
```

**Output Format** (`metrics_baseline.json`):
```json
{
  "experiment_id": "v1.0_20260327_100000",
  "timestamp": "2026-03-27T10:00:00",
  "model_version": "v1.0",
  "config_params": {"pass_threshold": 0.95},
  "sample_size": 250,
  
  "rmse": 9.8,
  "mae": 7.2,
  "brier_score": 0.082,
  "calibration_error": 0.045,
  
  "avg_latency_ms": 1340.5,
  "latency_percentiles": {
    "p50": 1280.0,
    "p90": 1920.0,
    "p95": 2340.0,
    "p99": 3100.0
  },
  
  "early_stop_metrics": {
    "early_stop_rate": 0.12,
    "avg_questions_when_stopped": 7.5,
    "avg_questions_full": 10.8,
    "avg_questions_overall": 10.0
  },
  
  "failure_rate": 0.004,
  
  "comparison": {
    "baseline_id": "v1.0_20260327_100000",
    "candidate_id": "v1.1_20260327_110000",
    "deltas": {
      "rmse": {
        "baseline": 9.8,
        "candidate": 10.1,
        "absolute_delta": 0.3,
        "relative_delta_pct": 3.1
      },
      "avg_questions": {
        "baseline": 10.0,
        "candidate": 8.7,
        "absolute_delta": -1.3,
        "relative_delta_pct": -13.0
      }
    }
  }
}
```

### 3. Experiment Runner (`run_experiments.sh` / `run_experiments.ps1`)

Orchestrates the complete experiment workflow:

1. **Run evaluation** → Calls `run_eval.py` to test backend API
2. **Compute metrics** → Calls `compute_metrics.py` to calculate RMSE/MAE/Brier
3. **Display results** → Pretty-prints metrics and comparison deltas
4. **Update registry** → Appends row to `experiment_registry.csv`

**Parameters**:

| Parameter | Description | Example |
|-----------|-------------|---------|
| `ExperimentType` | baseline or candidate | `baseline` |
| `ModelVersion` | Version identifier | `v1.1-role-specific` |
| `ConfigParams` | JSON config string | `'{"pass_threshold":0.92}'` |
| `CompareTo` | Baseline experiment ID for comparison | `baseline_20260327_100000` |
| `Slice` | Data slice filter | `junior`, `mid`, `senior` |
| `BackendUrl` | Backend API URL | `http://localhost:8080` |

---

## Workflow Examples

### Example 1: Baseline Measurement

```powershell
# Week 16 baseline (current system)
cd eval

.\run_experiments.ps1 `
    -ExperimentType baseline `
    -ModelVersion "embedding-v1.0" `
    -ConfigParams '{"pass_threshold":0.95,"fail_threshold":0.05,"min_questions":5}'
```

**Expected Output**:
```
[INFO] Starting experiment: baseline_20260327_100000
[INFO]   Model version: embedding-v1.0
[OK] Evaluation complete. Results: results\eval_results_20260327_100000.csv
[OK] Metrics computed. Report: results\metrics_baseline_20260327_100000.json

========================================
  Experiment Results: baseline_20260327_100000
========================================
  Model Version:     embedding-v1.0
  Sample Size:       250

Prediction Quality:
  RMSE:              9.800
  MAE:               7.200
  Brier Score:       0.082

Performance:
  Avg Latency:       1340.5 ms
  P50 Latency:       1280.0 ms

Early-Stop Metrics:
  Early-Stop Rate:   0.120
  Avg Questions:     10.0
========================================

[OK] Experiment complete!
[INFO]   Experiment ID:   baseline_20260327_100000
[INFO]   Raw results:     results\eval_results_20260327_100000.csv
[INFO]   Metrics report:  results\metrics_baseline_20260327_100000.json
[INFO]   Registry entry:  experiment_registry.csv
```

### Example 2: Candidate Policy with Comparison

```powershell
# Week 17 role-specific policy (junior role)
.\run_experiments.ps1 `
    -ExperimentType candidate `
    -ModelVersion "embedding-v1.1-junior" `
    -ConfigParams '{"pass_threshold":0.92,"fail_threshold":0.08,"min_questions":4,"role":"junior"}' `
    -CompareTo baseline_20260327_100000
```

**Expected Output**:
```
[INFO] Starting experiment: candidate_20260327_110000
[INFO] Comparing to baseline: baseline_20260327_100000
[OK] Metrics computed. Report: results\metrics_candidate_20260327_110000.json

========================================
  Experiment Results: candidate_20260327_110000
========================================
  RMSE:              10.100
  Avg Questions:     8.7
========================================

[INFO] Comparison to baseline baseline_20260327_100000:

  Metric Deltas:
  ✓ avg_questions       :   10.000 →    8.700 (↓ 13.0%)
  ✗ rmse                :    9.800 →   10.100 (↑  3.1%)
  ✓ avg_latency_ms      : 1340.500 → 1290.300 (↓  3.7%)

[OK] Experiment complete!
```

### Example 3: Slice-Specific Analysis

```powershell
# Test senior role separately
.\run_experiments.ps1 `
    -ExperimentType candidate `
    -ModelVersion "embedding-v1.1-senior" `
    -ConfigParams '{"pass_threshold":0.96,"fail_threshold":0.04,"min_questions":6,"role":"senior"}' `
    -Slice "senior" `
    -CompareTo baseline_20260327_100000
```

---

## Integration with Existing Tools

### With `run_eval.py`

The experiment runner calls `run_eval.py` internally. You can still use it standalone:

```bash
# Direct evaluation (no metrics computation)
python run_eval.py --backend http://localhost:8080 --output results/
```

### With `model_comparison.py`

For multi-model comparisons, use alongside experiment framework:

```bash
# Compare 3 models
python model_comparison.py --models baseline v1.1-junior v1.1-senior --output comparison.csv
```

### With Grafana Dashboards

Export experiment metrics to Grafana:

```bash
# Convert experiment registry to Prometheus format
python export_to_prometheus.py --input experiment_registry.csv --output metrics.prom
```

---

## Best Practices

### 1. Naming Conventions

**Model Versions**:
- `embedding-v1.0` → Current production
- `embedding-v1.1-role-specific` → Feature branch
- `embedding-v1.1-platt-scaled` → Specific improvement

**Experiment IDs**:
- Auto-generated: `{type}_{timestamp}` (e.g., `baseline_20260327_100000`)
- Do not manually edit

**Slices**:
- `all` → Full dataset
- `junior`, `mid`, `senior` → Role-based
- `short_interview`, `long_interview` → Duration-based

### 2. Comparison Workflow

Always run baseline first, then candidates:

```powershell
# Step 1: Establish baseline
$baseline = .\run_experiments.ps1 -ExperimentType baseline -ModelVersion "v1.0" -ConfigParams '{...}'

# Step 2: Test candidate
.\run_experiments.ps1 -ExperimentType candidate -ModelVersion "v1.1" -ConfigParams '{...}' -CompareTo $baseline
```

### 3. Experiment Hygiene

- **Document config changes**: Always include meaningful config params
- **Record sample sizes**: Ensure statistical significance (>200 recommended)
- **Note P-values**: Use significance testing before rollout decisions
- **Preserve baselines**: Never delete baseline experiments from registry

### 4. Metrics Interpretation

| Metric | Good Direction | Threshold |
|--------|---------------|-----------|
| RMSE | ↓ Lower | <10.0 (baseline: 9.8) |
| MAE | ↓ Lower | <8.0 (baseline: 7.2) |
| Brier Score | ↓ Lower | <0.10 (baseline: 0.082) |
| Avg Questions | ↓ Lower | 8-9 (target: -15% from 10.0) |
| Early-Stop Rate | → Stable | 10-20% (avoid >25%) |
| Latency P50 | ↓ Lower | <1500ms (baseline: 1280ms) |

---

## Troubleshooting

### Issue: Evaluation fails with authentication error

**Symptom**:
```
[FAIL] Authentication failed: 401 - Unauthorized
```

**Solution**:
Check backend credentials in `run_eval.py` or pass as arguments:
```bash
python run_eval.py --backend http://localhost:8080 --username testuser --password password
```

### Issue: Metrics show N/A values

**Symptom**:
```
RMSE:              N/A
```

**Solution**:
Check CSV format in evaluation results. Required columns:
- `actual_score`, `predicted_score` → For RMSE/MAE
- `actual_outcome`, `predicted_pass_prob` → For Brier score
- `latency_ms` → For latency metrics

### Issue: Cannot compare to baseline

**Symptom**:
```
[WARN] Baseline metrics not found: results/metrics_baseline_20260327_100000.json
```

**Solution**:
Verify baseline experiment ID matches filename pattern:
```powershell
# Check existing experiments
dir results\metrics_*.json | Select-Object Name
```

---

## Future Enhancements

Planned for Week 18+:

1. **Statistical Significance Testing**
   - Add t-tests for RMSE/MAE deltas
   - Add chi-squared tests for early-stop rate changes
   - Auto-compute required sample sizes

2. **Automated Experiment Scheduling**
   - Cron/scheduled tasks for nightly baseline runs
   - Slack/email notifications on metric regressions

3. **Multi-Armed Bandit Support**
   - Epsilon-greedy exploration
   - Thompson sampling for optimal policy selection

4. **Experiment Version Control**
   - Git integration for config tracking
   - Reproducible experiment environments

---

## Contact

**Owner**: ML Engineering Team  
**Maintainer**: AI Interview Backend Team  
**Last Updated**: March 27, 2026
