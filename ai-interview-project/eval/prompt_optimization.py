#!/usr/bin/env python3
"""
Prompt optimization experiment runner with grid search capabilities.
Systematically tests prompt variations to find optimal configurations.

Features:
- Few-shot optimization (0-shot to 5-shot)
- Chain-of-Thought (CoT) prompting experiments
- Temperature and Top-P parameter tuning
- Statistical analysis and quality metrics
- CSV output with cost analysis
- Heatmap generation for parameter visualization

Usage:
python eval/prompt_optimization.py --endpoint resume_analysis --output eval/prompt_experiments.csv
"""

import argparse
import csv
import json
import random
import statistics
import itertools
import os
from datetime import datetime
from typing import List, Dict, Any, Tuple

try:
    import matplotlib
    matplotlib.use('Agg')
    import matplotlib.pyplot as plt
    import seaborn as sns
    import numpy as np
    PLOTTING_AVAILABLE = True
except ImportError:
    PLOTTING_AVAILABLE = False

try:
    from scipy import stats
    SCIPY_AVAILABLE = True
except ImportError:
    SCIPY_AVAILABLE = False


class PromptExperiment:
    """Single prompt experiment configuration"""
    
    def __init__(self, 
                 shot_count: int,
                 use_cot: bool,
                 temperature: float,
                 top_p: float,
                 prompt_template: str):
        self.shot_count = shot_count
        self.use_cot = use_cot
        self.temperature = temperature
        self.top_p = top_p
        self.prompt_template = prompt_template
        self.results = []
    
    def get_config_key(self):
        """Generate unique configuration key"""
        cot_str = "cot" if self.use_cot else "direct"
        return f"shots{self.shot_count}_{cot_str}_t{self.temperature}_p{self.top_p}"


