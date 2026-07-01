import sqlite3
import numpy as np

conn = sqlite3.connect('results/ab_experiment.db')
cursor = conn.cursor()

print("=== WHY HIGH EARLY-STOP BUT LOW REDUCTION? ===\n")

# Check treatment sessions
cursor.execute("""
    SELECT s.slice, s.num_questions, s.early_stopped
    FROM sessions s
    WHERE s.experiment_id = 'week20_treatment_real'
    ORDER BY s.slice, s.num_questions
""")

sessions = cursor.fetchall()

by_slice = {'junior': [], 'mid': [], 'senior': []}
for slice_type, num_q, early in sessions:
    by_slice[slice_type].append((num_q, early))

print("Treatment (current config: junior min=4, mid min=5, senior min=6):\n")

for slice_type in ['junior', 'mid', 'senior']:
    data = by_slice[slice_type]
    num_qs = [d[0] for d in data]
    early_stops = [d[1] for d in data]
    
    min_q = 4 if slice_type == 'junior' else 5 if slice_type == 'mid' else 6
    
    # Count sessions at minimum
    at_min = sum(1 for q in num_qs if q == min_q)
    early_count = sum(early_stops)
    
    # Sessions stopped EXACTLY at minimum (not before)
    stopped_at_min = sum(1 for q, e in data if q == min_q and e == 1)
    
    print(f"{slice_type.capitalize()}:")
    print(f"  Avg questions: {np.mean(num_qs):.2f}")
    print(f"  Early-stopped: {early_count}/{len(data)} ({early_count*100/len(data):.1f}%)")
    print(f"  At minimum ({min_q}Q): {at_min}/{len(data)} ({at_min*100/len(data):.1f}%)")
    print(f"  Stopped EXACTLY at min: {stopped_at_min}/{len(data)} ({stopped_at_min*100/len(data):.1f}%)")
    print(f"  → Problem: {stopped_at_min} sessions hit min_questions wall (can't go lower)")
    print()

print("\n" + "="*70)
print("DIAGNOSIS: Most early-stops happen AT minimum, not BEFORE")
print("="*70)

print("\nSolution: Lower min_questions to allow more reduction")
print("\nSimulation: What if min = 3/4/5 (instead of 4/5/6)?")

# Control baseline
cursor.execute("""
    SELECT AVG(num_questions) FROM sessions 
    WHERE experiment_id = 'week20_control_real'
""")
control_avg = cursor.fetchone()[0]

# Current treatment
cursor.execute("""
    SELECT AVG(num_questions) FROM sessions 
    WHERE experiment_id = 'week20_treatment_real'
""")
treatment_current = cursor.fetchone()[0]

print(f"\nCurrent:")
print(f"  Control: {control_avg:.2f}")
print(f"  Treatment (min=4/5/6): {treatment_current:.2f}")
print(f"  Reduction: {treatment_current - control_avg:+.2f} ({(treatment_current-control_avg)/control_avg*100:+.1f}%)")

# Estimate with lower min
# Sessions at current minimum could go -1 question
potential_savings = 0
for slice_type in ['junior', 'mid', 'senior']:
    data = by_slice[slice_type]
    min_q = 4 if slice_type == 'junior' else 5 if slice_type == 'mid' else 6
    at_min = sum(1 for q in data if q == min_q)
    # If we lower min by 1, these sessions could save 1 question
    potential_savings += at_min

estimated_new_avg = treatment_current - (potential_savings / len(sessions))

print(f"\nEstimated with min=3/4/5:")
print(f"  Treatment: ~{estimated_new_avg:.2f}")
print(f"  Reduction: ~{estimated_new_avg - control_avg:+.2f} ({(estimated_new_avg-control_avg)/control_avg*100:+.1f}%)")
print(f"  Potential sessions benefiting: {potential_savings}/{len(sessions)}")

conn.close()
