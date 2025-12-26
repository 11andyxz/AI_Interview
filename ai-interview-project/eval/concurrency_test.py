#!/usr/bin/env python3
"""
Concurrency Test for Local Model Serving
Tests throughput and latency under concurrent load
"""

import time
import requests
import statistics
from concurrent.futures import ThreadPoolExecutor, as_completed
from typing import List, Dict, Any
import json


def send_request(prompt: str, request_id: int, backend_url: str = "http://localhost:8080") -> Dict[str, Any]:
    """Send a single request to the backend and measure latency"""
    start_time = time.time()
    
    try:
        response = requests.post(
            f"{backend_url}/api/ai/chat",
            json={
                "message": prompt,
                "conversationHistory": []
            },
            timeout=60
        )
        
        latency = time.time() - start_time
        
        if response.status_code == 200:
            result = response.json()
            response_text = result.get("question", "") if isinstance(result, dict) else str(result)
            return {
                "request_id": request_id,
                "success": True,
                "latency": latency,
                "response_length": len(response_text),
                "error": None
            }
        else:
            return {
                "request_id": request_id,
                "success": False,
                "latency": latency,
                "error": f"HTTP {response.status_code}"
            }
    
    except Exception as e:
        latency = time.time() - start_time
        return {
            "request_id": request_id,
            "success": False,
            "latency": latency,
            "error": str(e)
        }


def run_concurrency_test(num_requests: int = 20, num_workers: int = 5) -> Dict[str, Any]:
    """Run concurrent requests and collect metrics"""
    
    # Simple test prompt
    test_prompt = "请用一句话介绍人工智能。"
    
    print(f"Starting concurrency test8080 (Backend APIts} requests with {num_workers} workers")
    print(f"Target: http://localhost:11434 (Ollama)")
    print("-" * 60)
    
    results = []
    start_time = time.time()
    
    # Execute requests concurrently
    with ThreadPoolExecutor(max_workers=num_workers) as executor:
        futures = [
            executor.submit(send_request, test_prompt, i)
            for i in range(num_requests)
        ]
        
        for future in as_completed(futures):
            result = future.result()
            results.append(result)
            status = "✓" if result["success"] else "✗"
            print(f"{status} Request {result['request_id']}: {result['latency']:.2f}s")
    
    total_time = time.time() - start_time
    
    # Calculate metrics
    successful = [r for r in results if r["success"]]
    failed = [r for r in results if not r["success"]]
    
    latencies = [r["latency"] for r in successful]
    
    metrics = {
        "total_requests": num_requests,
        "successful": len(successful),
        "failed": len(failed),
        "success_rate": len(successful) / num_requests * 100,
        "total_time": total_time,
        "throughput": num_requests / total_time,
        "latency_avg": statistics.mean(latencies) if latencies else 0,
        "latency_median": statistics.median(latencies) if latencies else 0,
        "latency_p95": statistics.quantiles(latencies, n=20)[18] if len(latencies) > 1 else 0,
        "latency_min": min(latencies) if latencies else 0,
        "latency_max": max(latencies) if latencies else 0,
    }
    
    return metrics


def print_results(metrics: Dict[str, Any]):
    """Print formatted test results"""
    print("\n" + "=" * 60)
    print("CONCURRENCY TEST RESULTS")
    print("=" * 60)
    print(f"Total Requests:    {metrics['total_requests']}")
    print(f"Successful:        {metrics['successful']} ({metrics['success_rate']:.1f}%)")
    print(f"Failed:            {metrics['failed']}")
    print(f"Total Time:        {metrics['total_time']:.2f}s")
    print(f"Throughput:        {metrics['throughput']:.2f} req/s")
    print()
    print("Latency Statistics:")
    print(f"  Average:         {metrics['latency_avg']:.2f}s")
    print(f"  Median:          {metrics['latency_median']:.2f}s")
    print(f"  P95:             {metrics['latency_p95']:.2f}s")
    print(f"  Min:             {metrics['latency_min']:.2f}s")
    print(f"  Max:             {metrics['latency_max']:.2f}s")
    print("=" * 60)


if __name__ == "__main__":
    # Run test with 20 concurrent requests
    metrics = run_concurrency_test(num_requests=20, num_workers=5)
    print_results(metrics)
    
    # Save results to file
    output_file = "eval/concurrency_test_results.json"
    with open(output_file, "w", encoding="utf-8") as f:
        json.dump(metrics, f, indent=2, ensure_ascii=False)
    
    print(f"\nResults saved to: {output_file}")
