#!/usr/bin/env python3
"""
Aggregate last 7 days of inference logs and merged metrics to produce a weekly ML report.
Writes: `eval/weekly_ml_report.md`, `eval/weekly_latency_boxplot.png`, `eval/weekly_quality_cost.png`.

Usage: python eval/generate_weekly_ml_report.py --logs eval/model_inference_log_production.csv --merged-dir eval/ --out-dir eval/
"""
import argparse
import os
import glob
import pandas as pd
import numpy as np
import datetime
from pathlib import Path

try:
    import matplotlib
    matplotlib.use('Agg')
    import matplotlib.pyplot as plt
except Exception:
    plt = None

try:
    from scipy import stats
    SCIPY = True
except Exception:
    SCIPY = False


def read_logs(patterns):
    dfs = []
    for p in patterns:
        for path in glob.glob(p):
            try:
                df = pd.read_csv(path)
                df['__source'] = os.path.basename(path)
                dfs.append(df)
            except Exception:
                continue
    if not dfs:
        return pd.DataFrame()
    return pd.concat(dfs, ignore_index=True)


def filter_last_n_days(df, ts_col, n=7):
    if df.empty:
        return df
    if ts_col not in df.columns:
        return df
    try:
        df[ts_col] = pd.to_datetime(df[ts_col], errors='coerce')
        cutoff = pd.Timestamp.now() - pd.Timedelta(days=n)
        return df[df[ts_col] >= cutoff]
    except Exception:
        return df


