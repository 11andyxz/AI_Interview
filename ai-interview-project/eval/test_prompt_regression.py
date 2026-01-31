#!/usr/bin/env python3
"""
Prompt regression testing suite for version-to-version comparison.
Ensures prompt changes don't degrade quality and provides CI/CD integration.

Features:
- Version-to-version quality comparison
- Fixed benchmark test suite
- Automated degradation detection (>5% quality drop alerts)
- Statistical significance testing
- CI/CD integration support
- Rollback recommendations

Usage:
python eval/test_prompt_regression.py --baseline v1.0 --candidate v1.1 --endpoint resume_analysis
"""

import argparse
import csv
import json
import os
import statistics
from datetime import datetime
from typing import List, Dict, Any, Tuple, Optional

try:
    from scipy import stats
    SCIPY_AVAILABLE = True
except ImportError:
    SCIPY_AVAILABLE = False


class PromptVersion:
    """Represents a specific version of prompts for an endpoint"""
    
    def __init__(self, version: str, endpoint: str):
        self.version = version
        self.endpoint = endpoint
        self.templates = {}
        self.config = {}
        
    def load_from_file(self, file_path: str):
        """Load prompt templates and config from file"""
        if os.path.exists(file_path):
            with open(file_path, 'r', encoding='utf-8') as f:
                data = json.load(f)
                self.templates = data.get('templates', {})
                self.config = data.get('config', {})
        else:
            # Generate mock data for demo
            self._generate_mock_version()
    
    def _generate_mock_version(self):
        """Generate mock prompt version for demo"""
        if self.version == "v1.0":
            self.templates = {
                "resume_analysis": "Analyze this resume:\n{input}\nExtract skills and experience level.",
                "interview_report": "Create interview report:\n{input}\nProvide assessment scores."
            }
            self.config = {"temperature": 0.7, "top_p": 0.9, "use_cot": False}
        elif self.version == "v1.1":
            self.templates = {
                "resume_analysis": "Analyze the following resume systematically:\n{input}\nExtract skills, experience level, and strengths. Be specific and detailed.",
                "interview_report": "Generate a comprehensive interview assessment report:\n{input}\nProvide detailed scores for technical skills, communication, and problem-solving."
            }
            self.config = {"temperature": 0.6, "top_p": 0.95, "use_cot": True}


