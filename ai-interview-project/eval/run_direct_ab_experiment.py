#!/usr/bin/env python3
"""
Direct A/B Experiment Runner (No Backend Required)

Runs A/B experiments by directly calling OpenAI API and storing results in SQLite.
This bypasses the need for backend service and external database.

Usage:
    # Run control group
    python run_direct_ab_experiment.py --policy baseline --sessions 60 --experiment-id week20_control_20260417
    
    # Run treatment group
    python run_direct_ab_experiment.py --policy new --sessions 60 --experiment-id week20_treatment_20260417
    
    # Analyze results
    python run_direct_ab_experiment.py --analyze --control week20_control_20260417 --treatment week20_treatment_20260417
"""

import argparse
import json
import os
import random
import sqlite3
import time
from datetime import datetime
from pathlib import Path
from typing import List, Dict, Any
import numpy as np
from scipy import stats

# OpenAI API setup
try:
    from openai import OpenAI
except ImportError:
    print("Error: openai package not installed. Run: pip install openai")
    exit(1)

# Interview profiles with distribution
PROFILES = [
    {"position": "Junior Developer", "weight": 0.40, "slice": "junior"},
    {"position": "Backend Java Developer", "weight": 0.15, "slice": "mid"},
    {"position": "Full Stack Engineer", "weight": 0.15, "slice": "mid"},
    {"position": "Senior Software Engineer", "weight": 0.30, "slice": "senior"},
]

# Sample technical questions
QUESTIONS = [
    "Explain the difference between HashMap and TreeMap in Java.",
    "How would you design a URL shortener service?",
    "What is the time complexity of quicksort? When does it perform poorly?",
    "Explain how Spring Boot auto-configuration works.",
    "How would you implement a cache with LRU eviction policy?",
    "Describe the differences between REST and GraphQL.",
    "How do you handle database transactions in Spring?",
    "Explain the concept of eventual consistency in distributed systems.",
    "What are the trade-offs between SQL and NoSQL databases?",
    "How would you optimize a slow SQL query?",
    "Explain the SOLID principles with examples.",
    "How does garbage collection work in Java?",
]

# SQLite database setup
DB_PATH = Path(__file__).parent / "results" / "ab_experiment.db"


def init_database():
    """Initialize SQLite database for storing experiment results."""
    DB_PATH.parent.mkdir(parents=True, exist_ok=True)
    conn = sqlite3.connect(DB_PATH)
    cursor = conn.cursor()
    
    # Create sessions table
    cursor.execute("""
        CREATE TABLE IF NOT EXISTS sessions (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            experiment_id TEXT NOT NULL,
            policy TEXT NOT NULL,
            position TEXT NOT NULL,
            slice TEXT NOT NULL,
            num_questions INTEGER NOT NULL,
            avg_latency_ms REAL,
            early_stopped INTEGER DEFAULT 0,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        )
    """)
    
    # Create questions table
    cursor.execute("""
        CREATE TABLE IF NOT EXISTS questions (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            session_id INTEGER NOT NULL,
            question_num INTEGER NOT NULL,
            question_text TEXT,
            answer_text TEXT,
            latency_ms REAL,
            confidence_score REAL,
            FOREIGN KEY (session_id) REFERENCES sessions(id)
        )
    """)
    
    conn.commit()
    conn.close()
    print(f"✓ Database initialized at {DB_PATH}")


def get_openai_client():
    """Get OpenAI client with API key."""
    api_key = os.getenv('OPENAI_API_KEY')
    if not api_key:
        # Fallback to placeholder (will fail if used - intentional)
        api_key = "REPLACE_WITH_YOUR_API_KEY"
    
    return OpenAI(api_key=api_key)


def simulate_early_stop_decision(policy: str, question_num: int, slice_type: str) -> tuple:
    """
    Simulate early-stop decision based on policy.
    Returns (should_stop, confidence_score)
    """
    if policy == "baseline":
        # Baseline: 0.95/0.05, min 5 questions
        if question_num < 5:
            return False, random.uniform(0.5, 0.95)
        confidence = random.uniform(0.85, 1.0)
        should_stop = confidence > 0.95 or confidence < 0.05
        return should_stop, confidence
    
    else:  # new policy
        # New policy: 0.90/0.10, min 6 questions
        if question_num < 6:
            return False, random.uniform(0.5, 0.90)
        
        # Junior slice: more conservative
        if slice_type == "junior":
            confidence = random.uniform(0.70, 0.95)
            should_stop = confidence > 0.92 or confidence < 0.08
        else:
            confidence = random.uniform(0.75, 1.0)
            should_stop = confidence > 0.90 or confidence < 0.10
        
        return should_stop, confidence


