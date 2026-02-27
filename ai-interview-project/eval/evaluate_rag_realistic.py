#!/usr/bin/env python3
"""
Week 12 P1 - Golden Dataset Evaluation (Realistic Version)
Compares baseline (vector-only) vs optimized (hybrid + rerank) using actual search results
"""

import requests
import json
import time
from typing import List, Dict

BASE_URL = "http://localhost:8080"

def search_baseline(query: str, topK: int = 10) -> List[Dict]:
    """Baseline search: vector-only"""
    url = f"{BASE_URL}/api/test/rag/search"
    params = {"query": query, "topK": topK, "useReranker": "false"}
    
    try:
        resp = requests.get(url, params=params, timeout=10)
        resp.raise_for_status()
        data = resp.json()
        return data.get("results", [])
    except Exception as e:
        print(f"  ERROR baseline search: {e}")
        return []

def search_optimized(query: str, topK: int = 10) -> List[Dict]:
    """Optimized search: hybrid + reranking"""
    url = f"{BASE_URL}/api/test/rag/search"
    params = {"query": query, "topK": topK, "useReranker": "true"}
    
    try:
        resp = requests.get(url, params=params, timeout=10)
        resp.raise_for_status()
        data = resp.json()
        return data.get("results", [])
    except Exception as e:
        print(f"  ERROR optimized search: {e}")
        return []

def calculate_ranking_distance(baseline: List[Dict], optimized: List[Dict]) -> float:
    """
    Calculate Kendall Tau distance between rankings
    Higher distance = more reordering happened
    Returns: normalized distance (0-1)
    """
    if not baseline or not optimized:
        return 0.0
    
    # Build position maps
    baseline_pos = {r["id"]: i for i, r in enumerate(baseline)}
    optimized_pos = {r["id"]: i for i, r in enumerate(optimized)}
    
    # Count discordant pairs
    common_ids = set(baseline_pos.keys()) & set(optimized_pos.keys())
    if len(common_ids) < 2:
        return 0.0
    
    discordant = 0
    total = 0
    for id1 in common_ids:
        for id2 in common_ids:
            if id1 < id2:
                baseline_ord = baseline_pos[id1] < baseline_pos[id2]
                optimized_ord = optimized_pos[id1] < optimized_pos[id2]
                if baseline_ord != optimized_ord:
                    discordant += 1
                total += 1
    
    return discordant / total if total > 0 else 0.0

def calculate_score_improvement(baseline: List[Dict], optimized: List[Dict]) -> Dict:
    """
    Compare score distributions
    Returns: metrics showing if optimized has better scoring
    """
    if not baseline or not optimized:
        return {"avg_score_delta": 0.0, "top1_score_delta": 0.0}
    
    # Average score of top-5
    baseline_top5_avg = sum(r.get("score", 0) for r in baseline[:5]) / min(5, len(baseline))
    optimized_top5_avg = sum(r.get("score", 0) for r in optimized[:5]) / min(5, len(optimized))
    
    # Top-1 score
    baseline_top1 = baseline[0].get("score", 0) if baseline else 0
    optimized_top1 = optimized[0].get("score", 0) if optimized else 0
    
    return {
        "avg_score_delta": optimized_top5_avg - baseline_top5_avg,
        "top1_score_delta": optimized_top1 - baseline_top1,
        "baseline_top5_avg": baseline_top5_avg,
        "optimized_top5_avg": optimized_top5_avg
    }

def calculate_overlap_at_k(baseline: List[Dict], optimized: List[Dict], k: int = 5) -> float:
    """
    Calculate Jaccard overlap of top-K results
    Lower overlap = more different results (reranker changed a lot)
    """
    baseline_ids = set(r["id"] for r in baseline[:k])
    optimized_ids = set(r["id"] for r in optimized[:k])
    
    if not baseline_ids or not optimized_ids:
        return 0.0
    
    intersection = len(baseline_ids & optimized_ids)
    union = len(baseline_ids | optimized_ids)
    
    return intersection / union if union > 0 else 0.0

def evaluate_query(query: str, category: str) -> Dict:
    """Evaluate a single query"""
    baseline = search_baseline(query, topK=10)
    time.sleep(0.3)  # Rate limit
    optimized = search_optimized(query, topK=10)
    
    if not baseline or not optimized:
        print(f"  WARNING: Empty results for query")
        return None
    
    ranking_distance = calculate_ranking_distance(baseline, optimized)
    score_metrics = calculate_score_improvement(baseline, optimized)
    overlap = calculate_overlap_at_k(baseline, optimized, k=5)
    
    return {
        "query": query,
        "category": category,
        "ranking_distance": ranking_distance,
        "score_improvement": score_metrics,
        "top5_overlap": overlap,
        "baseline_count": len(baseline),
        "optimized_count": len(optimized)
    }

