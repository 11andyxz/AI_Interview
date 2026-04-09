#!/usr/bin/env python3
"""
Generate simulated A/B experiment data for early-stop policy comparison

Based on theoretical models from Week 18 analysis:
- Baseline policy (0.95/0.05, min 5 questions): ~10.2 avg questions
- New policy (0.90/0.10, min 6 questions): ~9.2 avg questions
- Expected reduction: ~10% (1 question per interview)
- RMSE: baseline 10.8, new policy 10.9-11.1 (slight increase acceptable)
"""

import json
import random
import numpy as np
from datetime import datetime, timedelta

# Set seed for reproducibility
random.seed(20260409)
np.random.seed(20260409)

# Baseline policy parameters (Week 15 metrics)
BASELINE_AVG_QUESTIONS = 10.2
BASELINE_RMSE = 10.8
BASELINE_STD = 2.5

# New policy parameters (theoretical projections)
NEW_POLICY_AVG_QUESTIONS = 9.2
NEW_POLICY_RMSE = 11.0
NEW_POLICY_STD = 2.3

def generate_interview_session(policy_type, session_id, start_date):
    """Generate one interview session with realistic metrics."""
    
    if policy_type == "baseline":
        # Baseline policy: 0.95/0.05, min 5 questions
        avg_q = BASELINE_AVG_QUESTIONS
        rmse = BASELINE_RMSE
        std = BASELINE_STD
        policy_conf = {"passThreshold": 0.95, "failThreshold": 0.05, "minQuestions": 5}
    else:
        # New policy: 0.90/0.10, min 6 questions
        avg_q = NEW_POLICY_AVG_QUESTIONS
        rmse = NEW_POLICY_RMSE
        std = NEW_POLICY_STD
        policy_conf = {"passThreshold": 0.90, "failThreshold": 0.10, "minQuestions": 6}
    
    # Generate question count (normal distribution, clipped to reasonable range)
    questions_asked = int(max(6, min(15, np.random.normal(avg_q, std))))
    
    # Generate pass probability (varies by interview quality)
    # Most interviews are borderline (0.4-0.6), some clear pass/fail
    outcome_roll = random.random()
    if outcome_roll < 0.3:  # Clear failure
        final_prob = random.uniform(0.05, 0.35)
        actual_outcome = "fail"
    elif outcome_roll < 0.7:  # Borderline
        final_prob = random.uniform(0.40, 0.60)
        actual_outcome = random.choice(["pass", "fail"])
    else:  # Clear pass
        final_prob = random.uniform(0.65, 0.95)
        actual_outcome = "pass"
    
    # Calculate if early-stop triggered
    early_stopped = False
    if questions_asked < 12:  # Only sessions with <12 questions likely stopped early
        if policy_type == "baseline":
            early_stopped = (final_prob >= 0.95 or final_prob <= 0.05) and questions_asked >= 5
        else:
            early_stopped = (final_prob >= 0.90 or final_prob <= 0.10) and questions_asked >= 6
    
    # Generate prediction error for RMSE calculation
    true_score = 60 if actual_outcome == "pass" else 40
    predicted_score = true_score + np.random.normal(0, rmse)
    error = abs(predicted_score - true_score)
    
    # Generate timestamp (spread over experiment window Apr 2-9)
    days_offset = random.randint(0, 7)
    hours_offset = random.randint(0, 23)
    timestamp = start_date + timedelta(days=days_offset, hours=hours_offset)
    
    positionType = random.choice([
        "Junior Developer", "Backend Java Developer",
        "Full Stack Engineer", "Senior Software Engineer"
    ])
    
    return {
        "sessionId": f"{policy_type}_{session_id}",
        "policyType": policy_type,
        "positionType": positionType,
        "questionsAsked": questions_asked,
        "finalPassProbability": round(final_prob, 3),
        "actualOutcome": actual_outcome,
        "predictedScore": round(predicted_score, 2),
        "trueScore": true_score,
        "predictionError": round(error, 2),
        "earlyStopTriggered": early_stopped,
        "timestamp": timestamp.isoformat(),
        "policyConfig": policy_conf
    }

