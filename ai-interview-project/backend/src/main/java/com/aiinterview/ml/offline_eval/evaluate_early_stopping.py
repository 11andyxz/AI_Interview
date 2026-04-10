#!/usr/bin/env python3
"""
Early-Stopping Policy Evaluation Script

Simulates early-stopping policies on historical interview data to assess
the trade-off between interview efficiency and prediction accuracy.

Usage:
    python evaluate_early_stopping.py --data data/sessions.csv --policy-config configs/policy.yaml
    python evaluate_early_stopping.py --data data/sessions.csv --policies configs/policy_*.yaml
"""

import argparse
import json
import os
import sys
from datetime import datetime
from typing import Dict, List, Tuple, Optional
import logging
import glob

try:
    import pandas as pd
    import numpy as np
    import yaml
except ImportError as e:
    print(f"Error: Missing required package. Install with:")
    print(f"  pip install pandas numpy pyyaml")
    sys.exit(1)

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)


class EarlyStoppingSimulator:
    """Simulates early-stopping policies on offline data"""
    
    def __init__(self, policy_config: Dict):
        """
        Initialize simulator with policy configuration.
        
        Args:
            policy_config: Early-stopping policy parameters
        """
        self.policy = policy_config
        self.results = {}
    
    @staticmethod
    def get_default_policy() -> Dict:
        """Get default early-stopping policy"""
        return {
            'name': 'baseline',
            'pass_threshold': 0.95,
            'fail_threshold': 0.05,
            'min_questions': 5,
            'confidence_requirement': 0.90,
            'stability_window': 2,
            'role_specific': {
                'junior': {
                    'min_questions': 4,
                    'pass_threshold': 0.92,
                    'fail_threshold': 0.08
                },
                'mid': {
                    'min_questions': 5,
                    'pass_threshold': 0.94,
                    'fail_threshold': 0.06
                },
                'senior': {
                    'min_questions': 6,
                    'pass_threshold': 0.96,
                    'fail_threshold': 0.04
                }
            }
        }
    
    def load_policy(self, policy_path: str) -> Dict:
        """Load policy from YAML file"""
        with open(policy_path, 'r') as f:
            return yaml.safe_load(f)
    
    def load_response_data(self, responses_path: str) -> pd.DataFrame:
        """Load detailed response-level data"""
        logger.info(f"Loading response data from {responses_path}")
        df = pd.read_csv(responses_path)
        logger.info(f"Loaded {len(df)} responses")
        return df
    
    def simulate_prediction_trajectory(
        self,
        responses_df: pd.DataFrame
    ) -> pd.DataFrame:
        """
        Simulate prediction evolution over interview progression.
        
        For each session, generate predicted pass probability after each question.
        
        Args:
            responses_df: DataFrame with response-level data
        
        Returns:
            DataFrame with prediction trajectory
        """
        trajectory_data = []
        
        for session_id in responses_df['session_id'].unique():
            session_responses = responses_df[responses_df['session_id'] == session_id].sort_values('created_at')
            
            cumulative_score = 0
            question_count = 0
            
            for idx, row in session_responses.iterrows():
                question_count += 1
                cumulative_score += row['evaluation_score']
                avg_score = cumulative_score / question_count
                
                # Simulate pass probability (placeholder - would use actual model)
                # Model confidence increases with more questions
                base_prob = avg_score / 100.0
                confidence = min(0.99, 0.5 + (question_count * 0.08))
                
                # Add noise to simulate uncertainty
                noise = np.random.normal(0, 0.05 * (1 - confidence))
                pass_probability = np.clip(base_prob + noise, 0, 1)
                
                trajectory_data.append({
                    'session_id': session_id,
                    'question_number': question_count,
                    'cumulative_score': cumulative_score,
                    'avg_score': avg_score,
                    'pass_probability': pass_probability,
                    'confidence': confidence
                })
        
        return pd.DataFrame(trajectory_data)
    
    def apply_stopping_rule(
        self,
        trajectory_df: pd.DataFrame,
        policy: Dict,
        role_level: str = 'mid'
    ) -> Dict:
        """
        Apply early-stopping rule to prediction trajectory.
        
        Args:
            trajectory_df: DataFrame with prediction evolution
            policy: Early-stopping policy
            role_level: Candidate role level
        
        Returns:
            Stopping decision and metadata
        """
        # Get role-specific thresholds
        role_policy = policy.get('role_specific', {}).get(role_level, {})
        min_q = role_policy.get('min_questions', policy['min_questions'])
        pass_thresh = role_policy.get('pass_threshold', policy['pass_threshold'])
        fail_thresh = role_policy.get('fail_threshold', policy['fail_threshold'])
        confidence_req = policy.get('confidence_requirement', 0.90)
        stability_window = policy.get('stability_window', 2)
        
        stopped = False
        stop_question = None
        stop_reason = None
        
        for idx, row in trajectory_df.iterrows():
            q_num = row['question_number']
            pass_prob = row['pass_probability']
            confidence = row['confidence']
            
            # Check minimum questions
            if q_num < min_q:
                continue
            
            # Check confidence requirement
            if confidence < confidence_req:
                continue
            
            # Check stability (prediction consistent over window)
            if q_num >= stability_window:
                recent_probs = trajectory_df[
                    trajectory_df['question_number'] <= q_num
                ].tail(stability_window)['pass_probability']
                
                prob_variance = recent_probs.var()
                if prob_variance > 0.01:  # Not stable
                    continue
            
            # Apply stopping thresholds
            if pass_prob >= pass_thresh:
                stopped = True
                stop_question = q_num
                stop_reason = 'early_pass'
                break
            elif pass_prob <= fail_thresh:
                stopped = True
                stop_question = q_num
                stop_reason = 'early_fail'
                break
        
        return {
            'stopped': stopped,
            'stop_question': stop_question,
            'stop_reason': stop_reason,
            'total_questions': len(trajectory_df),
            'questions_saved': len(trajectory_df) - stop_question if stopped else 0
        }
    
    def evaluate_policy(
        self,
        sessions_df: pd.DataFrame,
        responses_df: pd.DataFrame
    ) -> Dict:
        """
        Evaluate early-stopping policy on dataset.
        
        Args:
            sessions_df: Session-level data
            responses_df: Response-level data
        
        Returns:
            Policy evaluation results
        """
        logger.info(f"Evaluating policy: {self.policy['name']}")
        
        # Simulate prediction trajectories
        trajectory_df = self.simulate_prediction_trajectory(responses_df)
        
        # Apply policy to each session
        decisions = []
        for session_id in trajectory_df['session_id'].unique():
            session_trajectory = trajectory_df[trajectory_df['session_id'] == session_id]
            session_info = sessions_df[sessions_df['session_id'] == session_id].iloc[0]
            
            # Infer role level (placeholder - would come from data)
            role_level = 'mid'
            
            decision = self.apply_stopping_rule(session_trajectory, self.policy, role_level)
            decision['session_id'] = session_id
            decision['actual_score'] = session_info['avg_score']
            decision['actual_pass'] = session_info['avg_score'] >= 60
            
            decisions.append(decision)
        
        decisions_df = pd.DataFrame(decisions)
        
        # Calculate aggregate metrics
        stopped_sessions = decisions_df[decisions_df['stopped']]
        
        results = {
            'policy_name': self.policy['name'],
            'policy_config': self.policy,
            'total_sessions': len(decisions_df),
            'stopped_count': len(stopped_sessions),
            'stop_rate': float(len(stopped_sessions) / len(decisions_df)) if len(decisions_df) > 0 else 0.0,
            'avg_questions_total': float(decisions_df['total_questions'].mean()),
            'avg_questions_stopped': float(stopped_sessions['stop_question'].mean()) if len(stopped_sessions) > 0 else 0.0,
            'avg_questions_saved': float(stopped_sessions['questions_saved'].mean()) if len(stopped_sessions) > 0 else 0.0,
            'time_savings_pct': float(stopped_sessions['questions_saved'].sum() / decisions_df['total_questions'].sum() * 100) if len(decisions_df) > 0 else 0.0
        }
        
        # Analyze stopping correctness (premature stops)
        if len(stopped_sessions) > 0:
            early_pass = stopped_sessions[stopped_sessions['stop_reason'] == 'early_pass']
            early_fail = stopped_sessions[stopped_sessions['stop_reason'] == 'early_fail']
            
            results['early_pass_count'] = len(early_pass)
            results['early_fail_count'] = len(early_fail)
            
            if len(early_pass) > 0:
                premature_pass = early_pass[early_pass['actual_pass'] == False]
                results['premature_pass_rate'] = float(len(premature_pass) / len(early_pass))
            else:
                results['premature_pass_rate'] = 0.0
            
            if len(early_fail) > 0:
                premature_fail = early_fail[early_fail['actual_pass'] == True]
                results['premature_fail_rate'] = float(len(premature_fail) / len(early_fail))
            else:
                results['premature_fail_rate'] = 0.0
            
            results['overall_premature_rate'] = float(
                (len(early_pass[early_pass['actual_pass'] == False]) + 
                 len(early_fail[early_fail['actual_pass'] == True])) / len(stopped_sessions)
            )
        else:
            results['early_pass_count'] = 0
            results['early_fail_count'] = 0
            results['premature_pass_rate'] = 0.0
            results['premature_fail_rate'] = 0.0
            results['overall_premature_rate'] = 0.0
        
        logger.info(f"Policy evaluation complete. Stop rate: {results['stop_rate']:.1%}, "
                   f"Premature rate: {results['overall_premature_rate']:.1%}")
        
        return results
    
    def run(
        self,
        sessions_path: str,
        responses_path: str
    ) -> Dict:
        """
        Run policy simulation.
        
        Args:
            sessions_path: Path to sessions CSV
            responses_path: Path to responses CSV
        
        Returns:
            Simulation results
        """
        # Load data
        sessions_df = pd.read_csv(sessions_path)
        responses_df = self.load_response_data(responses_path)
        
        # Evaluate policy
        results = self.evaluate_policy(sessions_df, responses_df)
        
        # Add metadata
        results['timestamp'] = datetime.utcnow().isoformat()
        results['data_source'] = {
            'sessions': sessions_path,
            'responses': responses_path
        }
        
        return results