class RegressionTestSuite:
    """Main regression testing suite"""
    
    def __init__(self, endpoint: str):
        self.endpoint = endpoint
        self.benchmark_cases = []
        self.quality_threshold = 0.05  # 5% degradation threshold
        
    def load_benchmark_cases(self, benchmark_file: str):
        """Load fixed benchmark test cases"""
        if os.path.exists(benchmark_file):
            with open(benchmark_file, 'r', encoding='utf-8') as f:
                self.benchmark_cases = [json.loads(line) for line in f if line.strip()]
        else:
            # Generate mock benchmark for demo
            self.benchmark_cases = self._generate_mock_benchmark()
    
    def _generate_mock_benchmark(self) -> List[Dict]:
        """Generate mock benchmark cases"""
        if self.endpoint == "resume_analysis":
            return [
                {
                    "id": "bench_resume_001",
                    "input": "John Doe, Software Engineer, 5 years Python, Django, PostgreSQL. Worked at TechCorp building web applications.",
                    "expected_skills": ["Python", "Django", "PostgreSQL"],
                    "expected_level": "Senior",
                    "quality_weight": 1.0
                },
                {
                    "id": "bench_resume_002", 
                    "input": "Jane Smith, Frontend Developer, 2 years React, JavaScript, HTML/CSS. Fresh bootcamp graduate with portfolio.",
                    "expected_skills": ["React", "JavaScript", "HTML", "CSS"],
                    "expected_level": "Junior",
                    "quality_weight": 1.0
                },
                {
                    "id": "bench_resume_003",
                    "input": "Complex resume with multiple roles, tech leadership, and international experience spanning 10+ years.",
                    "expected_skills": ["Leadership", "Multiple Technologies"],
                    "expected_level": "Principal",
                    "quality_weight": 1.5  # Harder case, weight higher
                }
            ]
        elif self.endpoint == "interview_report":
            return [
                {
                    "id": "bench_interview_001",
                    "input": "Candidate solved algorithm problem correctly, communicated well, asked clarifying questions.",
                    "expected_sections": ["technical", "communication", "problem_solving"],
                    "expected_overall_score": 8.0,
                    "quality_weight": 1.0
                }
            ]
        return []
    
    def run_version_comparison(self, baseline_version: PromptVersion, candidate_version: PromptVersion) -> Dict[str, Any]:
        """Compare two prompt versions on benchmark suite"""
        print(f"Running regression test: {baseline_version.version} vs {candidate_version.version}")
        
        baseline_results = self._evaluate_version(baseline_version)
        candidate_results = self._evaluate_version(candidate_version)
        
        # Calculate metrics
        baseline_scores = [r['quality_score'] for r in baseline_results]
        candidate_scores = [r['quality_score'] for r in candidate_results]
        
        baseline_mean = statistics.mean(baseline_scores)
        candidate_mean = statistics.mean(candidate_scores)
        
        # Quality change
        quality_change = candidate_mean - baseline_mean
        quality_change_pct = (quality_change / baseline_mean) * 100 if baseline_mean > 0 else 0
        
        # Statistical significance test
        p_value = None
        is_significant = False
        if SCIPY_AVAILABLE and len(baseline_scores) > 1 and len(candidate_scores) > 1:
            t_stat, p_value = stats.ttest_rel(baseline_scores, candidate_scores)
            is_significant = p_value < 0.05
        
        # Regression detection
        is_regression = quality_change_pct < -self.quality_threshold * 100
        
        # Generate recommendation
        recommendation = self._generate_recommendation(quality_change_pct, is_regression, is_significant)
        
        return {
            'endpoint': self.endpoint,
            'baseline_version': baseline_version.version,
            'candidate_version': candidate_version.version,
            'baseline_mean_quality': baseline_mean,
            'candidate_mean_quality': candidate_mean,
            'quality_change': quality_change,
            'quality_change_pct': quality_change_pct,
            'p_value': p_value,
            'is_statistically_significant': is_significant,
            'is_regression': is_regression,
            'recommendation': recommendation,
            'timestamp': datetime.now().isoformat(),
            'benchmark_cases_count': len(self.benchmark_cases),
            'baseline_results': baseline_results,
            'candidate_results': candidate_results
        }
    
    def _evaluate_version(self, version: PromptVersion) -> List[Dict]:
        """Evaluate a prompt version on benchmark cases"""
        results = []
        
        template = version.templates.get(self.endpoint, "")
        if not template:
            print(f"Warning: No template found for endpoint {self.endpoint} in version {version.version}")
            template = "Process: {input}"
        
        for case in self.benchmark_cases:
            # Format prompt
            prompt = template.format(input=case['input'])
            
            # Simulate model call with version config
            quality_score = self._simulate_model_call(
                prompt, 
                version.config.get('temperature', 0.7),
                version.config.get('top_p', 0.9),
                version.config.get('use_cot', False),
                case
            )
            
            results.append({
                'case_id': case['id'],
                'prompt': prompt,
                'quality_score': quality_score,
                'weight': case.get('quality_weight', 1.0)
            })
        
        return results
    
    def _simulate_model_call(self, prompt: str, temperature: float, top_p: float, use_cot: bool, test_case: Dict) -> float:
        """Simulate model API call and return quality score"""
        # Base quality depends on prompt sophistication
        base_quality = 0.7
        
        # Longer, more detailed prompts tend to perform better
        if len(prompt) > 100:
            base_quality += 0.1
        
        # CoT generally improves quality but costs more
        if use_cot:
            base_quality += 0.15
        
        # Temperature effects (lower = more consistent)
        if temperature < 0.7:
            base_quality += 0.05
        
        # Random variation to simulate real model variance
        import random
        random_factor = random.uniform(-0.2, 0.2)
        
        # Case-specific adjustments
        case_difficulty = test_case.get('quality_weight', 1.0)
        if case_difficulty > 1.0:
            base_quality *= 0.9  # Harder cases have lower scores
        
        final_score = max(0.0, min(1.0, base_quality + random_factor))
        return final_score
    
    def _generate_recommendation(self, quality_change_pct: float, is_regression: bool, is_significant: bool) -> str:
        """Generate deployment recommendation"""
        if is_regression:
            if is_significant:
                return "REJECT: Significant quality regression detected. Do not deploy."
            else:
                return "CAUTION: Quality regression detected but not statistically significant. Review carefully."
        elif quality_change_pct > 5:
            return "APPROVE: Quality improvement detected. Safe to deploy."
        elif abs(quality_change_pct) < 2:
            return "APPROVE: No significant quality change. Safe to deploy."
        else:
            return "REVIEW: Minor quality change detected. Manual review recommended."
    
    def save_results(self, results: Dict, output_file: str):
        """Save regression test results"""
        os.makedirs(os.path.dirname(output_file) if os.path.dirname(output_file) else '.', exist_ok=True)
        
        # Convert numpy types to native Python types for JSON serialization
        def convert_types(obj):
            if hasattr(obj, 'item'):  # numpy scalar
                return obj.item()
            elif isinstance(obj, (list, tuple)):
                return [convert_types(item) for item in obj]
            elif isinstance(obj, dict):
                return {key: convert_types(value) for key, value in obj.items()}
            else:
                return obj
        
        results_serializable = convert_types(results)
        
        with open(output_file, 'w', encoding='utf-8') as f:
            json.dump(results_serializable, f, indent=2)
        
        print(f"Regression test results saved to: {output_file}")
    
    def generate_ci_output(self, results: Dict) -> Tuple[int, str]:
        """Generate CI/CD compatible output"""
        # Return (exit_code, summary_message)
        if results['is_regression']:
            return 1, f"REGRESSION DETECTED: Quality dropped by {results['quality_change_pct']:.2f}%"
        else:
            return 0, f"PASS: Quality change: {results['quality_change_pct']:.2f}%"
    
    def generate_alert_message(self, results: Dict) -> Optional[str]:
        """Generate alert message if degradation detected"""
        if results['is_regression']:
            return f"""
ALERT: Prompt Quality Regression Detected

Endpoint: {results['endpoint']}
Version: {results['baseline_version']} → {results['candidate_version']}
Quality Change: {results['quality_change_pct']:.2f}%
Statistical Significance: {results['is_statistically_significant']}
Recommendation: {results['recommendation']}

Baseline Quality: {results['baseline_mean_quality']:.3f}
Candidate Quality: {results['candidate_mean_quality']:.3f}

Action Required: Review prompt changes and consider rollback.
"""
        return None


