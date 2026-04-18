#!/usr/bin/env python3
"""
Generate A/B Readout Document from Analysis Results

Takes the JSON analysis output from run_ab_experiment.py --analyze
and generates a comprehensive markdown readout document.

Usage:
    python generate_ab_readout.py \
        --analysis eval/results/ab_analysis_week20_control_vs_week20_treatment.json \
        --output docs/week20_ab_readout_empirical.md
"""

import argparse
import json
from pathlib import Path
from datetime import datetime
from typing import Dict, Any


def generate_readout(analysis_data: Dict[str, Any], output_path: Path):
    """Generate markdown readout document from analysis data."""
    
    control = analysis_data['control_metrics']
    treatment = analysis_data['treatment_metrics']
    stats = analysis_data['statistical_tests']
    
    # Determine recommendation
    if stats['is_significant'] and stats['diff_pct'] < -5 and stats['p_value'] < 0.05:
        recommendation = "RAMP"
        recommendation_color = "✓"
        recommendation_rationale = f"Significant efficiency improvement ({stats['diff_pct']:.1f}%) with strong statistical evidence (p={stats['p_value']:.4f})"
    elif not stats['is_significant']:
        recommendation = "HOLD"
        recommendation_color = "⚠"
        recommendation_rationale = f"No significant difference detected (p={stats['p_value']:.4f})"
    elif stats['diff_pct'] > 0:
        recommendation = "ROLLBACK"
        recommendation_color = "✗"
        recommendation_rationale = f"Treatment performs worse than control (+{stats['diff_pct']:.1f}%)"
    else:
        recommendation = "REVIEW"
        recommendation_color = "?"
        recommendation_rationale = "Manual review recommended - results inconclusive"
    
    # Effect size interpretation
    abs_d = abs(stats['cohens_d'])
    if abs_d < 0.2:
        effect_size = "negligible"
    elif abs_d < 0.5:
        effect_size = "small"
    elif abs_d < 0.8:
        effect_size = "medium"
    else:
        effect_size = "large"
    
    # Generate markdown content
    content = f"""# Week 20 A/B Experiment Results (Empirical Data)

**Date**: {datetime.now().strftime('%B %d, %Y')}  
**Experiment IDs**: {analysis_data['control_id']} (control) vs {analysis_data['treatment_id']} (treatment)  
**Status**: COMPLETED  
**Recommendation**: **{recommendation_color} {recommendation}**

---

## Executive Summary

**Objective**: Validate Week 18 projected results with real treatment traffic. Compare baseline early-stop policy (0.95/0.05) against new policy (0.90/0.10) using empirical data from ≥60 sessions per arm.

**Key Finding**: Treatment shows **{stats['diff_pct']:+.1f}%** change in average questions ({control['avg_questions']:.2f} → {treatment['avg_questions']:.2f}). Statistical significance: **{'YES' if stats['is_significant'] else 'NO'}** (p={stats['p_value']:.4f}). Effect size: **{effect_size}** (Cohen's d={stats['cohens_d']:.3f}).

**Recommendation**: **{recommendation}** - {recommendation_rationale}

---

## Experiment Design

### Treatment Groups

| Group | Policy | Pass/Fail Thresholds | Min Questions | Sample Size | Data Type |
|-------|---------|---------------------|---------------|-------------|-----------|
| **Control** | Baseline | 0.95 / 0.05 | 5 | {control['sample_size']} | Real sessions |
| **Treatment** | New Policy | 0.90 / 0.10 | 6 | {treatment['sample_size']} | Real sessions |

### Data Collection

- **Control Group**: Real E2E sessions with baseline policy (0.95/0.05)
- **Treatment Group**: Real E2E sessions with new policy (0.90/0.10)
- **Environment**: Production-like staging with Aiven Cloud MySQL + OpenAI API
- **Timestamp**: {analysis_data['analysis_timestamp'][:10]}

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
| **Mean** | **{control['avg_questions']:.2f}** | **{treatment['avg_questions']:.2f}** | **{stats['diff_mean']:+.2f}** | **{stats['diff_pct']:+.1f}%** |
| Std Dev | {control['std_questions']:.2f} | {treatment['std_questions']:.2f} | - | - |
| Median | {control['median_questions']:.0f} | {treatment['median_questions']:.0f} | {treatment['median_questions'] - control['median_questions']:+.0f} | - |
| Min | {control['min_questions']:.0f} | {treatment['min_questions']:.0f} | - | - |
| Max | {control['max_questions']:.0f} | {treatment['max_questions']:.0f} | - | - |
| Sample Size | {control['sample_size']} | {treatment['sample_size']} | - | - |

**95% Confidence Interval for Difference**: [{stats['ci_95_lower']:.2f}, {stats['ci_95_upper']:.2f}]

**Interpretation**: {'Treatment reduces interview length by ' + f"{abs(stats['diff_mean']):.2f}" + ' questions on average.' if stats['diff_mean'] < 0 else 'Treatment does not reduce interview length.'}

### 2. Statistical Tests

| Test | Value | Threshold | Result |
|------|-------|-----------|--------|
| **p-value** | **{stats['p_value']:.4f}** | < 0.05 | **{'✓ Significant' if stats['is_significant'] else '✗ Not Significant'}** |
| t-statistic | {stats['t_statistic']:.3f} | - | - |
| Cohen's d | {stats['cohens_d']:.3f} | > 0.3 | {'✓ ' + effect_size.title() + ' effect' if abs(stats['cohens_d']) > 0.3 else '✗ Negligible effect'} |
| Statistical Power | {stats['power']} | ≥ 0.80 | {'✓ Adequate' if '0.80' in stats['power'] or '0.9' in stats['power'] else '⚠ May be underpowered'} |

**Effect Size Interpretation**: {effect_size.title()} effect (|d| = {abs(stats['cohens_d']):.3f})
- Negligible: |d| < 0.2
- Small: 0.2 ≤ |d| < 0.5
- Medium: 0.5 ≤ |d| < 0.8
- Large: |d| ≥ 0.8

### 3. Slice-Level Analysis

#### Junior Slice

| Metric | Control | Treatment | Change |
|--------|---------|-----------|--------|
| Sample Size | {control.get('junior_sample_size', 0)} | {treatment.get('junior_sample_size', 0)} | - |
| Avg Questions | {control.get('junior_avg_questions', 0.0):.2f} | {treatment.get('junior_avg_questions', 0.0):.2f} | {treatment.get('junior_avg_questions', 0.0) - control.get('junior_avg_questions', 0.0):+.2f} |
| Std Dev | {control.get('junior_std_questions', 0.0):.2f} | {treatment.get('junior_std_questions', 0.0):.2f} | - |

**Junior Slice Guardrail**: Sample size ≥ 20 per arm {'✓' if treatment.get('junior_sample_size', 0) >= 20 and control.get('junior_sample_size', 0) >= 20 else '✗'}

#### Mid Slice

| Metric | Control | Treatment | Change |
|--------|---------|-----------|--------|
| Sample Size | {control.get('mid_sample_size', 0)} | {treatment.get('mid_sample_size', 0)} | - |
| Avg Questions | {control.get('mid_avg_questions', 0.0):.2f} | {treatment.get('mid_avg_questions', 0.0):.2f} | {treatment.get('mid_avg_questions', 0.0) - control.get('mid_avg_questions', 0.0):+.2f} |
| Std Dev | {control.get('mid_std_questions', 0.0):.2f} | {treatment.get('mid_std_questions', 0.0):.2f} | - |

#### Senior Slice

| Metric | Control | Treatment | Change |
|--------|---------|-----------|--------|
| Sample Size | {control.get('senior_sample_size', 0)} | {treatment.get('senior_sample_size', 0)} | - |
| Avg Questions | {control.get('senior_avg_questions', 0.0):.2f} | {treatment.get('senior_avg_questions', 0.0):.2f} | {treatment.get('senior_avg_questions', 0.0) - control.get('senior_avg_questions', 0.0):+.2f} |
| Std Dev | {control.get('senior_std_questions', 0.0):.2f} | {treatment.get('senior_std_questions', 0.0):.2f} | - |

### 4. Latency Metrics

| Metric | Control | Treatment | Change |
|--------|---------|-----------|--------|
| Avg Latency | {control.get('avg_latency_ms', 0):.0f}ms | {treatment.get('avg_latency_ms', 0):.0f}ms | {(treatment.get('avg_latency_ms', 0) - control.get('avg_latency_ms', 0)):+.0f}ms |
| p50 Latency | {control.get('p50_latency_ms', 0):.0f}ms | {treatment.get('p50_latency_ms', 0):.0f}ms | {(treatment.get('p50_latency_ms', 0) - control.get('p50_latency_ms', 0)):+.0f}ms |
| p95 Latency | {control.get('p95_latency_ms', 0):.0f}ms | {treatment.get('p95_latency_ms', 0):.0f}ms | {(treatment.get('p95_latency_ms', 0) - control.get('p95_latency_ms', 0)):+.0f}ms |

---

## Decision Framework

### Go/Hold/Rollback Criteria

| Criterion | Threshold | Actual | Status |
|-----------|-----------|--------|--------|
| Statistical Significance | p < 0.05 | p = {stats['p_value']:.4f} | {'✓ Pass' if stats['is_significant'] else '✗ Fail'} |
| Efficiency Improvement | Δ < -5% | Δ = {stats['diff_pct']:.1f}% | {'✓ Pass' if stats['diff_pct'] < -5 else '✗ Fail'} |
| Effect Size | |d| > 0.3 | |d| = {abs(stats['cohens_d']):.3f} | {'✓ Pass' if abs(stats['cohens_d']) > 0.3 else '⚠ Borderline'} |
| Sample Size | n ≥ 60/arm | Control: {control['sample_size']}, Treatment: {treatment['sample_size']} | {'✓ Pass' if control['sample_size'] >= 60 and treatment['sample_size'] >= 60 else '⚠ Below target'} |
| Junior Sample | n ≥ 20/arm | Control: {control.get('junior_sample_size', 0)}, Treatment: {treatment.get('junior_sample_size', 0)} | {'✓ Pass' if control.get('junior_sample_size', 0) >= 20 and treatment.get('junior_sample_size', 0) >= 20 else '⚠ Below target'} |

### Final Recommendation: {recommendation_color} {recommendation}

**Rationale**: {recommendation_rationale}

**Next Steps**:
"""
    
    if recommendation == "RAMP":
        content += """
1. ✓ Begin gradual rollout: 10% → 25% → 50% → 100%
2. Monitor slice-level metrics closely (especially junior)
3. Set up automated alerts for RMSE regression
4. Prepare <5min rollback procedure
5. Week 21: Full production validation"""
    elif recommendation == "HOLD":
        content += """
1. ⚠ Do not proceed with rollout
2. Investigate lack of statistical significance
3. Consider increasing sample size or effect size
4. Re-evaluate treatment policy parameters
5. Run additional experiments if needed"""
    elif recommendation == "ROLLBACK":
        content += """
1. ✗ Do not proceed with rollout
2. Immediate rollback if already deployed
3. Root cause analysis required
4. Revise treatment policy
5. Re-test with corrected parameters"""
    else:
        content += """
1. ? Manual review required
2. Consult with stakeholders
3. Consider additional data collection
4. Evaluate risk tolerance
5. Document decision rationale"""
    
    content += f"""

---

## Appendix

### A. Experiment Metadata

- **Control ID**: {analysis_data['control_id']}
- **Treatment ID**: {analysis_data['treatment_id']}
- **Analysis Timestamp**: {analysis_data['analysis_timestamp']}
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
python backend/run_ab_experiment.py --policy baseline --sessions 60 --experiment-id {analysis_data['control_id']}

# Extract treatment data
python backend/run_ab_experiment.py --policy new --sessions 60 --experiment-id {analysis_data['treatment_id']}

# Run analysis
python backend/run_ab_experiment.py --analyze --control {analysis_data['control_id']} --treatment {analysis_data['treatment_id']}

# Generate readout
python backend/generate_ab_readout.py \\
    --analysis eval/results/ab_analysis_{analysis_data['control_id']}_vs_{analysis_data['treatment_id']}.json \\
    --output docs/week20_ab_readout_empirical.md
```

---

**Document generated by**: generate_ab_readout.py  
**Date**: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}
"""
    
    # Write to file
    output_path.parent.mkdir(parents=True, exist_ok=True)
    with open(output_path, 'w', encoding='utf-8') as f:
        f.write(content)
    
    print(f"✓ Readout document generated: {output_path}")


def main():
    parser = argparse.ArgumentParser(description='Generate A/B readout document from analysis JSON')
    parser.add_argument('--analysis', required=True, help='Path to analysis JSON file')
    parser.add_argument('--output', required=True, help='Output markdown file path')
    
    args = parser.parse_args()
    
    analysis_path = Path(args.analysis)
    if not analysis_path.exists():
        print(f"Error: Analysis file not found: {analysis_path}")
        return
    
    with open(analysis_path, 'r') as f:
        analysis_data = json.load(f)
    
    output_path = Path(args.output)
    generate_readout(analysis_data, output_path)


if __name__ == '__main__':
    main()
