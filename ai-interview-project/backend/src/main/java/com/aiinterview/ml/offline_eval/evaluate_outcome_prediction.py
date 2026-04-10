#!/usr/bin/env python3
"""
Outcome Prediction Model Evaluation Script

Evaluates the interview outcome prediction model using offline data.
Calculates RMSE, MAE, Brier score, and other quality metrics.

Usage:
    python evaluate_outcome_prediction.py --data data/sessions.csv --output results/eval.json
    python evaluate_outcome_prediction.py --data data/sessions.csv --cv 5
"""

import argparse
import json
import os
import sys
from datetime import datetime
from typing import Dict, List, Tuple, Optional
import logging

try:
    import pandas as pd
    import numpy as np
    from sklearn.model_selection import cross_val_score, KFold
    from sklearn.metrics import (
        mean_squared_error,
        mean_absolute_error,
        r2_score,
        accuracy_score,
        precision_score,
        recall_score,
        f1_score,
        brier_score_loss,
        confusion_matrix
    )
    import yaml
except ImportError as e:
    print(f"Error: Missing required package. Install with:")
    print(f"  pip install pandas numpy scikit-learn pyyaml")
    sys.exit(1)

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)


class OutcomePredictionEvaluator:
    """Evaluates outcome prediction model performance"""
    
    def __init__(self, config: Optional[Dict] = None):
        """
        Initialize evaluator with configuration.
        
        Args:
            config: Evaluation configuration (thresholds, etc.)
        """
        self.config = config or self.get_default_config()
        self.results = {}
    
    @staticmethod
    def get_default_config() -> Dict:
        """Get default evaluation configuration"""
        return {
            'pass_threshold': 60.0,  # Score threshold for pass/fail
            'confidence_threshold': 0.7,  # Minimum confidence for prediction
            'min_questions': 5,  # Minimum questions for evaluation
            'segment_by': ['tech_stack', 'question_count_bin'],
            'question_bins': [0, 4, 7, 10, float('inf')],
            'question_bin_labels': ['0-3', '4-6', '7-9', '10+']
        }
    
    def load_data(self, data_path: str) -> pd.DataFrame:
        """
        Load and preprocess evaluation data.
        
        Args:
            data_path: Path to CSV file with session data
        
        Returns:
            Preprocessed DataFrame
        """
        logger.info(f"Loading data from {data_path}")
        df = pd.DataFrame(data_path)
        
        # Filter by minimum questions
        min_q = self.config['min_questions']
        original_count = len(df)
        df = df[df['question_count'] >= min_q].copy()
        logger.info(f"Filtered {original_count - len(df)} sessions with <{min_q} questions")
        
        # Create pass/fail labels
        df['actual_pass'] = (df['avg_score'] >= self.config['pass_threshold']).astype(int)
        
        # Bin question counts
        df['question_count_bin'] = pd.cut(
            df['question_count'],
            bins=self.config['question_bins'],
            labels=self.config['question_bin_labels'],
            include_lowest=True
        )
        
        logger.info(f"Loaded {len(df)} sessions for evaluation")
        return df
    
    def calculate_regression_metrics(
        self,
        y_true: np.ndarray,
        y_pred: np.ndarray
    ) -> Dict[str, float]:
        """
        Calculate regression metrics (RMSE, MAE, R²).
        
        Args:
            y_true: Ground truth scores
            y_pred: Predicted scores
        
        Returns:
            Dictionary of metrics
        """
        return {
            'rmse': float(np.sqrt(mean_squared_error(y_true, y_pred))),
            'mae': float(mean_absolute_error(y_true, y_pred)),
            'r2': float(r2_score(y_true, y_pred)),
            'count': len(y_true)
        }
    
    def calculate_classification_metrics(
        self,
        y_true: np.ndarray,
        y_pred: np.ndarray,
        y_prob: Optional[np.ndarray] = None
    ) -> Dict[str, float]:
        """
        Calculate classification metrics (accuracy, precision, recall, F1).
        
        Args:
            y_true: Ground truth labels (0/1)
            y_pred: Predicted labels (0/1)
            y_prob: Predicted probabilities (optional, for Brier score)
        
        Returns:
            Dictionary of metrics
        """
        metrics = {
            'accuracy': float(accuracy_score(y_true, y_pred)),
            'precision': float(precision_score(y_true, y_pred, zero_division=0)),
            'recall': float(recall_score(y_true, y_pred, zero_division=0)),
            'f1': float(f1_score(y_true, y_pred, zero_division=0))
        }
        
        if y_prob is not None:
            metrics['brier_score'] = float(brier_score_loss(y_true, y_prob))
        
        # Confusion matrix
        tn, fp, fn, tp = confusion_matrix(y_true, y_pred).ravel()
        metrics['confusion_matrix'] = {
            'true_negative': int(tn),
            'false_positive': int(fp),
            'false_negative': int(fn),
            'true_positive': int(tp)
        }
        
        # False positive/negative rates
        metrics['false_positive_rate'] = float(fp / (fp + tn)) if (fp + tn) > 0 else 0.0
        metrics['false_negative_rate'] = float(fn / (fn + tp)) if (fn + tp) > 0 else 0.0
        
        return metrics
    
    def evaluate_overall(self, df: pd.DataFrame) -> Dict:
        """
        Evaluate overall model performance.
        
        Args:
            df: DataFrame with actual and predicted values
        
        Returns:
            Dictionary of overall metrics
        """
        logger.info("Calculating overall metrics")
        
        y_true_score = df['avg_score'].values
        y_pred_score = df.get('predicted_score', df['avg_score'] * 0.95).values  # Placeholder
        
        y_true_class = df['actual_pass'].values
        y_pred_class = (y_pred_score >= self.config['pass_threshold']).astype(int)
        
        # Calculate metrics
        regression_metrics = self.calculate_regression_metrics(y_true_score, y_pred_score)
        classification_metrics = self.calculate_classification_metrics(y_true_class, y_pred_class)
        
        return {
            'regression': regression_metrics,
            'classification': classification_metrics
        }
    
    def evaluate_by_segment(
        self,
        df: pd.DataFrame,
        segment_column: str
    ) -> Dict[str, Dict]:
        """
        Evaluate performance by data segment.
        
        Args:
            df: DataFrame with data
            segment_column: Column to segment by
        
        Returns:
            Dictionary of metrics per segment
        """
        logger.info(f"Evaluating by segment: {segment_column}")
        
        segment_results = {}
        
        for segment_value in df[segment_column].unique():
            segment_df = df[df[segment_column] == segment_value]
            
            if len(segment_df) < 5:  # Skip segments with too few samples
                continue
            
            y_true = segment_df['avg_score'].values
            y_pred = segment_df.get('predicted_score', segment_df['avg_score'] * 0.95).values
            
            segment_results[str(segment_value)] = self.calculate_regression_metrics(y_true, y_pred)
        
        return segment_results
    
    def run_cross_validation(
        self,
        df: pd.DataFrame,
        n_folds: int = 5
    ) -> Dict:
        """
        Run cross-validation evaluation.
        
        Args:
            df: DataFrame with data
            n_folds: Number of cross-validation folds
        
        Returns:
            Cross-validation results
        """
        logger.info(f"Running {n_folds}-fold cross-validation")
        
        X = df[['question_count', 'avg_score']].values  # Placeholder features
        y = df['avg_score'].values
        
        kf = KFold(n_splits=n_folds, shuffle=True, random_state=42)
        
        rmse_scores = []
        mae_scores = []
        
        for fold, (train_idx, test_idx) in enumerate(kf.split(X), 1):
            X_test = X[test_idx]
            y_test = y[test_idx]
            
            # Placeholder prediction (would use actual model here)
            y_pred = y_test * 0.95
            
            rmse = np.sqrt(mean_squared_error(y_test, y_pred))
            mae = mean_absolute_error(y_test, y_pred)
            
            rmse_scores.append(rmse)
            mae_scores.append(mae)
            
            logger.info(f"Fold {fold}: RMSE={rmse:.2f}, MAE={mae:.2f}")
        
        return {
            'n_folds': n_folds,
            'rmse_mean': float(np.mean(rmse_scores)),
            'rmse_std': float(np.std(rmse_scores)),
            'rmse_scores': [float(x) for x in rmse_scores],
            'mae_mean': float(np.mean(mae_scores)),
            'mae_std': float(np.std(mae_scores)),
            'mae_scores': [float(x) for x in mae_scores]
        }
    
    def evaluate(
        self,
        data_path: str,
        run_cv: bool = False,
        n_folds: int = 5
    ) -> Dict:
        """
        Run complete evaluation pipeline.
        
        Args:
            data_path: Path to evaluation data
            run_cv: Whether to run cross-validation
            n_folds: Number of CV folds
        
        Returns:
            Complete evaluation results
        """
        # Load data
        df = self.load_data(data_path)
        
        if df.empty:
            logger.error("No data available for evaluation")
            return {'error': 'No data'}
        
        # Overall evaluation
        results = {
            'timestamp': datetime.utcnow().isoformat(),
            'data_source': data_path,
            'config': self.config,
            'data_summary': {
                'total_sessions': len(df),
                'avg_questions': float(df['question_count'].mean()),
                'avg_score': float(df['avg_score'].mean())
            },
            'overall': self.evaluate_overall(df)
        }
        
        # Segment analysis
        results['by_segment'] = {}
        for segment_col in self.config.get('segment_by', []):
            if segment_col in df.columns:
                results['by_segment'][segment_col] = self.evaluate_by_segment(df, segment_col)
        
        # Cross-validation
        if run_cv:
            results['cross_validation'] = self.run_cross_validation(df, n_folds)
        
        logger.info("Evaluation complete")
        return results
    
    def save_results(self, results: Dict, output_path: str):
        """
        Save evaluation results to JSON file.
        
        Args:
            results: Evaluation results dictionary
            output_path: Output file path
        """
        output_dir = os.path.dirname(output_path)
        if output_dir and not os.path.exists(output_dir):
            os.makedirs(output_dir)
        
        with open(output_path, 'w') as f:
            json.dump(results, f, indent=2)
        
        logger.info(f"Results saved to {output_path}")
        
        # Print summary
        self.print_summary(results)
    
    def print_summary(self, results: Dict):
        """Print evaluation summary"""
        logger.info("\n=== Evaluation Summary ===")
        
        overall = results.get('overall', {})
        if 'regression' in overall:
            reg = overall['regression']
            logger.info(f"RMSE: {reg.get('rmse', 0):.2f}")
            logger.info(f"MAE: {reg.get('mae', 0):.2f}")
            logger.info(f"R²: {reg.get('r2', 0):.3f}")
        
        if 'classification' in overall:
            clf = overall['classification']
            logger.info(f"Accuracy: {clf.get('accuracy', 0):.1%}")
            logger.info(f"False Positive Rate: {clf.get('false_positive_rate', 0):.1%}")
            logger.info(f"False Negative Rate: {clf.get('false_negative_rate', 0):.1%}")


def main():
    parser = argparse.ArgumentParser(
        description='Evaluate outcome prediction model'
    )
    
    parser.add_argument(
        '--data', '-d',
        type=str,
        required=True,
        help='Path to evaluation data (CSV)'
    )
    parser.add_argument(
        '--config', '-c',
        type=str,
        help='Path to configuration file (YAML)'
    )
    parser.add_argument(
        '--output', '-o',
        type=str,
        default='results/outcome_evaluation.json',
        help='Output file path for results'
    )
    parser.add_argument(
        '--cv',
        type=int,
        metavar='FOLDS',
        help='Run cross-validation with specified number of folds'
    )
    
    args = parser.parse_args()
    
    # Load configuration
    config = None
    if args.config:
        with open(args.config, 'r') as f:
            config = yaml.safe_load(f)
        logger.info(f"Loaded configuration from {args.config}")
    
    # Run evaluation
    evaluator = OutcomePredictionEvaluator(config)
    results = evaluator.evaluate(
        data_path=args.data,
        run_cv=args.cv is not None,
        n_folds=args.cv if args.cv else 5
    )
    
    # Save results
    evaluator.save_results(results, args.output)


if __name__ == '__main__':
    main()