def compare_policies(
    policy_configs: List[Dict],
    sessions_path: str,
    responses_path: str
) -> Dict:
    """
    Compare multiple early-stopping policies.
    
    Args:
        policy_configs: List of policy configurations
        sessions_path: Path to sessions data
        responses_path: Path to responses data
    
    Returns:
        Comparison results
    """
    logger.info(f"Comparing {len(policy_configs)} policies")
    
    comparison_results = {
        'timestamp': datetime.utcnow().isoformat(),
        'policies': []
    }
    
    for policy_config in policy_configs:
        simulator = EarlyStoppingSimulator(policy_config)
        results = simulator.run(sessions_path, responses_path)
        comparison_results['policies'].append(results)
    
    # Generate comparison table
    comparison_table = []
    for policy_result in comparison_results['policies']:
        comparison_table.append({
            'policy': policy_result['policy_name'],
            'stop_rate': policy_result['stop_rate'],
            'avg_questions': policy_result['avg_questions_stopped'],
            'questions_saved': policy_result['avg_questions_saved'],
            'premature_rate': policy_result['overall_premature_rate'],
            'time_savings_pct': policy_result['time_savings_pct']
        })
    
    comparison_results['comparison_table'] = comparison_table
    
    # Print comparison
    logger.info("\n=== Policy Comparison ===")
    logger.info(f"{'Policy':<20} {'Stop Rate':<12} {'Avg Qs':<10} {'Saved':<10} {'Premature':<12} {'Time Saved':<12}")
    logger.info("-" * 90)
    for row in comparison_table:
        logger.info(f"{row['policy']:<20} {row['stop_rate']:<12.1%} {row['avg_questions']:<10.1f} "
                   f"{row['questions_saved']:<10.1f} {row['premature_rate']:<12.1%} {row['time_savings_pct']:<12.1f}%")
    
    return comparison_results


