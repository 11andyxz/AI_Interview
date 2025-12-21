// Save a single prompt+reply to results folder
// Usage: node save-output.js "Your prompt here"

const fetch = require('node-fetch');
const fs = require('fs');
const path = require('path');

const PROXY_URL = process.env.PROXY_URL || 'http://localhost:3001';
const RESULTS_DIR = path.join(__dirname, 'results');

async function main() {
  const prompt = process.argv.slice(2).join(' ') || 'Introduce yourself briefly as an AI interviewer.';

  if (!fs.existsSync(RESULTS_DIR)) {
    fs.mkdirSync(RESULTS_DIR, { recursive: true });
  }

  const start = Date.now();
  let data;
  try {
    const response = await fetch(`${PROXY_URL}/api/chat`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        messages: [{ role: 'user', content: prompt }],
        max_tokens: 256
      }),
      // Allow slow local CPU inference
      timeout: 120000
    });
    data = await response.json();
  } catch (error) {
    console.error('Request failed:', error.message);
    process.exit(1);
  }

  const elapsed = Date.now() - start;
  const timestamp = new Date().toISOString().replace(/[:.]/g, '-');
  const backend = data.backend || 'local';
  const base = `output_${backend}_${timestamp}`;

  const txtPath = path.join(RESULTS_DIR, `${base}.txt`);
  const jsonPath = path.join(RESULTS_DIR, `${base}.json`);

  const reply = (data.reply || '').trim();
  const content = [
    `Timestamp: ${new Date().toISOString()}`,
    `Backend: ${backend}`,
    `Latency: ${data.latency_ms ?? elapsed} ms`,
    '',
    'Prompt:',
    prompt,
    '',
    'Reply:',
    reply,
    ''
  ].join('\n');

  fs.writeFileSync(txtPath, content, 'utf8');
  fs.writeFileSync(jsonPath, JSON.stringify({ prompt, ...data }, null, 2), 'utf8');

  console.log('Saved:');
  console.log(' -', txtPath);
  console.log(' -', jsonPath);
}

main();
