import sqlite3
import numpy as np

conn = sqlite3.connect('results/ab_experiment.db')
cursor = conn.cursor()

print("=== THRESHOLD ANALYSIS ===\n")

# Get all treatment questions
cursor.execute("""
    SELECT q.confidence_score, q.question_num, s.slice
    FROM questions q
    JOIN sessions s ON q.session_id = s.id
    WHERE s.experiment_id = 'week20_treatment_real'
    ORDER BY q.confidence_score DESC
""")

questions = cursor.fetchall()

print(f"Total treatment questions: {len(questions)}\n")

# Current threshold: 0.90
current_threshold = 0.90
above_current = sum(1 for q in questions if q[0] > current_threshold)
current_pct = above_current * 100 / len(questions)

print(f"Current threshold: {current_threshold}")
print(f"  Questions > {current_threshold}: {above_current}/{len(questions)} ({current_pct:.1f}%)")

# Try lower thresholds
for new_threshold in [0.85, 0.80, 0.75]:
    above_new = sum(1 for q in questions if q[0] > new_threshold)
    new_pct = above_new * 100 / len(questions)
    increase = above_new - above_current
    
    print(f"\nIf threshold = {new_threshold}:")
    print(f"  Questions > {new_threshold}: {above_new}/{len(questions)} ({new_pct:.1f}%)")
    print(f"  Additional early-stop opportunities: +{increase} ({increase*100/len(questions):.1f}%)")

# Confidence distribution
print(f"\n" + "="*70)
print("CONFIDENCE DISTRIBUTION")
print("="*70)

confidences = [q[0] for q in questions]
print(f"Mean: {np.mean(confidences):.3f}")
print(f"Median: {np.median(confidences):.3f}")
print(f"Std: {np.std(confidences):.3f}")
print(f"\nPercentiles:")
for p in [25, 50, 75, 90, 95]:
    val = np.percentile(confidences, p)
    print(f"  {p}th: {val:.3f}")

# Most impactful threshold
print(f"\n" + "="*70)
print("RECOMMENDATION")
print("="*70)

print(f"\nOption 1: Lower threshold to 0.85")
print(f"  → +{sum(1 for c in confidences if 0.85 < c <= 0.90)} more questions can early-stop")
print(f"  → Simple change, minimal risk")

print(f"\nOption 2: Lower threshold to 0.80")
print(f"  → +{sum(1 for c in confidences if 0.80 < c <= 0.90)} more questions can early-stop")
print(f"  → More aggressive, higher risk of premature stops")

# Estimate impact on avg questions
# Rough estimate: each additional early-stop saves ~0.5-1 questions on average
above_085 = sum(1 for c in confidences if c > 0.85)
additional_stops_085 = above_085 - above_current

# Estimate: if we can stop earlier on these questions, save avg 1 question per session
potential_sessions_affected_085 = additional_stops_085 / 7  # avg 7 questions per session
estimated_reduction_085 = potential_sessions_affected_085 / 60  # 60 treatment sessions

print(f"\nEstimated impact (threshold=0.85):")
print(f"  Current avg: 6.43 questions")
print(f"  Estimated new: ~{6.43 - estimated_reduction_085:.2f} questions")
print(f"  Estimated reduction: ~{-estimated_reduction_085:.2f} ({-estimated_reduction_085/6.43*100:.1f}%)")
print(f"  vs Control 6.62: ~{(6.43-estimated_reduction_085-6.62)/6.62*100:+.1f}%")

conn.close()
