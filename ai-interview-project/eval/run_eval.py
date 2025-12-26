#!/usr/bin/env python3
"""
AI Interview Evaluation Runner

This script runs evaluation tests against the AI interview backend.
It loads prompts from JSONL files, calls the backend APIs, and generates
performance reports with latency, token usage, and failure metrics.

Usage:
    python run_eval.py --backend http://localhost:8080 --output results/
"""

import argparse
import json
import time
import csv
import os
from datetime import datetime
from typing import List, Dict, Any
import requests


class EvalRunner:
    """Main evaluation runner class"""
    
    def __init__(self, backend_url: str, output_dir: str, username: str = "testuser", password: str = "password", 
                 model_type: str = "backend", local_model_url: str = None, local_model_name: str = "llama2"):
        """
        Initialize the evaluation runner.
        
        Args:
            backend_url: Base URL of the backend API (e.g., http://localhost:8080)
            output_dir: Directory to save evaluation results
            username: Username for backend authentication (default: testuser)
            password: Password for backend authentication (default: password)
            model_type: Type of model to test - "backend" (via backend API) or "local" (direct OSS model)
            local_model_url: URL for local OSS model (e.g., http://localhost:11434/api/generate for Ollama)
            local_model_name: Name of local model (e.g., llama2, mistral, codellama)
        """
        self.backend_url = backend_url.rstrip('/')
        self.output_dir = output_dir
        self.results = []
        self.auth_token = None
        self.username = username
        self.password = password
        self.model_type = model_type
        self.local_model_url = local_model_url
        self.local_model_name = local_model_name
        
        # Create output directory if it doesn't exist
        os.makedirs(output_dir, exist_ok=True)
        
        # Authenticate only if using backend
        if self.model_type == "backend":
            self._authenticate()
        else:
            print(f"[LOCAL MODE] Will call local OSS model at {self.local_model_url}")
    
    def _authenticate(self):
        """Login to backend and obtain JWT token"""
        login_url = f"{self.backend_url}/api/auth/login"
        payload = {
            "username": self.username,
            "password": self.password
        }
        
        try:
            response = requests.post(login_url, json=payload, timeout=30)
            if response.status_code == 200:
                data = response.json()
                self.auth_token = data.get('accessToken') or data.get('token')  # Try both field names
                if self.auth_token:
                    print(f"[OK] Authenticated as {self.username}")
                else:
                    print(f"[WARN] Authenticated but no token in response")
            else:
                print(f"[FAIL] Authentication failed: {response.status_code} - {response.text}")
                print("  Continuing without auth (some APIs may fail)")
        except Exception as e:
            print(f"[ERROR] Authentication error: {str(e)}")
            print("  Continuing without auth (some APIs may fail)")
    
    def load_prompts(self, filepath: str) -> List[Dict[str, Any]]:
        """
        Load prompts from a JSONL file.
        
        Args:
            filepath: Path to the JSONL file
            
        Returns:
            List of prompt dictionaries
        """
        prompts = []
        with open(filepath, 'r', encoding='utf-8') as f:
            for line in f:
                if line.strip():
                    prompts.append(json.loads(line))
        return prompts
    
    def call_backend_api(self, endpoint: str, payload: Dict[str, Any]) -> Dict[str, Any]:
        """
        Call a backend API endpoint and measure performance.
        
        Args:
            endpoint: API endpoint path (e.g., /api/llm/question-generate)
            payload: Request payload
            
        Returns:
            Dictionary with response data, latency, and status
        """
        url = f"{self.backend_url}{endpoint}"
        start_time = time.time()
        
        headers = {'Content-Type': 'application/json'}
        if self.auth_token:
            headers['Authorization'] = f'Bearer {self.auth_token}'
        
        try:
            response = requests.post(
                url,
                json=payload,
                headers=headers,
                timeout=120
            )
            latency = (time.time() - start_time) * 1000  # Convert to milliseconds
            
            if response.status_code == 200:
                return {
                    'success': True,
                    'latency_ms': latency,
                    'response': response.json(),
                    'status_code': response.status_code
                }
            else:
                error_text = response.text if response.text else f"HTTP {response.status_code}"
                return {
                    'success': False,
                    'latency_ms': latency,
                    'error': error_text,
                    'status_code': response.status_code
                }
        except Exception as e:
            latency = (time.time() - start_time) * 1000
            return {
                'success': False,
                'latency_ms': latency,
                'error': str(e),
                'status_code': 0
            }
    
    def evaluate_prompts(self, prompts: List[Dict[str, Any]], prompt_type: str):
        """
        Evaluate a list of prompts and collect results.
        
        Args:
            prompts: List of prompt dictionaries
            prompt_type: Type of prompts (resume_analysis, interview_qa, multi_turn)
        """
        print(f"\n=== Evaluating {len(prompts)} {prompt_type} prompts ===\n")
        
        for i, prompt_data in enumerate(prompts, 1):
            print(f"[{i}/{len(prompts)}] Testing {prompt_data['id']}...")
            
            # TODO: Map prompt type to appropriate backend endpoint
            # For now, we'll implement a simple mapping
            result = self._evaluate_single_prompt(prompt_data, prompt_type)
            self.results.append(result)
            
            if result['success']:
                print(f"  Status: SUCCESS")
            else:
                print(f"  Status: FAILED (HTTP {result.get('status_code', 'unknown')})")
                # Always print error for debugging
                error_msg = result.get('error', '[No error field]')
                print(f"  Error: {str(error_msg)[:200] if error_msg else '[Empty error]'}")
            print(f"  Latency: {result['latency_ms']:.2f}ms")
    
    def _evaluate_single_prompt(self, prompt_data: Dict[str, Any], prompt_type: str) -> Dict[str, Any]:
        """
        Evaluate a single prompt by calling the appropriate backend endpoint.
        
        For resume_analysis prompts: Use /api/user/resume/{id}/analyze endpoint
        For interview_qa prompts: Use /api/llm/question-generate endpoint  
        For multi_turn prompts: Call /api/llm/question-generate + /api/llm/eval multiple times
        """
        result = {
            'id': prompt_data['id'],
            'task_type': prompt_data['task_type'],
            'difficulty': prompt_data['difficulty'],
            'prompt_type': prompt_type,
            'timestamp': datetime.now().isoformat()
        }
        
        try:
            # Route to appropriate API based on model type
            if self.model_type == "local":
                api_result = self._call_local_model_api(prompt_data)
            elif prompt_type == 'resume_analysis':
                # For resume analysis, we'll call a generic OpenAI endpoint
                # Since we don't have a real resume ID, we'll use the eval endpoint
                api_result = self._call_resume_analysis_api(prompt_data)
            elif prompt_type == 'interview_qa':
                # For interview Q&A, use question-generate endpoint
                api_result = self._call_interview_qa_api(prompt_data)
            elif prompt_type == 'multi_turn':
                # For multi-turn, simulate conversation flow
                api_result = self._call_multi_turn_api(prompt_data)
            else:
                raise ValueError(f"Unknown prompt type: {prompt_type}")
            
            # Merge API result into result dict
            result.update(api_result)
            
            # Extract token usage if available in response
            if result.get('success') and 'response' in result:
                response = result['response']
                # OpenAI typically returns usage info
                if isinstance(response, dict) and 'usage' in response:
                    result['tokens_used'] = response['usage'].get('total_tokens', 0)
                else:
                    result['tokens_used'] = self._estimate_tokens(prompt_data, result)
            else:
                result['tokens_used'] = 0
            
            # Add quality scoring for successful responses
            if result.get('success'):
                response_text = str(result.get('response', ''))
                quality_metrics = self._score_response(prompt_data, response_text)
                result.update(quality_metrics)
            else:
                # Failed responses get zero quality scores
                result.update({
                    'quality_score': 0,
                    'completeness_score': 0,
                    'format_score': 0,
                    'factuality_score': 0,
                    'coherence_score': 0
                })
                
        except Exception as e:
            result.update({
                'success': False,
                'latency_ms': 0,
                'error': str(e),
                'tokens_used': 0
            })
        
        return result
    
    def _call_local_model_api(self, prompt_data: Dict[str, Any]) -> Dict[str, Any]:
        """
        Call local OSS model directly (e.g., Ollama, vLLM, LocalAI).
        
        Supports common local model formats:
        - Ollama: POST /api/generate with {"model": "llama2", "prompt": "..."}
        - vLLM: POST /v1/completions with OpenAI-compatible format
        - LocalAI: POST /v1/completions with OpenAI-compatible format
        """
        if not self.local_model_url:
            return {
                'success': False,
                'latency_ms': 0,
                'error': 'Local model URL not configured',
                'status_code': 0
            }
        
        # Construct prompt from prompt_data
        full_prompt = prompt_data['prompt']
        if prompt_data.get('input_context'):
            full_prompt = f"{prompt_data.get('input_context')}\n\n{full_prompt}"
        
        start_time = time.time()
        
        try:
            # Detect model type from URL and construct appropriate payload
            if 'ollama' in self.local_model_url or ':11434' in self.local_model_url:
                # Ollama format
                payload = {
                    'model': self.local_model_name,
                    'prompt': full_prompt,
                    'stream': False
                }
            elif ':5000' in self.local_model_url or 'text-generation' in self.local_model_url:
                # text-generation-webui format
                payload = {
                    'prompt': full_prompt,
                    'max_new_tokens': 1000,
                    'temperature': 0.7,
                    'top_p': 0.9,
                    'do_sample': True
                }
            else:
                # OpenAI-compatible format (vLLM, LocalAI, etc.)
                payload = {
                    'model': self.local_model_name,
                    'prompt': full_prompt,
                    'max_tokens': 1000,
                    'temperature': 0.7
                }
            
            response = requests.post(
                self.local_model_url,
                json=payload,
                timeout=120
            )
            
            latency = (time.time() - start_time) * 1000
            
            if response.status_code == 200:
                response_data = response.json()
                
                # Extract response text based on format
                if 'response' in response_data:  # Ollama format
                    response_text = response_data['response']
                elif 'results' in response_data:  # text-generation-webui format
                    results = response_data['results']
                    if isinstance(results, list) and len(results) > 0:
                        response_text = results[0].get('text', '')
                    else:
                        response_text = str(results)
                elif 'choices' in response_data:  # OpenAI-compatible format
                    response_text = response_data['choices'][0].get('text', '')
                else:
                    response_text = str(response_data)
                
                return {
                    'success': True,
                    'latency_ms': latency,
                    'response': response_text,
                    'status_code': response.status_code
                }
            else:
                return {
                    'success': False,
                    'latency_ms': latency,
                    'error': f"HTTP {response.status_code}: {response.text}",
                    'status_code': response.status_code
                }
        
        except Exception as e:
            latency = (time.time() - start_time) * 1000
            return {
                'success': False,
                'latency_ms': latency,
                'error': str(e),
                'status_code': 0
            }
    
    def _call_resume_analysis_api(self, prompt_data: Dict[str, Any]) -> Dict[str, Any]:
        """Call backend API for resume analysis tasks"""
        # For evaluation purposes, we'll use a simplified payload
        # In production, this would require actual resume data
        payload = {
            'prompt': prompt_data['prompt'],
            'context': prompt_data.get('input_context', ''),
            'task': prompt_data['task_type']
        }
        
        # Using the eval endpoint as a generic LLM endpoint
        return self.call_backend_api('/api/llm/eval', {
            'question': prompt_data['prompt'],
            'answer': prompt_data.get('input_context', 'N/A'),
            'roleId': 'backend_java',
            'level': 'mid'
        })
    
    def _call_interview_qa_api(self, prompt_data: Dict[str, Any]) -> Dict[str, Any]:
        """Call backend API for interview Q&A tasks"""
        try:
            # Use question-generate endpoint
            payload = {
                'sessionId': f"eval_{prompt_data['id']}",
                'roleId': 'backend_java',
                'level': prompt_data.get('difficulty', 'medium'),
                'candidateInfo': {
                    'context': prompt_data.get('input_context', ''),
                    'prompt': prompt_data['prompt']
                }
            }
            
            return self.call_backend_api('/api/llm/question-generate', payload)
        except Exception as e:
            import traceback
            return {
                'success': False,
                'latency_ms': 0,
                'error': f"{str(e)}\n{traceback.format_exc()}",
                'status_code': 0
            }
    
    def _call_multi_turn_api(self, prompt_data: Dict[str, Any]) -> Dict[str, Any]:
        """Call backend API for multi-turn conversation tasks"""
        # For multi-turn, we'll make multiple API calls simulating a conversation
        # Start with question generation
        conversation_flow = prompt_data.get('conversation_flow', [])
        if conversation_flow is None:
            conversation_flow = []
        
        # First turn
        first_result = self._call_interview_qa_api(prompt_data)
        
        if not first_result.get('success'):
            return first_result
        
        # For evaluation purposes, record the first turn
        # A full implementation would continue the conversation
        first_result['turns_completed'] = 1
        first_result['expected_turns'] = len(conversation_flow)
        
        return first_result
    
    def _estimate_tokens(self, prompt_data: Dict[str, Any], result: Dict[str, Any]) -> int:
        """
        Estimate token usage when not provided by API.
        Rough estimate: ~4 characters per token for English text.
        """
        prompt_text = prompt_data.get('prompt') or ''
        input_context = prompt_data.get('input_context') or ''
        response_text = str(result.get('response', ''))
        
        total_chars = len(prompt_text) + len(input_context) + len(response_text)
        return total_chars // 4
    
    def _score_response(self, prompt_data: Dict[str, Any], response_text: str) -> Dict[str, Any]:
        """
        Simple rubric-based scoring for response quality.
        
        Returns dict with:
        - quality_score (0-100): Overall quality score
        - completeness_score (0-100): Length/detail adequacy
        - format_score (0-100): Basic structure compliance
        - factuality_score (0-100): Confidence/certainty (100 = no flags)
        - coherence_score (0-100): Sentence structure quality
        """
        if not response_text or response_text == 'None':
            return {
                'quality_score': 0,
                'completeness_score': 0,
                'format_score': 0,
                'factuality_score': 0,
                'coherence_score': 0
            }
        
        response_lower = response_text.lower()
        
        # 1. Completeness: Check response length
        length = len(response_text)
        if length < 50:
            completeness_score = 30
        elif length < 150:
            completeness_score = 60
        elif length < 300:
            completeness_score = 85
        else:
            completeness_score = 100
        
        # 2. Format: Check for basic structure (sentences, punctuation)
        has_sentences = '.' in response_text or '?' in response_text or '!' in response_text
        has_capitalization = any(c.isupper() for c in response_text)
        format_score = 50
        if has_sentences:
            format_score += 30
        if has_capitalization:
            format_score += 20
        format_score = min(format_score, 100)
        
        # 3. Factuality: Flag uncertainty markers
        uncertainty_words = ['i think', 'maybe', 'probably', 'might be', 'could be', 
                             'not sure', 'uncertain', 'guess', 'perhaps']
        uncertainty_count = sum(1 for word in uncertainty_words if word in response_lower)
        factuality_score = max(100 - (uncertainty_count * 15), 0)
        
        # 4. Coherence: Check sentence completeness
        sentences = [s.strip() for s in response_text.split('.') if s.strip()]
        incomplete_sentences = sum(1 for s in sentences if len(s) < 10)
        coherence_score = max(100 - (incomplete_sentences * 20), 50)
        
        # Overall quality: weighted average
        quality_score = int(
            completeness_score * 0.3 +
            format_score * 0.2 +
            factuality_score * 0.3 +
            coherence_score * 0.2
        )
        
        return {
            'quality_score': quality_score,
            'completeness_score': completeness_score,
            'format_score': format_score,
            'factuality_score': factuality_score,
            'coherence_score': coherence_score
        }
    
    def generate_report(self):
        """Generate CSV and Markdown reports from evaluation results"""
        timestamp = datetime.now().strftime('%Y%m%d_%H%M%S')
        
        # Generate CSV report
        csv_path = os.path.join(self.output_dir, f'eval_results_{timestamp}.csv')
        self._generate_csv_report(csv_path)
        
        # Generate Markdown report
        md_path = os.path.join(self.output_dir, f'eval_report_{timestamp}.md')
        self._generate_markdown_report(md_path)
        
        print(f"\n=== Reports Generated ===")
        print(f"CSV: {csv_path}")
        print(f"Markdown: {md_path}")
    
    def _generate_csv_report(self, filepath: str):
        """
        Generate CSV report with detailed results for each prompt.
        Includes: id, task_type, difficulty, success, latency_ms, tokens_used, error
        """
        if not self.results:
            print("No results to write to CSV")
            return
        
        fieldnames = [
            'id', 'task_type', 'difficulty', 'prompt_type',
            'success', 'latency_ms', 'tokens_used', 
            'quality_score', 'completeness_score', 'format_score', 
            'factuality_score', 'coherence_score',
            'error', 'timestamp'
        ]
        
        with open(filepath, 'w', newline='', encoding='utf-8') as f:
            writer = csv.DictWriter(f, fieldnames=fieldnames, extrasaction='ignore')
            writer.writeheader()
            
            for result in self.results:
                # Only write selected fields to CSV
                row = {k: result.get(k, '') for k in fieldnames}
                writer.writerow(row)
        
        print(f"CSV report written: {len(self.results)} results")
    
    def _generate_markdown_report(self, filepath: str):
        """
        Generate human-readable Markdown report with summary statistics.
        Includes: success rate, latency percentiles, token usage, failures by type.
        """
        if not self.results:
            with open(filepath, 'w', encoding='utf-8') as f:
                f.write("# Evaluation Report\n\nNo results to report.\n")
            return
        
        # Calculate statistics
        stats = self._calculate_statistics()
        
        with open(filepath, 'w', encoding='utf-8') as f:
            f.write("# AI Interview Evaluation Report\n\n")
            f.write(f"**Generated**: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n\n")
            f.write(f"**Backend**: {self.backend_url}\n\n")
            
            # Overall Summary
            f.write("## Overall Summary\n\n")
            f.write(f"- **Total Tests**: {stats['total']}\n")
            f.write(f"- **Successful**: {stats['successful']} ({stats['success_rate']:.1f}%)\n")
            f.write(f"- **Failed**: {stats['failed']}\n")
            f.write(f"- **Average Latency**: {stats['avg_latency']:.2f}ms\n")
            f.write(f"- **Median Latency (p50)**: {stats['p50_latency']:.2f}ms\n")
            f.write(f"- **95th Percentile (p95)**: {stats['p95_latency']:.2f}ms\n")
            f.write(f"- **Total Tokens**: {stats['total_tokens']}\n")
            f.write(f"- **Avg Tokens per Test**: {stats['avg_tokens']:.1f}\n\n")
            
            # Quality Metrics Summary
            if stats['avg_quality'] > 0:
                f.write("## Quality Metrics (Rubric-Based)\n\n")
                f.write(f"- **Overall Quality**: {stats['avg_quality']:.1f}/100\n")
                f.write(f"- **Completeness**: {stats['avg_completeness']:.1f}/100\n")
                f.write(f"- **Format Compliance**: {stats['avg_format']:.1f}/100\n")
                f.write(f"- **Factuality** (no uncertainty): {stats['avg_factuality']:.1f}/100\n")
                f.write(f"- **Coherence**: {stats['avg_coherence']:.1f}/100\n\n")
            
            # Breakdown by Prompt Type
            f.write("## Results by Prompt Type\n\n")
            for prompt_type, type_stats in stats['by_type'].items():
                f.write(f"### {prompt_type.replace('_', ' ').title()}\n\n")
                f.write(f"- Tests: {type_stats['count']}\n")
                f.write(f"- Success Rate: {type_stats['success_rate']:.1f}%\n")
                f.write(f"- Avg Latency: {type_stats['avg_latency']:.2f}ms\n")
                f.write(f"- p95 Latency: {type_stats['p95_latency']:.2f}ms\n")
                f.write(f"- Avg Tokens: {type_stats['avg_tokens']:.1f}\n\n")
            
            # Failures
            if stats['failed'] > 0:
                f.write("## Failures\n\n")
                f.write("| ID | Task Type | Error |\n")
                f.write("|-----|-----------|-------|\n")
                
                for result in self.results:
                    if not result.get('success', False):
                        error = result.get('error', 'Unknown error')
                        # Truncate long errors
                        if len(error) > 60:
                            error = error[:57] + "..."
                        f.write(f"| {result['id']} | {result['task_type']} | {error} |\n")
                f.write("\n")
            
            # Slowest Tests
            f.write("## Slowest Tests (Top 5)\n\n")
            sorted_results = sorted(self.results, key=lambda x: x.get('latency_ms', 0), reverse=True)
            f.write("| ID | Task Type | Latency (ms) |\n")
            f.write("|-----|-----------|-------------|\n")
            for result in sorted_results[:5]:
                f.write(f"| {result['id']} | {result['task_type']} | {result.get('latency_ms', 0):.2f} |\n")
            f.write("\n")
            
            # Sample Outputs
            f.write("## Sample Outputs\n\n")
            f.write("Representative examples from each test category:\n\n")
            
            # Group by prompt_type and show 2 samples per type
            samples_by_type = {}
            for result in self.results:
                if result.get('success'):
                    ptype = result.get('prompt_type', 'unknown')
                    if ptype not in samples_by_type:
                        samples_by_type[ptype] = []
                    if len(samples_by_type[ptype]) < 2:
                        samples_by_type[ptype].append(result)
            
            for prompt_type, samples in sorted(samples_by_type.items()):
                f.write(f"### {prompt_type.replace('_', ' ').title()}\n\n")
                for sample in samples:
                    f.write(f"**Test**: {sample['id']} ({sample['task_type']})\n\n")
                    
                    # Get response text
                    response = sample.get('response', '')
                    if isinstance(response, dict):
                        response_text = str(response)
                    else:
                        response_text = str(response)
                    
                    # Truncate if too long
                    if len(response_text) > 400:
                        response_text = response_text[:397] + "..."
                    
                    f.write(f"**Response**: {response_text}\n\n")
                    f.write(f"**Quality**: {sample.get('quality_score', 0)}/100 ")
                    f.write(f"(Completeness: {sample.get('completeness_score', 0)}, ")
                    f.write(f"Format: {sample.get('format_score', 0)}, ")
                    f.write(f"Factuality: {sample.get('factuality_score', 0)}, ")
                    f.write(f"Coherence: {sample.get('coherence_score', 0)})\n\n")
                    f.write(f"**Latency**: {sample.get('latency_ms', 0):.2f}ms | **Tokens**: {sample.get('tokens_used', 0)}\n\n")
                    f.write("---\n\n")
        
        print(f"Markdown report written: {filepath}")
    
    def _calculate_statistics(self) -> Dict[str, Any]:
        """Calculate summary statistics from results"""
        total = len(self.results)
        successful = sum(1 for r in self.results if r.get('success', False))
        failed = total - successful
        
        latencies = [r.get('latency_ms', 0) for r in self.results if r.get('success', False)]
        tokens = [r.get('tokens_used', 0) for r in self.results]
        
        # Quality scores (only from successful results)
        quality_scores = [r.get('quality_score', 0) for r in self.results if r.get('success', False)]
        completeness_scores = [r.get('completeness_score', 0) for r in self.results if r.get('success', False)]
        format_scores = [r.get('format_score', 0) for r in self.results if r.get('success', False)]
        factuality_scores = [r.get('factuality_score', 0) for r in self.results if r.get('success', False)]
        coherence_scores = [r.get('coherence_score', 0) for r in self.results if r.get('success', False)]
        
        # Calculate percentiles
        latencies_sorted = sorted(latencies) if latencies else [0]
        p50_idx = len(latencies_sorted) // 2
        p95_idx = int(len(latencies_sorted) * 0.95)
        
        stats = {
            'total': total,
            'successful': successful,
            'failed': failed,
            'success_rate': (successful / total * 100) if total > 0 else 0,
            'avg_latency': sum(latencies) / len(latencies) if latencies else 0,
            'p50_latency': latencies_sorted[p50_idx] if latencies_sorted else 0,
            'p95_latency': latencies_sorted[p95_idx] if latencies_sorted else 0,
            'total_tokens': sum(tokens),
            'avg_tokens': sum(tokens) / len(tokens) if tokens else 0,
            'avg_quality': sum(quality_scores) / len(quality_scores) if quality_scores else 0,
            'avg_completeness': sum(completeness_scores) / len(completeness_scores) if completeness_scores else 0,
            'avg_format': sum(format_scores) / len(format_scores) if format_scores else 0,
            'avg_factuality': sum(factuality_scores) / len(factuality_scores) if factuality_scores else 0,
            'avg_coherence': sum(coherence_scores) / len(coherence_scores) if coherence_scores else 0,
            'by_type': {}
        }
        
        # Calculate stats by prompt type
        for prompt_type in ['resume_analysis', 'interview_qa', 'multi_turn']:
            type_results = [r for r in self.results if r.get('prompt_type') == prompt_type]
            if not type_results:
                continue
            
            type_successful = sum(1 for r in type_results if r.get('success', False))
            type_latencies = [r.get('latency_ms', 0) for r in type_results if r.get('success', False)]
            type_tokens = [r.get('tokens_used', 0) for r in type_results]
            
            type_latencies_sorted = sorted(type_latencies) if type_latencies else [0]
            type_p95_idx = int(len(type_latencies_sorted) * 0.95)
            
            stats['by_type'][prompt_type] = {
                'count': len(type_results),
                'success_rate': (type_successful / len(type_results) * 100) if type_results else 0,
                'avg_latency': sum(type_latencies) / len(type_latencies) if type_latencies else 0,
                'p95_latency': type_latencies_sorted[type_p95_idx] if type_latencies_sorted else 0,
                'avg_tokens': sum(type_tokens) / len(type_tokens) if type_tokens else 0
            }
        
        return stats


