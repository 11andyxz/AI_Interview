#!/usr/bin/env python3
"""Diagnose vector store status"""
import requests
import json

BASE_URL = "http://localhost:8080"

print("=== Vector Store Diagnostic ===\n")

# 1. Test import
print("1. Testing import...")
payload = {
    'id': 'test_diagnostic',
    'content': 'Java garbage collection test question',
    'metadata': {'domain': 'Java', 'test': 'true'}
}

try:
    resp = requests.post(f"{BASE_URL}/api/admin/knowledge/import", json=payload, timeout=10)
    print(f"   Import Status: {resp.status_code}")
    if resp.status_code == 200:
        result = resp.json()
        print(f"   Response: {result}")
    else:
        print(f"   Error: {resp.text[:200]}")
except Exception as e:
    print(f"   ERROR: {e}")

# 2. Test search (no reranker)
print("\n2. Testing vector search (no filters, no reranker)...")
try:
    params = {
        'query': 'garbage collection',
        'topK': 5,
        'useReranker': 'false'
    }
    resp = requests.get(f"{BASE_URL}/api/test/rag/search", params=params, timeout=10)
    data = resp.json()
    
    print(f"   Success: {data.get('success')}")
    print(f"   Count: {data.get('count')}")
    print(f"   Latency: {data.get('latency_ms')}ms")
    
    results = data.get('results', [])
    if results:
        print(f"\n   Found {len(results)} results:")
        for i, r in enumerate(results[:3], 1):
            print(f"     {i}. {r.get('id')}: score={r.get('score', 0):.3f}")
    else:
        print("   ⚠️ No results returned!")
except Exception as e:
    print(f"   ERROR: {e}")

# 3. Test with reranker
print("\n3. Testing with reranker...")
try:
    params = {
        'query': 'Java',
        'topK': 3,
        'useReranker': 'true'
    }
    resp = requests.get(f"{BASE_URL}/api/test/rag/search", params=params, timeout=10)
    data = resp.json()
    
    print(f"   Success: {data.get('success')}")
    print(f"   Count: {data.get('count')}")
    
    results = data.get('results', [])
    if results:
        print(f"   Top result: {results[0].get('id')}")
    else:
        print("   ⚠️ No results!")
except Exception as e:
    print(f"   ERROR: {e}")

print("\n=== Diagnostic Complete ===")
