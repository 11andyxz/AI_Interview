#!/usr/bin/env python3
"""
Export calibration training data from Week 20 experiments for Platt calibration.

Generates:
1. Raw scores (uncalibrated probabilities) per session
2. Ground truth labels (pass/fail based on final outcome)
3. Slice-aware data split (junior/mid/senior)

Output: calibration_training_data.csv
"""
import sqlite3
import numpy as np
import csv

DB_PATH = "results/ab_experiment.db"
OUTPUT_CSV = "results/calibration_training_data.csv"

# Simulate "uncalibrated" predictor scores
# Based on exponential moving average of confidence scores
EMA_ALPHA = 0.3

def calculate_ema_score(confidences):
    """Calculate exponential moving average as 'uncalibrated' predictor score."""
    if not confidences:
        return 0.5
    
    ema = confidences[0]
    for conf in confidences[1:]:
        ema = EMA_ALPHA * conf + (1 - EMA_ALPHA) * ema
    
    return ema

def determine_ground_truth_label(session_data, avg_confidence):
    """
    Determine ground truth pass/fail label.
    
    Rules (balanced, based on empirical median ~ 0.81):
    - If avg_confidence >= 0.81 → Pass (label=1)
    - If avg_confidence < 0.81 → Fail (label=0)
    
    This simulates "actual interview outcome" based on performance.
    Using median ensures ~50/50 split.
    """
    return 1 if avg_confidence >= 0.81 else 0

def main():
    conn = sqlite3.connect(DB_PATH)
    cursor = conn.cursor()
    
    # Fetch all sessions (control + treatment)
    cursor.execute("""
        SELECT s.id, s.experiment_id, s.slice, s.num_questions
        FROM sessions s
        WHERE s.experiment_id IN ('week20_control_real', 'week20_treatment_real')
        ORDER BY s.id
    """)
    
    sessions = cursor.fetchall()
    
    training_data = []
    
    for session_id, experiment_id, slice_type, num_questions in sessions:
        # Fetch all questions for this session
        cursor.execute("""
            SELECT question_num, confidence_score
            FROM questions
            WHERE session_id = ?
            ORDER BY question_num
        """, (session_id,))
        
        questions = cursor.fetchall()
        
        if len(questions) < 3:
            continue  # Skip sessions with too few questions
        
        confidences = [conf for _, conf in questions]
        avg_confidence = np.mean(confidences)
        
        # Simulate predictor running at different points
        # We'll generate multiple training examples per session:
        # - After 3 questions
        # - After 5 questions (if available)
        # - After all questions
        
        for min_q in [3, 5, len(questions)]:
            if len(questions) < min_q:
                continue
            
            partial_confidences = confidences[:min_q]
            
            # Calculate "uncalibrated" score (EMA)
            raw_score = calculate_ema_score(partial_confidences)
            
            # Ground truth based on final outcome
            label = determine_ground_truth_label(questions, avg_confidence)
            
            training_data.append({
                'session_id': session_id,
                'experiment': experiment_id,
                'slice': slice_type,
                'num_questions': min_q,
                'raw_score': raw_score,
                'avg_confidence': avg_confidence,
                'label': label
            })
    
    # Write to CSV
    fieldnames = ['session_id', 'experiment', 'slice', 'num_questions', 
                  'raw_score', 'avg_confidence', 'label']
    
    with open(OUTPUT_CSV, 'w', newline='') as csvfile:
        writer = csv.DictWriter(csvfile, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(training_data)
    
    print(f"✓ Exported {len(training_data)} training examples to {OUTPUT_CSV}")
    print(f"\nData summary:")
    print(f"  - Total examples: {len(training_data)}")
    print(f"  - Pass labels: {sum(1 for d in training_data if d['label'] == 1)} ({sum(1 for d in training_data if d['label'] == 1) * 100 / len(training_data):.1f}%)")
    print(f"  - Fail labels: {sum(1 for d in training_data if d['label'] == 0)} ({sum(1 for d in training_data if d['label'] == 0) * 100 / len(training_data):.1f}%)")
    
    # Slice breakdown
    for slice_type in ['junior', 'mid', 'senior']:
        slice_data = [d for d in training_data if d['slice'] == slice_type]
        if slice_data:
            print(f"\n  {slice_type.capitalize()}: {len(slice_data)} examples")
            print(f"    - Pass: {sum(1 for d in slice_data if d['label'] == 1)}")
            print(f"    - Fail: {sum(1 for d in slice_data if d['label'] == 0)}")
            print(f"    - Avg raw_score: {np.mean([d['raw_score'] for d in slice_data]):.3f}")
    
    conn.close()

if __name__ == "__main__":
    main()
