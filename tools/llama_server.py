"""
Simple HTTP server for Llama-2 model using ctransformers
Replicates Week 2 POC setup (port 8080, /completion endpoint)
"""
from flask import Flask, request, jsonify
from ctransformers import AutoModelForCausalLM
import time
import os

app = Flask(__name__)

# Load model
MODEL_PATH = r"D:\dev\AI_Interview\tools\text-generation-webui\models\llama-2-7b-chat.Q4_K_M.gguf"
print(f"Loading model from {MODEL_PATH}...")
model = AutoModelForCausalLM.from_pretrained(
    MODEL_PATH,
    model_type='llama',
    gpu_layers=0,  # CPU only
    context_length=2048
)
print("Model loaded successfully!")

@app.route('/completion', methods=['POST'])
def completion():
    """
    Handle completion requests in Week 2 POC format
    Expected payload: {"prompt": "...", "max_tokens": 500, "temperature": 0.7}
    """
    data = request.json
    prompt = data.get('prompt', '')
    max_tokens = data.get('max_tokens', 500)
    temperature = data.get('temperature', 0.7)
    
    start_time = time.time()
    
    # Generate response
    response = model(
        prompt,
        max_new_tokens=max_tokens,
        temperature=temperature,
        top_p=0.9,
        repetition_penalty=1.1
    )
    
    latency = (time.time() - start_time) * 1000  # ms
    
    return jsonify({
        'text': response,
        'latency_ms': latency
    })

@app.route('/health', methods=['GET'])
def health():
    return jsonify({'status': 'ok', 'model': 'llama-2-7b-chat'})

if __name__ == '__main__':
    print("Starting Llama-2 server on http://localhost:8080")
    print("Endpoint: POST /completion")
    print("Format: {\"prompt\": \"...\", \"max_tokens\": 500, \"temperature\": 0.7}")
    app.run(host='0.0.0.0', port=8080, debug=False)
