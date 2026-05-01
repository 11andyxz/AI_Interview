#!/usr/bin/env python3
"""
Auto Summary Generator for AI Interview Experiments
Week 20 Task 3: Eval Pipeline Hardening

Automatically generates experiment summaries including:
- Delta vs baseline metrics
- Guardrail pass/fail status
- Statistical significance
- Decision recommendations
"""

import json
import csv
import sys
from pathlib import Path
from typing import Dict, List, Any, Optional
from datetime import datetime, timezone
import math


class SummaryGenerator:
    """Generates standardized experiment summaries"""
    
    # Guardrail thresholds (from Week 20 acceptance criteria)
    GUARDRAILS = {
        'junior_rmse_max': 11.5,        # Junior RMSE ≤ 11.5
        'premature_stop_max': 0.03,     # Premature stop rate < 3%
        'min_questions_min': 3,         # At least 3 questions
        'rmse_degradation_max': 0.15,   # Max 15% RMSE increase
    }
    
    def load_experiment(self, experiment_id: str, registry_path: Path) -> Optional[Dict[str, Any]]:
        """Load experiment from registry"""
        with open(registry_path, 'r', encoding='utf-8-sig') as f:
            reader = csv.DictReader(f)
            for row in reader:
                if row.get('experiment_id', '') == experiment_id:
                    # Parse config_params JSON
                    if row.get('config_params'):
                        row['config'] = json.loads(row['config_params'])
                    return row
        return None
    
    def calculate_delta(self, value: float, baseline: float) -> Dict[str, Any]:
        """Calculate delta metrics"""
        if baseline == 0:
            return {'absolute': value, 'relative': None, 'direction': 'N/A'}
        
        absolute_delta = value - baseline
        relative_delta = (absolute_delta / baseline) * 100
        direction = 'improvement' if absolute_delta < 0 else 'degradation'
        
        return {
            'absolute': absolute_delta,
            'relative': relative_delta,
            'direction': direction
        }
    
    def check_guardrails(self, experiment: Dict[str, Any]) -> Dict[str, Any]:
        """Check if experiment passes guardrails"""
        results = {
            'all_pass': True,
            'checks': []
        }
        
        # Check Junior RMSE
        if experiment.get('slice') == 'junior' and experiment.get('rmse'):
            try:
                rmse = float(experiment['rmse'])
                passed = rmse <= self.GUARDRAILS['junior_rmse_max']
                results['checks'].append({
                    'name': 'Junior RMSE ≤ 11.5',
                    'value': rmse,
                    'threshold': self.GUARDRAILS['junior_rmse_max'],
                    'passed': passed
                })
                if not passed:
                    results['all_pass'] = False
            except (ValueError, TypeError):
                pass
        
        # Check premature stop rate
        config = experiment.get('config', {})
        early_stop_rate = experiment.get('early_stop_rate')
        if early_stop_rate and early_stop_rate != 'N/A':
            try:
                rate = float(early_stop_rate)
                passed = rate < self.GUARDRAILS['premature_stop_max']
                results['checks'].append({
                    'name': 'Premature stop < 3%',
                    'value': rate,
                    'threshold': self.GUARDRAILS['premature_stop_max'],
                    'passed': passed
                })
                if not passed:
                    results['all_pass'] = False
            except (ValueError, TypeError):
                pass
        
        # Check minimum questions
        if 'min_questions' in config:
            try:
                min_q = float(config['min_questions'])
                passed = min_q >= self.GUARDRAILS['min_questions_min']
                results['checks'].append({
                    'name': 'Min questions ≥ 3',
                    'value': min_q,
                    'threshold': self.GUARDRAILS['min_questions_min'],
                    'passed': passed
                })
                if not passed:
                    results['all_pass'] = False
            except (ValueError, TypeError):
                pass
        
        return results
    
    def calculate_significance(self, control_size: int, treatment_size: int, 
                              control_mean: float, treatment_mean: float,
                              control_std: float, treatment_std: float) -> Dict[str, Any]:
        """Calculate statistical significance (simple t-test approximation)"""
        if control_size == 0 or treatment_size == 0:
            return {'significant': False, 'p_value': None, 'note': 'Insufficient data'}
        
        # Pooled standard error
        se = math.sqrt((control_std**2 / control_size) + (treatment_std**2 / treatment_size))
        
        if se == 0:
            return {'significant': False, 'p_value': None, 'note': 'Zero variance'}
        
        # T-statistic
        t_stat = abs(control_mean - treatment_mean) / se
        
        # Rough p-value approximation (assuming normal distribution)
        # For t > 2.58: p < 0.01, t > 1.96: p < 0.05
        if t_stat > 2.58:
            p_value = '< 0.01'
            significant = True
        elif t_stat > 1.96:
            p_value = '< 0.05'
            significant = True
        else:
            p_value = '≥ 0.05'
            significant = False
        
        return {
            'significant': significant,
            'p_value': p_value,
            't_statistic': t_stat,
            'note': 'Two-tailed t-test approximation'
        }
    
    def generate_summary(self, experiment_id: str, baseline_id: Optional[str],
                        registry_path: Path) -> Dict[str, Any]:
        """Generate complete experiment summary"""
        experiment = self.load_experiment(experiment_id, registry_path)
        if not experiment:
            return {'error': f'Experiment not found: {experiment_id}'}
        
        summary = {
            'experiment_id': experiment_id,
            'timestamp': experiment['timestamp'],
            'model_version': experiment['model_version'],
            'slice': experiment['slice'],
            'config': experiment.get('config', {}),
            'metrics': {},
            'guardrails': {},
            'comparison': {},
            'recommendation': ''
        }
        
        # Extract metrics
        for metric in ['rmse', 'mae', 'brier_score', 'early_stop_rate', 'avg_questions']:
            if metric in experiment and experiment[metric] and experiment[metric] != 'N/A':
                try:
                    summary['metrics'][metric] = float(experiment[metric])
                except ValueError:
                    summary['metrics'][metric] = experiment[metric]
        
        # Check guardrails
        guardrail_results = self.check_guardrails(experiment)
        summary['guardrails'] = guardrail_results
        
        # Compare to baseline if provided
        if baseline_id:
            baseline = self.load_experiment(baseline_id, registry_path)
            if baseline:
                summary['comparison']['baseline_id'] = baseline_id
                summary['comparison']['deltas'] = {}
                
                for metric in ['rmse', 'mae', 'brier_score', 'early_stop_rate', 'avg_questions']:
                    if metric in summary['metrics'] and metric in baseline and baseline[metric] != 'N/A':
                        try:
                            baseline_val = float(baseline[metric])
                            current_val = summary['metrics'][metric]
                            delta = self.calculate_delta(current_val, baseline_val)
                            summary['comparison']['deltas'][metric] = delta
                        except (ValueError, TypeError):
                            pass
        
        # Generate recommendation
        summary['recommendation'] = self._generate_recommendation(summary)
        
        return summary
    
    def _generate_recommendation(self, summary: Dict[str, Any]) -> str:
        """Generate deployment recommendation"""
        guardrails_pass = summary['guardrails'].get('all_pass', False)
        has_comparison = bool(summary['comparison'].get('deltas'))
        
        if not guardrails_pass:
            return "❌ DO NOT DEPLOY - Guardrail violations detected"
        
        if not has_comparison:
            return "✓ GUARDRAILS PASS - Baseline measurement (no comparison)"
        
        # Check for improvements
        deltas = summary['comparison']['deltas']
        
        # Check question reduction (lower is better)
        if 'avg_questions' in deltas:
            q_delta = deltas['avg_questions']
            if q_delta['relative'] and q_delta['relative'] < -5:  # >5% reduction
                if 'rmse' in deltas and deltas['rmse']['relative'] < 15:  # <15% RMSE increase
                    return "✅ DEPLOY - Significant efficiency gain, quality maintained"
        
        # Check quality improvement (lower RMSE is better)
        if 'rmse' in deltas:
            rmse_delta = deltas['rmse']
            if rmse_delta['relative'] and rmse_delta['relative'] < -10:  # >10% improvement
                return "✅ DEPLOY - Significant quality improvement"
        
        # No significant change
        return "⚠ HOLD - No significant improvement vs baseline"
    
    def print_summary(self, summary: Dict[str, Any]):
        """Print formatted summary"""
        if 'error' in summary:
            print(f"ERROR: {summary['error']}")
            return
        
        print(f"\n{'='*80}")
        print(f"EXPERIMENT SUMMARY: {summary['experiment_id']}")
        print(f"{'='*80}\n")
        
        print(f"Timestamp:      {summary['timestamp']}")
        print(f"Model Version:  {summary['model_version']}")
        print(f"Slice:          {summary['slice']}")
        
        print(f"\n--- Configuration ---")
        for key, value in summary['config'].items():
            print(f"  {key}: {value}")
        
        print(f"\n--- Metrics ---")
        for metric, value in summary['metrics'].items():
            print(f"  {metric}: {value}")
        
        print(f"\n--- Guardrails ---")
        guardrails = summary['guardrails']
        status = "✓ ALL PASS" if guardrails['all_pass'] else "✗ FAILURES"
        print(f"Status: {status}")
        for check in guardrails['checks']:
            symbol = "✓" if check['passed'] else "✗"
            print(f"  {symbol} {check['name']}: {check['value']} (threshold: {check['threshold']})")
        
        if summary['comparison'].get('deltas'):
            print(f"\n--- Comparison to Baseline ({summary['comparison']['baseline_id']}) ---")
            for metric, delta in summary['comparison']['deltas'].items():
                if delta['relative'] is not None:
                    sign = '+' if delta['absolute'] > 0 else ''
                    print(f"  {metric}: {sign}{delta['absolute']:.2f} ({sign}{delta['relative']:.1f}%) - {delta['direction']}")
                else:
                    print(f"  {metric}: {delta['absolute']:.2f} (absolute)")
        
        print(f"\n--- Recommendation ---")
        print(f"{summary['recommendation']}")
        print(f"\n{'='*80}\n")


