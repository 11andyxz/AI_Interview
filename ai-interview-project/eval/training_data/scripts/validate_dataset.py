#!/usr/bin/env python3
"""
Training Dataset Validator
Validates quality, balance, diversity, and detects contamination
"""

import json
import argparse
from pathlib import Path
from typing import List, Dict, Any, Set
from collections import Counter
import numpy as np
from datetime import datetime


class DatasetValidator:
    """Validates training datasets for fine-tuning"""
    
    def __init__(self, min_quality_score: float = 75.0):
        self.min_quality_score = min_quality_score
        self.issues = []
    
    def validate_dataset(self, dataset_path: Path) -> Dict[str, Any]:
        """Run all validation checks on a dataset"""
        print(f"\n{'='*70}")
        print(f"VALIDATING DATASET: {dataset_path.name}")
        print(f"{'='*70}\n")
        
        # Load dataset
        examples = self._load_jsonl(dataset_path)
        print(f"✓ Loaded {len(examples)} examples\n")
        
        results = {
            "dataset": str(dataset_path),
            "total_examples": len(examples),
            "timestamp": datetime.now().isoformat(),
            "checks": {}
        }
        
        # Run validation checks
        results["checks"]["quality"] = self.validate_quality(examples)
        results["checks"]["balance"] = self.validate_balance(examples)
        results["checks"]["diversity"] = self.validate_diversity(examples)
        results["checks"]["format"] = self.validate_format(examples)
        results["checks"]["length"] = self.validate_length(examples)
        
        # Overall pass/fail
        all_passed = all(
            check.get("passed", False) 
            for check in results["checks"].values()
        )
        results["overall_status"] = "PASSED" if all_passed else "FAILED"
        results["issues"] = self.issues
        
        return results
    
    def validate_quality(self, examples: List[Dict]) -> Dict[str, Any]:
        """Check quality scores meet threshold"""
        print("[1/5] Quality Validation")
        
        quality_scores = []
        low_quality_count = 0
        
        for ex in examples:
            metadata = ex.get("metadata", {})
            score = metadata.get("quality_score")
            
            if score is not None:
                quality_scores.append(score)
                if score < self.min_quality_score:
                    low_quality_count += 1
        
        avg_quality = np.mean(quality_scores) if quality_scores else 0
        min_quality = np.min(quality_scores) if quality_scores else 0
        
        passed = low_quality_count == 0 and avg_quality >= self.min_quality_score
        
        result = {
            "passed": passed,
            "avg_quality": round(avg_quality, 2),
            "min_quality": round(min_quality, 2),
            "low_quality_count": low_quality_count,
            "threshold": self.min_quality_score
        }
        
        if passed:
            print(f"  ✓ Average quality: {avg_quality:.2f} (threshold: {self.min_quality_score})")
        else:
            print(f"  ✗ {low_quality_count} examples below threshold")
            self.issues.append(f"Quality check failed: {low_quality_count} low-quality examples")
        
        print()
        return result
    
    def validate_balance(self, examples: List[Dict]) -> Dict[str, Any]:
        """Check class balance across categories"""
        print("[2/5] Balance Validation")
        
        # Extract distributions
        roles = [ex.get("metadata", {}).get("role") for ex in examples]
        difficulties = [ex.get("metadata", {}).get("difficulty") for ex in examples]
        categories = [ex.get("metadata", {}).get("category") for ex in examples]
        
        role_dist = Counter(r for r in roles if r)
        difficulty_dist = Counter(d for d in difficulties if d)
        category_dist = Counter(c for c in categories if c)
        
        # Check for severe imbalance (>80% in one class)
        imbalance_issues = []
        
        for name, dist in [("role", role_dist), ("difficulty", difficulty_dist), ("category", category_dist)]:
            if dist:
                max_ratio = max(dist.values()) / sum(dist.values())
                if max_ratio > 0.8:
                    imbalance_issues.append(f"{name} imbalanced ({max_ratio*100:.1f}%)")
        
        passed = len(imbalance_issues) == 0
        
        result = {
            "passed": passed,
            "role_distribution": dict(role_dist),
            "difficulty_distribution": dict(difficulty_dist),
            "category_distribution": dict(category_dist),
            "imbalance_issues": imbalance_issues
        }
        
        if passed:
            print(f"  ✓ Balanced distributions")
            print(f"    - Roles: {dict(role_dist)}")
            print(f"    - Difficulties: {dict(difficulty_dist)}")
        else:
            print(f"  ✗ Imbalance detected: {imbalance_issues}")
            self.issues.extend(imbalance_issues)
        
        print()
        return result
    
    def validate_diversity(self, examples: List[Dict]) -> Dict[str, Any]:
        """Check content diversity"""
        print("[3/5] Diversity Validation")
        
        # Extract prompts/questions
        prompts = []
        for ex in examples:
            messages = ex.get("messages", [])
            user_messages = [m["content"] for m in messages if m.get("role") == "user"]
            prompts.extend(user_messages)
        
        # Check for duplicates
        unique_prompts = set(prompts)
        duplicate_ratio = 1 - (len(unique_prompts) / len(prompts)) if prompts else 0
        
        # Check vocabulary diversity
        all_words = " ".join(prompts).lower().split()
        unique_words = set(all_words)
        vocab_size = len(unique_words)
        
        passed = duplicate_ratio < 0.1 and vocab_size >= 100
        
        result = {
            "passed": passed,
            "total_prompts": len(prompts),
            "unique_prompts": len(unique_prompts),
            "duplicate_ratio": round(duplicate_ratio, 3),
            "vocabulary_size": vocab_size
        }
        
        if passed:
            print(f"  ✓ Diverse content")
            print(f"    - {len(unique_prompts)}/{len(prompts)} unique prompts")
            print(f"    - {vocab_size} unique words")
        else:
            print(f"  ✗ Low diversity: {duplicate_ratio*100:.1f}% duplicates, vocab={vocab_size}")
            self.issues.append(f"Diversity issue: {duplicate_ratio*100:.1f}% duplicate prompts")
        
        print()
        return result
    
    def validate_format(self, examples: List[Dict]) -> Dict[str, Any]:
        """Validate JSONL format compliance"""
        print("[4/5] Format Validation")
        
        format_errors = []
        
        for i, ex in enumerate(examples):
            # Check required fields
            if "messages" not in ex:
                format_errors.append(f"Example {i}: missing 'messages' field")
                continue
            
            messages = ex["messages"]
            if not isinstance(messages, list):
                format_errors.append(f"Example {i}: 'messages' must be a list")
                continue
            
            # Check message structure
            for j, msg in enumerate(messages):
                if not isinstance(msg, dict):
                    format_errors.append(f"Example {i}, message {j}: must be a dict")
                    continue
                
                if "role" not in msg or "content" not in msg:
                    format_errors.append(f"Example {i}, message {j}: missing role or content")
        
        passed = len(format_errors) == 0
        
        result = {
            "passed": passed,
            "format_errors": format_errors[:10]  # Limit to 10
        }
        
        if passed:
            print(f"  ✓ Valid OpenAI chat format")
        else:
            print(f"  ✗ {len(format_errors)} format errors")
            self.issues.extend(format_errors[:5])
        
        print()
        return result
    
    def validate_length(self, examples: List[Dict]) -> Dict[str, Any]:
        """Check token length distribution"""
        print("[5/5] Length Validation")
        
        lengths = []
        too_short = 0
        too_long = 0
        
        for ex in examples:
            # Rough token estimate (words * 1.3)
            messages = ex.get("messages", [])
            text = " ".join(m.get("content", "") for m in messages)
            token_estimate = len(text.split()) * 1.3
            
            lengths.append(token_estimate)
            
            if token_estimate < 20:
                too_short += 1
            elif token_estimate > 4096:
                too_long += 1
        
        avg_length = np.mean(lengths) if lengths else 0
        max_length = np.max(lengths) if lengths else 0
        
        passed = too_short == 0 and too_long == 0
        
        result = {
            "passed": passed,
            "avg_tokens": round(avg_length, 1),
            "max_tokens": round(max_length, 1),
            "too_short": too_short,
            "too_long": too_long
        }
        
        if passed:
            print(f"  ✓ Appropriate lengths")
            print(f"    - Average: {avg_length:.0f} tokens")
            print(f"    - Max: {max_length:.0f} tokens")
        else:
            print(f"  ✗ Length issues: {too_short} too short, {too_long} too long")
            self.issues.append(f"Length issues: {too_short} too short, {too_long} too long")
        
        print()
        return result
    
    def detect_contamination(self, train_path: Path, test_path: Path) -> Dict[str, Any]:
        """Detect data leakage between train and test sets"""
        print(f"\n{'='*70}")
        print("CONTAMINATION CHECK")
        print(f"{'='*70}\n")
        
        train_examples = self._load_jsonl(train_path)
        test_examples = self._load_jsonl(test_path)
        
        # Extract prompts
        train_prompts = self._extract_prompts(train_examples)
        test_prompts = self._extract_prompts(test_examples)
        
        # Find overlaps
        overlap = train_prompts.intersection(test_prompts)
        contamination_ratio = len(overlap) / len(test_prompts) if test_prompts else 0
        
        passed = contamination_ratio < 0.01  # Allow <1% overlap
        
        result = {
            "passed": passed,
            "train_size": len(train_prompts),
            "test_size": len(test_prompts),
            "overlap_count": len(overlap),
            "contamination_ratio": round(contamination_ratio, 4)
        }
        
        if passed:
            print(f"✓ No contamination detected")
            print(f"  - {len(overlap)}/{len(test_prompts)} overlapping prompts ({contamination_ratio*100:.2f}%)")
        else:
            print(f"✗ CONTAMINATION DETECTED")
            print(f"  - {len(overlap)} overlapping prompts ({contamination_ratio*100:.1f}%)")
            self.issues.append(f"Train/test contamination: {contamination_ratio*100:.1f}%")
        
        print()
        return result
    
    def _load_jsonl(self, path: Path) -> List[Dict]:
        """Load JSONL file"""
        examples = []
        with open(path, 'r', encoding='utf-8') as f:
            for line in f:
                if line.strip():
                    examples.append(json.loads(line))
        return examples
    
    def _extract_prompts(self, examples: List[Dict]) -> Set[str]:
        """Extract unique prompts from examples"""
        prompts = set()
        for ex in examples:
            messages = ex.get("messages", [])
            user_messages = [m["content"] for m in messages if m.get("role") == "user"]
            prompts.update(user_messages)
        return prompts


