#!/usr/bin/env python3
"""
A/B Experiment Runner for Early-Stop Policy Comparison

Generates experiment data by running interview sessions with:
- Control group: Baseline policy (0.95/0.05, min 5 questions)
- Treatment group: New policy (0.90/0.10, min 6 questions)

Usage:
    # Run control sessions (60+ sessions)
    python run_ab_experiment.py --policy baseline --sessions 60 --experiment-id week20_control

    # Run treatment sessions (60+ sessions)
    python run_ab_experiment.py --policy new --sessions 60 --experiment-id week20_treatment
    
    # Analyze results
    python run_ab_experiment.py --analyze --control week20_control --treatment week20_treatment
"""

import argparse
import json
import os
import subprocess
import sys
import time
from pathlib import Path
from datetime import datetime
import mysql.connector
from typing import List, Dict, Any, Tuple
import numpy as np
from scipy import stats

# Interview profile distribution (Andy's requirement: Junior >= 20 sessions out of 60)
PROFILE_DISTRIBUTION = [
    {"positionType": "Junior Developer", "weight": 0.40, "target_questions": 6, "slice": "junior"},
    {"positionType": "Backend Java Developer", "weight": 0.15, "target_questions": 8, "slice": "mid"},
    {"positionType": "Full Stack Engineer", "weight": 0.15, "target_questions": 10, "slice": "mid"},
    {"positionType": "Senior Software Engineer", "weight": 0.30, "target_questions": 12, "slice": "senior"},
]

def get_db_connection():
    """Get database connection from environment variables."""
    return mysql.connector.connect(
        host=os.getenv('DB_HOST', 'mysql-4c9be66-andyxiongzheng-9267.g.aivencloud.com'),
        port=int(os.getenv('DB_PORT', '22629')),
        user=os.getenv('DB_USER', 'avnadmin'),
        password=os.getenv('DB_PASSWORD'),
        database=os.getenv('DB_NAME', 'ai_interview')
    )


def update_application_properties(enable_new_policy: bool):
    """Update application.properties to switch between baseline and new policy."""
    props_path = Path(__file__).parent / "src" / "main" / "resources" / "application.properties"
    
    if not props_path.exists():
        print(f"Error: application.properties not found at {props_path}")
        return False
    
    # Read current content
    with open(props_path, 'r', encoding='utf-8') as f:
        lines = f.readlines()
    
    # Update the new-policy.enabled line
    updated = False
    for i, line in enumerate(lines):
        if line.startswith('ml.prediction.early-stopping.new-policy.enabled='):
            lines[i] = f'ml.prediction.early-stopping.new-policy.enabled={str(enable_new_policy).lower()}\n'
            updated = True
            break
    
    if not updated:
        print("Warning: Could not find new-policy.enabled property")
        return False
    
    # Write back
    with open(props_path, 'w', encoding='utf-8') as f:
        f.writelines(lines)
    
    policy_name = "new policy (0.90/0.10)" if enable_new_policy else "baseline policy (0.95/0.05)"
    print(f"✓ Updated application.properties to use {policy_name}")
    return True


def run_e2e_sessions(num_sessions: int, policy: str, experiment_id: str):
    """Run E2E sessions for the specified policy."""
    print(f"\n{'='*70}")
    print(f"Running {num_sessions} sessions with {policy} policy")
    print(f"Experiment ID: {experiment_id}")
    print(f"{'='*70}\n")
    
    # Update configuration
    enable_new = (policy == "new")
    if not update_application_properties(enable_new):
        return False
    
    print(f"⚠ Application must be restarted with updated configuration")
    print(f"   Please restart: mvn spring-boot:run")
    print(f"\nPress Enter after application restart to continue...")
    input()
    
    # Record start timestamp for data extraction
    start_time = datetime.now()
    print(f"\n⏱ Experiment start time: {start_time.strftime('%Y-%m-%d %H:%M:%S')}")
    print(f"   Save this for data extraction: {experiment_id}")
    
    # Run E2E sessions
    try:
        cmd = [
            sys.executable,
            "run_e2e_sessions.py",
            "--sessions", str(num_sessions),
            "--base-url", "http://localhost:8080",
            "--verbose"
        ]
        
        result = subprocess.run(cmd, check=True)
        
        end_time = datetime.now()
        print(f"\n✓ Completed {num_sessions} sessions for {policy} policy")
        print(f"   Start time: {start_time.strftime('%Y-%m-%d %H:%M:%S')}")
        print(f"   End time:   {end_time.strftime('%Y-%m-%d %H:%M:%S')}")
        print(f"   Duration:   {(end_time - start_time).total_seconds():.1f}s")
        
        # Save experiment metadata
        metadata = {
            "experiment_id": experiment_id,
            "policy": policy,
            "num_sessions": num_sessions,
            "start_time": start_time.isoformat(),
            "end_time": end_time.isoformat(),
            "duration_seconds": (end_time - start_time).total_seconds()
        }
        
        metadata_path = Path(__file__).parent.parent / "eval" / "results" / f"{experiment_id}_metadata.json"
        metadata_path.parent.mkdir(parents=True, exist_ok=True)
        with open(metadata_path, 'w') as f:
            json.dump(metadata, f, indent=2)
        
        print(f"\n📝 Metadata saved to: {metadata_path}")
        print(f"\nNext step: Run analysis with --analyze --control <control_id> --treatment {experiment_id}")
        
        return True
        
    except subprocess.CalledProcessError as e:
        print(f"✗ Error running E2E sessions: {e}")
        return False


