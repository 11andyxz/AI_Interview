#!/usr/bin/env python3
"""
Re-analyze with CORRECT slice-aware policy (Week 16 recommendation).

Week 18 Bug: Used global min=6 (Senior standard) for all slices.
Week 16 Correct: Junior min=4, Mid min=5, Senior min=6.
"""
import sqlite3
import numpy as np
from scipy import stats
import json

DB_PATH = "results/ab_experiment.db"

def correct_early_stop_decision(policy: str, question_num: int, slice_type: str, confidence_score: float) -> bool:
    """
    CORRECTED slice-aware early-stop logic.
    
    Baseline Policy:
      - All slices: threshold 0.95/0.05, min 5 questions
    
    New Policy (CORRECTED - Week 16 recommendation):
      - Junior: threshold 0.90/0.10, min 4 questions  ← Fixed!
      - Mid:    threshold 0.90/0.10, min 5 questions
      - Senior: threshold 0.90/0.10, min 6 questions
    """
    if policy == "baseline":
        # Baseline: same for all slices
        if question_num < 5:
            return False
        return confidence_score > 0.95 or confidence_score < 0.05
    
    else:  # new policy - SLICE-AWARE
        if slice_type == "junior":
            if question_num < 4:  # ← Correct: 4 not 6!
                return False
            return confidence_score > 0.90 or confidence_score < 0.10
        
        elif slice_type == "mid":
            if question_num < 5:
                return False
            return confidence_score > 0.90 or confidence_score < 0.10
        
        else:  # senior
            if question_num < 6:
                return False
            return confidence_score > 0.90 or confidence_score < 0.10


def recompute_session(cursor, session_id, policy, slice_type):
    """Recompute session metrics with corrected policy."""
    cursor.execute("""
        SELECT question_num, confidence_score, latency_ms
        FROM questions
        WHERE session_id = ?
        ORDER BY question_num
    """, (session_id,))
    
    questions = cursor.fetchall()
    if not questions:
        return None
    
    # Find where to stop with corrected logic
    should_stop_at = len(questions)
    early_stopped = False
    
    for q_num, confidence, latency in questions:
        if correct_early_stop_decision(policy, q_num, slice_type, confidence):
            should_stop_at = q_num
            early_stopped = True
            break
    
    # Compute metrics
    relevant_questions = questions[:should_stop_at]
    avg_latency = sum(q[2] for q in relevant_questions) / len(relevant_questions)
    
    return {
        'num_questions': should_stop_at,
        'avg_latency_ms': avg_latency,
        'early_stopped': 1 if early_stopped else 0
    }


