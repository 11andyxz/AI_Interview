#!/usr/bin/env python3
"""Simple test of the model server - with longer timeout"""

import requests
import json
import time

try:
    print("Testing model server at http://localhost:8080...")
    print("(This may take 30-60 seconds for the first inference)\n")
    
    start = time.time()
    
    response = requests.post(
        'http://localhost:8080/completion',
        json={
            'prompt': 'Hello, introduce yourself:',
            'n_predict': 50,
            'temperature': 0.7,
            'stop': ['\nUser:', '\nHuman:']
        },
        timeout=120  # 2 minute timeout
    )
    
    elapsed = time.time() - start
    print(f"\nOK: Got response after {elapsed:.2f}s")
    print(f"Status: {response.status_code}\n")
    
    data = response.json()
    print("Response:")
    print(json.dumps(data, indent=2, ensure_ascii=False))
    
except KeyboardInterrupt:
    print("\nWARN: Interrupted by user")
except Exception as e:
    print(f"ERROR: {e}")
    import traceback
    traceback.print_exc()
