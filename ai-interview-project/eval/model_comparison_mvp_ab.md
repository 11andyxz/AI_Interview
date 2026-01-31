# A/B Comparison Report (MVP)

## Samples and Latency Summary

- gpt-3.5-turbo: n=40, mean=1981.92ms, p50=1374.94ms
- gpt-4o-mini: n=23, mean=1328.63ms, p50=1341.37ms

## Pairwise latency t-tests

- gpt-3.5-turbo vs gpt-4o-mini: n=40,23 | mean 1981.9169104099274 ms vs 1328.6345730657163 ms — p=0.0010 **statistically significant (p<0.05)**

## Quality vs Cost (aggregated)

- local: mean_quality=0.0000, mean_cost=0.000000, n=20

Pareto plot saved: d:\dev\AI_Interview\ai-interview-project\eval\model_comparison_mvp_ab_pareto.png
