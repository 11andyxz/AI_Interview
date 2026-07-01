# Week 27 Generated-Question Mapping Report

Generated: 2026-06-13T13:55:33.309163+00:00

## Result

- Status: PASS for unblock prototype.
- topic_coverage rows for repaired prefixes: 149.
- generated question embeddings: 154.
- generated embeddings with cluster_id: 150.

## Implementation

Generated questions now map to stable `gen-<sha12>` IDs. The mapper records coverage after embedding and cluster assignment. If no seeded question-bank cluster exists for the role, it assigns an explicit generated fallback cluster so failures are non-fatal.

## Stage B Scope

Topic coverage remains excluded from Stage B decisioning until fallback generated clusters are reviewed against seeded question-bank clusters.
