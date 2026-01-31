#!/usr/bin/env python3
"""
MVP pairwise latency comparison for A/B testing.
Reads `eval/model_inference_log.csv` and runs pairwise t-tests (if scipy available).
Outputs markdown summary at `eval/model_comparison_mvp_report.md`.
"""
import argparse
import csv
import statistics
import os

try:
    from scipy import stats
    SCIPY = True
except Exception:
    SCIPY = False


def load_logs(path):
    rows = []
    if not os.path.exists(path):
        return rows
    with open(path, 'r', encoding='utf-8') as f:
        r = csv.DictReader(f)
        for row in r:
            try:
                row['latency_ms'] = float(row.get('latency_ms', 0) or 0)
            except Exception:
                row['latency_ms'] = 0.0
            rows.append(row)
    return rows


def group_by_model(rows):
    d = {}
    for r in rows:
        m = r.get('model_version') or r.get('model') or 'unknown'
        d.setdefault(m, []).append(r['latency_ms'])
    return d


def pairwise_tests(grouped):
    keys = sorted(grouped.keys())
    out = []
    for i in range(len(keys)):
        for j in range(i+1, len(keys)):
            a = grouped[keys[i]]
            b = grouped[keys[j]]
            if len(a) < 2 or len(b) < 2:
                out.append({'a': keys[i], 'b': keys[j], 'n_a': len(a), 'n_b': len(b), 'pval': None, 'mean_a': statistics.mean(a) if a else None, 'mean_b': statistics.mean(b) if b else None})
                continue
            if SCIPY:
                t, p = stats.ttest_ind(a, b, equal_var=False)
            else:
                t, p = None, None
            out.append({'a': keys[i], 'b': keys[j], 'n_a': len(a), 'n_b': len(b), 'pval': p, 'mean_a': statistics.mean(a), 'mean_b': statistics.mean(b)})
    return out


def write_md(out_path, grouped, tests):
    # Expanded report: latency summary, pairwise tests, and optional quality-vs-cost Pareto
    try:
        import matplotlib.pyplot as plt
        MATPLOTLIB = True
    except Exception:
        MATPLOTLIB = False

    with open(out_path, 'w', encoding='utf-8') as f:
        f.write('# A/B Comparison Report (MVP)\n\n')
        f.write('## Samples and Latency Summary\n\n')
        for k, v in grouped.items():
            if v:
                f.write(f'- {k}: n={len(v)}, mean={statistics.mean(v):.2f}ms, p50={statistics.median(v):.2f}ms\n')
            else:
                f.write(f'- {k}: n=0\n')

        f.write('\n## Pairwise latency t-tests\n\n')
        if not tests:
            f.write('No latency tests performed.\n')
        else:
            for t in tests:
                line = f"- {t['a']} vs {t['b']}: n={t['n_a']},{t['n_b']} | mean {t['mean_a'] if t['mean_a'] is not None else 'N/A'} ms vs {t['mean_b'] if t['mean_b'] is not None else 'N/A'} ms"
                if t['pval'] is None:
                    line += ' — p-value not available (scipy missing or insufficient samples)'
                else:
                    line += f" — p={t['pval']:.4f}"
                    if t['pval'] < 0.05:
                        line += ' **statistically significant (p<0.05)**'
                f.write(line + '\n')

        # Try to include merged ML metrics (composite_quality & estimated_cost)
        merged_path = os.path.join('eval', 'ml_merged_metrics.csv')
        if os.path.exists(merged_path):
            try:
                rows = []
                with open(merged_path, 'r', encoding='utf-8') as mf:
                    rdr = csv.DictReader(mf)
                    for r in rdr:
                        try:
                            r['composite_quality'] = float(r.get('composite_quality', 0) or 0)
                        except Exception:
                            r['composite_quality'] = 0.0
                        try:
                            r['estimated_cost'] = float(r.get('estimated_cost', 0) or 0)
                        except Exception:
                            r['estimated_cost'] = 0.0
                        rows.append(r)

                # Aggregate per model_version
                agg = {}
                for r in rows:
                    m = r.get('model_version') or r.get('model') or 'unknown'
                    agg.setdefault(m, {'qs': [], 'costs': []})
                    agg[m]['qs'].append(r['composite_quality'])
                    agg[m]['costs'].append(r['estimated_cost'])

                f.write('\n## Quality vs Cost (aggregated)\n\n')
                for m, vals in agg.items():
                    if vals['qs']:
                        f.write(f"- {m}: mean_quality={statistics.mean(vals['qs']):.4f}, mean_cost={statistics.mean(vals['costs']):.6f}, n={len(vals['qs'])}\n")
                    else:
                        f.write(f"- {m}: no samples\n")

                # Pareto plot
                if MATPLOTLIB and agg:
                    try:
                        labels = []
                        qualities = []
                        costs = []
                        for m, vals in agg.items():
                            if vals['qs']:
                                labels.append(m)
                                qualities.append(statistics.mean(vals['qs']))
                                costs.append(statistics.mean(vals['costs']))
                        fig, ax = plt.subplots()
                        ax.scatter(costs, qualities)
                        for i, lbl in enumerate(labels):
                            ax.annotate(lbl, (costs[i], qualities[i]))
                        ax.set_xlabel('Estimated cost (USD)')
                        ax.set_ylabel('Mean composite quality')
                        ax.set_title('Quality vs Cost (Pareto)')
                        out_png = os.path.splitext(out_path)[0] + '_pareto.png'
                        fig.savefig(out_png)
                        f.write(f"\nPareto plot saved: {out_png}\n")
                    except Exception as e:
                        f.write(f"\nCould not generate Pareto plot: {e}\n")
            except Exception as e:
                f.write(f"\nCould not include merged ML metrics: {e}\n")
        else:
            f.write('\n(No merged ML metrics found at eval/ml_merged_metrics.csv; run advanced_eval and merge with inference logs to enable quality vs cost analysis)\n')


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--logs', default='eval/model_inference_log.csv')
    parser.add_argument('--output', default='eval/model_comparison_mvp_report.md')
    args = parser.parse_args()

    rows = load_logs(args.logs)
    if not rows:
        print('No logs found at', args.logs)
        return
    grouped = group_by_model(rows)
    tests = pairwise_tests(grouped)
    write_md(args.output, grouped, tests)
    print('Wrote', args.output)

if __name__ == '__main__':
    main()
