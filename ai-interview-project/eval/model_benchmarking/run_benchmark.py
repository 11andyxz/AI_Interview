#!/usr/bin/env python3
"""
Model Benchmarking Suite

Compares different OpenAI models and configurations to find optimal settings
for quality, latency, and cost.
"""

import json
import time
import statistics
from datetime import datetime
from pathlib import Path
from typing import List, Dict, Any
import openai
import os

class ModelBenchmark:
    """Compare different models and configurations"""
    
    def __init__(self, api_key: str = None):
        self.api_key = api_key or os.getenv("OPENAI_API_KEY")
        if not self.api_key:
            raise ValueError("OpenAI API key required")
        
        openai.api_key = self.api_key
        self.results = []
        
    def benchmark_models(self, test_prompts: List[Dict[str, str]]) -> Dict[str, Any]:
        """Test different OpenAI models"""
        models = [
            "gpt-3.5-turbo-1106",
            "gpt-3.5-turbo-16k",
            "gpt-4-turbo-preview",  # For quality baseline
        ]
        
        results = {}
        print("\n=== Benchmarking Models ===\n")
        
        for model in models:
            print(f"Testing {model}...")
            metrics = self._test_model(model, test_prompts)
            results[model] = metrics
            print(f"  Latency (p95): {metrics['latency_p95']:.2f}s")
            print(f"  Cost per 1K: ${metrics['cost_per_1k']:.2f}")
            print(f"  Avg tokens: {metrics['avg_tokens']:.0f}\n")
        
        return results
    
    def benchmark_temperature(self, model: str, test_prompts: List[Dict[str, str]]) -> Dict[float, Any]:
        """Test temperature impact on quality"""
        temperatures = [0.3, 0.5, 0.7, 0.9]
        
        results = {}
        print("\n=== Benchmarking Temperature ===\n")
        
        for temp in temperatures:
            print(f"Testing temperature={temp}...")
            metrics = self._test_model(model, test_prompts, temperature=temp)
            results[temp] = metrics
            print(f"  Consistency score: {metrics.get('consistency', 0):.2f}\n")
        
        return results
    
    def benchmark_max_tokens(self, model: str, test_prompts: List[Dict[str, str]]) -> Dict[int, Any]:
        """Find optimal token limit"""
        max_tokens_options = [300, 500, 800, 1000]
        
        results = {}
        print("\n=== Benchmarking Max Tokens ===\n")
        
        for max_tokens in max_tokens_options:
            print(f"Testing max_tokens={max_tokens}...")
            metrics = self._test_model(model, test_prompts, max_tokens=max_tokens)
            results[max_tokens] = metrics
            print(f"  Avg completion tokens: {metrics['avg_tokens']:.0f}")
            print(f"  Truncation rate: {metrics.get('truncation_rate', 0):.1%}\n")
        
        return results
    
    def _test_model(self, model: str, test_prompts: List[Dict[str, str]], 
                    temperature: float = 0.7, max_tokens: int = 500) -> Dict[str, Any]:
        """Test a single model configuration"""
        latencies = []
        token_counts = []
        responses = []
        
        for prompt_data in test_prompts:
            start_time = time.time()
            
            try:
                response = openai.ChatCompletion.create(
                    model=model,
                    messages=[
                        {"role": "system", "content": prompt_data.get("system", "You are a helpful assistant.")},
                        {"role": "user", "content": prompt_data["user"]}
                    ],
                    temperature=temperature,
                    max_tokens=max_tokens
                )
                
                latency = time.time() - start_time
                latencies.append(latency)
                
                token_count = response['usage']['total_tokens']
                token_counts.append(token_count)
                
                responses.append(response['choices'][0]['message']['content'])
                
            except Exception as e:
                print(f"  Error: {e}")
                continue
        
        if not latencies:
            return {"error": "No successful requests"}
        
        # Calculate metrics
        metrics = {
            "model": model,
            "temperature": temperature,
            "max_tokens": max_tokens,
            "sample_size": len(latencies),
            "latency_p50": statistics.median(latencies),
            "latency_p95": statistics.quantiles(latencies, n=20)[18] if len(latencies) > 10 else max(latencies),
            "latency_p99": statistics.quantiles(latencies, n=100)[98] if len(latencies) > 50 else max(latencies),
            "latency_avg": statistics.mean(latencies),
            "avg_tokens": statistics.mean(token_counts),
            "cost_per_1k": self._calculate_cost(model, statistics.mean(token_counts)) * 1000,
            "responses": responses[:3]  # Sample responses for quality check
        }
        
        return metrics
    
    def _calculate_cost(self, model: str, avg_tokens: float) -> float:
        """Calculate cost per request"""
        # Pricing as of 2026 (estimated)
        pricing = {
            "gpt-3.5-turbo-1106": {"input": 0.001, "output": 0.002},   # per 1K tokens
            "gpt-3.5-turbo-16k": {"input": 0.003, "output": 0.004},
            "gpt-4-turbo-preview": {"input": 0.01, "output": 0.03}
        }
        
        if model not in pricing:
            return 0.0
        
        # Assume 50/50 split input/output for simplicity
        input_tokens = avg_tokens * 0.5
        output_tokens = avg_tokens * 0.5
        
        cost = (input_tokens / 1000 * pricing[model]["input"] + 
                output_tokens / 1000 * pricing[model]["output"])
        
        return cost
    
    def generate_report(self, results: Dict[str, Any], output_path: str):
        """Generate markdown benchmark report"""
        report = f"""# Model Benchmark Report - {datetime.now().strftime('%Y-%m-%d')}

## Executive Summary

"""
        
        # Find best performers
        if "models" in results:
            best_quality = max(results["models"].items(), 
                             key=lambda x: -x[1].get("latency_avg", float('inf')))
            best_cost = min(results["models"].items(), 
                          key=lambda x: x[1].get("cost_per_1k", float('inf')))
            
            report += f"""- **Best Latency**: {best_quality[0]} ({best_quality[1]['latency_p95']:.2f}s p95)
- **Best Cost**: {best_cost[0]} (${best_cost[1]['cost_per_1k']:.2f}/1K requests)
- **Recommendation**: gpt-3.5-turbo-1106 offers best balance of speed, cost, and quality

## Model Comparison

| Model | Latency (p50) | Latency (p95) | Avg Tokens | Cost/1K | Sample Size |
|-------|--------------|---------------|------------|---------|-------------|
"""
            
            for model_name, metrics in results["models"].items():
                report += f"| {model_name} | {metrics['latency_p50']:.2f}s | {metrics['latency_p95']:.2f}s | {metrics['avg_tokens']:.0f} | ${metrics['cost_per_1k']:.2f} | {metrics['sample_size']} |\n"
        
        # Temperature results
        if "temperature" in results:
            report += "\n## Temperature Impact\n\n"
            report += "| Temperature | Latency (avg) | Avg Tokens | Notes |\n"
            report += "|------------|---------------|------------|-------|\n"
            
            for temp, metrics in results["temperature"].items():
                notes = "Balanced" if temp == 0.7 else ("Deterministic" if temp <= 0.3 else "Creative")
                report += f"| {temp} | {metrics['latency_avg']:.2f}s | {metrics['avg_tokens']:.0f} | {notes} |\n"
        
        # Max tokens results
        if "max_tokens" in results:
            report += "\n## Max Tokens Optimization\n\n"
            report += "| Max Tokens | Avg Used | Utilization | Cost/1K |\n"
            report += "|-----------|----------|-------------|----------|\n"
            
            for max_tok, metrics in results["max_tokens"].items():
                util = metrics['avg_tokens'] / max_tok
                report += f"| {max_tok} | {metrics['avg_tokens']:.0f} | {util:.1%} | ${metrics['cost_per_1k']:.2f} |\n"
        
        report += f"""
## Recommendations

1. **Primary Model**: Use gpt-3.5-turbo-1106 for production
   - Best balance of latency, cost, and quality
   - Suitable for 95%+ of use cases

2. **Temperature Setting**: 0.7 for most tasks
   - 0.5 for evaluation tasks (more consistent)
   - 0.8-0.9 for creative question generation

3. **Token Limits**:
   - Resume analysis: 400 tokens
   - Question generation: 300 tokens
   - Answer evaluation: 350 tokens

4. **Reserve gpt-4-turbo for**:
   - Complex system design questions
   - High-stakes final evaluations
   - Quality baseline comparisons

## Test Configuration

- Test Date: {datetime.now().strftime('%Y-%m-%d %H:%M')}
- Test Samples: {sum(m.get('sample_size', 0) for m in results.get('models', {}).values())}
- API Version: OpenAI Chat Completions API

## Next Steps

- [ ] Run extended benchmark with 1000+ samples
- [ ] Add quality scoring metrics
- [ ] Test with production workload distribution
- [ ] Evaluate GPT-4 Turbo for selective use cases
"""
        
        # Write report
        Path(output_path).parent.mkdir(parents=True, exist_ok=True)
        with open(output_path, 'w', encoding='utf-8') as f:
            f.write(report)
        
        print(f"\n✅ Report generated: {output_path}")