class PromptOptimizer:
    """Main prompt optimization experiment runner"""
    
    def __init__(self, endpoint: str):
        self.endpoint = endpoint
        self.experiments = []
        self.benchmark_data = []
        self.few_shot_examples = []
        
    def load_benchmark_data(self, benchmark_file: str):
        """Load benchmark test cases"""
        if not os.path.exists(benchmark_file):
            # Generate mock benchmark data for demo
            self.benchmark_data = self._generate_mock_benchmark()
        else:
            with open(benchmark_file, 'r', encoding='utf-8') as f:
                self.benchmark_data = [json.loads(line) for line in f if line.strip()]
    
    def load_few_shot_examples(self, examples_file: str):
        """Load few-shot examples"""
        if not os.path.exists(examples_file):
            # Generate mock examples for demo
            self.few_shot_examples = self._generate_mock_examples()
        else:
            with open(examples_file, 'r', encoding='utf-8') as f:
                self.few_shot_examples = [json.loads(line) for line in f if line.strip()]
    
    def _generate_mock_benchmark(self) -> List[Dict]:
        """Generate mock benchmark data for testing"""
        if self.endpoint == "resume_analysis":
            return [
                {
                    "id": f"test_{i}",
                    "input": f"Analyze this resume for software engineer position: Mock resume {i} with Python, Java, React skills",
                    "expected_skills": ["Python", "Java", "React"],
                    "expected_quality_score": 0.8 + random.uniform(-0.2, 0.2)
                }
                for i in range(20)
            ]
        elif self.endpoint == "interview_report":
            return [
                {
                    "id": f"test_{i}",
                    "input": f"Generate interview report for candidate {i} based on technical discussion about algorithms",
                    "expected_sections": ["technical_assessment", "communication", "problem_solving"],
                    "expected_quality_score": 0.75 + random.uniform(-0.25, 0.25)
                }
                for i in range(20)
            ]
        else:
            return []
    
    def _generate_mock_examples(self) -> List[Dict]:
        """Generate mock few-shot examples"""
        if self.endpoint == "resume_analysis":
            return [
                {
                    "input": "Resume: John Smith, 5 years Python development, worked at Google on search algorithms",
                    "output": "Skills: Python (Expert), Algorithms (Advanced), Search Systems (Advanced)\nExperience Level: Senior\nStrengths: Strong technical background at top-tier company"
                },
                {
                    "input": "Resume: Sarah Chen, 2 years React development, bootcamp graduate, portfolio includes e-commerce site",
                    "output": "Skills: React (Intermediate), Frontend Development (Intermediate), E-commerce (Beginner)\nExperience Level: Junior\nStrengths: Strong portfolio demonstrating practical skills"
                }
            ]
        elif self.endpoint == "interview_report":
            return [
                {
                    "input": "Candidate solved binary tree problem efficiently, explained approach clearly, asked good questions",
                    "output": "Technical Assessment: Strong (8/10) - Efficient solution with clear explanation\nCommunication: Excellent (9/10) - Clear articulation and good questions\nProblem Solving: Strong (8/10) - Systematic approach"
                }
            ]
        return []
    
    def create_prompt_variants(self):
        """Create all prompt experiment variants"""
        # Parameter ranges for grid search
        shot_counts = [0, 1, 3, 5]
        cot_options = [False, True]
        temperatures = [0.5, 0.7, 0.9, 1.0]
        top_p_values = [0.8, 0.9, 0.95, 1.0]
        
        base_template = self._get_base_template()
        
        for shot_count, use_cot, temp, top_p in itertools.product(
            shot_counts, cot_options, temperatures, top_p_values
        ):
            experiment = PromptExperiment(
                shot_count=shot_count,
                use_cot=use_cot,
                temperature=temp,
                top_p=top_p,
                prompt_template=base_template
            )
            self.experiments.append(experiment)
    
    def _get_base_template(self) -> str:
        """Get base prompt template for endpoint"""
        templates = {
            "resume_analysis": """Analyze the following resume and extract key information:

{few_shot_examples}

{cot_instruction}

Resume to analyze:
{input}

Output:""",
            
            "interview_report": """Generate a structured interview assessment report:

{few_shot_examples}

{cot_instruction}

Interview notes:
{input}

Report:"""
        }
        return templates.get(self.endpoint, "Process the following input:\n{input}")
    
    def run_experiment(self, experiment: PromptExperiment) -> Dict[str, float]:
        """Run single experiment and return metrics"""
        results = []
        total_tokens = 0
        total_cost = 0.0
        
        for test_case in self.benchmark_data:
            # Build prompt with few-shot examples
            few_shot_text = ""
            if experiment.shot_count > 0:
                selected_examples = self.few_shot_examples[:experiment.shot_count]
                few_shot_text = "\n\n".join([
                    f"Example:\nInput: {ex['input']}\nOutput: {ex['output']}"
                    for ex in selected_examples
                ]) + "\n\n"
            
            # Add CoT instruction if enabled
            cot_instruction = ""
            if experiment.use_cot:
                cot_instruction = "Think step by step and explain your reasoning before providing the final answer.\n"
            
            # Format prompt
            prompt = experiment.prompt_template.format(
                few_shot_examples=few_shot_text,
                cot_instruction=cot_instruction,
                input=test_case['input']
            )
            
            # Simulate API call with different parameters
            response_quality, tokens, cost = self._simulate_llm_call(
                prompt, experiment.temperature, experiment.top_p
            )
            
            results.append(response_quality)
            total_tokens += tokens
            total_cost += cost
        
        # Calculate metrics
        return {
            'mean_quality': statistics.mean(results),
            'std_quality': statistics.stdev(results) if len(results) > 1 else 0,
            'min_quality': min(results),
            'max_quality': max(results),
            'total_tokens': total_tokens,
            'total_cost': total_cost,
            'cost_per_request': total_cost / len(results) if results else 0,
            'quality_per_dollar': statistics.mean(results) / (total_cost + 0.001)  # avoid division by zero
        }
    
    def _simulate_llm_call(self, prompt: str, temperature: float, top_p: float) -> Tuple[float, int, float]:
        """Simulate LLM API call and return (quality_score, tokens, cost)"""
        # Simulate token count based on prompt length
        token_count = len(prompt.split()) * 1.3  # rough approximation
        
        # Simulate cost (mock pricing)
        cost = token_count * 0.002  # $0.002 per token
        
        # Simulate quality score based on parameters
        base_quality = 0.7
        
        # Temperature effects: lower = more consistent, higher = more creative but variable
        temp_effect = 0.1 * (1.0 - temperature)  # prefer lower temps for consistency
        
        # Top-p effects: similar to temperature
        top_p_effect = 0.05 * top_p
        
        # Random variation
        random_effect = random.uniform(-0.2, 0.2)
        
        quality = max(0.0, min(1.0, base_quality + temp_effect + top_p_effect + random_effect))
        
        return quality, int(token_count), cost
    
    def run_all_experiments(self) -> List[Dict]:
        """Run all experiments and return results"""
        results = []
        
        print(f"Running {len(self.experiments)} prompt experiments for endpoint: {self.endpoint}")
        
        for i, experiment in enumerate(self.experiments):
            print(f"Experiment {i+1}/{len(self.experiments)}: {experiment.get_config_key()}")
            
            metrics = self.run_experiment(experiment)
            
            result = {
                'experiment_id': experiment.get_config_key(),
                'endpoint': self.endpoint,
                'shot_count': experiment.shot_count,
                'use_cot': experiment.use_cot,
                'temperature': experiment.temperature,
                'top_p': experiment.top_p,
                'timestamp': datetime.now().isoformat(),
                **metrics
            }
            
            results.append(result)
        
        return results
    
    def save_results_csv(self, results: List[Dict], output_file: str):
        """Save experiment results to CSV"""
        if not results:
            return
        
        fieldnames = list(results[0].keys())
        
        os.makedirs(os.path.dirname(output_file) if os.path.dirname(output_file) else '.', exist_ok=True)
        
        with open(output_file, 'w', newline='', encoding='utf-8') as f:
            writer = csv.DictWriter(f, fieldnames=fieldnames)
            writer.writeheader()
            writer.writerows(results)
        
        print(f"Results saved to: {output_file}")
    
    def generate_analysis_report(self, results: List[Dict], output_dir: str):
        """Generate analysis report and visualizations"""
        if not PLOTTING_AVAILABLE:
            print("Matplotlib/Seaborn not available. Skipping visualizations.")
            return
        
        os.makedirs(output_dir, exist_ok=True)
        
        # Convert to analysis format
        df_data = []
        for r in results:
            df_data.append([
                r['shot_count'], r['use_cot'], r['temperature'], r['top_p'],
                r['mean_quality'], r['total_cost'], r['quality_per_dollar']
            ])
        
        # Generate heatmaps
        self._generate_temperature_heatmap(df_data, output_dir)
        self._generate_shots_analysis(df_data, output_dir)
        
        # Find optimal configurations
        optimal_quality = max(results, key=lambda x: x['mean_quality'])
        optimal_cost_efficiency = max(results, key=lambda x: x['quality_per_dollar'])
        
        # Generate markdown report
        report_file = os.path.join(output_dir, f"prompt_optimization_report_{self.endpoint}.md")
        with open(report_file, 'w', encoding='utf-8') as f:
            f.write(f"# Prompt Optimization Report - {self.endpoint.title()}\n\n")
            f.write(f"Generated: {datetime.now().isoformat()}\n\n")
            f.write(f"## Summary\n")
            f.write(f"- Total experiments: {len(results)}\n")
            f.write(f"- Benchmark test cases: {len(self.benchmark_data)}\n\n")
            
            f.write("## Optimal Configurations\n\n")
            f.write("### Best Quality\n")
            f.write(f"- Configuration: {optimal_quality['experiment_id']}\n")
            f.write(f"- Quality Score: {optimal_quality['mean_quality']:.3f}\n")
            f.write(f"- Cost per Request: ${optimal_quality['cost_per_request']:.4f}\n\n")
            
            f.write("### Best Cost Efficiency\n")
            f.write(f"- Configuration: {optimal_cost_efficiency['experiment_id']}\n")
            f.write(f"- Quality per Dollar: {optimal_cost_efficiency['quality_per_dollar']:.2f}\n")
            f.write(f"- Quality Score: {optimal_cost_efficiency['mean_quality']:.3f}\n\n")
            
            if SCIPY_AVAILABLE:
                f.write("## Statistical Analysis\n")
                self._add_statistical_analysis(results, f)
        
        print(f"Analysis report generated: {report_file}")
    
    def _generate_temperature_heatmap(self, data, output_dir):
        """Generate temperature vs top_p heatmap"""
        if not data:
            return
            
        # Group by temperature and top_p
        temp_vals = sorted(list(set([row[2] for row in data])))
        top_p_vals = sorted(list(set([row[3] for row in data])))
        
        quality_matrix = np.zeros((len(temp_vals), len(top_p_vals)))
        
        for row in data:
            temp_idx = temp_vals.index(row[2])
            top_p_idx = top_p_vals.index(row[3])
            quality_matrix[temp_idx][top_p_idx] = row[4]  # mean_quality
        
        plt.figure(figsize=(10, 6))
        sns.heatmap(quality_matrix, 
                    xticklabels=[f"top_p={p}" for p in top_p_vals],
                    yticklabels=[f"temp={t}" for t in temp_vals],
                    annot=True, fmt='.3f', cmap='viridis')
        plt.title(f'Quality Scores: Temperature vs Top-P ({self.endpoint})')
        plt.tight_layout()
        plt.savefig(os.path.join(output_dir, f'temperature_heatmap_{self.endpoint}.png'))
        plt.close()
    
    def _generate_shots_analysis(self, data, output_dir):
        """Generate few-shot analysis plot"""
        if not data:
            return
            
        shot_counts = sorted(list(set([row[0] for row in data])))
        shot_qualities = {}
        
        for shot_count in shot_counts:
            qualities = [row[4] for row in data if row[0] == shot_count]
            shot_qualities[shot_count] = statistics.mean(qualities) if qualities else 0
        
        plt.figure(figsize=(8, 5))
        plt.plot(shot_counts, [shot_qualities[sc] for sc in shot_counts], 'o-')
        plt.xlabel('Number of Few-Shot Examples')
        plt.ylabel('Mean Quality Score')
        plt.title(f'Few-Shot Performance ({self.endpoint})')
        plt.grid(True, alpha=0.3)
        plt.tight_layout()
        plt.savefig(os.path.join(output_dir, f'few_shot_analysis_{self.endpoint}.png'))
        plt.close()
    
    def _add_statistical_analysis(self, results, f):
        """Add statistical analysis to report"""
        # Compare CoT vs Direct
        cot_results = [r['mean_quality'] for r in results if r['use_cot']]
        direct_results = [r['mean_quality'] for r in results if not r['use_cot']]
        
        if len(cot_results) > 1 and len(direct_results) > 1:
            t_stat, p_val = stats.ttest_ind(cot_results, direct_results)
            f.write(f"### Chain-of-Thought Analysis\n")
            f.write(f"- CoT mean quality: {statistics.mean(cot_results):.3f}\n")
            f.write(f"- Direct mean quality: {statistics.mean(direct_results):.3f}\n")
            f.write(f"- T-test p-value: {p_val:.4f}\n")
            f.write(f"- Statistically significant: {'Yes' if p_val < 0.05 else 'No'}\n\n")