def write_markdown(out_path, lines):
    with open(out_path, 'w', encoding='utf-8') as f:
        f.write('\n'.join(lines))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--logs', nargs='*', default=['eval/model_inference_log*.csv'])
    parser.add_argument('--merged-dir', default='eval/')
    parser.add_argument('--out-dir', default='eval/')
    args = parser.parse_args()

    os.makedirs(args.out_dir, exist_ok=True)

    logs = read_logs(args.logs)
    merged = read_logs([os.path.join(args.merged_dir, 'ml_merged_metrics*.csv')])

    logs = filter_last_n_days(logs, 'timestamp', n=7)
    merged = filter_last_n_days(merged, 'timestamp', n=7)

    # if no timestamp filtering possible, keep available rows
    if logs.empty and not merged.empty:
        # join by id if possible
        logs = merged.copy()

    lines = []
    lines.append('# Weekly ML A/B Report')
    lines.append(f'Generated: {datetime.datetime.utcnow().isoformat()}Z')

    if logs.empty:
        lines.append('\nNo inference logs found for the last 7 days.\n')
        write_markdown(os.path.join(args.out_dir, 'weekly_ml_report.md'), lines)
        print('No logs to report')
        return

    # ensure latency and model columns
    if 'latency_ms' not in logs.columns and 'latency' in logs.columns:
        logs['latency_ms'] = logs['latency']

    if 'model_version' not in logs.columns and 'model' in logs.columns:
        logs['model_version'] = logs['model']

    # basic aggregation
    by_model = logs.groupby('model_version')

    stats_rows = []
    for name, g in by_model:
        lat = pd.to_numeric(g.get('latency_ms', pd.Series(dtype=float)), errors='coerce').dropna()
        tokens = pd.to_numeric(g.get('tokens', pd.Series(dtype=float)), errors='coerce').dropna()
        cost = pd.to_numeric(g.get('estimated_cost', pd.Series(dtype=float)), errors='coerce').dropna()
        
        # Failure mode analysis
        malformed = pd.to_numeric(g.get('malformed_output', pd.Series(dtype=float)), errors='coerce').fillna(0)
        off_topic = pd.to_numeric(g.get('off_topic', pd.Series(dtype=float)), errors='coerce').fillna(0)
        timeout = pd.to_numeric(g.get('timeout', pd.Series(dtype=float)), errors='coerce').fillna(0)
        
        # Interview metrics
        tech_depth = pd.to_numeric(g.get('technical_depth', pd.Series(dtype=float)), errors='coerce').dropna()
        clarity = pd.to_numeric(g.get('clarity', pd.Series(dtype=float)), errors='coerce').dropna()
        relevance = pd.to_numeric(g.get('relevance', pd.Series(dtype=float)), errors='coerce').dropna()
        
        stats_rows.append({'model': name,
                          'n': len(g),
                          'mean_ms': lat.mean() if not lat.empty else None,
                          'p50': lat.quantile(0.5) if not lat.empty else None,
                          'p95': lat.quantile(0.95) if not lat.empty else None,
                          'p99': lat.quantile(0.99) if not lat.empty else None,
                          'tokens_mean': tokens.mean() if not tokens.empty else None,
                          'cost_mean': cost.mean() if not cost.empty else None,
                          'malformed_rate': malformed.mean() if len(malformed) > 0 else None,
                          'off_topic_rate': off_topic.mean() if len(off_topic) > 0 else None,
                          'timeout_rate': timeout.mean() if len(timeout) > 0 else None,
                          'tech_depth_avg': tech_depth.mean() if not tech_depth.empty else None,
                          'clarity_avg': clarity.mean() if not clarity.empty else None,
                          'relevance_avg': relevance.mean() if not relevance.empty else None})

    summary_df = pd.DataFrame(stats_rows).sort_values('model')
    lines.append('\n## Per-model latency summary')
    lines.append(summary_df.to_markdown(index=False))
    
    # Add failure mode analysis
    lines.append('\n## Failure Mode Analysis')
    failure_cols = ['model', 'malformed_rate', 'off_topic_rate', 'timeout_rate']
    failure_df = summary_df[failure_cols].fillna(0)
    if not failure_df.empty:
        lines.append(failure_df.to_markdown(index=False))
    else:
        lines.append('No failure mode data available.')
    
    # Add interview rubric scores
    lines.append('\n## Interview Quality Metrics')
    rubric_cols = ['model', 'tech_depth_avg', 'clarity_avg', 'relevance_avg']
    rubric_df = summary_df[rubric_cols].fillna(0)
    if not rubric_df.empty:
        lines.append(rubric_df.to_markdown(index=False))
    else:
        lines.append('No interview quality data available.')

    # latency boxplot
    if plt is not None:
        plt.figure(figsize=(8,4))
        data = [pd.to_numeric(g.get('latency_ms', pd.Series(dtype=float)), errors='coerce').dropna() for _,g in by_model]
        labels = [name for name,_ in by_model]
        if any(len(d)>0 for d in data):
            plt.boxplot(data, labels=labels)
            plt.ylabel('latency_ms')
            plt.title('Latency by model (last 7 days)')
            boxfile = os.path.join(args.out_dir, 'weekly_latency_boxplot.png')
            plt.tight_layout()
            plt.savefig(boxfile)
            lines.append(f'\n![latency]({os.path.basename(boxfile)})')

    # quality vs cost scatter using merged metrics if present
    if not merged.empty and 'composite_quality' in merged.columns and 'estimated_cost' in merged.columns:
        try:
            merged['composite_quality'] = pd.to_numeric(merged['composite_quality'], errors='coerce')
            merged['estimated_cost'] = pd.to_numeric(merged['estimated_cost'], errors='coerce')
            agg = merged.groupby('model_version').agg({'composite_quality':'mean','estimated_cost':'mean'}).reset_index()
            if plt is not None and not agg.empty:
                plt.figure(figsize=(6,4))
                plt.scatter(agg['estimated_cost'], agg['composite_quality'])
                for i,row in agg.iterrows():
                    plt.annotate(row['model_version'], (row['estimated_cost'], row['composite_quality']))
                plt.xlabel('mean estimated_cost')
                plt.ylabel('mean composite_quality')
                plt.title('Quality vs Cost (last 7 days)')
                scfile = os.path.join(args.out_dir, 'weekly_quality_cost.png')
                plt.tight_layout()
                plt.savefig(scfile)
                lines.append(f'\n![quality_cost]({os.path.basename(scfile)})')
        except Exception:
            pass

    # statistical tests: pairwise t-test for latency if scipy available
    if SCIPY:
        models = list(by_model.groups.keys())
        if len(models) >= 2:
            base = list(by_model)[0]
            for name, g in list(by_model)[1:]:
                a = pd.to_numeric(base[1].get('latency_ms', pd.Series(dtype=float)), errors='coerce').dropna()
                b = pd.to_numeric(g.get('latency_ms', pd.Series(dtype=float)), errors='coerce').dropna()
                if len(a)>1 and len(b)>1:
                    t,p = stats.ttest_ind(a,b, equal_var=False)
                    lines.append(f'\nT-test {base[0]} vs {name}: n={len(a)},{len(b)}, p={p:.4g}, mean_ms={a.mean():.1f} vs {b.mean():.1f}')

    write_markdown(os.path.join(args.out_dir, 'weekly_ml_report.md'), lines)
    print('Wrote weekly report to', os.path.join(args.out_dir, 'weekly_ml_report.md'))


