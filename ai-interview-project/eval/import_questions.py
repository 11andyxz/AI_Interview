#!/usr/bin/env python3
"""
Import interview questions from golden_dataset into Redis vector store
Generates embeddings and stores them for RAG search
"""

import json
import os
import requests
import time
from pathlib import Path
from typing import List, Dict

BASE_URL = "http://localhost:8080"
QUESTIONS_DIR = "golden_dataset/interview_questions"

def load_all_questions(directory: str = QUESTIONS_DIR) -> List[Dict]:
    """Load all interview questions from JSON files"""
    questions = []
    questions_path = Path(directory)
    
    if not questions_path.exists():
        print(f"ERROR: Directory not found: {directory}")
        return []
    
    json_files = list(questions_path.glob("*.json"))
    print(f"Found {len(json_files)} question files")
    
    for json_file in json_files:
        try:
            with open(json_file, 'r', encoding='utf-8') as f:
                data = json.load(f)
                questions.append({
                    'id': data.get('questionId', json_file.stem),
                    'question': data.get('question', ''),
                    'domain': data.get('domain', 'General'),
                    'difficulty': data.get('difficulty', 'Unknown'),
                    'techStack': data.get('techStack', [])
                })
        except Exception as e:
            print(f"  ERROR loading {json_file.name}: {e}")
    
    return questions

def import_question(question: Dict, index: int, total: int) -> bool:
    """Import a single question via backend API"""
    url = f"{BASE_URL}/api/admin/knowledge/import"
    
    # Format tech stack as category
    tech_stack_str = ', '.join(question['techStack']) if question['techStack'] else question['domain']
    
    payload = {
        'id': question['id'],
        'content': question['question'],
        'metadata': {
            'domain': question['domain'],
            'difficulty': question['difficulty'],
            'category': tech_stack_str,
            'source': 'golden_dataset'
        }
    }
    
    try:
        resp = requests.post(url, json=payload, timeout=30)
        
        if resp.status_code == 200:
            print(f"  [{index}/{total}] ✓ {question['id']}: {question['question'][:60]}...")
            return True
        else:
            print(f"  [{index}/{total}] ✗ {question['id']}: HTTP {resp.status_code}")
            print(f"      Response: {resp.text[:200]}")
            return False
    except Exception as e:
        print(f"  [{index}/{total}] ✗ {question['id']}: {e}")
        return False

def batch_import_questions(questions: List[Dict]) -> Dict:
    """Batch import all questions"""
    print(f"\nImporting {len(questions)} questions to Redis vector store...")
    print("="*80)
    
    success_count = 0
    failed_count = 0
    start_time = time.time()
    
    for i, question in enumerate(questions, 1):
        if import_question(question, i, len(questions)):
            success_count += 1
        else:
            failed_count += 1
        
        # Rate limit: 0.2s between imports (avoid overloading OpenAI API)
        if i < len(questions):
            time.sleep(0.2)
    
    duration = time.time() - start_time
    
    print("\n" + "="*80)
    print("IMPORT SUMMARY")
    print("="*80)
    print(f"Total:    {len(questions)}")
    print(f"Success:  {success_count} ({success_count/len(questions)*100:.1f}%)")
    print(f"Failed:   {failed_count}")
    print(f"Duration: {duration:.1f}s ({duration/len(questions):.2f}s per question)")
    print("="*80)
    
    return {
        'total': len(questions),
        'success': success_count,
        'failed': failed_count,
        'duration': duration
    }

def create_import_endpoint_if_needed():
    """
    Check if import endpoint exists, if not, instructions to add it
    """
    url = f"{BASE_URL}/api/admin/knowledge/import"
    
    try:
        # Try a test POST to see if endpoint exists
        resp = requests.post(url, json={'id': '_test', 'content': 'test'}, timeout=5)
        # 404 = endpoint doesn't exist, anything else = exists (even if validation fails)
        if resp.status_code == 404:
            return False
        return True
    except Exception as e:
        print(f"Cannot check endpoint: {e}")
        return False

def main():
    print("="*80)
    print("     Interview Questions Import Tool")
    print("="*80)
    print()
    
    # Health check
    try:
        resp = requests.get(f"{BASE_URL}/api/test/rag/health", timeout=5)
        resp.raise_for_status()
        print("✓ Backend health check passed\n")
    except Exception as e:
        print(f"✗ Backend health check failed: {e}")
        print("  Make sure backend is running on port 8080")
        return 1
    
    # Load questions
    questions = load_all_questions()
    if not questions:
        print("✗ No questions loaded")
        return 1
    
    print(f"✓ Loaded {len(questions)} interview questions\n")
    
    # Check if we need import endpoint
    print("NOTE: This script requires a /api/admin/knowledge/import endpoint")
    print("      that accepts: {id, content, metadata} and calls:")
    print("      embeddingService.generateEmbedding() + vectorStore.upsert()\n")
    
    # Batch import
    result = batch_import_questions(questions)
    
    # Verify import
    if result['success'] > 0:
        print("\n✓ Import completed! Testing search...")
        try:
            test_resp = requests.get(
                f"{BASE_URL}/api/test/rag/search",
                params={'query': 'Java garbage collection', 'topK': 3},
                timeout=10
            )
            test_resp.raise_for_status()
            data = test_resp.json()
            results = data.get('results', [])
            print(f"  Search returned {len(results)} results:")
            for r in results[:3]:
                print(f"    - {r.get('id')} (score: {r.get('score', 0):.3f})")
        except Exception as e:
            print(f"  Search test failed: {e}")
    
    return 0 if result['failed'] == 0 else 1

if __name__ == "__main__":
    exit(main())