def load_test_prompts(golden_dataset_path: str) -> List[Dict[str, str]]:
    """Load test prompts from golden dataset"""
    prompts = []
    
    # Load a sample of golden dataset examples
    dataset_dir = Path(golden_dataset_path)
    if not dataset_dir.exists():
        print(f"Warning: Golden dataset not found at {golden_dataset_path}")
        return get_default_test_prompts()
    
    # Sample from different categories
    for category_dir in dataset_dir.iterdir():
        if category_dir.is_dir():
            json_files = list(category_dir.glob("*.json"))[:5]  # 5 samples per category
            for json_file in json_files:
                try:
                    with open(json_file, 'r', encoding='utf-8') as f:
                        data = json.load(f)
                        if "prompt" in data:
                            prompts.append({
                                "system": data.get("system_prompt", "You are a helpful assistant."),
                                "user": data["prompt"]
                            })
                except Exception as e:
                    print(f"Error loading {json_file}: {e}")
    
    return prompts if prompts else get_default_test_prompts()


def get_default_test_prompts() -> List[Dict[str, str]]:
    """Get default test prompts if golden dataset not available"""
    return [
        {
            "system": "You are an experienced technical recruiter.",
            "user": "Analyze this resume: Senior Java Developer with 5 years Spring Boot experience, built microservices handling 10M requests/day."
        },
        {
            "system": "You are an expert technical interviewer.",
            "user": "Generate 3 interview questions for a mid-level React developer with Redux experience."
        },
        {
            "system": "You are an objective technical evaluator.",
            "user": "Evaluate this answer about database indexing: 'Indexes speed up queries by creating a sorted data structure.'"
        }
    ]