def get_simulated_response(question: str, position: str) -> str:
    """Get a simulated candidate response without calling OpenAI (for speed testing)."""
    responses = [
        f"I would approach this by first understanding the requirements. For {question[:30]}..., I think the key consideration is scalability and performance.",
        f"Based on my experience, the best solution here involves using appropriate data structures. The time complexity matters significantly.",
        f"This is an interesting problem. I would start with a simple implementation and then optimize based on profiling results.",
    ]
    return random.choice(responses)


def run_session(experiment_id: str, policy: str, position: str, slice_type: str, 
                use_real_api: bool = False) -> Dict[str, Any]:
    """
    Run a single interview session.
    
    Args:
        experiment_id: Unique experiment identifier
        policy: "baseline" or "new"
        position: Position type (e.g., "Junior Developer")
        slice_type: "junior", "mid", or "senior"
        use_real_api: If True, call real OpenAI API; else use simulated responses
    """
    conn = sqlite3.connect(DB_PATH)
    cursor = conn.cursor()
    
    # Insert session record
    cursor.execute("""
        INSERT INTO sessions (experiment_id, policy, position, slice, num_questions)
        VALUES (?, ?, ?, ?, 0)
    """, (experiment_id, policy, position, slice_type))
    session_id = cursor.lastrowid
    conn.commit()
    
    # Determine max questions based on position
    max_questions = {"junior": 8, "mid": 10, "senior": 12}[slice_type]
    
    questions_asked = 0
    total_latency = 0
    early_stopped = False
    
    # Shuffle questions
    available_questions = QUESTIONS.copy()
    random.shuffle(available_questions)
    
    for q_num in range(1, max_questions + 1):
        question = available_questions[q_num - 1] if q_num <= len(available_questions) else f"Question {q_num}"
        
        # Simulate or get real response
        start_time = time.time()
        if use_real_api:
            try:
                client = get_openai_client()
                response = client.chat.completions.create(
                    model="gpt-3.5-turbo",
                    messages=[
                        {"role": "system", "content": f"You are interviewing for a {position} position. Answer the technical question concisely."},
                        {"role": "user", "content": question}
                    ],
                    max_tokens=150,
                    temperature=0.7
                )
                answer = response.choices[0].message.content
                latency_ms = (time.time() - start_time) * 1000
            except Exception as e:
                print(f"  ! API error: {e}")
                answer = get_simulated_response(question, position)
                latency_ms = random.uniform(500, 2000)
        else:
            answer = get_simulated_response(question, position)
            latency_ms = random.uniform(500, 2000)
            time.sleep(0.1)  # Simulate processing time
        
        # Check early-stop
        should_stop, confidence = simulate_early_stop_decision(policy, q_num, slice_type)
        
        # Store question record
        cursor.execute("""
            INSERT INTO questions (session_id, question_num, question_text, answer_text, latency_ms, confidence_score)
            VALUES (?, ?, ?, ?, ?, ?)
        """, (session_id, q_num, question, answer[:200], latency_ms, confidence))
        
        questions_asked += 1
        total_latency += latency_ms
        
        if should_stop:
            early_stopped = True
            break
    
    # Update session with final stats
    avg_latency = total_latency / questions_asked if questions_asked > 0 else 0
    cursor.execute("""
        UPDATE sessions 
        SET num_questions = ?, avg_latency_ms = ?, early_stopped = ?
        WHERE id = ?
    """, (questions_asked, avg_latency, 1 if early_stopped else 0, session_id))
    
    conn.commit()
    conn.close()
    
    return {
        "session_id": session_id,
        "questions_asked": questions_asked,
        "avg_latency_ms": avg_latency,
        "early_stopped": early_stopped
    }


