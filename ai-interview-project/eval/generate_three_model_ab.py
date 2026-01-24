#!/usr/bin/env python3
"""
Generate a comprehensive 3-model A/B test dataset including:
- gpt-3.5-turbo (baseline)
- gpt-4o-mini (current production candidate) 
- gpt-4-turbo (quality benchmark)

Usage: python eval/generate_three_model_ab.py
"""
import csv
import json
import random
import uuid
from datetime import datetime, timedelta


def generate_three_model_logs():
    """Generate synthetic logs for all three models with realistic distributions"""
    models = {
        'gpt-3.5-turbo': {
            'weight': 0.6,
            'latency_range': (800, 2500),
            'cost_per_token': 0.0015,
            'quality_bias': -0.1  # slightly lower quality
        },
        'gpt-4o-mini': {
            'weight': 0.3, 
            'latency_range': (600, 1800),
            'cost_per_token': 0.00015,
            'quality_bias': 0.0  # baseline quality
        },
        'gpt-4-turbo': {
            'weight': 0.1,
            'latency_range': (1200, 3500), 
            'cost_per_token': 0.03,
            'quality_bias': 0.15  # higher quality
        }
    }
    
    logs = []
    total_requests = 150
    
    # Distribute requests according to weights
    for model, config in models.items():
        num_requests = int(total_requests * config['weight'])
        
        for i in range(num_requests):
            latency = random.uniform(*config['latency_range'])
            tokens = random.randint(50, 300)
            cost = tokens * config['cost_per_token']
            
            # Generate timestamp in last 7 days
            base_time = datetime.now() - timedelta(days=random.uniform(0, 7))
            
            log = {
                'timestamp': base_time.isoformat(),
                'request_id': str(uuid.uuid4()),
                'model_version': model,
                'latency_ms': round(latency, 2),
                'tokens': tokens,
                'estimated_cost': round(cost, 6),
                'validator_pass': random.choice([0, 1]) if random.random() > 0.1 else None
            }
            logs.append(log)
    
    return logs


def generate_three_model_responses():
    """Generate reference and hypothesis pairs for all three models"""
    refs = []
    hyps = []
    
    # Sample interview questions and expected responses
    qa_pairs = [
        {
            "question": "Explain the difference between ArrayList and LinkedList in Java",
            "reference": "ArrayList uses dynamic arrays for storage with O(1) random access but O(n) insertion/deletion. LinkedList uses doubly-linked nodes with O(n) access but O(1) insertion/deletion at known positions.",
            "responses": {
                "gpt-3.5-turbo": "ArrayList is faster for getting elements, LinkedList is better for adding/removing elements in the middle.",
                "gpt-4o-mini": "ArrayList provides O(1) random access using arrays but O(n) insertion. LinkedList has O(n) access time but O(1) insertion/deletion when position is known.",
                "gpt-4-turbo": "ArrayList implements List interface using resizable arrays, providing constant-time random access O(1) but linear-time O(n) insertion/deletion. LinkedList uses doubly-linked list structure offering O(1) insertion/deletion at arbitrary positions but requires O(n) traversal for random access. Choice depends on access pattern: frequent random access favors ArrayList, frequent modifications favor LinkedList."
            }
        },
        {
            "question": "What is the time complexity of sorting algorithms?",
            "reference": "Common sorting algorithms: QuickSort O(n log n) average, O(n²) worst; MergeSort O(n log n) guaranteed; HeapSort O(n log n); BubbleSort O(n²); RadixSort O(nk) for integers.",
            "responses": {
                "gpt-3.5-turbo": "QuickSort is O(n log n) usually, MergeSort is always O(n log n), BubbleSort is O(n²).",
                "gpt-4o-mini": "QuickSort: O(n log n) average, O(n²) worst case. MergeSort: O(n log n) guaranteed. HeapSort: O(n log n). BubbleSort: O(n²).",
                "gpt-4-turbo": "Comparison-based sorts: QuickSort achieves O(n log n) average with O(n²) worst-case when poorly partitioned; MergeSort guarantees O(n log n) in all cases with O(n) space; HeapSort provides O(n log n) time with O(1) space. Non-comparison sorts like RadixSort achieve O(nk) for integers with k digits. Algorithm choice depends on data characteristics, stability requirements, and space constraints."
            }
        }
    ]
    
    for i, qa in enumerate(qa_pairs):
        # Create multiple instances for each model
        for model in ['gpt-3.5-turbo', 'gpt-4o-mini', 'gpt-4-turbo']:
            # Generate multiple variations per model
            num_variations = 15 if model == 'gpt-3.5-turbo' else (8 if model == 'gpt-4o-mini' else 3)
            
            for j in range(num_variations):
                ref_id = f"qa_{i}_{model}_{j}"
                
                refs.append({
                    'id': ref_id,
                    'text': qa['reference']
                })
                
                hyps.append({
                    'id': ref_id, 
                    'text': qa['responses'][model]
                })
    
    return refs, hyps


def main():
    # Generate logs
    logs = generate_three_model_logs()
    
    # Write logs CSV
    with open('eval/model_inference_log_three_models.csv', 'w', newline='', encoding='utf-8') as f:
        if logs:
            writer = csv.DictWriter(f, fieldnames=logs[0].keys())
            writer.writeheader()
            writer.writerows(logs)
    
    # Generate reference/hypothesis pairs
    refs, hyps = generate_three_model_responses()
    
    # Write JSONL files
    with open('eval/refs_three_models.jsonl', 'w', encoding='utf-8') as f:
        for ref in refs:
            f.write(json.dumps(ref) + '\n')
    
    with open('eval/hyps_three_models.jsonl', 'w', encoding='utf-8') as f:
        for hyp in hyps:
            f.write(json.dumps(hyp) + '\n')
    
    print(f"Generated {len(logs)} inference logs across 3 models")
    print(f"Generated {len(refs)} reference/hypothesis pairs")
    print("Files written:")
    print("- eval/model_inference_log_three_models.csv") 
    print("- eval/refs_three_models.jsonl")
    print("- eval/hyps_three_models.jsonl")


if __name__ == '__main__':
    main()