def generate_experiment():
    """Generate full A/B experiment dataset."""
    
    start_date = datetime(2026, 4, 2, 9, 0)
    
    baseline_sessions = [
        generate_interview_session("baseline", i+1, start_date)
        for i in range(10)
    ]
    
    new_policy_sessions = [
        generate_interview_session("new_policy", i+1, start_date)
        for i in range(10)
    ]
    
    # Calculate metrics
    baseline_avg_q = np.mean([s["questionsAsked"] for s in baseline_sessions])
    new_policy_avg_q = np.mean([s["questionsAsked"] for s in new_policy_sessions])
    
    baseline_rmse = np.sqrt(np.mean([s["predictionError"]**2 for s in baseline_sessions]))
    new_policy_rmse = np.sqrt(np.mean([s["predictionError"]**2 for s in new_policy_sessions]))
    
    baseline_early_stop_rate = sum(s["earlyStopTriggered"] for s in baseline_sessions) / len(baseline_sessions)
    new_policy_early_stop_rate = sum(s["earlyStopTriggered"] for s in new_policy_sessions) / len(new_policy_sessions)
    
    # Calculate premature stop rate (stopped before 8 questions)
    baseline_premature = sum(1 for s in baseline_sessions if s["earlyStopTriggered"] and s["questionsAsked"] < 8) / len(baseline_sessions)
    new_policy_premature = sum(1 for s in new_policy_sessions if s["earlyStopTriggered"] and s["questionsAsked"] < 8) / len(new_policy_sessions)
    
    metrics = {
        "baseline": {
            "avgQuestions": round(baseline_avg_q, 2),
            "rmse": round(baseline_rmse, 2),
            "earlyStopRate": round(baseline_early_stop_rate * 100, 1),
            "prematureStopRate": round(baseline_premature * 100, 1),
            "sampleSize": len(baseline_sessions)
        },
        "new_policy": {
            "avgQuestions": round(new_policy_avg_q, 2),
            "rmse": round(new_policy_rmse, 2),
            "earlyStopRate": round(new_policy_early_stop_rate * 100, 1),
            "prematureStopRate": round(new_policy_premature * 100, 1),
            "sampleSize": len(new_policy_sessions)
        },
        "comparison": {
            "questionReduction": round((baseline_avg_q - new_policy_avg_q) / baseline_avg_q * 100, 1),
            "questionReductionAbsolute": round(baseline_avg_q - new_policy_avg_q, 2),
            "rmseChange": round(new_policy_rmse - baseline_rmse, 2),
            "rmseChangePercent": round((new_policy_rmse - baseline_rmse) / baseline_rmse * 100, 1)
        }
    }
    
    return {
        "metadata": {
            "experimentName": "week18_early_stop_ab_test",
            "startDate": "2026-04-02",
            "endDate": "2026-04-09",
            "dataType": "simulated",
            "note": "Simulated data based on theoretical projections from Week 18 analysis. Real A/B test pending valid OpenAI API key."
        },
        "baselineSessions": baseline_sessions,
        "newPolicySessions": new_policy_sessions,
        "metrics": metrics
    }

if __name__ == "__main__":
    experiment_data = generate_experiment()
    
    # Save to file
    output_file = "week18_ab_experiment_data.json"
    with open(output_file, 'w', encoding='utf-8') as f:
        json.dump(experiment_data, f, indent=2)
    
    print(f"✓ Generated A/B experiment data: {output_file}")
    print(f"\n=== Metrics Summary ===")
    print(f"Baseline Policy (0.95/0.05, min 5):")
    print(f"  Avg Questions: {experiment_data['metrics']['baseline']['avgQuestions']}")
    print(f"  RMSE: {experiment_data['metrics']['baseline']['rmse']}")
    print(f"  Early-Stop Rate: {experiment_data['metrics']['baseline']['earlyStopRate']}%")
    print(f"\nNew Policy (0.90/0.10, min 6):")
    print(f"  Avg Questions: {experiment_data['metrics']['new_policy']['avgQuestions']}")
    print(f"  RMSE: {experiment_data['metrics']['new_policy']['rmse']}")
    print(f"  Early-Stop Rate: {experiment_data['metrics']['new_policy']['earlyStopRate']}%")
    print(f"\nComparison:")
    print(f"  Question Reduction: {experiment_data['metrics']['comparison']['questionReduction']}%")
    print(f"  Absolute Reduction: {experiment_data['metrics']['comparison']['questionReductionAbsolute']} questions")
    print(f"  RMSE Change: +{experiment_data['metrics']['comparison']['rmseChange']} ({experiment_data['metrics']['comparison']['rmseChangePercent']}%)")
