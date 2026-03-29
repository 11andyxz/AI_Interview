#!/usr/bin/env python3
"""
Compute standardized ML metrics for AI Interview evaluation.

This module provides functions to calculate RMSE, MAE, Brier score,
and other metrics for offline model evaluation.

Usage:
    python compute_metrics.py --input results/eval_results_latest.csv --output metrics_report.json
"""

import argparse
import json
import csv
import numpy as np
from typing import List, Dict, Any, Tuple
from datetime import datetime
import pandas as pd


def compute_rmse(y_true: List[float], y_pred: List[float]) -> float:
    """
    Compute Root Mean Squared Error.
    
    Args:
        y_true: Ground truth scores
        y_pred: Predicted scores
        
    Returns:
        RMSE value
    """
    if len(y_true) != len(y_pred):
        raise ValueError(f"Length mismatch: y_true={len(y_true)}, y_pred={len(y_pred)}")
    
    y_true = np.array(y_true)
    y_pred = np.array(y_pred)
    
    return np.sqrt(np.mean((y_true - y_pred) ** 2))


def compute_mae(y_true: List[float], y_pred: List[float]) -> float:
    """
    Compute Mean Absolute Error.
    
    Args:
        y_true: Ground truth scores
        y_pred: Predicted scores
        
    Returns:
        MAE value
    """
    if len(y_true) != len(y_pred):
        raise ValueError(f"Length mismatch: y_true={len(y_true)}, y_pred={len(y_pred)}")
    
    y_true = np.array(y_true)
    y_pred = np.array(y_pred)
    
    return np.mean(np.abs(y_true - y_pred))


def compute_brier_score(y_true_binary: List[int], y_pred_prob: List[float]) -> float:
    """
    Compute Brier score for binary classification.
    
    Brier Score = (1/N) * Σ(predicted_prob - actual_outcome)^2
    Lower is better (0 = perfect, 1 = worst).
    
    Args:
        y_true_binary: True labels (0 or 1)
        y_pred_prob: Predicted probabilities (0.0 to 1.0)
        
    Returns:
        Brier score
    """
    if len(y_true_binary) != len(y_pred_prob):
        raise ValueError(f"Length mismatch: y_true={len(y_true_binary)}, y_pred={len(y_pred_prob)}")
    
    y_true_binary = np.array(y_true_binary)
    y_pred_prob = np.array(y_pred_prob)
    
    return np.mean((y_pred_prob - y_true_binary) ** 2)


def compute_calibration_error(y_true_binary: List[int], y_pred_prob: List[float], n_bins: int = 10) -> float:
    """
    Compute Expected Calibration Error (ECE).
    
    ECE measures how well predicted probabilities match actual outcomes.
    Predictions are binned by confidence level, and calibration error is
    the weighted average of |predicted - actual| within each bin.
    
    Args:
        y_true_binary: True labels (0 or 1)
        y_pred_prob: Predicted probabilities (0.0 to 1.0)
        n_bins: Number of bins for calibration (default: 10)
        
    Returns:
        Expected Calibration Error
    """
    y_true_binary = np.array(y_true_binary)
    y_pred_prob = np.array(y_pred_prob)
    
    bins = np.linspace(0, 1, n_bins + 1)
    bin_indices = np.digitize(y_pred_prob, bins) - 1
    bin_indices = np.clip(bin_indices, 0, n_bins - 1)
    
    ece = 0.0
    total_samples = len(y_true_binary)
    
    for i in range(n_bins):
        mask = bin_indices == i
        if not mask.any():
            continue
        
        bin_size = mask.sum()
        bin_true = y_true_binary[mask].mean()
        bin_pred = y_pred_prob[mask].mean()
        
        ece += (bin_size / total_samples) * abs(bin_pred - bin_true)
    
    return ece


def compute_percentile_latency(latencies: List[float], percentiles: List[int] = [50, 90, 95, 99]) -> Dict[str, float]:
    """
    Compute latency percentiles.
    
    Args:
        latencies: List of latency values in milliseconds
        percentiles: Percentiles to compute (default: [50, 90, 95, 99])
        
    Returns:
        Dictionary mapping percentile names to values
    """
    latencies = np.array(latencies)
    
    result = {}
    for p in percentiles:
        result[f"p{p}"] = np.percentile(latencies, p)
    
    return result


