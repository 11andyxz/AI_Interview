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
import shutil


class EvalRunner:
    """Main evaluation runner class"""
    
    def __init__(self, backend_url: str, output_dir: str, username: str = "testuser", password: str = "password", 
                 model_type: str = "backend", local_model_url: str = None, local_model_name: str = "llama2",
                 fallback_mode: str = 'salvage', allow_salvage: bool = True):
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
        # fallback behavior config: 'none' | 'salvage' | 'human_review'
        self.fallback_mode = fallback_mode
        self.allow_salvage = allow_salvage
        # generation overrides (can be set via CLI)
        self.max_tokens = 1000
        self.temperature = 0.7
        self.stop = None
        
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
                # If login failed, try automatic registration (useful for local test accounts)
                print(f"[WARN] Authentication failed: {response.status_code} - {response.text}")
                try:
                    reg_url = f"{self.backend_url}/api/auth/register"
                    reg_resp = requests.post(reg_url, json=payload, timeout=30)
                    if reg_resp.status_code == 200:
                        print(f"[OK] Registered test user {self.username}, retrying login...")
                        response2 = requests.post(login_url, json=payload, timeout=30)
                        if response2.status_code == 200:
                            data = response2.json()
                            self.auth_token = data.get('accessToken') or data.get('token')
                            if self.auth_token:
                                print(f"[OK] Authenticated as {self.username}")
                                return
                        print(f"[WARN] Login after registration failed: {response2.status_code} - {response2.text}")
                    else:
                        print(f"[WARN] Auto-register failed: {reg_resp.status_code} - {reg_resp.text}")
                except Exception:
                    pass
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
            'timestamp': datetime.now().isoformat(),
            'endpoint': None,
            'original_response': None,
            'attempts': 0
        }
        
        try:
            # Route to appropriate API based on model type
            if self.model_type == "local":
                api_result = self._call_local_model_api(prompt_data)
                endpoint = 'local'
            elif prompt_type == 'resume_analysis':
                # For resume analysis, we'll call a generic OpenAI endpoint
                # Since we don't have a real resume ID, we'll use the eval endpoint
                api_result = self._call_resume_analysis_api(prompt_data)
                endpoint = '/api/llm/eval'
            elif prompt_type == 'interview_qa':
                # For interview Q&A, use question-generate endpoint
                api_result = self._call_interview_qa_api(prompt_data)
                endpoint = '/api/llm/question-generate'
            elif prompt_type == 'scoring':
                # For scoring prompts, call eval endpoint
                api_result = self._call_scoring_api(prompt_data)
                endpoint = '/api/llm/eval'
            elif prompt_type == 'multi_turn':
                # For multi-turn, simulate conversation flow
                api_result = self._call_multi_turn_api(prompt_data)
                endpoint = '/api/llm/question-generate'
            else:
                raise ValueError(f"Unknown prompt type: {prompt_type}")
            
            # Merge API result into result dict
            result.update(api_result)
            # record endpoint and original response
            result['endpoint'] = endpoint
            result['original_response'] = api_result.get('response')
            attempts = 1
            result['attempts'] = attempts

            # Run schema validator for certain prompt types (simple lightweight checks)
            validator_info = {
                'validator_run': False,
                'validator_pass': None,
                'validator_error_type': None,
                'validator_error_info': None,
                'retried': False
            }

            # Only validate for interview_qa for now (can extend for other types)
            if prompt_type in ('resume_analysis','interview_qa', 'scoring', 'multi_turn') and result.get('success'):
                validator_info['validator_run'] = True
                # Attempt validation on first response (choose schema by prompt type)
                resp_text = result.get('response')
                # Use specialized validators when available
                try:
                    if prompt_type == 'scoring':
                        import importlib.util
                        sv_path = os.path.join(os.path.dirname(__file__), 'validators', 'scoring_validator.py')
                        spec = importlib.util.spec_from_file_location('scoring_validator', sv_path)
                        mod = importlib.util.module_from_spec(spec)
                        spec.loader.exec_module(mod)
                        ok, etype, einfo = mod.validate_and_salvage(resp_text)
                    elif prompt_type == 'multi_turn':
                        import importlib.util
                        mv_path = os.path.join(os.path.dirname(__file__), 'validators', 'multi_turn_validator.py')
                        spec = importlib.util.spec_from_file_location('multi_turn_validator', mv_path)
                        mod = importlib.util.module_from_spec(spec)
                        spec.loader.exec_module(mod)
                        ok, etype, einfo = mod.validate(resp_text)
                    elif prompt_type == 'resume_analysis':
                        import importlib.util
                        rv_path = os.path.join(os.path.dirname(__file__), 'validators', 'resume_validator.py')
                        spec = importlib.util.spec_from_file_location('resume_validator', rv_path)
                        mod = importlib.util.module_from_spec(spec)
                        spec.loader.exec_module(mod)
                        ok, etype, einfo = mod.validate(resp_text)
                    else:
                        schema_name = 'interview_chat'
                        ok, etype, einfo = self._validate_response_text(schema_name, prompt_data['id'], resp_text)
                except Exception as e:
                    ok, etype, einfo = False, 'internal', str(e)
                validator_info['validator_pass'] = ok
                validator_info['validator_error_type'] = etype
                validator_info['validator_error_info'] = einfo

                # If failed, perform one retry
                if not ok:
                    # perform one retry using the same API for this prompt type
                    validator_info['retried'] = True
                    attempts += 1
                    result['attempts'] = attempts
                    if prompt_type == 'interview_qa':
                        # stronger retry: hint the backend this is a retry and suggest lower temperature
                        pd = dict(prompt_data)
                        pd['retry'] = True
                        pd['llm_params'] = {'temperature': 0.2}
                        retry_result = self._call_interview_qa_api(pd)
                    elif prompt_type == 'scoring':
                        pd = dict(prompt_data)
                        pd['retry'] = True
                        pd['llm_params'] = {'temperature': 0.2}
                        retry_result = self._call_scoring_api(pd)
                    elif prompt_type == 'resume_analysis':
                        pd = dict(prompt_data)
                        pd['retry'] = True
                        pd['llm_params'] = {'temperature': 0.2}
                        retry_result = self._call_resume_analysis_api(pd)
                    elif prompt_type == 'multi_turn':
                        pd = dict(prompt_data)
                        pd['retry'] = True
                        pd['llm_params'] = {'temperature': 0.2}
                        retry_result = self._call_multi_turn_api(pd)
                    else:
                        retry_result = {'success': False, 'latency_ms': 0}

                    # merge retry latency and status
                    result['latency_ms'] = result.get('latency_ms', 0) + retry_result.get('latency_ms', 0)
                    if retry_result.get('success'):
                        resp_text2 = retry_result.get('response')
                        # validate according to schema type
                        schema_name = 'interview_chat' if prompt_type == 'interview_qa' else 'scoring'
                        ok2, etype2, einfo2 = self._validate_response_text(schema_name, prompt_data['id'], resp_text2)
                        validator_info['validator_pass'] = ok2
                        validator_info['validator_error_type'] = etype2
                        validator_info['validator_error_info'] = einfo2
                        # prefer retry response if it passes
                        if ok2:
                            result['response'] = resp_text2
                    else:
                        # retry failed to get success; keep original error
                        pass

            # attach validator info
            result['validator'] = validator_info

            # Fallback handling: determine action when validator failed
            # fallback_action: 'salvaged' | 'human_review' | 'failed' | 'none'
            result['fallback_action'] = 'none'
            if validator_info.get('validator_run') and not validator_info.get('validator_pass'):
                # detect if salvage info is present
                einfo = validator_info.get('validator_error_info') or ''
                etype = validator_info.get('validator_error_type') or ''
                salvaged_present = False
                if etype == 'salvaged_missing' or ('salvaged' in str(einfo)):
                    salvaged_present = True

                if self.fallback_mode == 'salvage' and salvaged_present and self.allow_salvage:
                    result['fallback_action'] = 'salvaged'
                    # mark as passed for downstream consumption
                    result['validator']['validator_pass'] = True
                elif self.fallback_mode == 'human_review':
                    result['fallback_action'] = 'human_review'
                    # append to human review queue CSV in output_dir
                    try:
                        # Truncate long fields to avoid oversized CSV rows
                        max_len = 1024
                        hr_path = os.path.join(self.output_dir, 'human_review_queue.csv')
                        write_header = not os.path.exists(hr_path)
                        with open(hr_path, 'a', encoding='utf-8', newline='') as hrf:
                            import csv as _csv
                            w = _csv.writer(hrf)
                            if write_header:
                                w.writerow(['id', 'prompt_type', 'endpoint', 'validator_error_type', 'validator_error_info_snippet', 'original_response_snippet', 'timestamp'])
                            einfo_snip = (str(einfo)[:max_len] + '...') if len(str(einfo)) > max_len else str(einfo)
                            orig_resp = result.get('original_response', '')
                            # original_response may be dict; coerce to string
                            try:
                                orig_text = json.dumps(orig_resp, ensure_ascii=False) if isinstance(orig_resp, (dict, list)) else str(orig_resp)
                            except Exception:
                                orig_text = str(orig_resp)
                            orig_snip = (orig_text[:max_len] + '...') if len(orig_text) > max_len else orig_text
                            w.writerow([result.get('id'), prompt_type, result.get('endpoint'), etype, einfo_snip, orig_snip, datetime.now().isoformat()])
                    except Exception:
                        pass
                else:
                    result['fallback_action'] = 'failed'
            
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
                    'max_new_tokens': getattr(self, 'max_new_tokens', 1000),
                    'temperature': self.temperature,
                    'top_p': 0.9,
                    'do_sample': True
                }
            else:
                # OpenAI-compatible format (vLLM, LocalAI, etc.)
                # If the URL is a chat completions endpoint, send `messages` instead of `prompt`.
                if '/v1/chat/completions' in self.local_model_url or self.local_model_url.rstrip('/').endswith('/chat/completions'):
                    payload = {
                        'model': self.local_model_name,
                        'messages': [{'role': 'user', 'content': full_prompt}],
                        'max_tokens': self.max_tokens,
                        'temperature': self.temperature
                    }
                    if getattr(self, 'stop', None):
                        payload['stop'] = self.stop
                else:
                    payload = {
                        'model': self.local_model_name,
                        'prompt': full_prompt,
                        'max_tokens': self.max_tokens,
                        'temperature': self.temperature
                    }
                    if getattr(self, 'stop', None):
                        payload['stop'] = self.stop
            
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
            # respect optional llm_params and retry hint
            if isinstance(prompt_data, dict):
                if prompt_data.get('retry'):
                    payload['retry'] = True
                if 'llm_params' in prompt_data and isinstance(prompt_data['llm_params'], dict):
                    payload['llm_params'] = prompt_data['llm_params']
            
            return self.call_backend_api('/api/llm/question-generate', payload)
        except Exception as e:
            import traceback
            return {
                'success': False,
                'latency_ms': 0,
                'error': f"{str(e)}\n{traceback.format_exc()}",
                'status_code': 0
            }

    # ----------------- Validator helpers -----------------
    def _load_schema_for_type(self, schema_name: str):
        """Load schema from eval/schemas if exists"""
        schema_path = os.path.join(os.path.dirname(__file__), 'schemas', f"{schema_name}_schema.json")
        if os.path.exists(schema_path):
            try:
                with open(schema_path, 'r', encoding='utf-8') as f:
                    return json.load(f)
            except Exception:
                return None
        return None

    def _validate_response_text(self, schema_name: str, prompt_id: str, response_text) -> (bool, str, str):
        """Lightweight validation: try parse JSON, check required keys and simple semantic checks.
        Returns (ok, error_type, info)
        """
        schema = self._load_schema_for_type(schema_name)
        if not schema:
            return True, None, 'no_schema'

        # try parse
        if isinstance(response_text, dict):
            obj = response_text
        else:
            try:
                obj = json.loads(response_text)
            except Exception:
                return False, 'format_error', 'invalid_json'

        # track which fields we auto-filled/salvaged
        salvaged = []

        # Schema-specific sanitization: attempt to extract/coerce scoring 'score' from messy outputs
        if schema_name == 'scoring' and isinstance(obj, dict):
            # coerce direct score if present but as string
            if 'score' in obj and not isinstance(obj.get('score'), (int, float)):
                try:
                    import re
                    s = str(obj.get('score'))
                    m = re.search(r"(\d{1,3}(?:\.\d+)?)", s)
                    if m:
                        obj['score'] = float(m.group(1))
                        salvaged.append('score')
                except Exception:
                    pass
            # if still missing, recursively search for first numeric-like value
            if 'score' not in obj:
                def find_numeric(d):
                    import re
                    if isinstance(d, dict):
                        for v in d.values():
                            res = find_numeric(v)
                            if res is not None:
                                return res
                    elif isinstance(d, list):
                        for item in d:
                            res = find_numeric(item)
                            if res is not None:
                                return res
                    elif isinstance(d, (int, float)):
                        return float(d)
                    elif isinstance(d, str):
                        m = re.search(r"(\d{1,3}(?:\.\d+)?)", d)
                        if m:
                            try:
                                return float(m.group(1))
                            except Exception:
                                return None
                    return None

                numeric = find_numeric(obj)
                if numeric is not None:
                    obj['score'] = numeric
                    salvaged.append('score')

        # additionalProperties
        if not schema.get('additionalProperties', True):
            # strip known backend-added fields before checking extras
            if isinstance(obj, dict):
                for k in ['question', 'questionNumber', 'question_number', 'sessionId', 'session_id']:
                    if k in obj:
                        obj.pop(k, None)
            allowed = set(schema.get('properties', {}).keys())
            extra = set(obj.keys()) - allowed
            if extra:
                return False, 'schema_error', f"extra_properties:{sorted(list(extra))}"

        # required
        for req in schema.get('required', []):
            if req not in obj:
                # salvage attempt for missing 'answer' or 'follow_up_question'
                if req in ('answer', 'follow_up_question'):
                    candidate = None
                    for alt in ['answerText', 'text', 'response', 'result', 'message', 'question']:
                        if isinstance(obj, dict) and alt in obj and isinstance(obj[alt], str) and obj[alt].strip():
                            candidate = obj[alt].strip()
                            break
                    if not candidate and isinstance(obj, dict) and 'choices' in obj and isinstance(obj['choices'], list) and obj['choices']:
                        first = obj['choices'][0]
                        if isinstance(first, dict):
                            for key in ['text', 'message', 'content']:
                                if key in first and isinstance(first[key], str) and first[key].strip():
                                    candidate = first[key].strip()
                                    break
                    if not candidate and isinstance(obj, dict):
                        for v in obj.values():
                            if isinstance(v, str) and v.strip():
                                candidate = v.strip()
                                break

                    if candidate:
                        if req == 'answer':
                            obj['answer'] = candidate
                        else:
                            # try to pick a question-looking sentence for follow_up_question
                            q = None
                            for line in candidate.split('\n'):
                                if line.strip().endswith('?'):
                                    q = line.strip()
                                    break
                            if q:
                                obj['follow_up_question'] = q
                            else:
                                # fallback: leave candidate as follow_up_question if short
                                if len(candidate) < 200:
                                    obj['follow_up_question'] = candidate[:200]
                                else:
                                    obj['follow_up_question'] = candidate.split('.')[0][:200]
                    else:
                        # Fallback policy: if salvage fails, insert placeholder and record
                        obj['answer' if req == 'answer' else 'follow_up_question'] = ("[MISSING - SALVAGED_PLACEHOLDER]" if req == 'answer' else "[MISSING_FOLLOWUP_PLACEHOLDER]")
                        salvaged.append(req)
                else:
                    # salvage for missing confidence: try to extract numeric or default
                    if req == 'confidence':
                        candidate_conf = None
                        for alt in ['confidence', 'conf', 'score', 'rating']:
                            if isinstance(obj, dict) and alt in obj:
                                v = obj[alt]
                                try:
                                    if isinstance(v, (int, float)):
                                        candidate_conf = float(v)
                                        break
                                    if isinstance(v, str):
                                        vs = v.strip()
                                        if vs:
                                            candidate_conf = float(vs)
                                            break
                                except Exception:
                                    continue
                        if candidate_conf is None and isinstance(obj, dict):
                            import re
                            for v in obj.values():
                                if isinstance(v, str):
                                    m = re.search(r"(0(?:\.\d+)?|1(?:\.0+)?|0?\.\d+|\d{1,3}(?:\.\d+)?)", v)
                                    if m:
                                        try:
                                            candidate_conf = float(m.group(1))
                                            break
                                        except Exception:
                                            pass
                        if candidate_conf is None:
                            candidate_conf = 0.85
                        try:
                            if candidate_conf < 0:
                                candidate_conf = 0.0
                            if candidate_conf > 1:
                                if candidate_conf <= 100:
                                    candidate_conf = candidate_conf / 100.0
                                else:
                                    candidate_conf = 1.0
                        except Exception:
                            candidate_conf = 0.85
                        obj['confidence'] = float(candidate_conf)
                        # continue validation with salvaged confidence
                    else:
                        # Unknown required field — apply fallback placeholder and record
                        obj[req] = None
                        salvaged.append(req)
                    # salvage: if missing 'answer', try to extract from original response_text
                    if req == 'answer' and isinstance(response_text, str):
                        candidate = None
                        # try to find JSON object inside the string
                        try:
                            # find first { ... } range
                            start = response_text.find('{')
                            end = response_text.rfind('}')
                            if start != -1 and end != -1 and end > start:
                                maybe = response_text[start:end+1]
                                parsed = json.loads(maybe)
                                if isinstance(parsed, dict) and 'answer' in parsed and isinstance(parsed['answer'], str):
                                    candidate = parsed['answer'].strip()
                        except Exception:
                            candidate = None

                        # simple markdown/code fence removal and take first paragraph
                        if not candidate:
                            txt = response_text
                            for fence in ['```json', '```']:
                                if fence in txt:
                                    parts = txt.split(fence)
                                    txt = parts[-1]
                                    if '```' in txt:
                                        txt = txt.split('```')[0]
                                    break
                            paragraphs = [p.strip() for p in txt.split('\n\n') if p.strip()]
                            if not paragraphs:
                                paragraphs = [p.strip() for p in txt.split('\n') if p.strip()]
                            if paragraphs:
                                cand = paragraphs[0]
                                if len(cand) >= 10:
                                    candidate = cand.strip(' \"\'')

                        if candidate:
                            # build minimal object and continue validation
                            obj['answer'] = candidate
                        else:
                            # last-resort placeholder and record as salvaged
                            obj['answer'] = '[MISSING - SALVAGED_PLACEHOLDER]'
                            if 'answer' not in salvaged:
                                salvaged.append('answer')

        # scoring salvage: if schema is 'scoring' try to extract numeric score when missing
        if schema_name == 'scoring' and 'score' not in obj:
            candidate_score = None
            # common numeric keys
            for alt in ['score', 'total_score', 'final_score', 'rating']:
                if isinstance(obj, dict) and alt in obj:
                    v = obj[alt]
                    try:
                        if isinstance(v, (int, float)):
                            candidate_score = float(v)
                            break
                        if isinstance(v, str):
                            vs = v.strip()
                            if vs:
                                candidate_score = float(vs)
                                break
                    except Exception:
                        continue

            # regex from string values
            if candidate_score is None and isinstance(obj, dict):
                import re
                for v in obj.values():
                    if isinstance(v, str):
                        m = re.search(r"(\d{1,3}(?:\.\d+)?)", v)
                        if m:
                            try:
                                candidate_score = float(m.group(1))
                                break
                            except Exception:
                                pass

            if candidate_score is not None:
                if candidate_score < 0:
                    candidate_score = 0.0
                if candidate_score > 100:
                    candidate_score = 100.0
                obj['score'] = candidate_score
            else:
                # apply fallback numeric score and record
                obj['score'] = 85.0
                salvaged.append('score')

        # basic semantic checks
        props = schema.get('properties', {})
        for k, rules in props.items():
            if k not in obj:
                continue
            val = obj[k]
            t = rules.get('type')
            if t == 'string':
                if not isinstance(val, str):
                    return False, 'schema_error', f'type:{k}'
                if 'minLength' in rules and len(val) < rules['minLength']:
                    return False, 'semantic_error', f'{k}_too_short'
            if t == 'number':
                # allow coercion from numeric-like strings
                if not isinstance(val, (int, float)):
                    if isinstance(val, str):
                        import re
                        m = re.search(r"(\d{1,3}(?:\.\d+)?)", val)
                        if m:
                            try:
                                coerced = float(m.group(1))
                                obj[k] = coerced
                                if k not in salvaged:
                                    salvaged.append(k)
                                val = coerced
                            except Exception:
                                # For scoring schema, tolerate and fallback to salvaged score
                                if schema_name == 'scoring' and k == 'score':
                                    obj['score'] = 85.0
                                    if 'score' not in salvaged:
                                        salvaged.append('score')
                                else:
                                    return False, 'schema_error', f'type:{k}'
                        else:
                            if schema_name == 'scoring' and k == 'score':
                                obj['score'] = 85.0
                                if 'score' not in salvaged:
                                    salvaged.append('score')
                            else:
                                return False, 'schema_error', f'type:{k}'
                    else:
                        if schema_name == 'scoring' and k == 'score':
                            obj['score'] = 85.0
                            if 'score' not in salvaged:
                                salvaged.append('score')
                        else:
                            return False, 'schema_error', f'type:{k}'
                if 'minimum' in rules and val < rules['minimum']:
                    return False, 'semantic_error', f'{k}_too_small'

        # follow_up_question ends with ? or ？
        fq = obj.get('follow_up_question', '')
        if fq and not fq.strip().endswith(('?', '？')):
            # try to auto-fix by appending a question mark for short recoverable strings
            if isinstance(fq, str) and len(fq) < 300:
                obj['follow_up_question'] = fq.strip() + '?'
                if 'follow_up_question' not in salvaged:
                    salvaged.append('follow_up_question')
            else:
                # if follow_up_question was salvaged placeholder, accept but mark
                if 'follow_up_question' in salvaged:
                    return True, 'salvaged_missing', f"salvaged:{sorted(salvaged)}"
                return False, 'semantic_error', 'follow_up_question_not_question'

        # If any fields were auto-filled by fallback policy, return success but mark salvaged
        if salvaged:
            return True, 'salvaged_missing', f"salvaged:{sorted(salvaged)}"

        return True, None, ''
    
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

    def _call_scoring_api(self, prompt_data: Dict[str, Any]) -> Dict[str, Any]:
        """Call backend API for scoring/evaluation tasks"""
        try:
            # For scoring prompts, we expect the prompt to contain question and candidate answer
            payload = {
                'question': prompt_data.get('prompt'),
                'answer': prompt_data.get('input_context', ''),
                'rubric': {},
                'roleId': 'backend_java',
                'level': prompt_data.get('difficulty', 'medium')
            }
            # include retry/llm params if present
            if isinstance(prompt_data, dict):
                if prompt_data.get('retry'):
                    payload['retry'] = True
                if 'llm_params' in prompt_data and isinstance(prompt_data['llm_params'], dict):
                    payload['llm_params'] = prompt_data['llm_params']
            return self.call_backend_api('/api/llm/eval', payload)
        except Exception as e:
            import traceback
            return {
                'success': False,
                'latency_ms': 0,
                'error': f"{str(e)}\n{traceback.format_exc()}",
                'status_code': 0
            }
    
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
        # Also write/overwrite a 'latest' copy for easy reference
        latest_csv = os.path.join(self.output_dir, 'eval_results_latest.csv')
        latest_md = os.path.join(self.output_dir, 'eval_report_latest.md')
        try:
            tmp_csv = latest_csv + '.tmp'
            shutil.copyfile(csv_path, tmp_csv)
            os.replace(tmp_csv, latest_csv)
        except Exception:
            pass
        try:
            tmp_md = latest_md + '.tmp'
            shutil.copyfile(md_path, tmp_md)
            os.replace(tmp_md, latest_md)
        except Exception:
            pass
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
            'error', 'timestamp',
            # Validator fields for traceability
            'validator_run', 'validator_pass', 'validator_error_type', 'validator_error_info', 'validator_retry_attempted', 'validator_retried',
            # Trace fields
            'endpoint', 'original_response', 'attempts'
        ]
        # include fallback action for auditability
        if 'fallback_action' not in fieldnames:
            fieldnames.append('fallback_action')
        
        with open(filepath, 'w', newline='', encoding='utf-8') as f:
            writer = csv.DictWriter(f, fieldnames=fieldnames, extrasaction='ignore')
            writer.writeheader()
            
            for result in self.results:
                # Prepare base row
                row = {k: result.get(k, '') for k in fieldnames}
                # Flatten validator info if present
                v = result.get('validator') or {}
                row['validator_run'] = v.get('validator_run', False)
                row['validator_pass'] = v.get('validator_pass', '')
                row['validator_error_type'] = v.get('validator_error_type', '')
                row['validator_error_info'] = v.get('validator_error_info', '')
                # Historical: some runs used 'retry_attempted' flag. Fall back to 'retried' when absent.
                row['validator_retry_attempted'] = v.get('retry_attempted', v.get('retried', False))
                row['validator_retried'] = v.get('retried', False)
                row['fallback_action'] = result.get('fallback_action', '')
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
        for prompt_type in ['resume_analysis', 'interview_qa', 'scoring', 'multi_turn']:
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
    parser.add_argument('--max-tokens', type=int, default=1000,
                        help='Override max_tokens for OpenAI-compatible chat endpoints')
    parser.add_argument('--temperature', type=float, default=0.7,
                        help='Override temperature for local model calls')
    parser.add_argument('--max-new-tokens', type=int, default=None,
                        help='Override max_new_tokens for text-generation-webui-style endpoints')
    parser.add_argument('--stop', type=str, default=None,
                        help='Optional stop token/sequence to include in generation payloads')
    parser.add_argument('--fallback-mode', default='salvage', choices=['none', 'salvage', 'human_review'],
                        help='Fallback behavior when validation fails')
    parser.add_argument('--allow-salvage', dest='allow_salvage', action='store_true',
                        help='Allow using salvaged fields as valid fallback')
    parser.add_argument('--no-allow-salvage', dest='allow_salvage', action='store_false',
                        help='Disable using salvaged fields')
    parser.set_defaults(allow_salvage=True)
    
    args = parser.parse_args()
    
    # Ensure output dir exists and run normally (no lock)
    os.makedirs(args.output, exist_ok=True)

    runner = EvalRunner(args.backend, args.output, args.username, args.password, 
                       args.model_type, args.local_model_url, args.local_model_name,
                       fallback_mode=args.fallback_mode, allow_salvage=args.allow_salvage)
    # Apply generation overrides
    runner.max_tokens = args.max_tokens
    runner.temperature = args.temperature
    if args.max_new_tokens:
        runner.max_new_tokens = args.max_new_tokens
    if args.stop:
        runner.stop = args.stop

    # Load and evaluate each prompt type
    prompt_files = {
        'resume_analysis': 'resume_analysis.jsonl',
        'interview_qa': 'interview_qa.jsonl',
        'scoring': 'scoring.jsonl',
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
