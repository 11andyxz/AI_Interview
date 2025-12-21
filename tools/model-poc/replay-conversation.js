// Replay a conversation with the model and save a transcript
// Usage: node replay-conversation.js [--backend local|openai] [--name "Andy Zhang"]

const fetch = require('node-fetch');
const fs = require('fs');
const path = require('path');
require('dotenv').config();

const PROXY_URL = `http://localhost:${process.env.PROXY_PORT || 3001}`;
const BACKEND = (process.argv.join(' ').match(/--backend\s+(local|hf|openai)/)?.[1]) || process.env.BACKEND || 'local';
const CANDIDATE_NAME = (process.argv.join(' ').match(/--name\s+"([^"]+)"/)?.[1]) || 'Andy Zhang';
const RESULTS_DIR = path.join(__dirname, 'results', 'transcripts');

async function call(messages, maxTokens = 256) {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 120000);
  try {
    const res = await fetch(`${PROXY_URL}/api/chat`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ messages, max_tokens: maxTokens }),
      signal: controller.signal
    });
    if (!res.ok) throw new Error(`Proxy returned ${res.status}: ${res.statusText}`);
    return await res.json();
  } finally {
    clearTimeout(timeout);
  }
}

async function main() {
  if (!fs.existsSync(RESULTS_DIR)) fs.mkdirSync(RESULTS_DIR, { recursive: true });

  const systemPrompt = `You are an AI interviewer. Greet ${CANDIDATE_NAME} and ask if they are ready to begin. Keep answers concise and professional.`;

  const steps = [
    { role: 'system', content: systemPrompt },
    { role: 'user', content: 'Yes' },
    { role: 'user', content: 'Yes, please start with the first technical question.' },
    { role: 'user', content: 'Component is a generic stereotype for any Spring-managed component. Service is used for service-layer classes to indicate business logic. Repository is used in the persistence layer and also provides exception translation. Controller is used in the presentation layer to handle web requests and return views or responses.' },
    { role: 'user', content: 'Thanks, let\'s stop here.' }
  ];

  const messages = [];
  const transcript = [];
  const timestamp = new Date().toISOString().replace(/[:.]/g, '-');
  const baseName = `transcript_${BACKEND}_${timestamp}`;
  const txtPath = path.join(RESULTS_DIR, `${baseName}.txt`);
  const jsonPath = path.join(RESULTS_DIR, `${baseName}.json`);

  for (const step of steps) {
    messages.push(step);
    const resp = await call(messages);
    const reply = (resp.reply || '').trim();
    const latency = resp.latency_ms ?? 0;
    const backend = resp.backend || BACKEND;

    // Append to transcript
    if (step.role === 'system') {
      transcript.push(`[SYSTEM] ${step.content}`);
    } else if (step.role === 'user') {
      transcript.push(`[USER] ${step.content}`);
    }
    transcript.push(`[MODEL:${backend}] (latency ${latency} ms)\n${reply}`);

    // Push assistant reply into messages to keep context
    messages.push({ role: 'assistant', content: reply });

    // Small delay to be polite with local server
    await new Promise(r => setTimeout(r, 500));
  }

  const header = [
    `Timestamp: ${new Date().toISOString()}`,
    `Backend: ${BACKEND}`,
    `Candidate: ${CANDIDATE_NAME}`,
    ''
  ].join('\n');

  fs.writeFileSync(txtPath, header + transcript.join('\n\n') + '\n', 'utf8');
  fs.writeFileSync(jsonPath, JSON.stringify({ backend: BACKEND, candidate: CANDIDATE_NAME, steps, transcript }, null, 2), 'utf8');

  console.log('Saved transcript:');
  console.log(' -', txtPath);
  console.log(' -', jsonPath);
}

main().catch(err => {
  console.error('Replay failed:', err.message);
  process.exit(1);
});