def main():
    parser = argparse.ArgumentParser(
        description='Evaluate early-stopping policies'
    )
    
    parser.add_argument(
        '--data', '-d',
        type=str,
        required=True,
        help='Base path to evaluation data (will look for _sessions.csv and _responses.csv)'
    )
    parser.add_argument(
        '--policy-config', '-p',
        type=str,
        help='Path to single policy configuration (YAML)'
    )
    parser.add_argument(
        '--policies',
        type=str,
        help='Glob pattern for multiple policy configs (e.g., configs/policy_*.yaml)'
    )
    parser.add_argument(
        '--output', '-o',
        type=str,
        default='results/early_stop_evaluation.json',
        help='Output file path for results'
    )
    
    args = parser.parse_args()
    
    # Determine sessions and responses paths
    if args.data.endswith('.csv'):
        base_path = args.data.replace('.csv', '')
    else:
        base_path = args.data
    
    sessions_path = f"{base_path}_sessions.csv"
    responses_path = f"{base_path}_responses.csv"
    
    # Load policy configurations
    policy_configs = []
    
    if args.policy_config:
        with open(args.policy_config, 'r') as f:
            policy_configs.append(yaml.safe_load(f))
    elif args.policies:
        policy_files = glob.glob(args.policies)
        for policy_file in policy_files:
            with open(policy_file, 'r') as f:
                policy_configs.append(yaml.safe_load(f))
    else:
        # Use default policy
        policy_configs.append(EarlyStoppingSimulator.get_default_policy())
    
    logger.info(f"Loaded {len(policy_configs)} policy configurations")
    
    # Run evaluation
    if len(policy_configs) == 1:
        simulator = EarlyStoppingSimulator(policy_configs[0])
        results = simulator.run(sessions_path, responses_path)
    else:
        results = compare_policies(policy_configs, sessions_path, responses_path)
    
    # Save results
    output_dir = os.path.dirname(args.output)
    if output_dir and not os.path.exists(output_dir):
        os.makedirs(output_dir)
    
    with open(args.output, 'w') as f:
        json.dump(results, f, indent=2)
    
    logger.info(f"Results saved to {args.output}")


if __name__ == '__main__':
    main()
