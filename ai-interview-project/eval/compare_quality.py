#!/usr/bin/env python3
"""
Compare composite quality across model versions using chi-square on quality buckets,
and produce a Pareto (quality vs cost) plot and a markdown report.

Usage:
  python eval/compare_quality.py --merged eval/ml_merged_metrics.csv --out eval/model_comparison_full_report.md
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

try:
    import matplotlib.pyplot as plt
    MATPLOTLIB = True
except Exception:
    MATPLOTLIB = False


def load_merged(path):
    rows = []
    if not os.path.exists(path):
        return rows
    with open(path, 'r', encoding='utf-8') as f:
        rdr = csv.DictReader(f)
        for r in rdr:
            # normalize numeric fields
            try:
                r['composite_quality'] = float(r.get('composite_quality', '') or 0)
            except Exception:
                r['composite_quality'] = None
            try:
                r['estimated_cost'] = float(r.get('estimated_cost', '') or 0)
            except Exception:
                r['estimated_cost'] = None
            rows.append(r)
    return rows


def bucket_quality(q):
    if q is None:
        return 'unknown'
    try:
        if q < 0.33:
            return 'low'
        if q < 0.66:
            return 'medium'
        return 'high'
    except Exception:
        return 'unknown'


def contingency_table(rows):
    table = {}
    models = sorted({r.get('model_version') or 'unknown' for r in rows})
    buckets = ['low', 'medium', 'high']
    for m in models:
        table[m] = {b: 0 for b in buckets}
    for r in rows:
        m = r.get('model_version') or 'unknown'
        b = bucket_quality(r.get('composite_quality'))
        if b in buckets:
            table.setdefault(m, {bb: 0 for bb in buckets})
            table[m][b] += 1
    # convert to matrix
    matrix = []
    for m in models:
        matrix.append([table[m][b] for b in buckets])
    return models, buckets, matrix


def cramers_v(chisq, n, r, k):
    # Cramer's V = sqrt(chi2 / (n * (min(r,k)-1)))
    try:
        return (chisq / (n * (min(r, k) - 1))) ** 0.5
    except Exception:
        return None


def write_report(out_md, rows, models, buckets, matrix, chi2_res, pareto_png=None):
    with open(out_md, 'w', encoding='utf-8') as f:
        f.write('# Model Comparison: Quality (chi-square) and Pareto\n\n')
        f.write('## Samples per model and bucket\n\n')
        for i, m in enumerate(models):
            counts = matrix[i]
            f.write(f'- {m}: ' + ', '.join([f"{b}={counts[j]}" for j, b in enumerate(buckets)]) + '\n')

        f.write('\n## Chi-square Test\n\n')
        if chi2_res is None:
            f.write('Chi-square not performed (scipy missing or insufficient samples).\n')
        else:
            chi2, p, dof, expected = chi2_res
            f.write(f'- chi2={chi2:.4f}, p={p:.4e}, dof={dof}\n')
            cv = cramers_v(chi2, sum(sum(r) for r in matrix), len(models), len(buckets))
            if cv is not None:
                f.write(f'- Cramer\'s V (effect size)={cv:.4f}\n')

        if pareto_png and os.path.exists(pareto_png):
            f.write('\n## Pareto (quality vs cost)\n\n')
            f.write(f'![Pareto]({pareto_png})\n')
        else:
            f.write('\n(No Pareto plot generated)\n')


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--merged', default='eval/ml_merged_metrics.csv')
    parser.add_argument('--out', default='eval/model_comparison_full_report.md')
    parser.add_argument('--pareto', default='eval/model_comparison_pareto.png')
    args = parser.parse_args()

    rows = load_merged(args.merged)
    if not rows:
        print('No merged metrics at', args.merged)
        return

    models, buckets, matrix = contingency_table(rows)

    chi2_res = None
    try:
        import numpy as np
        obs = np.array(matrix)
        if SCIPY and obs.size and obs.sum() > 0:
            chi2, p, dof, expected = stats.chi2_contingency(obs)
            chi2_res = (float(chi2), float(p), int(dof), expected.tolist())
    except Exception:
        chi2_res = None

    # Pareto: mean composite_quality vs mean estimated_cost per model
    agg = {}
    for r in rows:
        m = r.get('model_version') or 'unknown'
        agg.setdefault(m, {'qs': [], 'costs': []})
        q = r.get('composite_quality')
        if q is not None and q != '':
            try:
                agg[m]['qs'].append(float(q))
            except Exception:
                pass
        c = r.get('estimated_cost')
        if c is not None and c != '':
            try:
                agg[m]['costs'].append(float(c))
            except Exception:
                pass

    pareto_png = None
    if MATPLOTLIB and agg:
        labels = []
        qualities = []
        costs = []
        for m, v in agg.items():
            if v['qs']:
                labels.append(m)
                qualities.append(statistics.mean(v['qs']))
                costs.append(statistics.mean(v['costs']) if v['costs'] else 0.0)
        if labels:
            fig, ax = plt.subplots()
            ax.scatter(costs, qualities)
            for i, lbl in enumerate(labels):
                ax.annotate(lbl, (costs[i], qualities[i]))
            ax.set_xlabel('Estimated cost (USD)')
            ax.set_ylabel('Mean composite quality')
            ax.set_title('Quality vs Cost (Pareto)')
            os.makedirs(os.path.dirname(args.pareto), exist_ok=True)
            fig.savefig(args.pareto)
            pareto_png = args.pareto

    write_report(args.out, rows, models, buckets, matrix, chi2_res, pareto_png=pareto_png)
    print('Wrote report to', args.out)


if __name__ == '__main__':
    main()
