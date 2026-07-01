# Week 20 A/B Experiment Results (Empirical Data)

**Date**: April 17, 2026  
**Experiment IDs**: week20_control_real (control) vs week20_treatment_real (treatment)  
**Status**: COMPLETED  
**Recommendation**: **✗ ROLLBACK**

---

## Executive Summary

**Objective**: Validate Week 18 projected results with real treatment traffic. Compare baseline early-stop policy (0.95/0.05) against new policy (0.90/0.10) using empirical data from ≥60 sessions per arm.

**Key Finding**: Treatment shows **+10.0%** change in average questions (6.85 → 7.53). Statistical significance: **YES** (p=0.0104). Effect size: **small** (Cohen's d=-0.433).

**Recommendation**: **ROLLBACK** - Treatment performs worse than control (+10.0%)

---

## Experiment Design

### Treatment Groups

| Group | Policy | Pass/Fail Thresholds | Min Questions | Sample Size | Data Type |
|-------|---------|---------------------|---------------|-------------|-----------|
| **Control** | Baseline | 0.95 / 0.05 | 5 | 120 | Real sessions |
| **Treatment** | New Policy | 0.90 / 0.10 | 6 | 60 | Real sessions |

### Data Collection

- **Control Group**: Real E2E sessions with baseline policy (0.95/0.05)
- **Treatment Group**: Real E2E sessions with new policy (0.90/0.10)
- **Environment**: Production-like staging with Aiven Cloud MySQL + OpenAI API
- **Timestamp**: 2026-04-17

### Success Metrics

**Primary**:
1. **Average questions per session** - Target: 8-12% reduction
2. **Statistical significance** - Target: p < 0.05 with adequate power
3. **Effect size** - Target: Cohen's d > 0.3 (at least small effect)

**Guardrails**:
- Minimum 6-question threshold enforced
- Junior slice RMSE ≤ 11.5 (maintained or improved)
- Premature stop rate < 3%

---

## Results

### 1. Primary Metric: Average Questions per Interview

| Metric | Control (Baseline) | Treatment (New) | Change | % Change |
|--------|-------------------|-----------------|--------|----------|
| **Mean** | **6.85** | **7.53** | **+0.68** | **+10.0%** |
| Std Dev | 1.84 | 1.27 | - | - |
| Median | 6 | 8 | +2 | - |
| Min | 5 | 6 | - | - |
| Max | 12 | 11 | - | - |
| Sample Size | 120 | 60 | - | - |

**95% Confidence Interval for Difference**: [0.22, 1.14]

**Interpretation**: Treatment does not reduce interview length.

### 2. Statistical Tests

| Test | Value | Threshold | Result |
|------|-------|-----------|--------|
| **p-value** | **0.0104** | < 0.05 | **✓ Significant** |
| t-statistic | -2.588 | - | - |
| Cohen's d | -0.433 | > 0.3 | ✓ Small effect |
| Statistical Power | >=0.80 | ≥ 0.80 | ✓ Adequate |

**Effect Size Interpretation**: Small effect (|d| = 0.433)
- Negligible: |d| < 0.2
- Small: 0.2 ≤ |d| < 0.5
- Medium: 0.5 ≤ |d| < 0.8
- Large: |d| ≥ 0.8

### 3. Slice-Level Analysis

#### Junior Slice

| Metric | Control | Treatment | Change |
|--------|---------|-----------|--------|
| Sample Size | 48 | 24 | - |
| Avg Questions | 6.38 | 7.71 | +1.33 |
| Std Dev | 1.28 | 0.62 | - |

**Junior Slice Guardrail**: Sample size ≥ 20 per arm ✓

#### Mid Slice

| Metric | Control | Treatment | Change |
|--------|---------|-----------|--------|
| Sample Size | 36 | 18 | - |
| Avg Questions | 7.22 | 7.06 | -0.17 |
| Std Dev | 1.93 | 1.16 | - |

#### Senior Slice

| Metric | Control | Treatment | Change |
|--------|---------|-----------|--------|
| Sample Size | 36 | 18 | - |
| Avg Questions | 7.11 | 7.78 | +0.67 |
| Std Dev | 2.24 | 1.83 | - |

### 4. Latency Metrics

| Metric | Control | Treatment | Change |
|--------|---------|-----------|--------|
| Avg Latency | 1410ms | 1580ms | +170ms |
| p50 Latency | 1406ms | 1531ms | +125ms |
| p95 Latency | 1748ms | 1963ms | +215ms |

---

## Decision Framework

### Go/Hold/Rollback Criteria

| Criterion | Threshold | Actual | Status |
|-----------|-----------|--------|--------|
| Statistical Significance | p < 0.05 | p = 0.0104 | ✓ Pass |
| Efficiency Improvement | Δ < -5% | Δ = 10.0% | ✗ Fail |
| Effect Size | |d| > 0.3 | |d| = 0.433 | ✓ Pass |
| Sample Size | n ≥ 60/arm | Control: 120, Treatment: 60 | ✓ Pass |
| Junior Sample | n ≥ 20/arm | Control: 48, Treatment: 24 | ✓ Pass |

### Final Recommendation: ✗ ROLLBACK

**Rationale**: Treatment performs worse than control (+10.0%)

**Next Steps**:

1. ✗ Do not proceed with rollout
2. Immediate rollback if already deployed
3. Root cause analysis required
4. Revise treatment policy
5. Re-test with corrected parameters

---

## Appendix

### A. Experiment Metadata

- **Control ID**: week20_control_real
- **Treatment ID**: week20_treatment_real
- **Analysis Timestamp**: 2026-04-17T22:12:23.794266
- **Analysis Tool**: run_ab_experiment.py (Week 20)

### B. Statistical Methodology

**Tests Applied**:
- Independent samples t-test (two-tailed)
- Cohen's d for effect size
- 95% confidence interval for difference in means
- Significance level: α = 0.05

**Assumptions**:
- Independent samples
- Approximately normal distribution (validated by sample size n ≥ 30)
- Homogeneity of variance

### C. Reproducibility

To reproduce this analysis:
```bash
# Extract control data
python backend/run_ab_experiment.py --policy baseline --sessions 60 --experiment-id week20_control_real

# Extract treatment data
python backend/run_ab_experiment.py --policy new --sessions 60 --experiment-id week20_treatment_real

# Run analysis
python backend/run_ab_experiment.py --analyze --control week20_control_real --treatment week20_treatment_real

# Generate readout
python backend/generate_ab_readout.py \
    --analysis eval/results/ab_analysis_week20_control_real_vs_week20_treatment_real.json \
    --output docs/week20_ab_readout_empirical.md
```

---

**Document generated by**: generate_ab_readout.py  
**Date**: 2026-04-17 22:12:38
