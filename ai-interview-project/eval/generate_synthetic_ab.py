#!/usr/bin/env python3
"""
Generate synthetic A/B inference logs and reference/hypothesis pairs for demo.
Writes:
 - eval/model_inference_log_synth.csv
 - eval/refs.jsonl
 - eval/hyps.jsonl

Usage: python eval/generate_synthetic_ab.py
"""
import csv
import json
import random
import datetime
import os

OUT_LOG = os.path.join('eval', 'model_inference_log_synth.csv')
OUT_REFS = os.path.join('eval', 'refs.jsonl')
OUT_HYPS = os.path.join('eval', 'hyps.jsonl')

def main():
    os.makedirs('eval', exist_ok=True)
    models = ['gpt-4o-mini', 'gpt-4-turbo']
    N = 120
    rows = []
    refs = {}
    hyps = {}
    for i in range(1, N+1):
        _id = f'synth_{i:04d}'
        # assign model by weighted split: 80/20
        model = random.choices(models, weights=[0.8, 0.2])[0]
        # base latencies differ per model
        if model == 'gpt-4o-mini':
            latency = max(50, random.gauss(120, 30))
            cost = 0.00012
        else:
            latency = max(80, random.gauss(160, 40))
            cost = 0.00035
        latency = round(latency, 2)
        cost = round(cost, 6)
        ts = (datetime.datetime.utcnow() - datetime.timedelta(minutes=random.randint(0, 10000))).isoformat() + 'Z'
        tokens = random.randint(20, 200)
        validator_pass = random.choice([True]*92 + [False]*8)
        rows.append({'id': _id, 'model_version': model, 'latency_ms': latency, 'estimated_cost': cost, 'timestamp': ts, 'tokens_used': tokens, 'validator_pass': str(validator_pass)})

        # references and hypotheses: simple templated text with slight variation per model
        ref = f"Reference answer for {_id}: candidate demonstrated skill A, B and project X."
        if model == 'gpt-4o-mini':
            hyp = ref + ' (concise, correct)'
        else:
            hyp = ref + ' (detailed, very accurate)'
        refs[_id] = ref
        hyps[_id] = hyp

    with open(OUT_LOG, 'w', newline='', encoding='utf-8') as f:
        writer = csv.DictWriter(f, fieldnames=['id','model_version','latency_ms','estimated_cost','timestamp','tokens_used','validator_pass'])
        writer.writeheader()
        writer.writerows(rows)

    with open(OUT_REFS, 'w', encoding='utf-8') as rf, open(OUT_HYPS, 'w', encoding='utf-8') as hf:
        for k in refs:
            rf.write(json.dumps({'id': k, 'reference': refs[k]}) + '\n')
            hf.write(json.dumps({'id': k, 'hypothesis': hyps[k]}) + '\n')

    print('Wrote synthetic logs to', OUT_LOG)
    print('Wrote refs to', OUT_REFS, 'and hyps to', OUT_HYPS)

if __name__ == '__main__':
    main()
