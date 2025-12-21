/**
 * Unified Proxy Server for Open-Source Model POC
 * Supports switching between local model, Hugging Face, and OpenAI
 */

const express = require('express');
const fetch = require('node-fetch');
require('dotenv').config();

const app = express();
app.use(express.json());
app.use(express.urlencoded({ extended: true }));

// Configuration
const BACKEND = process.env.BACKEND || 'local';
const PROXY_PORT = process.env.PROXY_PORT || 3001;
const LOCAL_MODEL_URL = process.env.LOCAL_MODEL_URL || 'http://localhost:8080/completion';
const LOCAL_MODEL_TYPE = process.env.LOCAL_MODEL_TYPE || 'llamacpp';
const HF_TOKEN = process.env.HF_TOKEN;
const HF_MODEL = process.env.HF_MODEL || 'meta-llama/Llama-2-7b-chat-hf';
const OPENAI_API_KEY = process.env.OPENAI_API_KEY;
const OPENAI_MODEL = process.env.OPENAI_MODEL || 'gpt-3.5-turbo';

console.log(`Starting proxy with backend: ${BACKEND}`);

// Helper: Convert chat messages to prompt
function messagesToPrompt(messages) {
  if (!messages || messages.length === 0) {
    return '';
  }
  
  return messages.map(msg => {
    const role = msg.role === 'assistant' ? 'Assistant' : 'User';
    return `${role}: ${msg.content}`;
  }).join('\n') + '\nAssistant:';
}

// Helper: Call local model (llama.cpp)
async function callLocalLlamaCpp(prompt, maxTokens = 256) {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 120000); // 2 minute timeout
  
  try {
    const response = await fetch(LOCAL_MODEL_URL, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        prompt,
        n_predict: maxTokens,
        temperature: 0.7,
        top_p: 0.9,
        stop: ['\nUser:', '\nHuman:']
      }),
      signal: controller.signal
    });
    
    if (!response.ok) {
      throw new Error(`Server returned ${response.status}: ${response.statusText}`);
    }
    
    const data = await response.json();
    return data.content || data.text || JSON.stringify(data);
  } finally {
    clearTimeout(timeout);
  }
}

// Helper: Call local model (text-generation-webui)
async function callLocalWebUI(prompt, maxTokens = 256) {
  const response = await fetch(LOCAL_MODEL_URL.replace('/completion', '/api/v1/generate'), {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      prompt,
      max_new_tokens: maxTokens,
      temperature: 0.7,
      top_p: 0.9,
      stop_sequence: ['\nUser:', '\nHuman:']
    })
  });
  
  const data = await response.json();
  return data.results?.[0]?.text || data.generated_text || JSON.stringify(data);
}

// Helper: Call Hugging Face
async function callHuggingFace(prompt, maxTokens = 256) {
  const response = await fetch(`https://api-inference.huggingface.co/models/${HF_MODEL}`, {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${HF_TOKEN}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      inputs: prompt,
      parameters: {
        max_new_tokens: maxTokens,
        temperature: 0.7,
        top_p: 0.9,
        return_full_text: false
      },
      options: {
        wait_for_model: true
      }
    })
  });
  
  const data = await response.json();
  if (data.error) {
    throw new Error(`HF API Error: ${data.error}`);
  }
  return data[0]?.generated_text || data.generated_text || JSON.stringify(data);
}

// Helper: Call OpenAI
async function callOpenAI(messages, maxTokens = 256) {
  const response = await fetch('https://api.openai.com/v1/chat/completions', {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${OPENAI_API_KEY}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      model: OPENAI_MODEL,
      messages,
      max_tokens: maxTokens,
      temperature: 0.7
    })
  });
  
  const data = await response.json();
  if (data.error) {
    throw new Error(`OpenAI API Error: ${data.error.message}`);
  }
  return data.choices[0].message.content;
}

// Main endpoint: chat
app.post('/api/chat', async (req, res) => {
  try {
    const { messages, max_tokens = 256 } = req.body;
    const startTime = Date.now();
    
    let reply;
    
    if (BACKEND === 'openai') {
      reply = await callOpenAI(messages, max_tokens);
    } else {
      const prompt = messagesToPrompt(messages);
      
      if (BACKEND === 'hf') {
        reply = await callHuggingFace(prompt, max_tokens);
      } else if (LOCAL_MODEL_TYPE === 'webui') {
        reply = await callLocalWebUI(prompt, max_tokens);
      } else {
        reply = await callLocalLlamaCpp(prompt, max_tokens);
      }
    }
    
    const latency = Date.now() - startTime;
    
    res.json({
      reply: reply.trim(),
      backend: BACKEND,
      latency_ms: latency,
      timestamp: new Date().toISOString()
    });
    
  } catch (error) {
    console.error('Error:', error);
    res.status(500).json({
      error: error.message,
      backend: BACKEND
    });
  }
});

// Health check
app.get('/health', (req, res) => {
  res.json({
    status: 'ok',
    backend: BACKEND,
    timestamp: new Date().toISOString()
  });
});

// Start server
app.listen(PROXY_PORT, () => {
  console.log(`Proxy server running on http://localhost:${PROXY_PORT}`);
  console.log(`Backend: ${BACKEND}`);
  console.log(`Health check: http://localhost:${PROXY_PORT}/health`);
});
