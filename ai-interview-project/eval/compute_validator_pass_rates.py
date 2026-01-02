#!/usr/bin/env python3
"""Compute validator pass rates per prompt_type from latest CSV in results directory.
Prints summary and lists failing rows.
"""
import csv
import glob
import os

res_dir = os.path.join(os.path.dirname(__file__), 'results')
pattern = os.path.join(res_dir, 'eval_results_*.csv')
files = glob.glob(pattern)
if not files:
    print('No result CSVs found in', res_dir)
    raise SystemExit(1)
files.sort()
latest = files[-1]
print('Using CSV:', latest)

counts = {}
fails = {}
with open(latest, newline='', encoding='utf-8') as f:
    reader = csv.DictReader(f)
    for row in reader:
        ptype = row.get('prompt_type') or 'unknown'
        counts.setdefault(ptype, {'total':0, 'validator_run':0, 'validator_pass':0, 'items':[]})
        counts[ptype]['total'] += 1
        vr = row.get('validator_run', '')
        vpass = row.get('validator_pass', '')
        if vr and vr.lower() in ('true','1'):
            counts[ptype]['validator_run'] += 1
            if vpass and vpass.lower() in ('true','1'):
                counts[ptype]['validator_pass'] += 1
            else:
                counts[ptype]['items'].append(row)

print('\nValidator pass rates by prompt_type:')
ok = True
for ptype, v in counts.items():
    total_validated = v['validator_run']
    passed = v['validator_pass']
    rate = (passed / total_validated * 100) if total_validated else 100.0
    print(f"- {ptype}: validated={total_validated} passed={passed} pass_rate={rate:.2f}%")
    if total_validated and rate < 98.0:
        ok = False

if not ok:
    print('\nFailures (sample rows):')
    for ptype, v in counts.items():
        for row in v['items'][:10]:
            print(f"{ptype} - id={row.get('id')} error_type={row.get('validator_error_type')} info={row.get('validator_error_info')}")

print('\nDone')
