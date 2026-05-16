import sqlite3
import numpy as np

conn = sqlite3.connect('results/ab_experiment.db')
cursor = conn.cursor()

print("=== CONFIDENCE & EARLY-STOP ANALYSIS ===\n")

for exp_id in ['week20_control_real', 'week20_treatment_real']:
    policy = 'baseline (0.95)' if 'control' in exp_id else 'new (0.90)'
    
    print(f"{exp_id} ({policy}):")
    
    # Get early-stopped sessions
    cursor.execute("""
        SELECT s.slice, s.num_questions, s.early_stopped
        FROM sessions s
        WHERE s.experiment_id = ?
        ORDER BY s.slice, s.num_questions
    """, (exp_id,))
    
    sessions_by_slice = {'junior': [], 'mid': [], 'senior': []}
    for slice_type, num_q, early_stop in cursor.fetchall():
        sessions_by_slice[slice_type].append((num_q, early_stop))
    
    for slice_type in ['junior', 'mid', 'senior']:
        sessions = sessions_by_slice[slice_type]
        if not sessions:
            continue
        
        num_qs = [s[0] for s in sessions]
        early_stops = [s[1] for s in sessions]
        
        early_stop_count = sum(early_stops)
        early_stop_pct = early_stop_count * 100 / len(sessions)
        
        # Check min_questions distribution
        if 'control' in exp_id:
            min_q = 5
        else:
            min_q = 4 if slice_type == 'junior' else 5 if slice_type == 'mid' else 6
        
        at_min = sum(1 for q in num_qs if q == min_q)
        at_min_pct = at_min * 100 / len(sessions)
        
        print(f"  {slice_type.capitalize()}: N={len(sessions)}")
        print(f"    Avg questions: {np.mean(num_qs):.2f} (range: {min(num_qs)}-{max(num_qs)})")
        print(f"    Early-stopped: {early_stop_count}/{len(sessions)} ({early_stop_pct:.1f}%)")
        print(f"    At min ({min_q}Q): {at_min}/{len(sessions)} ({at_min_pct:.1f}%)")
        
        # Get confidence scores for this slice
        cursor.execute("""
            SELECT q.confidence_score
            FROM questions q
            JOIN sessions s ON q.session_id = s.id
            WHERE s.experiment_id = ? AND s.slice = ?
        """, (exp_id, slice_type))
        
        confidences = [row[0] for row in cursor.fetchall()]
        threshold = 0.95 if 'control' in exp_id else 0.90
        
        above_threshold = sum(1 for c in confidences if c > threshold)
        above_pct = above_threshold * 100 / len(confidences)
        
        print(f"    Confidence: mean={np.mean(confidences):.3f}, >{threshold}: {above_pct:.1f}%")
        print()
    
    print()

conn.close()