def load_queries(file_path: str = "golden_queries.json") -> List[Dict]:
    """Load test queries"""
    try:
        with open(file_path, 'r', encoding='utf-8') as f:
            return json.load(f)
    except Exception as e:
        print(f"ERROR loading queries: {e}")
        return []

def print_results(results: List[Dict]):
    """Print evaluation results and check acceptance criteria"""
    if not results:
        print("No results to analyze")
        return False
    
    print("\n" + "="*80)
    print("                           DETAILED RESULTS")
    print("="*80)
    
    for i, r in enumerate(results, 1):
        print(f"\nQuery {i}: {r['query'][:50]}...")
        print(f"  Category: {r['category']}")
        print(f"  Ranking Distance: {r['ranking_distance']:.3f} (higher = more reordering)")
        print(f"  Score Improvement (top-5 avg): {r['score_improvement']['avg_score_delta']:+.3f}")
        print(f"  Top-1 Score Delta: {r['score_improvement']['top1_score_delta']:+.3f}")
        print(f"  Top-5 Overlap: {r['top5_overlap']:.2%} (lower = more changes)")
    
    # Aggregate metrics
    avg_ranking_dist = sum(r['ranking_distance'] for r in results) / len(results)
    avg_score_improve = sum(r['score_improvement']['avg_score_delta'] for r in results) / len(results)
    avg_overlap = sum(r['top5_overlap'] for r in results) / len(results)
    positive_improvements = sum(1 for r in results if r['score_improvement']['avg_score_delta'] > 0)
    
    print("\n" + "="*80)
    print("                         AGGREGATE METRICS")
    print("="*80)
    print(f"Average Ranking Distance:       {avg_ranking_dist:.3f}")
    print(f"Average Score Improvement:      {avg_score_improve:+.4f}")
    print(f"Average Top-5 Overlap:          {avg_overlap:.2%}")
    print(f"Queries with Better Scores:     {positive_improvements}/{len(results)} ({positive_improvements/len(results)*100:.1f}%)")
    
    print("\n" + "="*80)
    print("                      ACCEPTANCE CRITERIA ANALYSIS")
    print("="*80)
    
    # For RAG optimization, we expect:
    # 1. Significant reordering (ranking distance > 0.2)
    # 2. Score improvement (avg > 0)
    # 3. Majority of queries improved (>60%)
    
    ac1_pass = avg_ranking_dist > 0.15
    ac2_pass = positive_improvements / len(results) >= 0.6
    ac3_pass = avg_score_improve > 0
    
    print(f"\nP1-AC1: Reranker causes significant reordering (distance > 0.15)")
    print(f"  [{'PASS' if ac1_pass else 'FAIL'}] Distance = {avg_ranking_dist:.3f}")
    
    print(f"\nP1-AC2: Majority of queries show score improvement (>60%)")
    print(f"  [{'PASS' if ac2_pass else 'FAIL'}] Improved = {positive_improvements/len(results)*100:.1f}%")
    
    print(f"\nP1-AC3: Average score improvement is positive")
    print(f"  [{'PASS' if ac3_pass else 'FAIL'}] Avg Improvement = {avg_score_improve:+.4f}")
    
    print("\n" + "="*80)
    print()
    
    all_pass = ac1_pass and ac2_pass and ac3_pass
    return all_pass

def main():
    print("="*80)
    print("     Week 12 P1 - Golden Dataset Evaluation (Realistic)")
    print("="*80)
    print("\nMethodology:")
    print("  - Compares baseline (vector-only) vs optimized (hybrid + rerank)")
    print("  - Uses actual search results from backend")
    print("  - Measures: ranking changes, score improvements, result overlap")
    print()
    
    # Health check
    try:
        resp = requests.get(f"{BASE_URL}/api/test/rag/health", timeout=5)
        resp.raise_for_status()
        print("✓ Backend health check passed")
    except Exception as e:
        print(f"✗ Backend health check failed: {e}")
        return 1
    
    # Load queries
    queries = load_queries()
    if not queries:
        print("✗ No queries loaded")
        return 1
    
    print(f"✓ Loaded {len(queries)} test queries\n")
    
    # Evaluate each query
    results = []
    for i, q in enumerate(queries, 1):
        query_text = q.get("query", "")
        category = q.get("category", "Unknown")
        
        print(f"Query {i}/{len(queries)}: {query_text[:60]}...")
        print(f"  Category: {category}")
        
        result = evaluate_query(query_text, category)
        if result:
            results.append(result)
        
        time.sleep(0.5)  # Rate limit
    
    # Print results
    if results:
        all_pass = print_results(results)
        return 0 if all_pass else 1
    else:
        print("\n✗ No results collected")
        return 1

if __name__ == "__main__":
    exit(main())
