/**
 * Test single request to proxy server
 */

const fetch = require('node-fetch');

const PROXY_URL = 'http://localhost:3001';

async function testSingleRequest() {
  console.log('Testing single request to proxy...\n');
  
  const testMessage = {
    messages: [
      { role: 'user', content: 'Hello, can you introduce yourself briefly?' }
    ],
    max_tokens: 128
  };
  
  try {
    const startTime = Date.now();
    
    const response = await fetch(`${PROXY_URL}/api/chat`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(testMessage)
    });
    
    const data = await response.json();
    const totalTime = Date.now() - startTime;
    
    console.log('Response:');
    console.log('-----------------------------');
    console.log(`Backend: ${data.backend}`);
    console.log(`Latency: ${data.latency_ms || totalTime}ms`);
    console.log(`Reply:\n${data.reply}`);
    console.log('-----------------------------\n');
    
    if (data.error) {
      console.error('Error:', data.error);
      process.exit(1);
    }
    
  } catch (error) {
    console.error('Request failed:', error.message);
    process.exit(1);
  }
}

testSingleRequest();
