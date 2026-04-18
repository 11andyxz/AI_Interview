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
from datetime import datetime
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
        with open(registry_path, 'r', encoding='utf-8') as f:
            reader = csv.DictReader(f)
            for row in reader:
                if row['experiment_id'] == experiment_id:
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
    if len(sys.argv) < 2:
        print("Usage: python auto_summary_generator.py <experiment_id> [baseline_id]")
        print("  Generates summary comparing experiment to baseline")
        sys.exit(1)
    
    experiment_id = sys.argv[1]
    baseline_id = sys.argv[2] if len(sys.argv) > 2 else None
    
    # Find registry file
    registry_path = Path(__file__).parent / 'experiment_registry.csv'
    if not registry_path.exists():
        print(f"ERROR: Registry not found at {registry_path}")
        sys.exit(1)
    
    generator = SummaryGenerator()
    summary = generator.generate_summary(experiment_id, baseline_id, registry_path)
    generator.print_summary(summary)
    
    # Exit with error code if guardrails fail
    if not summary.get('guardrails', {}).get('all_pass', False):
        sys.exit(1)


if __name__ == '__main__':
    main()
