#!/usr/bin/env python3
"""
Infer missing `validator_pass` values in a merged metrics CSV.
Strategy: compute median of `composite_quality` (where present) and set `validator_pass`=1 when row's `composite_quality` >= median, else 0.
Usage:
  python eval/backfill_validator_pass.py --merged eval/ml_merged_metrics_ab.csv --out eval/ml_merged_metrics_ab_filled.csv
"""
import argparse
import csv
import os
import statistics


def load_rows(path):
    rows = []
    with open(path, 'r', encoding='utf-8') as f:
        rdr = csv.DictReader(f)
        for r in rdr:
            rows.append(r)
    return rows


def write_rows(path, fieldnames, rows):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', newline='', encoding='utf-8') as f:
        w = csv.DictWriter(f, fieldnames=fieldnames)
        w.writeheader()
        for r in rows:
            w.writerow(r)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--merged', required=True)
    parser.add_argument('--out', required=True)
    args = parser.parse_args()

    rows = load_rows(args.merged)
    if not rows:
        print('No rows found at', args.merged)
        return

    # collect composite quality values
    qualities = []
    for r in rows:
        v = r.get('composite_quality')
        if v is None or v == '':
            continue
        try:
            qualities.append(float(v))
        except Exception:
            continue

    if not qualities:
        print('No composite_quality values found; cannot infer validator_pass')
        return

    median_q = statistics.median(qualities)
    filled = 0
    preserved = 0

    fieldnames = list(rows[0].keys())
    if 'validator_pass' not in fieldnames:
        fieldnames.append('validator_pass')

    for r in rows:
        vp = r.get('validator_pass')
        if vp is None or vp == '':
            # attempt to infer
            try:
                cq = float(r.get('composite_quality') or 0)
            except Exception:
                cq = 0.0
            inferred = 1 if cq >= median_q else 0
            r['validator_pass'] = str(inferred)
            filled += 1
        else:
            preserved += 1

    write_rows(args.out, fieldnames, rows)
    print(f'Wrote {args.out} (filled {filled} rows, preserved {preserved} existing) — median_quality={median_q:.4f}')


if __name__ == '__main__':
    main()
