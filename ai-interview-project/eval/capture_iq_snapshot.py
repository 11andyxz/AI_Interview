import json
import requests
from pathlib import Path

ROOT = Path(__file__).resolve().parent
PROMPTS = ROOT / 'poc_prompts' / 'interview_qa.jsonl'
OUT_DIR = ROOT / 'results_local_integration'
OUT_DIR.mkdir(parents=True, exist_ok=True)
LOCAL_URL = 'http://127.0.0.1:7860/v1/chat/completions'
MODEL_NAME = 'llama-2-7b-chat'
TARGET_ID = 'IQ-19'

# Load prompts
prompt_text = None
with open(PROMPTS, 'r', encoding='utf-8') as f:
    for line in f:
        obj = json.loads(line)
        if obj.get('id') == TARGET_ID:
            prompt_text = obj.get('prompt')
            break

if not prompt_text:
    print('Prompt not found for', TARGET_ID)
    raise SystemExit(1)

messages = [
    {"role": "system", "content": "You are an interview assistant."},
    {"role": "user", "content": prompt_text}
]

payload = {
    'model': MODEL_NAME,
    'messages': messages,
    'max_tokens': 1000,
    'temperature': 0.7
}

resp = requests.post(LOCAL_URL, json=payload, timeout=300)
resp.raise_for_status()
data = resp.json()

out_path = OUT_DIR / f'{TARGET_ID}_snapshot.json'
with open(out_path, 'w', encoding='utf-8') as f:
    json.dump({'prompt': prompt_text, 'response_raw': data}, f, ensure_ascii=False, indent=2)

print('Saved snapshot to', out_path)
