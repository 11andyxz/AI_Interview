# Open-Source Model POC Guide

This guide helps you quickly set up an open-source model POC and compare quality/latency against OpenAI.

## Option A: Local Deployment (Recommended, no additional costs)

### 1. Choose and Download Model

**Recommended Model:** Llama-2-7B-Chat GGUF Q4 quantized (~3.8GB)

**Download Location:**
- Hugging Face: https://huggingface.co/TheBloke/Llama-2-7B-Chat-GGUF
- Select file: `llama-2-7b-chat.Q4_K_M.gguf` (~3.8GB)

**Download Command (Windows PowerShell):**
```powershell
# Install huggingface-cli if needed
pip install huggingface-hub

# Download model file to D drive
huggingface-cli download TheBloke/Llama-2-7B-Chat-GGUF llama-2-7b-chat.Q4_K_M.gguf --local-dir D:\models
```

**Alternative Models:**
- Mistral-7B-Instruct GGUF Q4: https://huggingface.co/TheBloke/Mistral-7B-Instruct-v0.2-GGUF (~4GB)
- Vicuna-7B GGUF Q4: https://huggingface.co/TheBloke/vicuna-7B-v1.5-GGUF (~4GB)

### 2. Install and Start Model Server

**Method 1: Using llama.cpp (Simplest, recommended for Windows)**

```powershell
# Clone llama.cpp
git clone https://github.com/ggerganov/llama.cpp.git
cd llama.cpp

# Windows build (requires CMake and VS Build Tools)
cmake -B build
cmake --build build --config Release

# Or download pre-built binaries:
# https://github.com/ggerganov/llama.cpp/releases

# Start server (API mode) - model from D drive
.\build\bin\Release\server.exe -m D:\models\llama-2-7b-chat.Q4_K_M.gguf --port 8080 --host 0.0.0.0 -c 2048
```

**Method 2: Using text-generation-webui (More features)**

```powershell
# Clone repository
git clone https://github.com/oobabooga/text-generation-webui.git
cd text-generation-webui

# Windows one-click install
.\start_windows.bat

# Place downloaded model in models/ directory

# Start API service
python server.py --api --listen --model llama-2-7b-chat.Q4_K_M
```

### 3. Test Local Service

```powershell
# llama.cpp format
curl -X POST http://localhost:8080/completion `
  -H "Content-Type: application/json" `
  -d '{"prompt":"Hello, how are you?","n_predict":128}'

# text-generation-webui format
curl -X POST http://localhost:7860/api/v1/generate `
  -H "Content-Type: application/json" `
  -d '{"prompt":"Hello, how are you?","max_new_tokens":128}'
```

## Option B: Using Hosted API (Quick, but has usage costs)

### Hugging Face Inference API

```powershell
# Set token
$env:HF_TOKEN="your_huggingface_token_here"

# Call API
curl -X POST "https://api-inference.huggingface.co/models/meta-llama/Llama-2-7b-chat-hf" `
  -H "Authorization: Bearer $env:HF_TOKEN" `
  -H "Content-Type: application/json" `
  -d '{"inputs":"Hello, how are you?","parameters":{"max_new_tokens":128}}'
```

## Using Unified Proxy Interface

Start the proxy server (see `proxy-server.js`), it will automatically forward requests to your configured backend:

```powershell
# Install dependencies
npm install

# Configure backend (in .env file)
# BACKEND=local  or hf or openai

# Start
node proxy-server.js
```

Your frontend needs no changes, just send requests to `http://localhost:3001`

## Run Benchmarks

```powershell
# Test single request
node test-single.js

# Run full benchmark comparison (local vs OpenAI)
node benchmark.js
```

## Hardware Requirements

- **CPU Mode** (quantized models): Works but slow (~5-10s per response)
- **NVIDIA GPU** (recommended):
  - 7B Q4 models: Minimum 4GB VRAM (8GB+ recommended)
  - Performance: RTX 3060/3070 ~1-2s, RTX 4090/A100 ~0.3-0.5s

## Storage Requirements

- Single 7B Q4 model: ~3.8-4.5GB
- llama.cpp service: ~500MB
- text-generation-webui: ~3-5GB (with dependencies)
- **Total**: ~8-10GB

## Next Steps

1. Choose and download model (start with llama-2-7b-chat Q4)
2. Start local service (llama.cpp or webui)
3. Start proxy server
4. Run benchmark.js to compare with OpenAI
5. Check results/ directory for detailed comparison reports

## License Notes

- Llama-2: Meta license (allows research and commercial use with terms)
- Check individual model LICENSE files for specific terms
