import sqlite3
import numpy as np

conn = sqlite3.connect('results/ab_experiment.db')
cursor = conn.cursor()

print("=== DETAILED ANALYSIS ===\n")

for exp_id in ['week20_control_real', 'week20_treatment_real']:
    policy = 'baseline' if 'control' in exp_id else 'new'
    min_q = 5 if policy == 'baseline' else 6
    threshold = 0.95 if policy == 'baseline' else 0.90
    
    cursor.execute("""
        SELECT num_questions, early_stopped
        FROM sessions
        WHERE experiment_id = ?
    """, (exp_id,))
    
    sessions = cursor.fetchall()
    questions = [s[0] for s in sessions]
    early_stopped = [s[1] for s in sessions]
    
    print(f"{exp_id} ({policy}, threshold={threshold}, min={min_q}):")
    print(f"  N = {len(sessions)}")
    print(f"  Avg questions: {np.mean(questions):.2f} ± {np.std(questions):.2f}")
    print(f"  Range: [{min(questions)}, {max(questions)}]")
    print(f"  Early-stop rate: {sum(early_stopped)*100/len(sessions):.1f}%")
    
    # Count sessions at each length
    print(f"  Distribution:")
    for q_len in range(min(questions), max(questions)+1):
        count = sum(1 for q in questions if q == q_len)
        pct = count*100/len(sessions)
        print(f"    {q_len} questions: {count} sessions ({pct:.1f}%)")
    
    print()

conn.close()