def extract_experiment_data(control_id: str, treatment_id: str):
    """Extract experiment data from database and perform statistical analysis."""
    print(f"\n{'='*70}")
    print(f"Analyzing A/B Experiment Results")
    print(f"Control:   {control_id}")
    print(f"Treatment: {treatment_id}")
    print(f"{'='*70}\n")
    
    # Load metadata
    eval_results_dir = Path(__file__).parent.parent / "eval" / "results"
    control_meta = load_metadata(eval_results_dir / f"{control_id}_metadata.json")
    treatment_meta = load_metadata(eval_results_dir / f"{treatment_id}_metadata.json")
    
    if not control_meta or not treatment_meta:
        print("✗ Failed to load experiment metadata")
        return False
    
    # Extract data from database
    conn = get_db_connection()
    try:
        control_data = extract_session_data(conn, control_meta['start_time'], control_meta['end_time'])
        treatment_data = extract_session_data(conn, treatment_meta['start_time'], treatment_meta['end_time'])
        
        print(f"\n📊 Data Summary:")
        print(f"   Control sessions:   {len(control_data)}")
        print(f"   Treatment sessions: {len(treatment_data)}")
        
        if len(control_data) < 30 or len(treatment_data) < 30:
            print(f"\n⚠ Warning: Sample size may be too small for robust statistical analysis")
        
        # Compute metrics
        control_metrics = compute_arm_metrics(control_data, "Control")
        treatment_metrics = compute_arm_metrics(treatment_data, "Treatment")
        
        # Statistical tests
        statistical_results = perform_statistical_tests(control_data, treatment_data)
        
        # Generate report
        report_path = eval_results_dir / f"ab_analysis_{control_id}_vs_{treatment_id}.json"
        report_data = {
            "control_id": control_id,
            "treatment_id": treatment_id,
            "control_metrics": control_metrics,
            "treatment_metrics": treatment_metrics,
            "statistical_tests": statistical_results,
            "analysis_timestamp": datetime.now().isoformat()
        }
        
        with open(report_path, 'w') as f:
            json.dump(report_data, f, indent=2)
        
        print(f"\n✓ Analysis saved to: {report_path}")
        
        # Print summary
        print_analysis_summary(control_metrics, treatment_metrics, statistical_results)
        
        return True
        
    except Exception as e:
        print(f"✗ Error during analysis: {e}")
        import traceback
        traceback.print_exc()
        return False
    finally:
        conn.close()


def load_metadata(path: Path) -> Dict[str, Any]:
    """Load experiment metadata from JSON file."""
    if not path.exists():
        print(f"✗ Metadata file not found: {path}")
        return None
    
    with open(path, 'r') as f:
        return json.load(f)


