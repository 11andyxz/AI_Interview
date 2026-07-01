#!/usr/bin/env python3
"""
Re-analyze existing real data with corrected early-stop logic.
Does NOT call OpenAI API - just re-judges when to stop based on existing questions.
"""
import sqlite3
import random
import json
from datetime import datetime

DB_PATH = "results/ab_experiment.db"

def corrected_early_stop_decision(policy: str, question_num: int, slice_type: str, confidence_score: float) -> bool:
    """
    CORRECTED early-stop logic that matches Andy's policy intent.
    
    Baseline (0.95/0.05):
    - Min 5 questions
    - Stop if confidence > 0.95 or < 0.05
    
    New Policy (0.90/0.10):
    - Min 6 questions
    - Stop if confidence > 0.90 or < 0.10
    - More lenient thresholds → easier to stop → FEWER questions
    """
    if policy == "baseline":
        if question_num < 5:
            return False
        return confidence_score > 0.95 or confidence_score < 0.05
    
    else:  # new policy
        if question_num < 6:
            return False
        return confidence_score > 0.90 or confidence_score < 0.10


def recompute_session_metrics(session_id: int, policy: str, slice_type: str, conn):
    """
    Re-compute how many questions this session should have asked.
    Uses existing real question data and confidence scores.
    """
    cursor = conn.cursor()
    
    # Get all questions for this session (in order)
    cursor.execute("""
        SELECT question_num, confidence_score, latency_ms
        FROM questions
        WHERE session_id = ?
        ORDER BY question_num
    """, (session_id,))
    
    questions = cursor.fetchall()
    if not questions:
        return None
    
    # Find where we should have stopped with corrected logic
    actual_num_questions = len(questions)
    should_stop_at = actual_num_questions  # default: ask all
    early_stopped = False
    
    for q_num, confidence, latency in questions:
        if corrected_early_stop_decision(policy, q_num, slice_type, confidence):
            should_stop_at = q_num
            early_stopped = True
            break
    
    # Compute avg latency for questions we should have asked
    relevant_questions = questions[:should_stop_at]
    avg_latency = sum(q[2] for q in relevant_questions) / len(relevant_questions) if relevant_questions else 0
    
    return {
        'num_questions': should_stop_at,
        'avg_latency_ms': avg_latency,
        'early_stopped': 1 if early_stopped else 0
    }


def main():
    conn = sqlite3.connect(DB_PATH)
    cursor = conn.cursor()
    
    # Get all sessions
    cursor.execute("SELECT id, experiment_id, policy, slice FROM sessions")
    sessions = cursor.fetchall()
    
    print(f"Found {len(sessions)} sessions")
    print("\nRe-computing with CORRECTED early-stop logic...\n")
    
    updated_count = 0
    for session_id, exp_id, policy, slice_type in sessions:
        metrics = recompute_session_metrics(session_id, policy, slice_type, conn)
        
        if metrics:
            cursor.execute("""
                UPDATE sessions
                SET num_questions = ?,
                    avg_latency_ms = ?,
                    early_stopped = ?
                WHERE id = ?
            """, (metrics['num_questions'], metrics['avg_latency_ms'], 
                  metrics['early_stopped'], session_id))
            updated_count += 1
    
    conn.commit()
    
    print(f"✓ Updated {updated_count} sessions with corrected logic")
    
    # Show summary
    print("\n=== CORRECTED RESULTS ===")
    for exp_id in ['week20_control_real', 'week20_treatment_real']:
        cursor.execute("""
            SELECT 
                COUNT(*) as n,
                AVG(num_questions) as avg_q,
                SUM(early_stopped)*100.0/COUNT(*) as early_pct
            FROM sessions
            WHERE experiment_id = ?
        """, (exp_id,))
        
        n, avg_q, early_pct = cursor.fetchone()
        policy_name = "Control (baseline)" if "control" in exp_id else "Treatment (new)"
        print(f"{policy_name}: N={n}, Avg={avg_q:.2f} questions, Early-stop={early_pct:.1f}%")
    
    conn.close()
    print("\n✓ Database updated. Run analysis again to see corrected results.")


if __name__ == "__main__":
    main()
