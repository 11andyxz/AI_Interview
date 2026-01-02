import requests

URL = 'http://127.0.0.1:7860/v1/chat/completions'
MODEL = 'llama-2-7b-chat'
PROMPTS = [
    'Say hi.',
    'Summarize: keep it short.',
    'What is 2+2?'
]
for p in PROMPTS:
    payload = {'model': MODEL, 'messages':[{'role':'user','content':p}], 'max_tokens':64, 'temperature':0.0}
    try:
        r = requests.post(URL, json=payload, timeout=30)
        print('warmup status', r.status_code)
    except Exception as e:
        print('warmup error', e)
