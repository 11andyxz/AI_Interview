#!/usr/bin/env python3
"""
Schema Validator for AI Interview Evaluation Results
Week 20 Task 3: Eval Pipeline Hardening

Validates experiment outputs against required schema to ensure:
- All required fields present
- Value ranges correct
- Data types valid
- Reproducibility metadata complete
"""

import json
import csv
import sys
from pathlib import Path
from typing import Dict, List, Any, Optional
from datetime import datetime
import re


class SchemaValidator:
    """Validates evaluation results and registry entries"""
    
    # Required fields for experiment registry
    REGISTRY_REQUIRED_FIELDS = [
        'experiment_id', 'timestamp', 'model_version', 'slice',
        'config_params', 'sample_size', 'notes'
    ]
    
    # Optional metric fields (at least one must be present)
    REGISTRY_METRIC_FIELDS = [
        'rmse', 'mae', 'brier_score', 'early_stop_rate', 'avg_questions'
    ]
    
    # Required fields for eval results CSV
    EVAL_RESULT_REQUIRED_FIELDS = [
        'session_id', 'slice', 'num_questions', 'early_stopped',
        'outcome'
    ]
    
    # Value ranges for validation
    VALUE_RANGES = {
        'rmse': (0, 100),           # Score RMSE (0-100 scale)
        'mae': (0, 100),            # Mean Absolute Error
        'brier_score': (0, 1),      # Brier score (probability calibration)
        'pass_threshold': (0, 1),   # Early-stop pass threshold
        'fail_threshold': (0, 1),   # Early-stop fail threshold
        'min_questions': (1, 20),   # Minimum questions before early stop
        'early_stop_rate': (0, 1),  # Proportion of sessions stopped early
        'avg_questions': (1, 30),   # Average questions per session
        'sample_size': (1, 10000),  # Number of sessions
    }
    
    def validate_registry_row(self, row: Dict[str, Any]) -> tuple[bool, List[str]]:
        """Validate a single experiment registry row"""
        errors = []
        
        # Check required fields
        for field in self.REGISTRY_REQUIRED_FIELDS:
            if field not in row or not row[field]:
                errors.append(f"Missing required field: {field}")
        
        # Check at least one metric present
        has_metric = any(row.get(field) and row[field] != 'N/A' 
                        for field in self.REGISTRY_METRIC_FIELDS)
        if not has_metric:
            errors.append("No valid metrics found (at least one required)")
        
        # Validate experiment_id format
        if 'experiment_id' in row:
            if not re.match(r'^[a-z0-9_-]+$', row['experiment_id']):
                errors.append(f"Invalid experiment_id format: {row['experiment_id']}")
        
        # Validate timestamp format
        if 'timestamp' in row:
            try:
                datetime.fromisoformat(row['timestamp'].replace('Z', '+00:00'))
            except (ValueError, AttributeError):
                errors.append(f"Invalid timestamp format: {row['timestamp']}")
        
        # Validate config_params is valid JSON
        if 'config_params' in row and row['config_params']:
            try:
                config = json.loads(row['config_params'])
                # Validate config parameter ranges
                config_errors = self._validate_config_params(config)
                errors.extend(config_errors)
            except json.JSONDecodeError as e:
                errors.append(f"Invalid config_params JSON: {e}")
        
        # Validate metric value ranges
        for field, (min_val, max_val) in self.VALUE_RANGES.items():
            if field in row and row[field] and row[field] != 'N/A':
                try:
                    value = float(row[field])
                    if not min_val <= value <= max_val:
                        errors.append(
                            f"{field}={value} out of range [{min_val}, {max_val}]"
                        )
                except ValueError:
                    errors.append(f"Invalid numeric value for {field}: {row[field]}")
        
        return len(errors) == 0, errors
    
    def _validate_config_params(self, config: Dict[str, Any]) -> List[str]:
        """Validate configuration parameters"""
        errors = []
        
        for param, (min_val, max_val) in self.VALUE_RANGES.items():
            if param in config:
                try:
                    value = float(config[param])
                    if not min_val <= value <= max_val:
                        errors.append(
                            f"Config {param}={value} out of range [{min_val}, {max_val}]"
                        )
                except (ValueError, TypeError):
                    # Skip non-numeric config values (e.g., strings)
                    pass
        
        return errors
    
    def validate_eval_results(self, csv_path: Path) -> tuple[bool, List[str]]:
        """Validate evaluation results CSV"""
        errors = []
        
        if not csv_path.exists():
            return False, [f"File not found: {csv_path}"]
        
        try:
            with open(csv_path, 'r', encoding='utf-8') as f:
                reader = csv.DictReader(f)
                headers = reader.fieldnames
                
                # Check required fields present
                for field in self.EVAL_RESULT_REQUIRED_FIELDS:
                    if field not in headers:
                        errors.append(f"Missing required column: {field}")
                
                if errors:
                    return False, errors
                
                # Validate each row
                for i, row in enumerate(reader, start=2):  # Start at 2 (header=1)
                    row_errors = self._validate_eval_result_row(row, i)
                    errors.extend(row_errors)
                    
                    # Stop after first 10 errors to avoid overwhelming output
                    if len(errors) >= 10:
                        errors.append("... (showing first 10 errors only)")
                        break
        
        except Exception as e:
            return False, [f"Error reading CSV: {e}"]
        
        return len(errors) == 0, errors
    
    def _validate_eval_result_row(self, row: Dict[str, Any], line_num: int) -> List[str]:
        """Validate single eval result row"""
        errors = []
        
        # Validate session_id format
        if not row.get('session_id'):
            errors.append(f"Line {line_num}: Missing session_id")
        
        # Validate slice is one of: junior, mid, senior
        slice_val = row.get('slice', '').lower()
        if slice_val not in ['junior', 'mid', 'senior']:
            errors.append(f"Line {line_num}: Invalid slice '{slice_val}' (must be junior/mid/senior)")
        
        # Validate num_questions is positive integer
        try:
            num_q = int(row.get('num_questions', 0))
            if num_q < 1 or num_q > 30:
                errors.append(f"Line {line_num}: num_questions={num_q} out of range [1, 30]")
        except ValueError:
            errors.append(f"Line {line_num}: num_questions must be integer")
        
        # Validate early_stopped is boolean
        early_stop_val = str(row.get('early_stopped', '')).lower()
        if early_stop_val not in ['true', 'false', '0', '1']:
            errors.append(f"Line {line_num}: early_stopped must be true/false")
        
        # Validate outcome is pass/fail
        outcome = row.get('outcome', '').lower()
        if outcome not in ['pass', 'fail']:
            errors.append(f"Line {line_num}: outcome must be pass/fail")
        
        return errors
    
    def validate_registry_file(self, registry_path: Path) -> tuple[bool, Dict[str, Any]]:
        """Validate entire experiment registry CSV"""
        errors = []
        stats = {
            'total_rows': 0,
            'valid_rows': 0,
            'invalid_rows': 0,
            'experiments': []
        }
        
        if not registry_path.exists():
            return False, {'error': f"Registry file not found: {registry_path}"}
        
        try:
            with open(registry_path, 'r', encoding='utf-8') as f:
                reader = csv.DictReader(f)
                
                for i, row in enumerate(reader, start=2):
                    stats['total_rows'] += 1
                    valid, row_errors = self.validate_registry_row(row)
                    
                    if valid:
                        stats['valid_rows'] += 1
                        stats['experiments'].append(row['experiment_id'])
                    else:
                        stats['invalid_rows'] += 1
                        for err in row_errors:
                            errors.append(f"Line {i} ({row.get('experiment_id', 'unknown')}): {err}")
        
        except Exception as e:
            return False, {'error': f"Error reading registry: {e}"}
        
        stats['errors'] = errors
        return len(errors) == 0, stats