def main():
    parser = argparse.ArgumentParser(description='Run AI interview evaluation')
    parser.add_argument('--backend', default='http://localhost:8080', 
                        help='Backend API base URL')
    parser.add_argument('--output', default='results', 
                        help='Output directory for results')
    parser.add_argument('--prompts-dir', default='prompts',
                        help='Directory containing JSONL prompt files')
    parser.add_argument('--username', default='testuser',
                        help='Username for authentication')
    parser.add_argument('--password', default='password',
                        help='Password for authentication')
    parser.add_argument('--model-type', default='backend', choices=['backend', 'local'],
                        help='Model type: "backend" (via backend API + OpenAI) or "local" (direct OSS model)')
    parser.add_argument('--local-model-url', default='http://localhost:11434/api/generate',
                        help='URL for local OSS model (e.g., Ollama at http://localhost:11434/api/generate)')
    parser.add_argument('--local-model-name', default='llama2',
                        help='Name of local model to use (e.g., llama2, mistral, codellama)')
    
    args = parser.parse_args()
    
    runner = EvalRunner(args.backend, args.output, args.username, args.password, 
                       args.model_type, args.local_model_url, args.local_model_name)
    
    # Load and evaluate each prompt type
    prompt_files = {
        'resume_analysis': 'resume_analysis.jsonl',
        'interview_qa': 'interview_qa.jsonl',
        'multi_turn': 'multi_turn.jsonl'
    }
    
    for prompt_type, filename in prompt_files.items():
        filepath = os.path.normpath(os.path.join(args.prompts_dir, filename))
        if os.path.exists(filepath):
            prompts = runner.load_prompts(filepath)
            runner.evaluate_prompts(prompts, prompt_type)
        else:
            print(f"Warning: {filepath} not found, skipping {prompt_type}")
    
    # Generate reports
    runner.generate_report()


if __name__ == '__main__':
    main()
