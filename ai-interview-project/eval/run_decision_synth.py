#!/usr/bin/env python3
import csv
import statistics
import os
try:
    from scipy import stats
    SCIPY = True
except Exception:
    SCIPY = False

path = 'eval/ml_merged_metrics_synth.csv'
if not os.path.exists(path):
    print('Missing', path)
    raise SystemExit(1)

rows = []
with open(path, 'r', encoding='utf-8') as f:
    rdr = csv.DictReader(f)
    for r in rdr:
        try:
            r['latency_ms'] = float(r.get('latency_ms') or 0)
        except Exception:
            r['latency_ms'] = None
        rows.append(r)

groups = {}
for r in rows:
    m = r.get('model_version') or 'unknown'
    groups.setdefault(m, []).append(r['latency_ms'])

models = sorted(groups.keys())
print('Models found:', models)
if len(models) < 2:
    print('Need >=2 models')
    raise SystemExit(0)

baseline = models[0]
for m in models[1:]:
    a = [x for x in groups[baseline] if x is not None]
    b = [x for x in groups[m] if x is not None]
    mean_a = statistics.mean(a) if a else None
    mean_b = statistics.mean(b) if b else None
    pval = None
    if SCIPY and a and b and len(a)>1 and len(b)>1:
        t,p = stats.ttest_ind(a,b,equal_var=False)
        pval = float(p)
    print(f'Compare {baseline} vs {m}: n={len(a)},{len(b)} mean_ms={mean_a:.1f} vs {mean_b:.1f} p={pval}')
    if pval is not None and pval<0.05 and mean_b < mean_a:
        print('Recommendation: switch to', m)
    else:
        print('Recommendation: do not switch')
