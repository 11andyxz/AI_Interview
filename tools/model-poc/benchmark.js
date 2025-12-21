/**
 * Benchmark script to compare local model vs OpenAI
 * Runs test prompts and collects quality/latency metrics
 */

const fetch = require('node-fetch');
const fs = require('fs');
const path = require('path');

const PROXY_URL = 'http://localhost:3001';
const RESULTS_DIR = path.join(__dirname, 'results');

// Test prompts (interview scenarios)
const TEST_PROMPTS = [
  {
    id: 1,
    category: 'introduction',
    messages: [
      { role: 'user', content: 'Hello, can you introduce yourself as an AI interviewer?' }
    ]
  },
  {
    id: 2,
    category: 'technical_question',
    messages: [
      { role: 'user', content: 'Can you explain the difference between @Component, @Repository, @Service, and @Controller annotations in Spring framework?' }
    ]
  },
  {
    id: 3,
    category: 'follow_up',
    messages: [
      { role: 'user', content: 'What is eventual consistency in distributed systems?' },
      { role: 'assistant', content: 'Eventual consistency means that in a distributed system, replicas may not be immediately consistent, but they will converge to the same state if no new updates are made.' },
      { role: 'user', content: 'Can you give a practical example?' }
    ]
  },
  {
    id: 4,
    category: 'database',
    messages: [
      { role: 'user', content: 'Explain the difference between SQL and NoSQL databases.' }
    ]
  },
  {
    id: 5,
    category: 'system_design',
    messages: [
      { role: 'user', content: 'How would you design a scalable chat application?' }
    ]
  }
];

async function callProxy(messages, maxTokens = 256) {
  const startTime = Date.now();
  
  const response = await fetch(`${PROXY_URL}/api/chat`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ messages, max_tokens: maxTokens })
  });
  
  const data = await response.json();
  const totalTime = Date.now() - startTime;
  
  return {
    reply: data.reply || '',
    backend: data.backend,
    latency: data.latency_ms || totalTime,
    error: data.error || null
  };
}

async function runBenchmark() {
  console.log('Starting benchmark...\n');
  
  // Create results directory
  if (!fs.existsSync(RESULTS_DIR)) {
    fs.mkdirSync(RESULTS_DIR, { recursive: true });
  }
  
  const results = [];
  const timestamp = new Date().toISOString().replace(/[:.]/g, '-');
  
  for (const prompt of TEST_PROMPTS) {
    console.log(`Running test ${prompt.id}/${TEST_PROMPTS.length}: ${prompt.category}`);
    
    try {
      const result = await callProxy(prompt.messages, 256);
      
      results.push({
        prompt_id: prompt.id,
        category: prompt.category,
        input: prompt.messages,
        output: result.reply,
        backend: result.backend,
        latency_ms: result.latency,
        error: result.error,
        timestamp: new Date().toISOString()
      });
      
      console.log(`  Completed in ${result.latency}ms`);
      console.log(`  Backend: ${result.backend}`);
      console.log(`  Preview: ${result.reply.substring(0, 100)}...\n`);
      
      // Small delay between requests
      await new Promise(resolve => setTimeout(resolve, 1000));
      
    } catch (error) {
      console.error(`  Failed: ${error.message}\n`);
      results.push({
        prompt_id: prompt.id,
        category: prompt.category,
        input: prompt.messages,
        error: error.message,
        timestamp: new Date().toISOString()
      });
    }
  }
  
  // Calculate statistics
  const latencies = results.filter(r => r.latency_ms).map(r => r.latency_ms);
  const stats = {
    total_requests: results.length,
    successful: results.filter(r => !r.error).length,
    failed: results.filter(r => r.error).length,
    latency: {
      mean: latencies.reduce((a, b) => a + b, 0) / latencies.length,
      median: latencies.sort((a, b) => a - b)[Math.floor(latencies.length / 2)],
      min: Math.min(...latencies),
      max: Math.max(...latencies),
      p95: latencies.sort((a, b) => a - b)[Math.floor(latencies.length * 0.95)]
    }
  };
  
  // Save results
  const outputFile = path.join(RESULTS_DIR, `benchmark_${timestamp}.json`);
  fs.writeFileSync(outputFile, JSON.stringify({ stats, results }, null, 2));
  
  console.log('\n' + '='.repeat(60));
  console.log('Benchmark Complete!');
  console.log('='.repeat(60));
  console.log(`Results saved to: ${outputFile}\n`);
  console.log('Statistics:');
  console.log(`  Total requests: ${stats.total_requests}`);
  console.log(`  Successful: ${stats.successful}`);
  console.log(`  Failed: ${stats.failed}`);
  console.log('\nLatency (ms):');
  console.log(`  Mean: ${stats.latency.mean.toFixed(2)}`);
  console.log(`  Median: ${stats.latency.median.toFixed(2)}`);
  console.log(`  Min: ${stats.latency.min}`);
  console.log(`  Max: ${stats.latency.max}`);
  console.log(`  95th percentile: ${stats.latency.p95}`);
  console.log('='.repeat(60) + '\n');
  
  // Generate simple comparison report
  const reportFile = path.join(RESULTS_DIR, `report_${timestamp}.txt`);
  let report = 'BENCHMARK REPORT\n';
  report += '='.repeat(60) + '\n\n';
  report += `Date: ${new Date().toLocaleString()}\n`;
  report += `Backend: ${results[0]?.backend || 'unknown'}\n\n`;
  report += 'STATISTICS\n';
  report += '-'.repeat(60) + '\n';
  report += `Total Requests: ${stats.total_requests}\n`;
  report += `Success Rate: ${(stats.successful / stats.total_requests * 100).toFixed(2)}%\n\n`;
  report += 'LATENCY (ms)\n';
  report += '-'.repeat(60) + '\n';
  report += `Mean:   ${stats.latency.mean.toFixed(2)}\n`;
  report += `Median: ${stats.latency.median.toFixed(2)}\n`;
  report += `Min:    ${stats.latency.min}\n`;
  report += `Max:    ${stats.latency.max}\n`;
  report += `P95:    ${stats.latency.p95}\n\n`;
  report += 'SAMPLE RESPONSES\n';
  report += '-'.repeat(60) + '\n';
  results.slice(0, 3).forEach(r => {
    report += `\nPrompt ${r.prompt_id} (${r.category}):\n`;
    report += `Input: ${r.input[r.input.length - 1].content.substring(0, 80)}...\n`;
    report += `Output: ${r.output.substring(0, 200)}...\n`;
    report += `Latency: ${r.latency_ms}ms\n`;
  });
  
  fs.writeFileSync(reportFile, report);
  console.log(`Text report saved to: ${reportFile}\n`);
}

// Run benchmark
runBenchmark().catch(error => {
  console.error('Benchmark failed:', error);
  process.exit(1);
});
