#!/usr/bin/env python3
"""
A/B Experiment Runner for Early-Stop Policy Comparison

Generates experiment data by running interview sessions with:
- Control group: Baseline policy (0.95/0.05, min 5 questions)
- Treatment group: New policy (0.90/0.10, min 6 questions)

Usage:
    # Run baseline policy sessions
    python run_ab_experiment.py --policy baseline --sessions 50

    # Run new policy sessions  
    python run_ab_experiment.py --policy new --sessions 50
    
    # Analyze results
    python run_ab_experiment.py --analyze
"""

import argparse
import json
import subprocess
import sys
import time
from pathlib import Path
from datetime import datetime

# Interview profile distribution
PROFILE_DISTRIBUTION = [
    {"positionType": "Junior Developer", "weight": 0.40, "target_questions": 6},
    {"positionType": "Backend Java Developer", "weight": 0.15, "target_questions": 8},
    {"positionType": "Full Stack Engineer", "weight": 0.15, "target_questions": 10},
    {"positionType": "Senior Software Engineer", "weight": 0.30, "target_questions": 12},
]

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

def run_e2e_sessions(num_sessions: int, policy: str):
    """Run E2E sessions for the specified policy."""
    print(f"\n{'='*70}")
    print(f"Running {num_sessions} sessions with {policy} policy")
    print(f"{'='*70}\n")
    
    # Update configuration
    enable_new = (policy == "new")
    if not update_application_properties(enable_new):
        return False
    
    print(f"⚠ Application must be restarted with updated configuration")
    print(f"   Please restart: mvn spring-boot:run")
    print(f"\nPress Enter after application restart to continue...")
    input()
    
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
        print(f"\n✓ Completed {num_sessions} sessions for {policy} policy")
        return True
        
    except subprocess.CalledProcessError as e:
        print(f"✗ Error running E2E sessions: {e}")
        return False

def extract_experiment_data():
    """Extract experiment data from database and analyze results."""
    print(f"\n{'='*70}")
    print("Extracting and analyzing experiment data")
    print(f"{'='*70}\n")
    
    # TODO: Implement data extraction from database
    # This would query interview and interview_message tables
    # Group by policy (based on created_at timestamp ranges)
    # Calculate metrics: avg questions, RMSE, premature stop rate, latency
    
    print("Data extraction not yet implemented")
    print("Manual steps:")
    print("1. Query database for interview sessions created during baseline run")
    print("2. Query database for interview sessions created during new policy run")
    print("3. Use eval/compute_metrics.py to calculate statistical comparison")
    
    return True

def main():
    parser = argparse.ArgumentParser(description='A/B Experiment Runner')
    parser.add_argument('--policy', choices=['baseline', 'new'], 
                       help='Policy to test (baseline or new)')
    parser.add_argument('--sessions', type=int, default=50,
                       help='Number of sessions to run (default: 50)')
    parser.add_argument('--analyze', action='store_true',
                       help='Extract and analyze experiment data')
    
    args = parser.parse_args()
    
    if args.analyze:
        extract_experiment_data()
    elif args.policy:
        run_e2e_sessions(args.sessions, args.policy)
    else:
        parser.print_help()
        print("\nExample workflow:")
        print("  1. python run_ab_experiment.py --policy baseline --sessions 50")
        print("  2. python run_ab_experiment.py --policy new --sessions 50")
        print("  3. python run_ab_experiment.py --analyze")

if __name__ == '__main__':
    main()
