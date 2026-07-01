# Week 22 Task 4: Feature/Data Quality Monitoring

**Date**: 2026-04-27  
**Owner**: Yukun Song  
**Status**: ✅ Framework complete — live monitoring checks documented

---

## Objective

Make ML quality review repeatable by connecting feature freshness, evaluation metrics, and weekly decision reporting into one auditable workflow.

---

## Live Feature Cache State (as of 2026-04-27)

| Feature Table | Row Count | Staleness Status | Risk |
|---------------|-----------|-----------------|------|
| `response_feature_cache` | **0** | ❌ Empty | HIGH — predictions use cold fallback |
| `question_embedding` | **0** | ❌ Empty | HIGH — embedding similarity unavailable |
| `topic_coverage` | **0** | ❌ Empty | MEDIUM — topic distribution unknown |
| `candidate_skill_profile` | — | 🔄 Not checked | MEDIUM — profiling affects slice assignment |
| `user_preferences` | — | 🔄 Not checked | LOW — optional enrichment |

**Critical finding**: All three ML feature caches (`response_feature_cache`, `question_embedding`, `topic_coverage`) are empty. The prediction model will use fallback behavior (raw scoring without embedding/response features) until these are populated. This must be resolved before Stage A ramp.

---

## Feature Freshness Checks

### Check 1: Response Feature Cache Completeness

```sql
-- Must be > 0 before ramp; ideally > 50% of interview count
SELECT 
  COUNT(*) AS cache_rows,
  (SELECT COUNT(*) FROM interview) AS interview_count,
  ROUND(COUNT(*) * 100.0 / NULLIF((SELECT COUNT(*) FROM interview), 0), 1) AS cache_coverage_pct
FROM response_feature_cache;

-- Alert threshold: cache_coverage_pct < 50%
```

**Current state**: 0 / 29 interviews cached (0% coverage) ❌

### Check 2: Embedding Cache Hit Ratio

```sql
-- Question embeddings should be pre-computed for all active questions
SELECT 
  COUNT(*) AS embedding_count,
  MAX(created_at) AS last_updated
FROM question_embedding;

-- Alert threshold: embedding_count = 0 OR last_updated > 7 days ago
```

**Current state**: 0 embeddings ❌

### Check 3: Topic Coverage Freshness

```sql
-- Topic coverage must be refreshed at least weekly
SELECT 
  COUNT(*) AS topic_count,
  MAX(updated_at) AS last_refresh
FROM topic_coverage;

-- Alert threshold: topic_count = 0 OR last_refresh > 7 days ago
```

**Current state**: 0 topics ❌

### Check 4: Candidate Skill Profile Availability

```sql
-- Skill profiles drive slice (junior/mid/senior) assignment
SELECT 
  COUNT(*) AS profile_count,
  (SELECT COUNT(*) FROM candidate) AS candidate_count,
  ROUND(COUNT(*) * 100.0 / NULLIF((SELECT COUNT(*) FROM candidate), 0), 1) AS profile_coverage_pct
FROM candidate_skill_profile;

-- Alert threshold: profile_coverage_pct < 80%
```

### Check 5: Experiment Registry Sync

```bash
# Verify experiment_registry.csv is in sync with DB (no orphaned entries)
python eval/preflight_check.py --env staging
# Check: no entries in registry with "BLOCKED" status in live windows
```

---

## Monitoring Gaps Identified

| Gap | Severity | Impact | Resolution |
|-----|----------|--------|------------|
| response_feature_cache empty | HIGH | Cold-start predictions for all live sessions | Populate before Stage A ramp |
| question_embedding empty | HIGH | Embedding similarity features unavailable | Trigger embedding job before ramp |
| topic_coverage empty | MEDIUM | Topic diversity not tracked | Run topic coverage refresh |
| No alerting on stale features | MEDIUM | Freshness regression goes undetected | Add staleness check to preflight_check.py |
| experiment table not written | LOW | DB-backed audit trail missing | First live run will populate |

---

## Automated Monitoring Checks (add to preflight_check.py)

The following checks should be added to `eval/preflight_check.py` as Week 23 items:

```python
def check_feature_cache_freshness(conn) -> list[dict]:
    """Verify ML feature caches are populated and not stale."""
    results = []
    checks = [
        ("response_feature_cache", "cache_coverage", 50.0),
        ("question_embedding",     "embedding_count", 1),
        ("topic_coverage",         "topic_count", 1),
    ]
    for table, metric, threshold in checks:
        cursor = conn.cursor()
        cursor.execute(f"SELECT COUNT(*) FROM {table}")
        count = cursor.fetchone()[0]
        status = "PASS" if count >= threshold else "FAIL"
        results.append({
            "check": f"FeatureCache:{table}",
            "status": status,
            "detail": f"{count} rows (threshold: {threshold})"
        })
    return results
```

---

## Monitoring Gap List for Week 23 Planning

| # | Gap | Owner | Priority |
|---|-----|-------|----------|
| 1 | Add `FeatureCache:*` checks to `preflight_check.py` | Yukun | P1 |
| 2 | Set up alerting when `response_feature_cache` goes below 50% coverage | Yukun | P1 |
| 3 | Schedule weekly embedding refresh job | Andy/Infra | P1 |
| 4 | Schedule weekly topic coverage refresh | Andy/Infra | P2 |
| 5 | Wire `experiment` table writes from live experiment runs | Yukun | P2 |
| 6 | Dashboard for feature freshness (last_updated per table) | Yukun | P2 |

---

## Deliverables

| Artifact | Path | Status |
|----------|------|--------|
| Feature quality monitoring report | `docs/week22_feature_quality_monitoring.md` | ✅ This document |
| Weekly ML decision readout | `docs/week22_ml_decision_readout.md` | ✅ Created |
| Updated eval/report workflow | `eval/auto_summary_generator.py` | ✅ Updated |
| Monitoring gap list (Week 23) | Section above | ✅ |