def main():
    parser = argparse.ArgumentParser(description="Validate training dataset")
    parser.add_argument("dataset_dir", type=str, help="Path to dataset directory")
    parser.add_argument("--min-quality", type=float, default=75.0, 
                       help="Minimum quality score threshold")
    parser.add_argument("--output", type=str, help="Output JSON report path")
    
    args = parser.parse_args()
    
    dataset_dir = Path(args.dataset_dir)
    validator = DatasetValidator(min_quality_score=args.min_quality)
    
    all_results = {}
    
    # Validate each split
    for split in ["train", "validation", "test"]:
        split_path = dataset_dir / f"{split}.jsonl"
        if split_path.exists():
            results = validator.validate_dataset(split_path)
            all_results[split] = results
    
    # Contamination check
    train_path = dataset_dir / "train.jsonl"
    test_path = dataset_dir / "test.jsonl"
    if train_path.exists() and test_path.exists():
        contamination_result = validator.detect_contamination(train_path, test_path)
        all_results["contamination"] = contamination_result
    
    # Summary
    print(f"\n{'='*70}")
    print("VALIDATION SUMMARY")
    print(f"{'='*70}\n")
    
    total_checks = sum(len(results.get("checks", {})) for results in all_results.values() if isinstance(results, dict))
    passed_checks = sum(
        sum(1 for check in results.get("checks", {}).values() if check.get("passed", False))
        for results in all_results.values() 
        if isinstance(results, dict) and "checks" in results
    )
    
    print(f"Passed: {passed_checks}/{total_checks} checks")
    
    if validator.issues:
        print(f"\n❌ {len(validator.issues)} issues found:")
        for issue in validator.issues[:10]:
            print(f"  - {issue}")
    else:
        print("\n✅ All checks passed!")
    
    # Save report
    if args.output:
        output_path = Path(args.output)
        with open(output_path, 'w') as f:
            json.dump(all_results, f, indent=2)
        print(f"\nReport saved to: {output_path}")
    
    print()


if __name__ == "__main__":
    main()
