#!/usr/bin/env python3
"""
Slice-aware threshold optimization.
Find optimal threshold for each slice (junior/mid/senior) based on empirical data.
"""
import sqlite3
import numpy as np
from scipy import stats

DB_PATH = "results/ab_experiment.db"

def test_threshold_for_slice(cursor, slice_type, threshold, min_questions):
    """Test a specific threshold for a specific slice."""
    # Get all treatment sessions for this slice
    cursor.execute("""
        SELECT s.id FROM sessions s
        WHERE s.experiment_id = 'week20_treatment_real' AND s.slice = ?
    """, (slice_type,))
    
    session_ids = [row[0] for row in cursor.fetchall()]
    
    results = []
    for session_id in session_ids:
        cursor.execute("""
            SELECT question_num, confidence_score
            FROM questions
            WHERE session_id = ?
            ORDER BY question_num
        """, (session_id,))
        
        questions = cursor.fetchall()
        
        # Find early-stop point
        stopped_at = len(questions)
        early_stopped = False
        
        for q_num, confidence in questions:
            if q_num < min_questions:
                continue
            if confidence > threshold or confidence < (1 - threshold):
                stopped_at = q_num
                early_stopped = True
                break
        
        results.append({
            'num_questions': stopped_at,
            'early_stopped': early_stopped
        })
    
    avg_questions = np.mean([r['num_questions'] for r in results])
    early_stop_pct = sum(r['early_stopped'] for r in results) * 100 / len(results)
    
    return {
        'avg_questions': avg_questions,
        'early_stop_pct': early_stop_pct,
        'n': len(results)
    }


def main():
    conn = sqlite3.connect(DB_PATH)
    cursor = conn.cursor()
    
    print("="*70)
    print("SLICE-AWARE THRESHOLD OPTIMIZATION")
    print("="*70)
    print("\nTesting thresholds: 0.80, 0.82, 0.85, 0.87, 0.90, 0.92")
    print("Per slice: Junior (min=4), Mid (min=5), Senior (min=6)\n")
    
    # Get control baseline per slice
    baseline = {}
    for slice_type in ['junior', 'mid', 'senior']:
        cursor.execute("""
            SELECT AVG(num_questions) FROM sessions
            WHERE experiment_id = 'week20_control_real' AND slice = ?
        """, (slice_type,))
        baseline[slice_type] = cursor.fetchone()[0]
    
    # Test different thresholds per slice
    thresholds = [0.80, 0.82, 0.85, 0.87, 0.90, 0.92]
    
    slice_configs = {
        'junior': 4,
        'mid': 5,
        'senior': 6
    }
    
    best_configs = {}
    
    for slice_type, min_q in slice_configs.items():
        print(f"\n{'='*70}")
        print(f"{slice_type.upper()} SLICE (min={min_q} questions)")
        print(f"Baseline: {baseline[slice_type]:.2f} questions")
        print(f"{'='*70}")
        
        results = []
        for threshold in thresholds:
            result = test_threshold_for_slice(cursor, slice_type, threshold, min_q)
            reduction = result['avg_questions'] - baseline[slice_type]
            reduction_pct = (reduction / baseline[slice_type]) * 100
            
            results.append({
                'threshold': threshold,
                'avg_questions': result['avg_questions'],
                'reduction': reduction,
                'reduction_pct': reduction_pct,
                'early_stop_pct': result['early_stop_pct']
            })
            
            print(f"\nThreshold {threshold}:")
            print(f"  Avg questions: {result['avg_questions']:.2f} ({reduction:+.2f}, {reduction_pct:+.1f}%)")
            print(f"  Early-stop rate: {result['early_stop_pct']:.1f}%")
        
        # Find best (maximize reduction while keeping early-stop reasonable)
        # Prioritize reduction, but warn if early-stop > 80%
        best = max(results, key=lambda x: abs(x['reduction']))
        
        print(f"\n→ BEST: threshold={best['threshold']}")
        print(f"  Reduction: {best['reduction']:+.2f} ({best['reduction_pct']:+.1f}%)")
        print(f"  Early-stop: {best['early_stop_pct']:.1f}%")
        
        if best['early_stop_pct'] > 80:
            print(f"  ⚠️ WARNING: Early-stop rate very high (>{80}%)")
        
        best_configs[slice_type] = best
    
    # Overall recommendation
    print(f"\n\n{'='*70}")
    print("RECOMMENDED SLICE-AWARE CONFIGURATION")
    print(f"{'='*70}")
    
    print("\n```yaml")
    print("early_stopping:")
    print("  enabled: true")
    print("  slices:")
    for slice_type in ['junior', 'mid', 'senior']:
        config = best_configs[slice_type]
        min_q = slice_configs[slice_type]
        print(f"    {slice_type}:")
        print(f"      min_questions: {min_q}")
        print(f"      pass_threshold: {config['threshold']}")
        print(f"      fail_threshold: {1 - config['threshold']:.2f}")
        print(f"      # Expected: {config['reduction_pct']:+.1f}% reduction, {config['early_stop_pct']:.1f}% early-stop")
    print("```")
    
    # Calculate overall impact
    total_reduction = sum(best_configs[s]['reduction'] * (
        24/60 if s == 'junior' else 18/60 if s == 'mid' else 18/60
    ) for s in ['junior', 'mid', 'senior'])
    
    overall_baseline = sum(baseline[s] * (
        24/60 if s == 'junior' else 18/60 if s == 'mid' else 18/60
    ) for s in ['junior', 'mid', 'senior'])
    
    overall_pct = (total_reduction / overall_baseline) * 100
    
    print(f"\nExpected overall impact:")
    print(f"  Baseline: {overall_baseline:.2f} questions")
    print(f"  Optimized: {overall_baseline + total_reduction:.2f} questions")
    print(f"  Reduction: {total_reduction:+.2f} ({overall_pct:+.1f}%)")
    
    # Compare to uniform threshold
    print(f"\n{'='*70}")
    print("COMPARISON: Slice-aware vs Uniform Threshold")
    print(f"{'='*70}")
    
    # Test uniform 0.85
    uniform_results = {}
    for slice_type, min_q in slice_configs.items():
        result = test_threshold_for_slice(cursor, slice_type, 0.85, min_q)
        uniform_results[slice_type] = result['avg_questions'] - baseline[slice_type]
    
    uniform_total = sum(uniform_results[s] * (
        24/60 if s == 'junior' else 18/60 if s == 'mid' else 18/60
    ) for s in ['junior', 'mid', 'senior'])
    uniform_pct = (uniform_total / overall_baseline) * 100
    
    print(f"\nUniform threshold=0.85:")
    print(f"  Reduction: {uniform_total:+.2f} ({uniform_pct:+.1f}%)")
    
    print(f"\nSlice-aware (optimized):")
    print(f"  Reduction: {total_reduction:+.2f} ({overall_pct:+.1f}%)")
    
    print(f"\nImprovement: {(total_reduction - uniform_total):+.2f} questions ({(overall_pct - uniform_pct):+.1f}pp)")
    
    conn.close()


if __name__ == "__main__":
    main()