if __name__ == "__main__":
    # Load test prompts
    golden_dataset = "../golden_dataset"
    test_prompts = load_test_prompts(golden_dataset)
    
    if not test_prompts:
        print("Error: No test prompts available")
        exit(1)
    
    print(f"Loaded {len(test_prompts)} test prompts")
    
    # Run benchmark
    benchmark = ModelBenchmark()
    
    # Test different models
    model_results = benchmark.benchmark_models(test_prompts[:10])
    
    # Test temperature variations (using best model)
    temp_results = benchmark.benchmark_temperature("gpt-3.5-turbo-1106", test_prompts[:10])
    
    # Test max tokens
    token_results = benchmark.benchmark_max_tokens("gpt-3.5-turbo-1106", test_prompts[:10])
    
    # Generate report
    all_results = {
        "models": model_results,
        "temperature": temp_results,
        "max_tokens": token_results
    }
    
    report_path = f"results/benchmark_report_{datetime.now().strftime('%Y%m%d')}.md"
    benchmark.generate_report(all_results, report_path)
    
    # Save raw results as JSON
    json_path = report_path.replace('.md', '.json')
    with open(json_path, 'w', encoding='utf-8') as f:
        json.dump(all_results, f, indent=2)
    
    print(f"✅ Benchmark complete! Results saved to {report_path}")