def main():
    """CLI for auto summary generation"""
    import argparse

    parser = argparse.ArgumentParser(
        description="Auto-generate ML experiment summary and weekly readout"
    )
    # Legacy positional interface (kept for backward compat)
    parser.add_argument("experiment_id", nargs="?", default=None,
                        help="Experiment ID to summarize (legacy positional arg)")
    parser.add_argument("baseline_id_pos", nargs="?", default=None,
                        help="Baseline experiment ID (legacy positional arg)")
    # Week-level readout flags (Week 22+)
    parser.add_argument("--week", type=int, default=None,
                        help="Generate a week-level readout for this week number (e.g. --week 22)")
    parser.add_argument("--include-live", action="store_true",
                        help="Include live stage results from eval/results/week<N>_stage_*_live.json")
    parser.add_argument("--output", default=None,
                        help="Write readout to this Markdown file path")
    args = parser.parse_args()

    registry_path = Path(__file__).parent / "experiment_registry.csv"
    if not registry_path.exists():
        print(f"ERROR: Registry not found at {registry_path}")
        sys.exit(1)

    if args.week is not None:
        _generate_week_readout(args.week, args.include_live, args.output, registry_path)
        return

    # Legacy single-experiment mode
    experiment_id = args.experiment_id
    if not experiment_id:
        print("Usage: python auto_summary_generator.py <experiment_id> [baseline_id]")
        print("       python auto_summary_generator.py --week 22 [--include-live] [--output path.md]")
        sys.exit(1)

    baseline_id = args.baseline_id_pos
    generator = SummaryGenerator()
    summary = generator.generate_summary(experiment_id, baseline_id, registry_path)
    generator.print_summary(summary)


