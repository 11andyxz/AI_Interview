#!/usr/bin/env python3
"""
Model Comparison Script
Compare two different models using the evaluation harness
"""

import subprocess
import json
import os
from pathlib import Path
from functools import lru_cache


def run_eval_for_model(model_name: str, output_suffix: str):
    """Run evaluation harness for a specific model"""
    print(f"\n{'='*60}")
    print(f"Running evaluation for: {model_name}")
    print(f"{'='*60}\n")
    
    # Set environment variable for model selection (if needed)
    env = os.environ.copy()
    env['EVAL_MODEL'] = model_name
    
    # Run the evaluation
    result = subprocess.run(
        ["python", "run_eval.py", "--mode", "backend", "--output-suffix", output_suffix],
        env=env,
        capture_output=True,
        text=True
    )
    
    if result.returncode != 0:
        print(f"Error running evaluation: {result.stderr}")
        return None
    
    print(result.stdout)
    return True


def compare_results(baseline_file: str, comparison_file: str):
    """Compare two evaluation result files"""
    
    with open(baseline_file, 'r', encoding='utf-8') as f:
        baseline = json.load(f)
    
    with open(comparison_file, 'r', encoding='utf-8') as f:
        comparison = json.load(f)
    
    print(f"\n{'='*60}")
    print("MODEL COMPARISON SUMMARY")
    print(f"{'='*60}\n")
    
    print(f"Baseline Model: {baseline.get('model', 'gpt-4o-mini')}")
    print(f"Comparison Model: {comparison.get('model', 'gpt-3.5-turbo')}")
    print()
    
    # Compare key metrics
    metrics = [
        ('total_tests', 'Total Tests'),
        ('passed', 'Passed'),
        ('failed', 'Failed'),
        ('avg_latency', 'Avg Latency (s)'),
        ('p95_latency', 'P95 Latency (s)'),
        ('avg_quality_score', 'Avg Quality Score'),
    ]
    
    print(f"{'Metric':<25} {'Baseline':<15} {'Comparison':<15} {'Diff':<15}")
    print("-" * 70)
    
    for key, label in metrics:
        base_val = baseline.get(key, 0)
        comp_val = comparison.get(key, 0)
        
        if isinstance(base_val, float):
            diff = comp_val - base_val
            print(f"{label:<25} {base_val:<15.2f} {comp_val:<15.2f} {diff:+.2f}")
        else:
            diff = comp_val - base_val
            print(f"{label:<25} {base_val:<15} {comp_val:<15} {diff:+d}")
    
    print()
    
    # Quality breakdown
    if 'quality_breakdown' in baseline and 'quality_breakdown' in comparison:
        print("Quality Score Breakdown:")
        print(f"{'Dimension':<25} {'Baseline':<15} {'Comparison':<15}")
        print("-" * 55)
        
        for dim in ['completeness', 'format', 'factuality', 'coherence']:
            base_score = baseline['quality_breakdown'].get(dim, 0)
            comp_score = comparison['quality_breakdown'].get(dim, 0)
            print(f"{dim.title():<25} {base_score:<15.1f} {comp_score:<15.1f}")
        
        print()
    
    # Winner determination
    print("Winner Analysis:")
    if comparison['avg_quality_score'] > baseline['avg_quality_score']:
        print(f"  Quality: Comparison model (+{comparison['avg_quality_score'] - baseline['avg_quality_score']:.1f} points)")
    else:
        print(f"  Quality: Baseline model (better by {baseline['avg_quality_score'] - comparison['avg_quality_score']:.1f} points)")
    
    if comparison['avg_latency'] < baseline['avg_latency']:
        print(f"  Speed: Comparison model ({comparison['avg_latency']:.2f}s vs {baseline['avg_latency']:.2f}s)")
    else:
        print(f"  Speed: Baseline model ({baseline['avg_latency']:.2f}s vs {comparison['avg_latency']:.2f}s)")
    
    print(f"{'='*60}\n")


@lru_cache(maxsize=128)
def cached_model_response(prompt):
    """Simulate a cached model response"""
    # Simulate model response logic (replace with actual call if needed)
    return f"Response for: {prompt}"


def main():
    baseline_file = "results/eval_results_baseline.json"
    comparison_file = "results/eval_results_comparison.json"

    if not os.path.exists(baseline_file):
        print("Baseline results not found. Running baseline evaluation...")
        run_eval_for_model("gpt-4o-mini", "baseline")

    print("\nRunning comparison evaluation...")
    run_eval_for_model("gpt-3.5-turbo", "comparison")

    print("\nTesting cached responses:")
        print(cached_model_response("What is AI?"));print(cached_model_response("What is AI?"));print(cached_model_response("Explain machine learning."))

    if os.path.exists(baseline_file) and os.path.exists(comparison_file):
        print("\nComparing results...")
        compare_results(baseline_file, comparison_file)
    else:
        print("Error: Could not find result files for comparison")

if __name__ == "__main__":
    main()
    print(cached_model_response("What is AI?"))