def compute_early_stop_metrics(results: List[Dict[str, Any]]) -> Dict[str, Any]:
    """
    Compute early-stopping specific metrics.
    
    Args:
        results: List of evaluation result dictionaries
        
    Returns:
        Dictionary with early-stop metrics
    """
    total_sessions = len(results)
    early_stopped = sum(1 for r in results if r.get('early_stopped', False))
    
    stopped_questions = [r['num_questions'] for r in results if r.get('early_stopped', False)]
    full_questions = [r['num_questions'] for r in results if not r.get('early_stopped', False)]
    
    metrics = {
        'early_stop_rate': early_stopped / total_sessions if total_sessions > 0 else 0.0,
        'avg_questions_when_stopped': np.mean(stopped_questions) if stopped_questions else 0.0,
        'avg_questions_full': np.mean(full_questions) if full_questions else 0.0,
        'avg_questions_overall': np.mean([r['num_questions'] for r in results]) if results else 0.0,
        'question_reduction_pct': 0.0  # Will be calculated when comparing to baseline
    }
    
    return metrics


def analyze_slice_performance(results: List[Dict[str, Any]], slice_key: str) -> Dict[str, Dict[str, Any]]:
    """
    Analyze performance broken down by slices (e.g., job role, seniority).
    
    Args:
        results: List of evaluation result dictionaries
        slice_key: Key to slice by (e.g., 'job_role', 'seniority_level')
        
    Returns:
        Dictionary mapping slice values to metric dictionaries
    """
    slices = {}
    
    for result in results:
        slice_value = result.get(slice_key, 'unknown')
        
        if slice_value not in slices:
            slices[slice_value] = []
        
        slices[slice_value].append(result)
    
    slice_metrics = {}
    for slice_value, slice_results in slices.items():
        y_true = [r['actual_score'] for r in slice_results if 'actual_score' in r]
        y_pred = [r['predicted_score'] for r in slice_results if 'predicted_score' in r]
        
        if y_true and y_pred and len(y_true) == len(y_pred):
            slice_metrics[slice_value] = {
                'sample_size': len(slice_results),
                'rmse': compute_rmse(y_true, y_pred),
                'mae': compute_mae(y_true, y_pred),
                'avg_questions': np.mean([r['num_questions'] for r in slice_results])
            }
    
    return slice_metrics


def generate_metrics_report(results: List[Dict[str, Any]], model_version: str, config_params: Dict[str, Any]) -> Dict[str, Any]:
    """
    Generate a comprehensive metrics report.
    
    Args:
        results: List of evaluation result dictionaries
        model_version: Model version identifier
        config_params: Configuration parameters used
        
    Returns:
        Complete metrics report dictionary
    """
    # Extract scores
    y_true = [r['actual_score'] for r in results if 'actual_score' in r]
    y_pred = [r['predicted_score'] for r in results if 'predicted_score' in r]
    
    # Extract binary outcomes for Brier score (pass/fail)
    y_true_binary = [1 if r['actual_outcome'] == 'pass' else 0 for r in results if 'actual_outcome' in r]
    y_pred_prob = [r['predicted_pass_prob'] for r in results if 'predicted_pass_prob' in r]
    
    # Extract latencies
    latencies = [r['latency_ms'] for r in results if 'latency_ms' in r]
    
    report = {
        'experiment_id': f"{model_version}_{datetime.now().strftime('%Y%m%d_%H%M%S')}",
        'timestamp': datetime.now().isoformat(),
        'model_version': model_version,
        'config_params': config_params,
        'sample_size': len(results),
        
        # Regression metrics
        'rmse': compute_rmse(y_true, y_pred) if y_true and y_pred else None,
        'mae': compute_mae(y_true, y_pred) if y_true and y_pred else None,
        
        # Classification metrics
        'brier_score': compute_brier_score(y_true_binary, y_pred_prob) if y_true_binary and y_pred_prob else None,
        'calibration_error': compute_calibration_error(y_true_binary, y_pred_prob) if y_true_binary and y_pred_prob else None,
        
        # Latency metrics
        'avg_latency_ms': np.mean(latencies) if latencies else None,
        'latency_percentiles': compute_percentile_latency(latencies) if latencies else {},
        
        # Early-stop metrics
        'early_stop_metrics': compute_early_stop_metrics(results) if results else {},
        
        # Additional stats
        'failure_rate': sum(1 for r in results if r.get('error')) / len(results) if results else 0.0
    }
    
    return report