def _generate_week_readout(week: int, include_live: bool, output: Optional[str],
                           registry_path: Path) -> None:
    """Generate a week-level ML decision readout Markdown document."""
    results_dir = registry_path.parent / "results"
    prefix = f"week{week}"

    # Load week entries from registry
    entries = []
    with open(registry_path, newline="", encoding="utf-8-sig") as f:
        reader = csv.DictReader(f)
        for row in reader:
            if row.get("experiment_id", "").startswith(prefix):
                entries.append(row)

    # Load live stage artifacts if requested
    live_stages: dict = {}
    if include_live:
        for stage in ("a", "b", "c"):
            # Try both naming patterns used by run_ramp_validation.py
            candidates = [
                results_dir / f"{prefix}_stage{stage}_live.json",
                results_dir / f"{prefix}_live_stage{stage}_result.json",
            ]
            for artifact in candidates:
                if artifact.exists():
                    with open(artifact) as f:
                        raw = json.load(f)
                    # run_ramp_validation.py wraps stage data under {"stages": [...]}
                    if "stages" in raw and raw["stages"]:
                        live_stages[stage.upper()] = raw["stages"][0]
                    else:
                        live_stages[stage.upper()] = raw
                    break

    lines = [
        f"# Week {week} ML Decision Readout",
        f"",
        f"**Generated**: {datetime.now(timezone.utc).strftime('%Y-%m-%dT%H:%M:%SZ')}  ",
        f"**Source**: `eval/auto_summary_generator.py --week {week}"
        + (" --include-live" if include_live else "") + "`",
        f"",
        f"## Registry Entries for Week {week}",
        f"",
        f"| Experiment ID | Stage | Traffic % | Decision | Notes |",
        f"|---------------|-------|-----------|----------|-------|",
    ]

    for e in entries:
        config = {}
        try:
            config = json.loads(e.get("config_params", "{}"))
        except (json.JSONDecodeError, TypeError):
            pass
        stage = config.get("stage", "?")
        traffic = config.get("traffic_pct", "?")
        decision = config.get("decision", e.get("notes", "TBD").split()[-1] if e.get("notes") else "TBD")
        notes = e.get("notes", "")[:80]
        lines.append(f"| {e['experiment_id']} | {stage} | {traffic}% | {decision} | {notes} |")

    if not entries:
        lines.append(f"| _(no entries yet)_ | — | — | — | — |")

    lines += ["", "## Live Stage Results", ""]
    if live_stages:
        for stage_label, data in live_stages.items():
            decision = data.get("decision", "N/A")
            rationale = data.get("rationale", [])
            metrics = data.get("metrics", {})
            source = data.get("data_source", "unknown")
            ts = data.get("timestamp", "N/A")
            lines.append(f"### Stage {stage_label} — {decision}")
            lines.append(f"")
            lines.append(f"- **Data source**: {source}")
            lines.append(f"- **Timestamp**: {ts}")
            lines.append(f"- **n_control**: {metrics.get('n_control', 'N/A')}")
            lines.append(f"- **n_treatment**: {metrics.get('n_treatment', 'N/A')}")
            if metrics.get("avg_questions_delta_pct") is not None:
                lines.append(f"- **avg_questions_delta_pct**: {metrics.get('avg_questions_delta_pct')}%")
            if metrics.get("premature_stop_rate") is not None:
                lines.append(f"- **premature_stop_rate**: {metrics.get('premature_stop_rate'):.1%}" if isinstance(metrics.get("premature_stop_rate"), float) else f"- **premature_stop_rate**: {metrics.get('premature_stop_rate')}")
            lines.append(f"- **Rationale**: {'; '.join(rationale) if rationale else 'N/A'}")
            lines.append(f"")
    else:
        lines.append("_Live stage artifacts not yet available or --include-live not specified._")
        lines.append("")

    lines += [
        "## Guardrail Summary",
        "",
        "| Guardrail | Threshold | Status |",
        "|-----------|-----------|--------|",
        "| avg_questions_delta_pct | <= +5% | _TBD_ |",
        "| premature_stop_rate | < 3% | _TBD_ |",
        "| p95_latency_ms | < 3000 ms | _TBD_ |",
        "| junior_rmse | <= 45.0 | _TBD_ |",
        "",
        f"_Update this section after Stage C completes._",
    ]

    content = "\n".join(lines) + "\n"

    if output:
        out_path = Path(output)
        out_path.parent.mkdir(parents=True, exist_ok=True)
        out_path.write_text(content, encoding="utf-8")
        print(f"Readout written to {output}")
    else:
        sys.stdout.buffer.write(content.encode("utf-8"))
        sys.stdout.buffer.flush()


if __name__ == '__main__':
    main()