def extract_session_data(conn, start_time: str, end_time: str) -> List[Dict[str, Any]]:
    """Extract interview session data from database."""
    cursor = conn.cursor(dictionary=True)
    
    query = """
        SELECT 
            i.id as interview_id,
            i.position_type,
            i.status,
            i.created_at,
            i.updated_at,
            COUNT(DISTINCT im.id) as num_questions,
            AVG(im.processing_time_ms) as avg_latency_ms
        FROM interview i
        LEFT JOIN interview_message im ON i.id = im.interview_id AND im.is_from_ai = 0
        WHERE i.created_at >= %s AND i.created_at <= %s
        GROUP BY i.id
        HAVING num_questions >= 5
        ORDER BY i.created_at
    """
    
    cursor.execute(query, (start_time, end_time))
    sessions = cursor.fetchall()
    
    # Classify by slice
    for session in sessions:
        position = session['position_type']
        if 'Junior' in position:
            session['slice'] = 'junior'
        elif 'Senior' in position:
            session['slice'] = 'senior'
        else:
            session['slice'] = 'mid'
    
    cursor.close()
    return sessions


def compute_arm_metrics(data: List[Dict[str, Any]], arm_name: str) -> Dict[str, Any]:
    """Compute metrics for one experimental arm."""
    if not data:
        return {}
    
    num_questions = [s['num_questions'] for s in data]
    latencies = [s['avg_latency_ms'] for s in data if s['avg_latency_ms']]
    
    # Overall metrics
    metrics = {
        "sample_size": len(data),
        "avg_questions": np.mean(num_questions),
        "std_questions": np.std(num_questions, ddof=1) if len(num_questions) > 1 else 0,
        "median_questions": np.median(num_questions),
        "min_questions": np.min(num_questions),
        "max_questions": np.max(num_questions),
        "avg_latency_ms": np.mean(latencies) if latencies else 0,
        "p50_latency_ms": np.percentile(latencies, 50) if latencies else 0,
        "p95_latency_ms": np.percentile(latencies, 95) if latencies else 0,
    }
    
    # Slice-level metrics
    for slice_name in ['junior', 'mid', 'senior']:
        slice_data = [s for s in data if s['slice'] == slice_name]
        if slice_data:
            slice_questions = [s['num_questions'] for s in slice_data]
            metrics[f"{slice_name}_sample_size"] = len(slice_data)
            metrics[f"{slice_name}_avg_questions"] = np.mean(slice_questions)
            metrics[f"{slice_name}_std_questions"] = np.std(slice_questions, ddof=1) if len(slice_questions) > 1 else 0
    
    print(f"\n📈 {arm_name} Metrics:")
    print(f"   Total sessions: {metrics['sample_size']}")
    print(f"   Avg questions:  {metrics['avg_questions']:.2f} ± {metrics['std_questions']:.2f}")
    print(f"   Median:         {metrics['median_questions']:.0f}")
    print(f"   Range:          [{metrics['min_questions']:.0f}, {metrics['max_questions']:.0f}]")
    print(f"   Avg latency:    {metrics['avg_latency_ms']:.0f}ms (p50={metrics['p50_latency_ms']:.0f}, p95={metrics['p95_latency_ms']:.0f})")
    
    return metrics


def perform_statistical_tests(control_data: List[Dict[str, Any]], 
                              treatment_data: List[Dict[str, Any]]) -> Dict[str, Any]:
    """Perform statistical tests between control and treatment groups."""
    control_questions = [s['num_questions'] for s in control_data]
    treatment_questions = [s['num_questions'] for s in treatment_data]
    
    # T-test for avg questions
    t_stat, p_value = stats.ttest_ind(control_questions, treatment_questions)
    
    # Effect size (Cohen's d)
    pooled_std = np.sqrt((np.var(control_questions, ddof=1) + np.var(treatment_questions, ddof=1)) / 2)
    cohens_d = (np.mean(control_questions) - np.mean(treatment_questions)) / pooled_std if pooled_std > 0 else 0
    
    # Confidence interval for difference in means
    diff_mean = np.mean(treatment_questions) - np.mean(control_questions)
    se_diff = np.sqrt(np.var(control_questions, ddof=1)/len(control_questions) + 
                      np.var(treatment_questions, ddof=1)/len(treatment_questions))
    ci_95 = (diff_mean - 1.96*se_diff, diff_mean + 1.96*se_diff)
    
    # Percent change
    pct_change = (diff_mean / np.mean(control_questions)) * 100 if np.mean(control_questions) > 0 else 0
    
    results = {
        "t_statistic": float(t_stat),
        "p_value": float(p_value),
        "cohens_d": float(cohens_d),
        "diff_mean": float(diff_mean),
        "diff_pct": float(pct_change),
        "ci_95_lower": float(ci_95[0]),
        "ci_95_upper": float(ci_95[1]),
        "is_significant": bool(p_value < 0.05),
        "power": ">=0.80" if len(control_data) >= 50 and len(treatment_data) >= 50 else "<0.80"
    }
    
    return results


