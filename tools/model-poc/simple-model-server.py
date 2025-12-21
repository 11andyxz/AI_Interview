"""
Simple Model Server using ctransformers (pure Python, no compilation needed)
Provides OpenAI-compatible API for GGUF models
"""

from ctransformers import AutoModelForCausalLM
from http.server import HTTPServer, BaseHTTPRequestHandler
import json
import time
import sys
import threading
import os

# Model path from environment variable
MODEL_PATH = os.getenv("LOCAL_MODEL_PATH", "models/llama-2-7b-chat.Q4_K_M.gguf")

print(f"Loading model from {MODEL_PATH}...")
try:
    model = AutoModelForCausalLM.from_pretrained(
        MODEL_PATH,
        model_type="llama",
        context_length=2048,
        threads=4
    )
    print("Model loaded successfully! (CPU inference)")
except Exception as e:
    print(f"Error loading model: {e}")
    import traceback
    traceback.print_exc()
    sys.exit(1)



# HTTP Request Handler
class ModelHandler(BaseHTTPRequestHandler):
    def do_POST(self):
        content_length = int(self.headers.get('Content-Length', 0))
        body = self.rfile.read(content_length).decode('utf-8')
        
        try:
            data = json.loads(body)
        except:
            self.send_response(400)
            self.send_header('Content-Type', 'application/json')
            self.end_headers()
            self.wfile.write(json.dumps({'error': 'Invalid JSON'}).encode('utf-8'))
            return
        
        # Handle /completion endpoint
        if self.path == '/completion':
            prompt = data.get('prompt', '')
            max_tokens = data.get('n_predict', 256)
            temperature = data.get('temperature', 0.7)
            
            try:
                start_time = time.time()
                response = model(prompt, max_new_tokens=max_tokens, temperature=temperature)
                latency = (time.time() - start_time) * 1000
                
                self.send_response(200)
                self.send_header('Content-Type', 'application/json')
                self.end_headers()
                self.wfile.write(json.dumps({
                    'content': response,
                    'latency_ms': latency
                }).encode('utf-8'))
            except Exception as e:
                self.send_response(500)
                self.send_header('Content-Type', 'application/json')
                self.end_headers()
                self.wfile.write(json.dumps({'error': str(e)}).encode('utf-8'))
        
        # Handle /v1/chat/completions endpoint
        elif self.path == '/v1/chat/completions':
            messages = data.get('messages', [])
            max_tokens = data.get('max_tokens', 256)
            temperature = data.get('temperature', 0.7)
            
            # Convert messages to prompt
            prompt = ""
            for msg in messages:
                role = msg.get('role', 'user')
                content = msg.get('content', '')
                if role == 'system':
                    prompt += f"System: {content}\n"
                elif role == 'user':
                    prompt += f"User: {content}\n"
                elif role == 'assistant':
                    prompt += f"Assistant: {content}\n"
            prompt += "Assistant:"
            
            try:
                start_time = time.time()
                response = model(prompt, max_new_tokens=max_tokens, temperature=temperature)
                latency = (time.time() - start_time) * 1000
                
                self.send_response(200)
                self.send_header('Content-Type', 'application/json')
                self.end_headers()
                self.wfile.write(json.dumps({
                    'choices': [{
                        'message': {
                            'role': 'assistant',
                            'content': response.strip()
                        },
                        'finish_reason': 'stop'
                    }],
                    'usage': {
                        'total_tokens': len(prompt.split()) + len(response.split())
                    },
                    'latency_ms': latency
                }).encode('utf-8'))
            except Exception as e:
                self.send_response(500)
                self.send_header('Content-Type', 'application/json')
                self.end_headers()
                self.wfile.write(json.dumps({'error': str(e)}).encode('utf-8'))
        
        else:
            self.send_response(404)
            self.send_header('Content-Type', 'application/json')
            self.end_headers()
            self.wfile.write(json.dumps({'error': 'Not found'}).encode('utf-8'))
    
    def do_GET(self):
        if self.path == '/health':
            self.send_response(200)
            self.send_header('Content-Type', 'application/json')
            self.end_headers()
            self.wfile.write(json.dumps({
                'status': 'ok',
                'model': MODEL_PATH
            }).encode('utf-8'))
        else:
            self.send_response(404)
            self.end_headers()
    
    def log_message(self, format, *args):
        """Suppress default logging"""
        pass


if __name__ == '__main__':
    print("\n" + "="*60)
    print("Model Server Running!")
    print("="*60)
    print(f"Model: {MODEL_PATH}")
    print(f"Endpoint (llama.cpp): http://localhost:8080/completion")
    print(f"Endpoint (OpenAI): http://localhost:8080/v1/chat/completions")
    print(f"Health check: http://localhost:8080/health")
    print("="*60 + "\n")
    
    server = HTTPServer(('0.0.0.0', 8080), ModelHandler)
    print("Server running on http://0.0.0.0:8080")
    print("Press CTRL+C to quit\n")
    
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nShutting down...")
        server.shutdown()