def main():
    parser = argparse.ArgumentParser(description='Run prompt optimization experiments')
    parser.add_argument('--endpoint', required=True, choices=['resume_analysis', 'interview_report'],
                       help='Endpoint to optimize prompts for')
    parser.add_argument('--output', default='eval/prompt_experiments.csv',
                       help='Output CSV file for results')
    parser.add_argument('--benchmark', help='Benchmark test cases file (JSONL)')
    parser.add_argument('--examples', help='Few-shot examples file (JSONL)')
    parser.add_argument('--analysis-dir', default='eval/prompt_analysis',
                       help='Directory for analysis outputs')
    
    args = parser.parse_args()
    
    # Initialize optimizer
    optimizer = PromptOptimizer(args.endpoint)
    
    # Load data
    if args.benchmark:
        optimizer.load_benchmark_data(args.benchmark)
    else:
        optimizer.load_benchmark_data('')  # Will generate mock data
    
    if args.examples:
        optimizer.load_few_shot_examples(args.examples)
    else:
        optimizer.load_few_shot_examples('')  # Will generate mock data
    
    # Create experiments
    optimizer.create_prompt_variants()
    
    # Run experiments
    results = optimizer.run_all_experiments()
    
    # Save results
    optimizer.save_results_csv(results, args.output)
    
    # Generate analysis
    optimizer.generate_analysis_report(results, args.analysis_dir)
    
    print(f"\nPrompt optimization completed for endpoint: {args.endpoint}")
    print(f"Results: {args.output}")
    print(f"Analysis: {args.analysis_dir}")


if __name__ == '__main__':
    main()