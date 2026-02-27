#!/usr/bin/env python3
"""
Merge inference logs with ML metrics to produce a combined CSV for comparison.

Usage:
  python eval/merge_metrics.py --logs eval/model_inference_log.csv --ml eval/ml_metrics.csv --output eval/ml_merged_metrics.csv

The script attempts to match rows by `id` (preferred) or falls back to index-based matching.
Computes a `composite_quality` score as a weighted combination of available metrics.
"""
import argparse
import csv
import os
import statistics


def load_csv(path):
    rows = []
    if not os.path.exists(path):
        return rows
    with open(path, 'r', encoding='utf-8') as f:
        rdr = csv.DictReader(f)
        for r in rdr:
            rows.append(r)
    return rows


def find_id_field(row):
    for k in ['id', 'example_id', 'request_id', 'req_id', 'input_id', 'sample_id']:
        if k in row:
            return k
    return None


def compute_composite(m):
    # weights: sbert_sim 0.4, rougeL 0.3, bleu 0.3 (fall back to available metrics)
    try:
        sbert = float(m.get('sbert_sim', 0) or 0)
    except Exception:
        sbert = 0.0
    try:
        rouge = float(m.get('rougeL', m.get('rouge1', 0)) or 0)
    except Exception:
        rouge = 0.0
    try:
        bleu = float(m.get('bleu', 0) or 0)
    except Exception:
        bleu = 0.0
    # normalize if values appear as percentages (0-100)
    for v in (sbert, rouge, bleu):
        if v > 1.0:
            pass
    # simple weighted sum
    return 0.4 * sbert + 0.3 * rouge + 0.3 * bleu


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--logs', default='eval/model_inference_log.csv')
    parser.add_argument('--ml', default='eval/ml_metrics.csv')
    parser.add_argument('--output', default='eval/ml_merged_metrics.csv')
    args = parser.parse_args()

    logs = load_csv(args.logs)
    ml = load_csv(args.ml)

    if not logs and not ml:
        print('No logs or ML metrics found; nothing to merge.')
        return

    # Build ML dict by id if possible
    ml_by_id = {}
    if ml:
        id_field = find_id_field(ml[0])
        if id_field:
            for r in ml:
                ml_by_id[r.get(id_field)] = r

    out_rows = []

    # Determine id field in logs
    log_id_field = None
    if logs:
        log_id_field = find_id_field(logs[0])

    for idx, lr in enumerate(logs):
        out = {}
        # preserve some fields
        out['model_version'] = lr.get('model_version') or lr.get('model') or lr.get('model_name') or lr.get('endpoint') or 'unknown'
        try:
            out['latency_ms'] = float(lr.get('latency_ms', lr.get('latency', 0)) or 0)
        except Exception:
            out['latency_ms'] = 0.0
        try:
            out['estimated_cost'] = float(lr.get('estimated_cost', 0) or 0)
        except Exception:
            out['estimated_cost'] = 0.0

        # find matching ML metrics
        matched = None
        if ml_by_id and log_id_field:
            lid = lr.get(log_id_field)
            matched = ml_by_id.get(lid)
        if not matched and ml:
            # fallback: match by index
            if idx < len(ml):
                matched = ml[idx]

        if matched:
            out['id'] = matched.get('id') or lr.get(log_id_field) or f'idx_{idx}'
            out['bleu'] = matched.get('bleu', '')
            out['rouge1'] = matched.get('rouge1', '')
            out['rougeL'] = matched.get('rougeL', '')
            out['sbert_sim'] = matched.get('sbert_sim', '')
            out['perplexity'] = matched.get('perplexity', '')
            out['technical_depth'] = matched.get('technical_depth', '')
            out['clarity'] = matched.get('clarity', '')
            out['relevance'] = matched.get('relevance', '')
            out['token_efficiency'] = matched.get('token_efficiency', '')
            out['malformed_output'] = matched.get('malformed_output', '')
            out['off_topic'] = matched.get('off_topic', '')
            out['timeout'] = matched.get('timeout', '')
            try:
                out['composite_quality'] = compute_composite(matched)
            except Exception:
                out['composite_quality'] = 0.0
        else:
            out['id'] = lr.get(log_id_field) or f'idx_{idx}'
            out['bleu'] = ''
            out['rouge1'] = ''
            out['rougeL'] = ''
            out['sbert_sim'] = ''
            out['composite_quality'] = ''

        out_rows.append(out)

    # write output
    os.makedirs(os.path.dirname(args.output), exist_ok=True)
    fieldnames = ['id', 'model_version', 'latency_ms', 'estimated_cost', 'composite_quality', 'bleu', 'rouge1', 'rougeL', 'sbert_sim', 'perplexity', 'technical_depth', 'clarity', 'relevance', 'token_efficiency', 'malformed_output', 'off_topic', 'timeout']
    with open(args.output, 'w', newline='', encoding='utf-8') as out:
        writer = csv.DictWriter(out, fieldnames=fieldnames)
        writer.writeheader()
        for r in out_rows:
            writer.writerow(r)

    print('Wrote merged metrics to', args.output)


if __name__ == '__main__':
    main()
