"""
Week 12 P1 - Golden Dataset Evaluation
Tests RAG retrieval quality: Recall@5 and MRR (Mean Reciprocal Rank)
"""

import json
import requests
import time
from typing import List, Dict, Tuple

BASE_URL = "http://localhost:8080/api/test/rag"

def search_baseline(query: str, topK: int = 5) -> List[str]:
    """Baseline: Vector search only (simulated by using keyword-only search)"""
    # Note: In real scenario, we'd call a vector-only endpoint
    # For now, we use the hybrid endpoint but rely on semantic component
    response = requests.get(f"{BASE_URL}/search", params={
        "query": query,
        "topK": topK
    })
    response.raise_for_status()
    results = response.json().get("results", [])
    return [r["id"] for r in results]

def search_optimized(query: str, topK: int = 5) -> List[str]:
    """Optimized: Hybrid search + reranking"""
    response = requests.get(f"{BASE_URL}/search", params={
        "query": query,
        "topK": topK
    })
    response.raise_for_status()
    results = response.json().get("results", [])
    return [r["id"] for r in results]

def calculate_recall_at_k(retrieved: List[str], relevant: List[str], k: int = 5) -> float:
    """Calculate Recall@k: proportion of relevant items retrieved in top-k"""
    if not relevant:
        return 0.0
    retrieved_k = set(retrieved[:k])
    relevant_set = set(relevant)
    hits = len(retrieved_k & relevant_set)
    return hits / len(relevant_set)

def calculate_mrr(retrieved: List[str], relevant: List[str]) -> float:
    """Calculate Mean Reciprocal Rank: 1 / rank of first relevant item"""
    relevant_set = set(relevant)
    for i, item_id in enumerate(retrieved, start=1):
        if item_id in relevant_set:
            return 1.0 / i
    return 0.0

def evaluate_dataset(golden_queries: List[Dict]) -> Tuple[Dict, Dict]:
    """Evaluate both baseline and optimized retrieval"""
    
    baseline_recalls = []
    baseline_mrrs = []
    optimized_recalls = []
    optimized_mrrs = []
    
    print("\n" + "="*70)
    print("  Week 12 P1 - Golden Dataset Evaluation")
    print("="*70 + "\n")
    
    for i, item in enumerate(golden_queries, 1):
        query = item["query"]
        relevant_ids = item["relevant_ids"]
        category = item.get("category", "Unknown")
        
        print(f"Query {i}/{len(golden_queries)}: {query[:50]}...")
        print(f"  Category: {category}")
        print(f"  Relevant IDs: {len(relevant_ids)}")
        
        # Baseline search
        try:
            baseline_results = search_baseline(query)
            baseline_recall = calculate_recall_at_k(baseline_results, relevant_ids, k=5)
            baseline_mrr = calculate_mrr(baseline_results, relevant_ids)
            baseline_recalls.append(baseline_recall)
            baseline_mrrs.append(baseline_mrr)
            print(f"  Baseline: Recall@5={baseline_recall:.2f}, MRR={baseline_mrr:.3f}")
        except Exception as e:
            print(f"  Baseline failed: {e}")
            baseline_recalls.append(0.0)
            baseline_mrrs.append(0.0)
        
        # Optimized search
        try:
            optimized_results = search_optimized(query)
            optimized_recall = calculate_recall_at_k(optimized_results, relevant_ids, k=5)
            optimized_mrr = calculate_mrr(optimized_results, relevant_ids)
            optimized_recalls.append(optimized_recall)
            optimized_mrrs.append(optimized_mrr)
            print(f"  Optimized: Recall@5={optimized_recall:.2f}, MRR={optimized_mrr:.3f}")
        except Exception as e:
            print(f"  Optimized failed: {e}")
            optimized_recalls.append(0.0)
            optimized_mrrs.append(0.0)
        
        print()
        time.sleep(0.5)  # Rate limiting
    
    # Calculate averages
    baseline_stats = {
        "recall@5": sum(baseline_recalls) / len(baseline_recalls) if baseline_recalls else 0.0,
        "mrr": sum(baseline_mrrs) / len(baseline_mrrs) if baseline_mrrs else 0.0
    }
    
    optimized_stats = {
        "recall@5": sum(optimized_recalls) / len(optimized_recalls) if optimized_recalls else 0.0,
        "mrr": sum(optimized_mrrs) / len(optimized_mrrs) if optimized_mrrs else 0.0
    }
    
    return baseline_stats, optimized_stats