def main():
    """CLI for schema validation"""
    if len(sys.argv) < 2:
        print("Usage: python schema_validator.py <file_to_validate>")
        print("  Validates experiment registry CSV or eval results CSV")
        sys.exit(1)
    
    file_path = Path(sys.argv[1])
    validator = SchemaValidator()
    
    # Determine file type and validate
    if 'registry' in file_path.name.lower():
        print(f"Validating experiment registry: {file_path}")
        valid, stats = validator.validate_registry_file(file_path)
        
        if valid:
            print(f"✓ PASS - Registry valid")
            print(f"  Total experiments: {stats['total_rows']}")
            print(f"  Valid rows: {stats['valid_rows']}")
        else:
            print(f"✗ FAIL - Registry validation failed")
            print(f"  Total rows: {stats['total_rows']}")
            print(f"  Valid rows: {stats['valid_rows']}")
            print(f"  Invalid rows: {stats['invalid_rows']}")
            print(f"\nErrors:")
            for err in stats.get('errors', [])[:20]:  # Show first 20
                print(f"  - {err}")
            sys.exit(1)
    
    else:
        print(f"Validating eval results: {file_path}")
        valid, errors = validator.validate_eval_results(file_path)
        
        if valid:
            print(f"✓ PASS - Eval results valid")
        else:
            print(f"✗ FAIL - Eval results validation failed")
            print(f"\nErrors:")
            for err in errors[:20]:
                print(f"  - {err}")
            sys.exit(1)


if __name__ == '__main__':
    main()