if __name__ == '__main__':
    main()
#!/usr/bin/env python3
"""
Weekly ML report generator (MVP)
- Aggregates model_inference_log entries (CSV or newline logs)
- Produces markdown summary with latency distributions and quality vs cost scatter placeholder
- Saves charts as PNGs (requires matplotlib)

Usage:
python eval/generate_weekly_ml_report.py --logs model_inference_log.csv --output reports/week_ml_report.md
"""

import argparse
import csv
import statistics
import os

try:
    import matplotlib.pyplot as plt
    MATPLOTLIB = True
except Exception:
    MATPLOTLIB = False


def load_logs(path):
    rows = []
    if not os.path.exists(path):
        return rows
    with open(path, 'r', encoding='utf-8') as f:
        r = csv.DictReader(f)
        for row in r:
            rows.append(row)
    return rows


def write_markdown(path, stats, notes):
    with open(path, 'w', encoding='utf-8') as f:
        f.write('# Weekly ML Report\n\n')
        f.write('## Summary\n\n')
        for k,v in stats.items():
            f.write(f'- **{k}**: {v}\n')
        f.write('\n## Notes\n\n')
        f.write(notes)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--logs', required=True, help='CSV of model inference logs')
    parser.add_argument('--output', required=True, help='Markdown output')
    args = parser.parse_args()

    rows = load_logs(args.logs)
    if not rows:
        print('No logs found')
        return

    latencies = [float(r.get('latency_ms', 0) or 0) for r in rows]
    costs = [float(r.get('estimated_cost', 0) or 0) for r in rows]
    models = set(r.get('model_version','') for r in rows)

    stats = {}
    stats['count'] = len(rows)
    stats['avg_latency_ms'] = round(statistics.mean(latencies),2) if latencies else 0
    stats['p50_ms'] = round(statistics.median(latencies),2) if latencies else 0
    stats['p95_ms'] = round(sorted(latencies)[int(len(latencies)*0.95)-1],2) if latencies else 0
    stats['total_cost'] = round(sum(costs),6)
    notes = f'Models observed: {sorted(models)}\n'

    # placeholder: plot latency boxplot by model if matplotlib available
    if MATPLOTLIB:
        try:
            import numpy as np
            data_by_model = {}
            for r in rows:
                data_by_model.setdefault(r.get('model_version','unknown'), []).append(float(r.get('latency_ms',0) or 0))
            fig, ax = plt.subplots()
            ax.boxplot([v for v in data_by_model.values()], labels=[k for k in data_by_model.keys()])
            ax.set_title('Latency by model (ms)')
            fig_path = os.path.splitext(args.output)[0] + '_latency_boxplot.png'
            fig.savefig(fig_path)
            notes += f'Latency boxplot saved: {fig_path}\n'
        except Exception as e:
            notes += f'Could not generate plots: {e}\n'

    write_markdown(args.output, stats, notes)
    print(f'Weekly report written to {args.output}')

if __name__ == '__main__':
    main()