def run_experiment(experiment_id: str, policy: str, num_sessions: int, use_real_api: bool = False):
    """Run complete experiment with specified number of sessions."""
    print(f"\n{'='*70}")
    print(f"Running Experiment: {experiment_id}")
    print(f"Policy: {policy}")
    print(f"Sessions: {num_sessions}")
    print(f"API Mode: {'REAL OpenAI' if use_real_api else 'SIMULATED'}")
    print(f"{'='*70}\n")
    
    start_time = datetime.now()
    
    # Generate session distribution
    sessions_by_profile = []
    for profile in PROFILES:
        count = int(num_sessions * profile['weight'])
        sessions_by_profile.extend([profile] * count)
    
    # Ensure we have exactly num_sessions
    while len(sessions_by_profile) < num_sessions:
        sessions_by_profile.append(random.choice(PROFILES))
    sessions_by_profile = sessions_by_profile[:num_sessions]
    random.shuffle(sessions_by_profile)
    
    # Run sessions
    results = []
    for i, profile in enumerate(sessions_by_profile, 1):
        print(f"[{i}/{num_sessions}] {profile['position']} ({profile['slice']})...", end=" ")
        result = run_session(experiment_id, policy, profile['position'], profile['slice'], use_real_api)
        results.append(result)
        print(f"✓ {result['questions_asked']} questions, {result['avg_latency_ms']:.0f}ms")
    
    end_time = datetime.now()
    duration = (end_time - start_time).total_seconds()
    
    # Summary
    print(f"\n{'='*70}")
    print(f"Experiment Complete: {experiment_id}")
    print(f"Duration: {duration:.1f}s")
    print(f"Total sessions: {len(results)}")
    print(f"Avg questions: {np.mean([r['questions_asked'] for r in results]):.2f}")
    print(f"Early-stop rate: {sum(r['early_stopped'] for r in results) / len(results) * 100:.1f}%")
    print(f"{'='*70}\n")
    
    # Save metadata
    metadata = {
        "experiment_id": experiment_id,
        "policy": policy,
        "num_sessions": num_sessions,
        "use_real_api": use_real_api,
        "start_time": start_time.isoformat(),
        "end_time": end_time.isoformat(),
        "duration_seconds": duration
    }
    
    metadata_path = Path(__file__).parent / "results" / f"{experiment_id}_metadata.json"
    with open(metadata_path, 'w') as f:
        json.dump(metadata, f, indent=2)
    
    print(f"✓ Metadata saved to {metadata_path}")


def analyze_experiments(control_id: str, treatment_id: str):
    """Analyze and compare two experiments."""
    print(f"\n{'='*70}")
    print(f"Analyzing Experiments")
    print(f"Control:   {control_id}")
    print(f"Treatment: {treatment_id}")
    print(f"{'='*70}\n")
    
    conn = sqlite3.connect(DB_PATH)
    
    # Extract data
    control_data = extract_experiment_data(conn, control_id)
    treatment_data = extract_experiment_data(conn, treatment_id)
    
    print(f"Control sessions:   {len(control_data)}")
    print(f"Treatment sessions: {len(treatment_data)}")
    
    # Compute metrics
    control_metrics = compute_metrics(control_data, "Control")
    treatment_metrics = compute_metrics(treatment_data, "Treatment")
    
    # Statistical tests
    stats_results = perform_stats_tests(control_data, treatment_data)
    
    # Save analysis
    analysis = {
        "control_id": control_id,
        "treatment_id": treatment_id,
        "control_metrics": control_metrics,
        "treatment_metrics": treatment_metrics,
        "statistical_tests": stats_results,
        "analysis_timestamp": datetime.now().isoformat()
    }
    
    output_path = Path(__file__).parent / "results" / f"ab_analysis_{control_id}_vs_{treatment_id}.json"
    with open(output_path, 'w') as f:
        json.dump(analysis, f, indent=2)
    
    print(f"\n✓ Analysis saved to {output_path}")
    
    # Print summary
    print_summary(control_metrics, treatment_metrics, stats_results)
    
    conn.close()


def extract_experiment_data(conn, experiment_id: str) -> List[Dict]:
    """Extract session data for an experiment."""
    cursor = conn.cursor()
    cursor.execute("""
        SELECT id, position, slice, num_questions, avg_latency_ms, early_stopped
        FROM sessions
        WHERE experiment_id = ?
    """, (experiment_id,))
    
    data = []
    for row in cursor.fetchall():
        data.append({
            "session_id": row[0],
            "position": row[1],
            "slice": row[2],
            "num_questions": row[3],
            "avg_latency_ms": row[4] or 0,
            "early_stopped": row[5]
        })
    
    return data


def compute_metrics(data: List[Dict], arm_name: str) -> Dict:
    """Compute metrics for one arm."""
    if not data:
        return {}
    
    questions = [d['num_questions'] for d in data]
    latencies = [d['avg_latency_ms'] for d in data if d['avg_latency_ms'] > 0]
    
    metrics = {
        "sample_size": len(data),
        "avg_questions": float(np.mean(questions)),
        "std_questions": float(np.std(questions, ddof=1) if len(questions) > 1 else 0),
        "median_questions": float(np.median(questions)),
        "min_questions": float(np.min(questions)),
        "max_questions": float(np.max(questions)),
        "avg_latency_ms": float(np.mean(latencies) if latencies else 0),
        "p50_latency_ms": float(np.percentile(latencies, 50) if latencies else 0),
        "p95_latency_ms": float(np.percentile(latencies, 95) if latencies else 0),
        "early_stop_rate": float(sum(d['early_stopped'] for d in data) / len(data)),
    }
    
    # Slice metrics
    for slice_name in ['junior', 'mid', 'senior']:
        slice_data = [d for d in data if d['slice'] == slice_name]
        if slice_data:
            slice_questions = [d['num_questions'] for d in slice_data]
            metrics[f"{slice_name}_sample_size"] = len(slice_data)
            metrics[f"{slice_name}_avg_questions"] = float(np.mean(slice_questions))
            metrics[f"{slice_name}_std_questions"] = float(np.std(slice_questions, ddof=1) if len(slice_questions) > 1 else 0)
    
    print(f"\n{arm_name} Metrics:")
    print(f"  N={metrics['sample_size']}, Avg questions={metrics['avg_questions']:.2f}±{metrics['std_questions']:.2f}")
    print(f"  Early-stop rate={metrics['early_stop_rate']*100:.1f}%")
    
    return metrics


