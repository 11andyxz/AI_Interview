# Blockers and Risks — Week 22 (updated 2026-04-27)

## Active Blockers

_No local/staging blockers remain as of 2026-04-27._  
CI environment still needs `DB_*` and `OPENAI_API_KEY` secrets configured.

## Resolved Blockers

### BLOCKER-01: OpenAI API Key ✅ RESOLVED (2026-04-27)
- **Was**: `OPENAI_API_KEY` not set — OpenAI check failing in preflight
- **Resolution**: Key validated locally — `OpenAI:api_key_valid` PASS, models endpoint 1042ms
- **Remaining**: Set key in CI secrets for automated runs

### BLOCKER-00: Aiven MySQL Connectivity ✅ RESOLVED (2026-04-27)
- **Was**: DB credentials unavailable — all experiment runs fell back to SQLite
- **Resolution**: Aiven MySQL confirmed live (879ms connection latency). `preflight_check.py` MySQL gate now passes.
- **Live state**: 29 interviews, 0 experiments, 0 feature cache rows (see Task 4 monitoring gap)

---

## Active Risks

**1. Low Live Sample Size (HIGH)**
- 29 interviews in DB, last active 2026-04-09. Stage A requires ≥ 20 treatment sessions for reliable guardrail evaluation.
- Mitigation: Do not advance ramp stages below minimum sample thresholds; label low-power results explicitly.

**2. Feature Cache Empty (HIGH)**
- `response_feature_cache`, `question_embedding`, `topic_coverage` all have 0 rows.
- Prediction quality relies on feature freshness; empty cache means fallback behavior on first live sessions.
- Mitigation: Populate caches before Stage A; see Task 4 monitoring checks.

**3. Junior Slice Regression (MEDIUM)**
- Junior RMSE guardrail relaxed from 11.5 → 45.0 in Week 21. Path to re-tightening is undefined.
- Mitigation: Task 3 (Week 22) to analyze live slice data and define staged tightening plan.

**4. API Reliability (MEDIUM)**
- OpenAI error/timeout rates not tracked alongside ML quality metrics.
- Mitigation: Track error rate and timeout rate per ramp stage; include in ramp readout.

**5. Stage A High-Variance Small Sample (MEDIUM)**
- Week 21 Stage A triggered ROLLBACK at n=6 (avg_questions_delta=6.8% > 5%). Result was noise, not signal.
- Mitigation: Enforce n_treatment ≥ 20 minimum before evaluating Stage A guardrails.

---

## Resolved / Historical Risks

**OSS Model Latency (RESOLVED — Week 3)**
- CPU: 47.8s avg; GPU anomaly on RTX 3060. Decision: use GPT-4o-mini in production.

**Coherence Gap (RESOLVED — Week 3)**
- Llama-2 68.7 vs GPT 99.1. Resolved by staying on OpenAI models.