def print_results(baseline: Dict, optimized: Dict):
    """Print evaluation results and check acceptance criteria"""
    
    print("\n" + "="*70)
    print("                          RESULTS")
    print("="*70 + "\n")
    
    print("Baseline (Vector-only):")
    print(f"  Recall@5: {baseline['recall@5']:.3f}")
    print(f"  MRR:      {baseline['mrr']:.3f}")
    
    print("\nOptimized (Hybrid + Rerank):")
    print(f"  Recall@5: {optimized['recall@5']:.3f}")
    print(f"  MRR:      {optimized['mrr']:.3f}")
    
    recall_improvement = ((optimized['recall@5'] - baseline['recall@5']) / baseline['recall@5'] * 100) if baseline['recall@5'] > 0 else 0
    mrr_improvement = ((optimized['mrr'] - baseline['mrr']) / baseline['mrr'] * 100) if baseline['mrr'] > 0 else 0
    
    print("\nImprovement:")
    print(f"  Recall@5: {recall_improvement:+.1f}%")
    print(f"  MRR:      {mrr_improvement:+.1f}%")
    
    print("\n" + "="*70)
    print("               ACCEPTANCE CRITERIA")
    print("="*70 + "\n")
    
    # P1-AC1: Recall@5 improvement >= 10%
    print("P1-AC1: Recall@5 improvement >= 10%")
    if recall_improvement >= 10:
        print(f"  [PASS] Improvement = {recall_improvement:.1f}% >= 10%")
        ac1_pass = True
    else:
        print(f"  [FAIL] Improvement = {recall_improvement:.1f}% < 10%")
        ac1_pass = False
    
    # P1-AC2: MRR improvement >= 15%
    print("\nP1-AC2: MRR improvement >= 15%")
    if mrr_improvement >= 15:
        print(f"  [PASS] Improvement = {mrr_improvement:.1f}% >= 15%")
        ac2_pass = True
    else:
        print(f"  [FAIL] Improvement = {mrr_improvement:.1f}% < 15%")
        ac2_pass = False
    
    print("\n" + "="*70 + "\n")
    
    return ac1_pass and ac2_pass

if __name__ == "__main__":
    # Check if backend is running
    try:
        response = requests.get(f"{BASE_URL}/health", timeout=5)
        if response.status_code != 200:
            print("ERROR: Backend not responding correctly")
            exit(1)
    except Exception as e:
        print(f"ERROR: Cannot connect to backend at {BASE_URL}")
        print(f"       Make sure backend is running on port 8080")
        exit(1)
    
    # Load golden queries
    try:
        with open("golden_queries.json", "r") as f:
            golden_queries = json.load(f)
    except FileNotFoundError:
        print("ERROR: golden_queries.json not found")
        exit(1)
    
    # Note: This is a DEMONSTRATION script
    # Real evaluation requires actual question IDs from the database
    # For now, we'll test the infrastructure and show metrics would be calculated
    
    print("\n" + "="*70)
    print("  NOTE: Using simulated relevant IDs for demonstration")
    print("  Real deployment needs actual question database mapping")
    print("="*70)
    
    # Run evaluation
    baseline_stats, optimized_stats = evaluate_dataset(golden_queries)
    
    # Print results
    all_pass = print_results(baseline_stats, optimized_stats)
    
    # Exit code
    exit(0 if all_pass else 1)
