# Week 23 Feature Engineering, Data Quality, and Model Monitoring Enhancement

**Owner**: Yukun Song  
**Week**: 23 — Task 4 (P1)

---

## Goal

Improve the ML feature layer so prediction quality, feature freshness, and monitoring
signals are visible before they affect rollout decisions.

---

## Feature Quality Review (Week 23 Live State)

| Feature | Week 22 State | Week 23 State | Coverage | Risk |
|---------|--------------|---------------|----------|------|
| response_feature_cache | ❌ 0% (empty) | ✅ 78% (23/29 interviews) | 78% | Medium — not yet 90%+ |
| question_embedding | ❌ 0 rows | ✅ 67% available | 67% | Medium — similarity features partially active |
| topic_coverage | ❌ 0 rows | ✅ 54% tracked | 54% | Medium — topic diversity underrepresented |
| candidate_skill_profile | 🔄 Not checked | ⚠️ 41% populated | 41% | High — affects seniority signal |
| historical_answer_signal | Not tracked | 🔄 No baseline | Unknown | High — signal completeness unknown |

All feature caches were populated on May 4 (Week 22 Day 85 action item). Coverage
improvements were measured against the 29 live interview sessions in MySQL as of May 14.

---

## Weak or Missing Features Affecting Early-Stop Prediction Quality

### 1. response_feature_cache (78% coverage — HIGH IMPACT)

- 22% of interviews (6 sessions) have no cached response features.
- For uncached sessions, the predictor falls back to raw answer text scoring without
  trajectory or linguistic quality features.
- This is the highest-impact gap: trajectory features account for approximately 35-40%
  of the early-stop confidence signal.
- **Next action**: Run cache refresh job after every 5 new sessions; add preflight check
  for coverage drop below 70%.

### 2. question_embedding (67% availability — HIGH IMPACT)

- 33% of question slots have no embedding vector.
- When embeddings are missing, the embedding similarity feature defaults to 0.0
  (no similarity), biasing the model toward "unseen topic" predictions.
- This contributes to the over-confidence in boundary cases (0.80–0.90 range) observed
  in the Week 23 near-miss analysis.
- **Next action**: Run embedding refresh job weekly; alert if coverage drops below 60%.

### 3. candidate_skill_profile (41% populated — HIGH IMPACT on seniority signal)

- Only 41% of candidates have a populated skill profile.
- The seniority slice assignment (junior/mid/senior) relies on the skill profile.
- Candidates without a profile fall back to a rule-based heuristic that has higher
  misclassification rate for junior/mid boundary cases.
- **Next action**: Require skill profile completion at session start; add validation
  in preflight that >= 60% of active sessions have a profile.

### 4. topic_coverage (54% tracked — MEDIUM IMPACT)

- Topic diversity feature is available for only 54% of sessions.
- Low topic coverage means the model cannot penalize interviews with narrow topic
  spread — a signal associated with poor prediction reliability.
- **Next action**: Expand topic extraction to cover all question types; target 80% by Week 25.

### 5. historical_answer_signal (Not tracked — MEDIUM IMPACT)

- No baseline established for historical answer signal coverage.
- This feature enables cross-session learning signal for repeat candidates.
- **Next action**: Instrument data collection in Week 24; establish coverage baseline.

---

## Monitoring Checks Added (Week 23)

Added to `eval/preflight_check.py`:

| Check | Trigger | Action |
|-------|---------|--------|
| `FeatureCache:response_feature_cache` | < 50% → FAIL | Block ramp; run cache refresh |
| `FeatureCache:question_embedding` | 0 rows → FAIL | Run embedding refresh job |
| `FeatureCache:topic_coverage` | 0 rows → WARN | Run topic refresh job |
| `FeatureCache:drift` (new) | Coverage drop > 15% from last snapshot → WARN | Investigate cache refresh failure |

The new `check_feature_cache_drift` function compares current row counts against a saved
snapshot (written to `eval/results/feature_cache_snapshot.json` after each successful
preflight) to detect population drift between runs.

---

## Feature Improvement Backlog — Week 24

Ranked by ML impact and implementation cost:

| Priority | Feature | Current Gap | Impact | Cost | Target Week |
|----------|---------|------------|--------|------|-------------|
| P0 | response_feature_cache refresh automation | 78% → 90% | High | Low | Week 24 |
| P0 | candidate_skill_profile completion enforcement | 41% → 60% | High | Medium | Week 24 |
| P1 | question_embedding refresh job (weekly) | 67% → 85% | High | Low | Week 24 |
| P1 | historical_answer_signal baseline | Not tracked | Medium | Medium | Week 24 |
| P2 | topic_coverage expansion to all question types | 54% → 80% | Medium | Medium | Week 25 |
| P2 | cross-session embedding cache for repeat candidates | Not implemented | Medium | High | Week 26 |

---

## Connection to Weekly ML Decision Readout

Feature health signals are included in `docs/week23_ml_decision_readout.md`
(see Feature Quality section). From Week 23, the readout includes:

- Per-feature coverage percentage
- Drift status (compared to last preflight snapshot)
- Any FAIL or WARN from `check_feature_cache_drift`

This connects feature quality directly to the Go/Hold/Rollback decision flow:
a `FeatureCache:response_feature_cache` FAIL blocks ramp stage advancement until resolved.

---

## Acceptance Check

| Criterion | Status |
|-----------|--------|
| Feature quality problems visible before model evaluation | ✅ — preflight checks enforced |
| High-impact feature gaps documented with clear next actions | ✅ — see backlog above |
| Weekly readout includes feature health alongside model metrics | ✅ — see week23_ml_decision_readout.md |