def compare_experiments(baseline_report: Dict[str, Any], candidate_report: Dict[str, Any]) -> Dict[str, Any]:
    """
    Compare two experiment reports and compute deltas.
    
    Args:
        baseline_report: Baseline experiment metrics
        candidate_report: Candidate experiment metrics
        
    Returns:
        Comparison report with absolute and relative deltas
    """
    comparison = {
        'baseline_id': baseline_report['experiment_id'],
        'candidate_id': candidate_report['experiment_id'],
        'timestamp': datetime.now().isoformat(),
        'deltas': {}
    }
    
    # Compare key metrics
    metrics_to_compare = ['rmse', 'mae', 'brier_score', 'calibration_error', 'avg_latency_ms']
    
    for metric in metrics_to_compare:
        baseline_val = baseline_report.get(metric)
        candidate_val = candidate_report.get(metric)
        
        if baseline_val is not None and candidate_val is not None:
            absolute_delta = candidate_val - baseline_val
            relative_delta = (candidate_val - baseline_val) / baseline_val if baseline_val != 0 else 0.0
            
            comparison['deltas'][metric] = {
                'baseline': baseline_val,
                'candidate': candidate_val,
                'absolute_delta': absolute_delta,
                'relative_delta_pct': relative_delta * 100
            }
    
    # Compare early-stop metrics
    baseline_early_stop = baseline_report.get('early_stop_metrics', {})
    candidate_early_stop = candidate_report.get('early_stop_metrics', {})
    
    if baseline_early_stop and candidate_early_stop:
        baseline_questions = baseline_early_stop.get('avg_questions_overall', 0)
        candidate_questions = candidate_early_stop.get('avg_questions_overall', 0)
        
        comparison['deltas']['avg_questions'] = {
            'baseline': baseline_questions,
            'candidate': candidate_questions,
            'absolute_delta': candidate_questions - baseline_questions,
            'relative_delta_pct': ((candidate_questions - baseline_questions) / baseline_questions * 100) if baseline_questions > 0 else 0.0
        }
    
    return comparison


def load_and_compute_metrics(csv_file: str, model_version: str, config_params: Dict[str, Any]) -> Dict[str, Any]:
    """
    Load results from CSV and compute all metrics.
    
    Args:
        csv_file: Path to CSV file with evaluation results
        model_version: Model version identifier
        config_params: Configuration parameters
        
    Returns:
        Complete metrics report
    """
    results = []
    
    with open(csv_file, 'r', encoding='utf-8') as f:
        reader = csv.DictReader(f)
        for row in reader:
            # Convert numeric fields
            result = {
                'actual_score': float(row.get('actual_score', 0)) if row.get('actual_score') else None,
                'predicted_score': float(row.get('predicted_score', 0)) if row.get('predicted_score') else None,
                'actual_outcome': row.get('actual_outcome'),
                'predicted_pass_prob': float(row.get('predicted_pass_prob', 0)) if row.get('predicted_pass_prob') else None,
                'latency_ms': float(row.get('latency_ms', 0)) if row.get('latency_ms') else None,
                'num_questions': int(row.get('num_questions', 0)) if row.get('num_questions') else None,
                'early_stopped': row.get('early_stopped', 'false').lower() == 'true',
                'job_role': row.get('job_role'),
                'seniority_level': row.get('seniority_level'),
                'error': row.get('error')
            }
            results.append(result)
    
    return generate_metrics_report(results, model_version, config_params)


def main():
    """Main entry point for standalone execution."""
    parser = argparse.ArgumentParser(description='Compute standardized ML metrics for AI Interview evaluation')
    parser.add_argument('--input', required=True, help='Input CSV file with evaluation results')
    parser.add_argument('--output', required=True, help='Output JSON file for metrics report')
    parser.add_argument('--model-version', default='unknown', help='Model version identifier')
    parser.add_argument('--config', default='{}', help='Configuration parameters as JSON string')
    parser.add_argument('--baseline', help='Baseline metrics JSON for comparison')
    
    args = parser.parse_args()
    
    config_params = json.loads(args.config)
    
    print(f"[INFO] Computing metrics for {args.input}")
    report = load_and_compute_metrics(args.input, args.model_version, config_params)
    
    # If baseline provided, compute comparison
    if args.baseline:
        with open(args.baseline, 'r') as f:
            baseline_report = json.load(f)
        
        comparison = compare_experiments(baseline_report, report)
        report['comparison'] = comparison
        
        print(f"[INFO] Comparison to baseline:")
        for metric, delta in comparison['deltas'].items():
            print(f"  {metric}: {delta['baseline']:.3f} → {delta['candidate']:.3f} ({delta['relative_delta_pct']:+.1f}%)")
    
    # Write report
    with open(args.output, 'w') as f:
        json.dump(report, f, indent=2)
    
    print(f"[OK] Metrics report saved to {args.output}")
    print(f"  RMSE: {report.get('rmse', 'N/A')}")
    print(f"  MAE: {report.get('mae', 'N/A')}")
    print(f"  Brier Score: {report.get('brier_score', 'N/A')}")
    print(f"  Sample Size: {report.get('sample_size', 0)}")


if __name__ == '__main__':
    main()