def print_analysis_summary(control_metrics: Dict[str, Any], 
                          treatment_metrics: Dict[str, Any],
                          statistical_results: Dict[str, Any]):
    """Print human-readable analysis summary."""
    print(f"\n{'='*70}")
    print("STATISTICAL ANALYSIS SUMMARY")
    print(f"{'='*70}\n")
    
    print(f"📊 Primary Metric: Average Questions per Interview")
    print(f"   Control:   {control_metrics['avg_questions']:.2f} ± {control_metrics['std_questions']:.2f}")
    print(f"   Treatment: {treatment_metrics['avg_questions']:.2f} ± {treatment_metrics['std_questions']:.2f}")
    print(f"   Difference: {statistical_results['diff_mean']:.2f} ({statistical_results['diff_pct']:+.1f}%)")
    print(f"   95% CI:     [{statistical_results['ci_95_lower']:.2f}, {statistical_results['ci_95_upper']:.2f}]")
    
    print(f"\n🔬 Statistical Tests:")
    print(f"   t-statistic: {statistical_results['t_statistic']:.3f}")
    print(f"   p-value:     {statistical_results['p_value']:.4f}")
    print(f"   Significant: {'YES ✓' if statistical_results['is_significant'] else 'NO ✗'} (α=0.05)")
    print(f"   Effect size: {statistical_results['cohens_d']:.3f} (Cohen's d)")
    print(f"   Power:       {statistical_results['power']}")
    
    # Effect size interpretation
    abs_d = abs(statistical_results['cohens_d'])
    if abs_d < 0.2:
        effect_interpretation = "negligible"
    elif abs_d < 0.5:
        effect_interpretation = "small"
    elif abs_d < 0.8:
        effect_interpretation = "medium"
    else:
        effect_interpretation = "large"
    print(f"   Interpretation: {effect_interpretation} effect")
    
    # Decision recommendation
    print(f"\n💡 Recommendation:")
    if statistical_results['is_significant'] and statistical_results['diff_pct'] < -5:
        print(f"   ✓ RAMP - Significant efficiency improvement detected")
    elif not statistical_results['is_significant']:
        print(f"   ⚠ HOLD - No significant difference detected")
    elif statistical_results['diff_pct'] > 0:
        print(f"   ✗ ROLLBACK - Treatment performs worse than control")
    else:
        print(f"   ? REVIEW - Manual review recommended")
    
    print(f"\n{'='*70}\n")


def main():
    parser = argparse.ArgumentParser(description='A/B Experiment Runner and Analyzer')
    parser.add_argument('--policy', choices=['baseline', 'new'], 
                       help='Policy to test (baseline or new)')
    parser.add_argument('--sessions', type=int, default=60,
                       help='Number of sessions to run (default: 60)')
    parser.add_argument('--experiment-id', type=str,
                       help='Unique experiment identifier (e.g., week20_control)')
    parser.add_argument('--analyze', action='store_true',
                       help='Analyze experiment results')
    parser.add_argument('--control', type=str,
                       help='Control experiment ID for analysis')
    parser.add_argument('--treatment', type=str,
                       help='Treatment experiment ID for analysis')
    
    args = parser.parse_args()
    
    if args.analyze:
        if not args.control or not args.treatment:
            print("Error: --analyze requires --control and --treatment experiment IDs")
            return
        extract_experiment_data(args.control, args.treatment)
    elif args.policy and args.experiment_id:
        run_e2e_sessions(args.sessions, args.policy, args.experiment_id)
    else:
        parser.print_help()
        print("\n📖 Example Workflow:")
        print("  1. python run_ab_experiment.py --policy baseline --sessions 60 --experiment-id week20_control")
        print("     [Restart application after config update]")
        print("  2. python run_ab_experiment.py --policy new --sessions 60 --experiment-id week20_treatment")
        print("     [Restart application after config update]")
        print("  3. python run_ab_experiment.py --analyze --control week20_control --treatment week20_treatment")

if __name__ == '__main__':
    main()
