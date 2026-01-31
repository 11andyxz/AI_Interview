#!/usr/bin/env python3
"""
Minimal decision rule: compare models on latency (t-test) and validator_pass rate.
Outputs a short recommendation per pair: 'switch' / 'do not switch' / 'inconclusive'.

Usage: python eval/decision_rule.py --merged eval/ml_merged_metrics.csv
"""
import argparse
import csv
import os
import statistics

try:
    from scipy import stats
    SCIPY = True
except Exception:
    SCIPY = False


def load_rows(path):
    rows = []
    if not os.path.exists(path):
        return rows
    with open(path, 'r', encoding='utf-8') as f:
        rdr = csv.DictReader(f)
        for r in rdr:
            try:
                r['latency_ms'] = float(r.get('latency_ms') or r.get('latency') or 0)
            except Exception:
                r['latency_ms'] = None
            # validator_pass may be 'True'/'False' or 1/0
            vp = r.get('validator_pass')
            if vp is None:
                r['validator_pass'] = None
            else:
                if str(vp).lower() in ('true', '1'):
                    r['validator_pass'] = 1
                elif str(vp).lower() in ('false', '0'):
                    r['validator_pass'] = 0
                else:
                    try:
                        r['validator_pass'] = int(vp)
                    except Exception:
                        r['validator_pass'] = None
            rows.append(r)
    return rows


def group(rows):
    d = {}
    for r in rows:
        m = r.get('model_version') or r.get('model') or r.get('endpoint') or 'unknown'
        d.setdefault(m, {'latencies': [], 'passes': []})
        if r.get('latency_ms') is not None:
            d[m]['latencies'].append(r['latency_ms'])
        if r.get('validator_pass') is not None:
            d[m]['passes'].append(r['validator_pass'])
    return d


def decision_for_pair(base, cand):
    # base and cand are dicts with 'latencies' and 'passes'
    n_base = len(base['latencies'])
    n_cand = len(cand['latencies'])
    mean_base = statistics.mean(base['latencies']) if base['latencies'] else None
    mean_cand = statistics.mean(cand['latencies']) if cand['latencies'] else None
    pass_base = (sum(base['passes'])/len(base['passes'])) if base['passes'] else None
    pass_cand = (sum(cand['passes'])/len(cand['passes'])) if cand['passes'] else None

    pval = None
    if SCIPY and base['latencies'] and cand['latencies'] and len(base['latencies'])>1 and len(cand['latencies'])>1:
        try:
            t,p = stats.ttest_ind(base['latencies'], cand['latencies'], equal_var=False)
            pval = float(p)
        except Exception:
            pval = None

    # decision logic (minimal): prefer lower latency if significant and quality not worse
    decision = 'inconclusive'
    reasons = []
    if mean_base is not None and mean_cand is not None:
        if pval is not None and pval < 0.05 and mean_cand < mean_base:
            # check pass rates
            if pass_base is None or pass_cand is None:
                decision = 'inconclusive (missing validator_pass)'
            else:
                # allow small drop (1%)
                if pass_cand >= pass_base - 0.01:
                    decision = 'switch'
                else:
                    decision = 'do not switch (quality drop)'
        else:
            decision = 'do not switch (no significant latency improvement)'
    else:
        decision = 'inconclusive (insufficient latency data)'

    return {'decision': decision, 'mean_base': mean_base, 'mean_cand': mean_cand, 'pval': pval, 'pass_base': pass_base, 'pass_cand': pass_cand, 'n_base': n_base, 'n_cand': n_cand}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--merged', default='eval/ml_merged_metrics.csv')
    args = parser.parse_args()

    rows = load_rows(args.merged)
    if not rows:
        print('No merged metrics found at', args.merged)
        return
    grouped = group(rows)
    models = sorted(grouped.keys())
    if len(models) < 2:
        print('Need at least 2 models for A/B decision')
        return

    # choose baseline as first model (minimal rule)
    baseline = models[0]
    print('Baseline model chosen (by sorted name):', baseline)
    for m in models[1:]:
        res = decision_for_pair(grouped[baseline], grouped[m])
        print(f"Compare {baseline} vs {m}: decision={res['decision']}, n={res['n_base']},{res['n_cand']}, mean_ms={res['mean_base']:.1f} vs {res['mean_cand']:.1f} , p={res['pval']}")


if __name__ == '__main__':
    main()