def main():
    conn = sqlite3.connect(DB_PATH)
    cursor = conn.cursor()
    
    print("=" * 70)
    print("CORRECTING WEEK 18 BUG: Applying slice-aware min_questions")
    print("=" * 70)
    print("\nWeek 18 bug: global min=6 (used Senior standard for all)")
    print("Week 16 correct: Junior min=4, Mid min=5, Senior min=6\n")
    
    # Update all sessions
    cursor.execute("SELECT id, experiment_id, policy, slice FROM sessions")
    sessions = cursor.fetchall()
    
    print(f"Recomputing {len(sessions)} sessions with CORRECTED policy...\n")
    
    for session_id, exp_id, policy, slice_type in sessions:
        metrics = recompute_session(cursor, session_id, policy, slice_type)
        if metrics:
            cursor.execute("""
                UPDATE sessions
                SET num_questions = ?,
                    avg_latency_ms = ?,
                    early_stopped = ?
                WHERE id = ?
            """, (metrics['num_questions'], metrics['avg_latency_ms'], 
                  metrics['early_stopped'], session_id))
    
    conn.commit()
    print("✓ All sessions updated\n")
    
    # Analyze results
    print("=" * 70)
    print("CORRECTED RESULTS")
    print("=" * 70)
    
    results = {}
    
    for exp_id in ['week20_control_real', 'week20_treatment_real']:
        policy_name = "Control" if "control" in exp_id else "Treatment"
        
        # Overall metrics
        cursor.execute("""
            SELECT 
                COUNT(*) as n,
                AVG(num_questions) as avg_q,
                SUM(early_stopped)*100.0/COUNT(*) as early_pct,
                MIN(num_questions) as min_q,
                MAX(num_questions) as max_q
            FROM sessions
            WHERE experiment_id = ?
        """, (exp_id,))
        
        n, avg_q, early_pct, min_q, max_q = cursor.fetchone()
        
        # Get all questions for std calculation
        cursor.execute("SELECT num_questions FROM sessions WHERE experiment_id = ?", (exp_id,))
        all_q = [row[0] for row in cursor.fetchall()]
        std_q = np.std(all_q, ddof=1) if len(all_q) > 1 else 0
        
        # Slice breakdown
        cursor.execute("""
            SELECT 
                slice,
                COUNT(*) as n,
                AVG(num_questions) as avg_q,
                SUM(early_stopped)*100.0/COUNT(*) as early_pct
            FROM sessions
            WHERE experiment_id = ?
            GROUP BY slice
            ORDER BY slice
        """, (exp_id,))
        
        slice_data = {}
        for slice_type, sn, savg, searly in cursor.fetchall():
            # Get slice questions for std
            cursor.execute("""
                SELECT num_questions FROM sessions 
                WHERE experiment_id = ? AND slice = ?
            """, (exp_id, slice_type))
            slice_q = [row[0] for row in cursor.fetchall()]
            sstd = np.std(slice_q, ddof=1) if len(slice_q) > 1 else 0
            
            slice_data[slice_type] = {
                'n': sn,
                'avg': savg,
                'std': sstd,
                'early_pct': searly
            }
        
        results[exp_id] = {
            'n': n,
            'avg': avg_q,
            'std': std_q if std_q else 0,
            'early_pct': early_pct,
            'min': min_q,
            'max': max_q,
            'slices': slice_data
        }
        
        print(f"\n{policy_name}:")
        print(f"  Sample size: {n}")
        print(f"  Avg questions: {avg_q:.2f} ± {std_q:.2f}")
        print(f"  Range: [{min_q}, {max_q}]")
        print(f"  Early-stop rate: {early_pct:.1f}%")
        print(f"\n  By slice:")
        for slice_type in ['junior', 'mid', 'senior']:
            if slice_type in slice_data:
                s = slice_data[slice_type]
                print(f"    {slice_type.capitalize()}: N={s['n']}, Avg={s['avg']:.2f}±{s['std']:.2f}, Early-stop={s['early_pct']:.1f}%")
    
    # Statistical comparison
    print("\n" + "=" * 70)
    print("STATISTICAL ANALYSIS")
    print("=" * 70)
    
    cursor.execute("SELECT num_questions FROM sessions WHERE experiment_id = 'week20_control_real'")
    control_questions = [row[0] for row in cursor.fetchall()]
    
    cursor.execute("SELECT num_questions FROM sessions WHERE experiment_id = 'week20_treatment_real'")
    treatment_questions = [row[0] for row in cursor.fetchall()]
    
    control_mean = np.mean(control_questions)
    treatment_mean = np.mean(treatment_questions)
    diff = treatment_mean - control_mean
    pct_change = (diff / control_mean) * 100
    
    # t-test
    t_stat, p_value = stats.ttest_ind(control_questions, treatment_questions)
    
    # Cohen's d
    pooled_std = np.sqrt(((len(control_questions)-1)*np.std(control_questions, ddof=1)**2 + 
                          (len(treatment_questions)-1)*np.std(treatment_questions, ddof=1)**2) / 
                         (len(control_questions) + len(treatment_questions) - 2))
    cohens_d = (control_mean - treatment_mean) / pooled_std
    
    # Confidence interval
    se_diff = np.sqrt(np.var(control_questions, ddof=1)/len(control_questions) + 
                      np.var(treatment_questions, ddof=1)/len(treatment_questions))
    ci_lower = diff - 1.96 * se_diff
    ci_upper = diff + 1.96 * se_diff
    
    print(f"\nControl:   {control_mean:.2f} ± {np.std(control_questions, ddof=1):.2f} (N={len(control_questions)})")
    print(f"Treatment: {treatment_mean:.2f} ± {np.std(treatment_questions, ddof=1):.2f} (N={len(treatment_questions)})")
    print(f"\nDifference: {diff:+.2f} questions ({pct_change:+.1f}%)")
    print(f"95% CI: [{ci_lower:.2f}, {ci_upper:.2f}]")
    print(f"\nt-statistic: {t_stat:.3f}")
    print(f"p-value: {p_value:.4f}")
    print(f"Significant: {'YES ✓' if p_value < 0.05 else 'NO'}")
    print(f"Cohen's d: {cohens_d:.3f} ({'small' if abs(cohens_d) < 0.5 else 'medium' if abs(cohens_d) < 0.8 else 'large'} effect)")
    
    if diff < 0:
        print(f"\n{'='*70}")
        print(f"✓ SUCCESS: Treatment REDUCES questions by {abs(pct_change):.1f}%")
        print(f"{'='*70}")
    else:
        print(f"\n✗ Treatment INCREASES questions by {pct_change:.1f}%")
    
    # Save analysis JSON
    analysis = {
        'timestamp': '2026-04-18T' + __import__('datetime').datetime.now().strftime('%H:%M:%S'),
        'experiment_ids': {
            'control': 'week20_control_real',
            'treatment': 'week20_treatment_real'
        },
        'methodology': 'CORRECTED slice-aware policy (Week 16 recommendation)',
        'policy_correction': {
            'week18_bug': 'global min_questions=6 (all slices)',
            'week16_correct': 'slice-aware min_questions (junior=4, mid=5, senior=6)'
        },
        'control': {
            'n': int(results['week20_control_real']['n']),
            'avg': float(results['week20_control_real']['avg']),
            'std': float(results['week20_control_real']['std']),
            'early_pct': float(results['week20_control_real']['early_pct']),
            'min': int(results['week20_control_real']['min']),
            'max': int(results['week20_control_real']['max']),
            'slices': {k: {kk: float(vv) if isinstance(vv, (np.floating, float)) else int(vv) 
                          for kk, vv in v.items()} 
                      for k, v in results['week20_control_real']['slices'].items()}
        },
        'treatment': {
            'n': int(results['week20_treatment_real']['n']),
            'avg': float(results['week20_treatment_real']['avg']),
            'std': float(results['week20_treatment_real']['std']),
            'early_pct': float(results['week20_treatment_real']['early_pct']),
            'min': int(results['week20_treatment_real']['min']),
            'max': int(results['week20_treatment_real']['max']),
            'slices': {k: {kk: float(vv) if isinstance(vv, (np.floating, float)) else int(vv) 
                          for kk, vv in v.items()} 
                      for k, v in results['week20_treatment_real']['slices'].items()}
        },
        'comparison': {
            'difference': float(diff),
            'percent_change': float(pct_change),
            'ci_95': [float(ci_lower), float(ci_upper)],
            't_statistic': float(t_stat),
            'p_value': float(p_value),
            'significant': bool(p_value < 0.05),
            'cohens_d': float(cohens_d),
            'effect_size': 'small' if abs(cohens_d) < 0.5 else 'medium' if abs(cohens_d) < 0.8 else 'large'
        },
        'decision': 'RAMP' if (diff < 0 and p_value < 0.05) else 'HOLD'
    }
    
    output_file = "results/ab_analysis_week20_corrected.json"
    with open(output_file, 'w') as f:
        json.dump(analysis, f, indent=2)
    
    print(f"\n✓ Analysis saved to {output_file}")
    
    conn.close()


if __name__ == "__main__":
    main()