def main():
    parser = argparse.ArgumentParser(description='Run prompt regression tests')
    parser.add_argument('--baseline', required=True, help='Baseline prompt version (e.g., v1.0)')
    parser.add_argument('--candidate', required=True, help='Candidate prompt version (e.g., v1.1)')
    parser.add_argument('--endpoint', required=True, choices=['resume_analysis', 'interview_report'],
                       help='Endpoint to test')
    parser.add_argument('--benchmark', help='Benchmark test cases file (JSONL)')
    parser.add_argument('--baseline-prompts', help='Baseline prompt templates file (JSON)')
    parser.add_argument('--candidate-prompts', help='Candidate prompt templates file (JSON)')
    parser.add_argument('--output', default='eval/regression_test_results.json',
                       help='Output file for detailed results')
    parser.add_argument('--threshold', type=float, default=0.05,
                       help='Quality degradation threshold (default: 0.05 = 5%)')
    parser.add_argument('--ci-mode', action='store_true',
                       help='Run in CI/CD mode with exit codes')
    
    args = parser.parse_args()
    
    # Initialize test suite
    test_suite = RegressionTestSuite(args.endpoint)
    test_suite.quality_threshold = args.threshold
    
    # Load benchmark cases
    if args.benchmark:
        test_suite.load_benchmark_cases(args.benchmark)
    else:
        test_suite.load_benchmark_cases('')  # Will generate mock data
    
    # Load prompt versions
    baseline_version = PromptVersion(args.baseline, args.endpoint)
    candidate_version = PromptVersion(args.candidate, args.endpoint)
    
    if args.baseline_prompts:
        baseline_version.load_from_file(args.baseline_prompts)
    else:
        baseline_version.load_from_file('')  # Will generate mock data
        
    if args.candidate_prompts:
        candidate_version.load_from_file(args.candidate_prompts)
    else:
        candidate_version.load_from_file('')  # Will generate mock data
    
    # Run comparison
    results = test_suite.run_version_comparison(baseline_version, candidate_version)
    
    # Save detailed results
    test_suite.save_results(results, args.output)
    
    # Generate alert if needed
    alert_message = test_suite.generate_alert_message(results)
    if alert_message:
        print(alert_message)
    
    # Print summary
    print(f"\n=== Regression Test Summary ===")
    print(f"Endpoint: {results['endpoint']}")
    print(f"Versions: {results['baseline_version']} → {results['candidate_version']}")
    print(f"Quality Change: {results['quality_change_pct']:.2f}%")
    print(f"Recommendation: {results['recommendation']}")
    
    # CI/CD mode
    if args.ci_mode:
        exit_code, ci_message = test_suite.generate_ci_output(results)
        print(f"\nCI Result: {ci_message}")
        exit(exit_code)


if __name__ == '__main__':
    main()