# Week 24 Feature-Writing Pipeline Enablement

**Date**: 2026-05-22  
**Owner**: Yukun Song  
**Monitoring period**: May 18–22, 2026

---

## Summary

| Table | Row Count (May 22) | Status | Blocker |
|-------|-------------------|--------|---------|
| response_feature_cache | 0 | ❌ Empty | Yes |
| question_embedding | 0 | ❌ Empty | Yes |
| topic_coverage | 0 | ❌ Empty | Warn |
| candidate_skill_profile | 0 | ❌ Empty | Warn |
| interview | 29 | Stale (last: Apr 9) | — |
| template_questions | 5 | Present | — |
| knowledge_base | 3 | Present | — |

**All four ML feature tables are empty. No feature population has occurred since DB creation.**

---

## Write Path Trace

### 1. response_feature_cache

**Entity**: `com.aiinterview.ml.nlp.entity.ResponseFeatureCache`  
**Extractor**: `com.aiinterview.ml.nlp.ResponseFeatureExtractor`  
**Repository**: `com.aiinterview.ml.nlp.repository.ResponseFeatureCacheRepository`

**Schema** (relevant columns):
```
session_id    VARCHAR(100)
question_id   VARCHAR(100)
response_text TEXT
feature_vector JSON
llm_score     DOUBLE
predicted_score DOUBLE
prediction_error DOUBLE
model_version VARCHAR(50)
```

**Gap**: `ResponseFeatureExtractor` exists as a bean but is not called from `LlmGatewayController.eval()` or `LlmEvaluationService`. After an answer is evaluated via `POST /api/llm/eval`, no write path to `response_feature_cache` is triggered.

**Fix path**: In `LlmGatewayController.eval()`, after `evaluationService.evaluateAnswer()` resolves, call:
```java
responseFeatureExtractor.extractAndCache(sessionId, questionId, answer, result.getScore());
```
The `question_id` must be included in the eval request body (currently optional / missing).

**Idempotency**: The repository should use `INSERT IGNORE` or upsert on `(session_id, question_id)` to prevent duplicate rows on retry.

---

### 2. question_embedding

**Entity**: `com.aiinterview.ml.embedding.entity.QuestionEmbedding`  
**Service**: `com.aiinterview.ml.embedding.service.EmbeddingService`  
**Repository**: `com.aiinterview.ml.embedding.repository.QuestionEmbeddingRepository`

**Schema** (relevant columns):
```
question_id   VARCHAR(100)
role_id       BIGINT
question_text TEXT
embedding     BLOB
cluster_id    INT
cluster_label VARCHAR(200)
```

**Available source data for backfill**:
- `template_questions`: 5 rows (linked to templates via `template_id`; no `role_id` column — role_id mapping needed from `interview_template`)
- `knowledge_base`: 3 rows
- Total candidate questions for initial backfill: **8 rows**

**Gap**: `EmbeddingService` exists but no scheduled job or backfill script calls it for the existing question bank.

**Fix path (backfill)**:
```python
# eval/backfill_question_embeddings.py (to be created)
# For each question in template_questions / knowledge_base:
#   1. Call OpenAI embeddings API (text-embedding-ada-002)
#   2. INSERT INTO question_embedding (question_id, question_text, embedding, role_id, created_at)
#   3. Skip if question_id already exists (idempotent)
```

**Idempotency**: Check `EXISTS (SELECT 1 FROM question_embedding WHERE question_id = ?)` before each insert.

---

### 3. topic_coverage

**Entity**: `com.aiinterview.ml.embedding.entity.TopicCoverage`  
**Tracker**: `com.aiinterview.ml.embedding.service.TopicCoverageTracker`  
**Repository**: `com.aiinterview.ml.embedding.repository.TopicCoverageRepository`

**Schema** (relevant columns):
```
session_id        VARCHAR(100)
role_id           BIGINT
total_questions   INT
coverage_ratio    DOUBLE
shannon_entropy   DOUBLE
cluster_distribution JSON
```

**Gap**: `TopicCoverageTracker.recordQuestionAsked()` is not called from `LlmGatewayController.questionGenerate()`. The service exists but has no call site in the live interview flow.

**Fix path**: In `LlmGatewayController.questionGenerate()`, after the question is generated:
```java
topicCoverageTracker.recordQuestionAsked(sessionId, generatedQuestion, roleId);
```

---

### 4. candidate_skill_profile

**Entity**: `com.aiinterview.ml.prediction.entity.CandidateSkillProfile`  
**Schema** (relevant columns):
```
session_id              VARCHAR(100) UNIQUE
question_count          INT
cumulative_score        DOUBLE
pass_probability        DOUBLE
predicted_final_score   DOUBLE
early_stopping_triggered BIT(1)
```

**Gap**: No evidence of write path being called from live flow. This table is populated by the ML prediction service based on cumulative answer scores.

**Fix path**: Requires `EarlyStoppingService` or equivalent to write after each answer evaluation. Not blocking Stage A evaluation directly (metric capture uses `experiment_metric`), but required for full ML feature pipeline.

---

## Smoke Test Evidence

No features have been successfully written as of May 22, 2026. All tables remain at 0 rows. No local/staging test can be run without a running backend instance in this environment.

**Test plan (to execute after backend is running)**:
1. POST `/api/llm/eval` with `sessionId`, `questionId`, `answer`, `question` → check `response_feature_cache` for new row
2. Run `eval/backfill_question_embeddings.py` → check `question_embedding` for 8 new rows
3. POST `/api/llm/question-generate` with valid session → check `topic_coverage` row written for session

---

## Validation Checks Required Before Stage A Evaluation

| Check | Threshold | Current | Status |
|-------|-----------|---------|--------|
| response_feature_cache rows | > 0 | 0 | ❌ FAIL |
| question_embedding rows | > 0 | 0 | ❌ FAIL |
| topic_coverage rows | > 0 | 0 | ⚠️ WARN |
| Feature freshness (newest row age) | < 7 days | N/A (no rows) | ❌ Not evaluable |
| response_feature_cache / interview coverage | ≥ 50% | 0% | ❌ FAIL |

All validation checks fail clearly when the pipeline is disabled. Preflight result: BLOCKED (pass=9, warn=10, fail=2).

---

## Implementation Priority

| Priority | Task | Estimated impact |
|----------|------|-----------------|
| P0 | Add `ResponseFeatureExtractor` call in eval endpoint | Unblocks preflight FAIL #1 |
| P0 | Create and run `backfill_question_embeddings.py` | Unblocks preflight FAIL #2 |
| P1 | Add `TopicCoverageTracker` call in question-generate | Converts WARN to PASS |
| P2 | Connect `CandidateSkillProfile` writes | Full ML pipeline |

See `eval/results/week24_feature_population_snapshot.json` for full per-table counts and unblock path details.
