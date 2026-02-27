#!/usr/bin/env python3
"""
Advanced evaluation utilities for A/B testing (MVP).

Features (MVP):
- Compute BLEU/ROUGE (reference-based) using `nltk` / `rouge_score` if installed
- Compute SBERT cosine similarity (requires `sentence-transformers`)
- Produce a CSV with per-example ML metrics to feed into model comparison

Note: This script is a utility; install optional dependencies:
pip install nltk rouge-score sentence-transformers numpy scipy

Usage:
python eval/advanced_eval.py --references refs.jsonl --hypotheses hyps.jsonl --output ml_metrics.csv
"""

import argparse
import csv
import json
import math
import re
from typing import List, Dict

try:
    from sentence_transformers import SentenceTransformer, util
    SBERT_AVAILABLE = True
except Exception:
    SBERT_AVAILABLE = False

try:
    from rouge_score import rouge_scorer
    ROUGE_AVAILABLE = True
except Exception:
    ROUGE_AVAILABLE = False

try:
    import nltk
    from nltk.translate.bleu_score import sentence_bleu
    NLTK_AVAILABLE = True
except Exception:
    NLTK_AVAILABLE = False


def compute_bleu(ref: str, hyp: str) -> float:
    if not NLTK_AVAILABLE:
        return 0.0
    try:
        ref_tokens = ref.split()
        hyp_tokens = hyp.split()
        score = sentence_bleu([ref_tokens], hyp_tokens, weights=(0.5, 0.5))
        return float(score)
    except Exception:
        return 0.0


def compute_perplexity(text: str) -> float:
    """Simplified perplexity based on word frequency (mock implementation)"""
    if not text or len(text.strip()) == 0:
        return float('inf')
    words = text.lower().split()
    if len(words) == 0:
        return float('inf')
    # Mock perplexity: inverse of average word length (simpler proxy)
    avg_word_len = sum(len(w) for w in words) / len(words)
    return max(1.0, 20.0 / avg_word_len)


def analyze_interview_rubric(text: str) -> Dict[str, float]:
    """Mock interview-specific rubric scoring"""
    text_lower = text.lower()
    
    # Technical depth: presence of technical keywords
    tech_keywords = ['algorithm', 'complexity', 'data structure', 'optimization', 'implementation', 'design pattern', 'architecture']
    tech_score = min(1.0, sum(1 for kw in tech_keywords if kw in text_lower) / 3.0)
    
    # Clarity: sentence structure and length
    sentences = text.split('.')
    avg_sentence_len = sum(len(s.split()) for s in sentences) / max(1, len(sentences))
    clarity_score = max(0.0, min(1.0, 1.0 - abs(avg_sentence_len - 15) / 15))
    
    # Relevance: question-answer alignment (mock based on text length)
    relevance_score = min(1.0, max(0.3, len(text.split()) / 100.0))
    
    return {
        'technical_depth': tech_score,
        'clarity': clarity_score,
        'relevance': relevance_score
    }


def detect_failure_modes(text: str) -> Dict[str, bool]:
    """Detect common failure modes in generated text"""
    if not text or len(text.strip()) == 0:
        return {'malformed_output': True, 'off_topic': True, 'timeout': False}
    
    text_lower = text.lower()
    
    # Malformed output: check for JSON fragments, incomplete sentences
    malformed = bool(re.search(r'[\{\}\[\]]', text)) or text.endswith(('...', 'incomplete', 'error'))
    
    # Off-topic: very short responses or contains error messages
    off_topic = len(text.split()) < 5 or any(phrase in text_lower for phrase in ['sorry', 'i cannot', 'not available', 'error occurred'])
    
    # Timeout would be detected at request level, not text level
    timeout = False
    
    return {
        'malformed_output': malformed,
        'off_topic': off_topic, 
        'timeout': timeout
    }


def compute_rouge(ref: str, hyp: str):
    if not ROUGE_AVAILABLE:
        return {'rouge1': 0.0, 'rougeL': 0.0}
    try:
        scorer = rouge_scorer.RougeScorer(['rouge1', 'rougeL'], use_stemmer=True)
        sc = scorer.score(ref, hyp)
        return {'rouge1': sc['rouge1'].fmeasure, 'rougeL': sc['rougeL'].fmeasure}
    except Exception:
        return {'rouge1': 0.0, 'rougeL': 0.0}


def compute_sbert(refs: List[str], hyp: str, model=None) -> float:
    if not SBERT_AVAILABLE:
        return 0.0
    try:
        if model is None:
            model = SentenceTransformer('all-MiniLM-L6-v2')
        emb_h = model.encode(hyp, convert_to_tensor=True)
        emb_refs = model.encode(refs, convert_to_tensor=True)
        sims = util.cos_sim(emb_h, emb_refs)
        # take max similarity across references
        max_sim = float(sims.max())
        return max_sim
    except Exception:
        return 0.0


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--references', required=True, help='JSONL file with reference texts per id')
    parser.add_argument('--hypotheses', required=True, help='JSONL file with hypothesis texts per id')
    parser.add_argument('--output', required=True, help='CSV output path')
    args = parser.parse_args()

    refs = {}
    with open(args.references, 'r', encoding='utf-8') as f:
        for line in f:
            if not line.strip():
                continue
            j = json.loads(line)
            refs[j['id']] = j.get('reference', '')

    hyps = {}
    with open(args.hypotheses, 'r', encoding='utf-8') as f:
        for line in f:
            if not line.strip():
                continue
            j = json.loads(line)
            hyps[j['id']] = j.get('hypothesis', '')

    model = None
    if SBERT_AVAILABLE:
        try:
            model = SentenceTransformer('all-MiniLM-L6-v2')
        except Exception:
            model = None

    with open(args.output, 'w', newline='', encoding='utf-8') as out:
        fieldnames = ['id','bleu','rouge1','rougeL','sbert_sim','perplexity','technical_depth','clarity','relevance','token_efficiency','malformed_output','off_topic','timeout']
        writer = csv.DictWriter(out, fieldnames=fieldnames)
        writer.writeheader()
        for _id, hyp in hyps.items():
            ref = refs.get(_id, '')
            bleu = compute_bleu(ref, hyp)
            rouge = compute_rouge(ref, hyp)
            sbert = compute_sbert([ref], hyp, model=model) if ref else 0.0
            perplexity = compute_perplexity(hyp)
            rubric = analyze_interview_rubric(hyp)
            failure_modes = detect_failure_modes(hyp)
            
            # Token efficiency (mock: quality per word)
            word_count = len(hyp.split())
            token_efficiency = (bleu + rouge['rouge1'] + sbert) / max(1, word_count) if word_count > 0 else 0.0
            
            writer.writerow({
                'id': _id, 
                'bleu': bleu, 
                'rouge1': rouge['rouge1'], 
                'rougeL': rouge['rougeL'], 
                'sbert_sim': sbert,
                'perplexity': perplexity,
                'technical_depth': rubric['technical_depth'],
                'clarity': rubric['clarity'],
                'relevance': rubric['relevance'],
                'token_efficiency': token_efficiency,
                'malformed_output': int(failure_modes['malformed_output']),
                'off_topic': int(failure_modes['off_topic']),
                'timeout': int(failure_modes['timeout'])
            })

    print(f"ML metrics written to {args.output}")


if __name__ == '__main__':
    main()
