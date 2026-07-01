# Week 26 Quality Metric Upgrade

**Date:** 2026-06-05
**Metric version before:** heuristic-v1.0
**Metric version after:** llm-v1.0

---

## 1. Background

The `estimateQuality()` method in `LlmGatewayController` was a 3-bucket length heuristic:

```
question.length() < 20  → 30.0
question.length() > 200 → 80.0
else                    → 60.0
```

This produced only 3 discrete values, zero variance for questions in the same length bucket, and no signal about technical depth, specificity, or role relevance. All Week 25 treatment sessions fell into the 60.0 bucket, making Welch's t-test meaningless.

---

## 2. Upgrade Design

A new `QuestionQualityScorer` service was added at:
`backend/src/main/java/com/aiinterview/ml/quality/QuestionQualityScorer.java`

### Scoring approach

Each generated question is scored via a single `gpt-3.5-turbo` call (temperature=0.0) with the following prompt:

> You are evaluating the quality of a technical interview question.
> Role: {roleId}, Level: {level}
> Score the following question on a scale of 0 to 100.
> Consider: specificity, technical depth, role relevance, clarity, and non-repetitiveness.
> Question: "{question}"
> Respond with only a single integer between 0 and 100.

The response is parsed for the first integer in [0, 100]. If parsing fails or the LLM call throws, the method falls back to `heuristicFallback()`.

### Fallback

`QuestionQualityScorer.heuristicFallback()` preserves the original 3-bucket logic so that experiment_metric writes are never blocked by a scorer failure.

### Activation

`QuestionQualityScorer` is a `@Service` bean and is always active. The `questionGenerate()` endpoint was changed from `.map()` to `.flatMap()` to accommodate the async scoring call.

---

## 3. Controller Changes

`LlmGatewayController.questionGenerate()` now:
1. Calls `qualityScorer.score(question, roleId, level)` after the question is generated.
2. Uses the returned score in `experimentTracker.recordMetric()`.
3. Falls back to `QuestionQualityScorer.heuristicFallback()` if the scorer bean is null.

The old `estimateQuality()` private method remains in the controller for the streaming endpoint (`questionGenerateStream()`) and the chat endpoint, which are not experiment-tracked for question quality.

---

## 4. Metric Version Separation

| Metric version | Rows | Period | Notes |
|---|---|---|---|
| heuristic-v1.0 | Week 25 rows (n=30) | Before June 2026 | Scores: 30, 60, or 80 only |
| llm-v1.0 | Week 26 rows (n=36+) | June 2026 onward | GPT-scored 0–100 |

Week 25 and Week 26 rows are mixed in `experiment_metric`. The experiment registry labels metric versions per collection entry for readout version tracking.

---

## 5. Current Limitation

All Week 26 sessions were collected with no prior conversation history. The backend generates the same generic question for all no-history sessions ("I see. Can you provide more details about that?"). The LLM scorer assigned ~60 to this question consistently, producing zero variance in Week 26 treatment scores.

The upgrade will produce meaningful variance when sessions include real conversation history and generate diverse, context-specific questions.

---

## 6. Deliverables

- `backend/src/main/java/com/aiinterview/ml/quality/QuestionQualityScorer.java` — new scorer service
- Updated `LlmGatewayController.java` — wired scorer, changed `.map()` to `.flatMap()`
- `eval/results/week26_quality_metric_comparison.json` — comparison snapshot