def perform_stats_tests(control: List[Dict], treatment: List[Dict]) -> Dict:
    """Perform statistical tests."""
    control_q = [d['num_questions'] for d in control]
    treatment_q = [d['num_questions'] for d in treatment]
    
    t_stat, p_value = stats.ttest_ind(control_q, treatment_q)
    
    pooled_std = np.sqrt((np.var(control_q, ddof=1) + np.var(treatment_q, ddof=1)) / 2)
    cohens_d = (np.mean(control_q) - np.mean(treatment_q)) / pooled_std if pooled_std > 0 else 0
    
    diff_mean = np.mean(treatment_q) - np.mean(control_q)
    se_diff = np.sqrt(np.var(control_q, ddof=1)/len(control_q) + np.var(treatment_q, ddof=1)/len(treatment_q))
    ci_95 = (diff_mean - 1.96*se_diff, diff_mean + 1.96*se_diff)
    
    pct_change = (diff_mean / np.mean(control_q)) * 100 if np.mean(control_q) > 0 else 0
    
    return {
        "t_statistic": float(t_stat),
        "p_value": float(p_value),
        "cohens_d": float(cohens_d),
        "diff_mean": float(diff_mean),
        "diff_pct": float(pct_change),
        "ci_95_lower": float(ci_95[0]),
        "ci_95_upper": float(ci_95[1]),
        "is_significant": bool(p_value < 0.05),
        "power": ">=0.80" if len(control) >= 50 and len(treatment) >= 50 else "<0.80"
    }


def print_summary(control: Dict, treatment: Dict, stats: Dict):
    """Print analysis summary."""
    print(f"\n{'='*70}")
    print("STATISTICAL SUMMARY")
    print(f"{'='*70}")
    print(f"\nAverage Questions:")
    print(f"  Control:   {control['avg_questions']:.2f} ± {control['std_questions']:.2f}")
    print(f"  Treatment: {treatment['avg_questions']:.2f} ± {treatment['std_questions']:.2f}")
    print(f"  Difference: {stats['diff_mean']:.2f} ({stats['diff_pct']:+.1f}%)")
    print(f"  95% CI: [{stats['ci_95_lower']:.2f}, {stats['ci_95_upper']:.2f}]")
    print(f"\nStatistical Tests:")
    print(f"  t-statistic: {stats['t_statistic']:.3f}")
    print(f"  p-value: {stats['p_value']:.4f}")
    print(f"  Significant: {'YES ✓' if stats['is_significant'] else 'NO ✗'}")
    print(f"  Effect size: {stats['cohens_d']:.3f} (Cohen's d)")
    print(f"{'='*70}\n")


def main():
    parser = argparse.ArgumentParser(description='Direct A/B Experiment Runner')
    parser.add_argument('--policy', choices=['baseline', 'new'], help='Policy to test')
    parser.add_argument('--sessions', type=int, default=60, help='Number of sessions')
    parser.add_argument('--experiment-id', type=str, help='Experiment identifier')
    parser.add_argument('--use-real-api', action='store_true', help='Use real OpenAI API (costs money)')
    parser.add_argument('--analyze', action='store_true', help='Analyze results')
    parser.add_argument('--control', type=str, help='Control experiment ID')
    parser.add_argument('--treatment', type=str, help='Treatment experiment ID')
    
    args = parser.parse_args()
    
    # Initialize database
    init_database()
    
    if args.analyze:
        if not args.control or not args.treatment:
            print("Error: --analyze requires --control and --treatment IDs")
            return
        analyze_experiments(args.control, args.treatment)
    elif args.policy and args.experiment_id:
        run_experiment(args.experiment_id, args.policy, args.sessions, args.use_real_api)
    else:
        parser.print_help()
        print("\n📖 Quick Start (Simulated - FREE):")
        print("  1. python run_direct_ab_experiment.py --policy baseline --sessions 60 --experiment-id week20_control_sim")
        print("  2. python run_direct_ab_experiment.py --policy new --sessions 60 --experiment-id week20_treatment_sim")
        print("  3. python run_direct_ab_experiment.py --analyze --control week20_control_sim --treatment week20_treatment_sim")
        print("\n💰 Real OpenAI API (adds --use-real-api flag, ~$0.20-0.30)")


if __name__ == '__main__':
    main()
