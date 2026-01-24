#!/usr/bin/env python3
"""
Combine two eval result CSVs into one A/B CSV with `model_version` column.
Usage: python eval/combine_runs_for_ab.py --a eval/results/eval_results_20251231_185035.csv --model_a gpt-3.5-turbo --b eval/results/eval_results_20251222_202648.csv --model_b gpt-4o-mini --out eval/model_inference_log_ab.csv
"""
import argparse
import csv
import os

def read_rows(path):
    rows = []
    with open(path, 'r', encoding='utf-8') as f:
        rdr = csv.DictReader(f)
        for r in rdr:
            rows.append(r)
    return rows

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--a', required=True)
    parser.add_argument('--model_a', required=True)
    parser.add_argument('--b', required=True)
    parser.add_argument('--model_b', required=True)
    parser.add_argument('--out', default='eval/model_inference_log_ab.csv')
    args = parser.parse_args()

    rows_a = read_rows(args.a)
    rows_b = read_rows(args.b)

    # unify fieldnames by union
    fieldset = set()
    for r in rows_a + rows_b:
        fieldset.update(r.keys())
    fieldnames = ['id','model_version','latency_ms','estimated_cost','timestamp','tokens_used','validator_pass']
    # ensure existing extra fields are preserved
    extra = [f for f in fieldset if f not in fieldnames]
    fieldnames += sorted(extra)

    os.makedirs(os.path.dirname(args.out), exist_ok=True)
    with open(args.out, 'w', newline='', encoding='utf-8') as out:
        writer = csv.DictWriter(out, fieldnames=fieldnames)
        writer.writeheader()
        for r in rows_a:
            r_out = {k: r.get(k, '') for k in fieldnames}
            r_out['model_version'] = args.model_a
            # normalize latency key
            if 'latency_ms' not in r and 'latency' in r:
                r_out['latency_ms'] = r.get('latency')
            writer.writerow(r_out)
        for r in rows_b:
            r_out = {k: r.get(k, '') for k in fieldnames}
            r_out['model_version'] = args.model_b
            if 'latency_ms' not in r and 'latency' in r:
                r_out['latency_ms'] = r.get('latency')
            writer.writerow(r_out)

    print('Wrote combined AB CSV to', args.out)

if __name__ == '__main__':
    main()
