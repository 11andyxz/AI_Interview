#!/usr/bin/env python3
"""Quick test import - import 5 sample questions"""
import requests
import json
import time

BASE_URL = "http://localhost:8080"

# Sample questions
questions = [
    {
        'id': 'java_gc_001',
        'question': 'Please explain Java garbage collection mechanism and its basic principles.',
        'domain': 'Java',
        'difficulty': 'L2',
        'techStack': ['Java', 'JVM']
    },
    {
        'id': 'react_hooks_001',
        'question': 'Explain React hooks and their benefits. What problems do they solve?',
        'domain': 'Frontend',
        'difficulty': 'L3',
        'techStack': ['React', 'JavaScript']
    },
    {
        'id': 'sql_optimization_001',
        'question': 'How to optimize database queries? What indexing strategies work best?',
        'domain': 'Database',
        'difficulty': 'L3',
        'techStack': ['SQL', 'MySQL']
    },
    {
        'id': 'microservices_001',  
        'question': 'Explain microservices architecture and its advantages over monolithic design.',
        'domain': 'Architecture',
        'difficulty': 'L3',
        'techStack': ['System Design', 'Architecture']
    },
    {
        'id': 'docker_001',
        'question': 'What are Docker containers? Explain the difference between containers and VMs.',
        'domain': 'DevOps',
        'difficulty': 'L2',
        'techStack': ['Docker', 'DevOps']
    }
]

print("Quick Import Test")
print("=" * 50)

success = 0
failed = 0

for i, q in enumerate(questions, 1):
    payload = {
        'id': q['id'],
        'content': q['question'],
        'metadata': {
            'domain': q['domain'],
            'difficulty': q['difficulty'],
            'category': ', '.join(q['techStack']),
            'source': 'test'
        }
    }
    
    try:
        resp = requests.post(f"{BASE_URL}/api/admin/knowledge/import", json=payload, timeout=30)
        if resp.status_code == 200:
            print(f"[{i}/5] OK: {q['id']}")
            success += 1
        else:
            print(f"[{i}/5] FAIL: {q['id']} - HTTP {resp.status_code}")
            failed += 1
    except Exception as e:
        print(f"[{i}/5] ERROR: {q['id']} - {e}")
        failed += 1
    
    time.sleep(0.3)

print("=" * 50)
print(f"Success: {success}/5")
print(f"Failed: {failed}/5")

# Test search
print("\nTesting search...")
try:
    resp = requests.get(f"{BASE_URL}/api/test/rag/search", 
                        params={'query': 'Java', 'topK': 3}, timeout=10)
    data = resp.json()
    results = data.get('results', [])
    print(f"Search returned {len(results)} results")
    for r in results:
        print(f"  - {r.get('id')}: {r.get('score', 0):.3f}")
except Exception as e:
    print(f"Search failed: {e}")
