import sqlite3
import numpy as np

conn = sqlite3.connect('results/ab_experiment.db')
cursor = conn.cursor()

print("=== CONFIDENCE SCORE DISTRIBUTIONS ===\n")

for exp_id in ['week20_control_real', 'week20_treatment_real']:
    policy = 'baseline' if 'control' in exp_id else 'new'
    
    # Get all confidence scores
    cursor.execute("""
        SELECT q.confidence_score, s.slice
        FROM questions q
        JOIN sessions s ON q.session_id = s.id
        WHERE s.experiment_id = ?
    """, (exp_id,))
    
    data = cursor.fetchall()
    scores = [d[0] for d in data]
    
    print(f"{exp_id} ({policy}):")
    print(f"  N = {len(scores)}")
    print(f"  Range: [{min(scores):.3f}, {max(scores):.3f}]")
    print(f"  Mean: {np.mean(scores):.3f}")
    print(f"  % > 0.95: {sum(1 for s in scores if s > 0.95)*100/len(scores):.1f}%")
    print(f"  % > 0.90: {sum(1 for s in scores if s > 0.90)*100/len(scores):.1f}%")
    
    # By slice (junior only)
    junior_scores = [d[0] for d in data if d[1] == 'junior']
    if junior_scores:
        print(f"  Junior confidence: [{min(junior_scores):.3f}, {max(junior_scores):.3f}], mean={np.mean(junior_scores):.3f}")
    print()

conn.close()
