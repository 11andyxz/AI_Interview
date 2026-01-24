# A/B Comparison Report (MVP)

## Samples and Latency Summary

- gpt-4o-mini: n=94, mean=117.55ms, p50=117.85ms
- gpt-4-turbo: n=26, mean=158.51ms, p50=170.85ms

## Pairwise latency t-tests

- gpt-4-turbo vs gpt-4o-mini: n=26,94 | mean 158.5080769230769 ms vs 117.54542553191489 ms — p=0.0000 **statistically significant (p<0.05)**

## Quality vs Cost (aggregated)

- local: mean_quality=0.0000, mean_cost=0.000000, n=20

Pareto plot saved: eval\model_comparison_mvp_synth_pareto.png
