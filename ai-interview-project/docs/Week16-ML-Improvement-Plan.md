# Week 16 ML Improvement Plan

**Document Version**: 1.0  
**Last Updated**: March 16, 2026  
**Priority**: P0

## Executive Summary

Based on the Week 16 ML Metrics Baseline analysis, this document identifies three high-impact improvement opportunities and proposes concrete interventions to address identified weaknesses.

## Identified Weak Segments

### 1. Senior Role False Negative Rate (6.0%)

**Current Performance**:
- Junior roles: 4.1% false negative
- Mid-level roles: 5.2% false negative
- **Senior roles: 6.0% false negative** ⚠️

**Impact**:
- Higher risk of incorrect "fail" predictions for qualified senior candidates
- Potential business impact on senior hiring pipeline

**Root Cause Hypothesis**:
- Senior responses often include strategic/architectural thinking harder to quantify
- Current feature set may over-weight code-level details
- Technical term dictionary may be junior-biased

### 2. Low Activation Rate for Short Interviews (3-4 Questions)

**Current Performance**:
- 3-4 questions: 45% activation, RMSE 15.2
- 5-7 questions: 68% activation, RMSE 10.1

**Impact**:
- Prediction service unavailable in nearly half of short sessions
- Missed opportunity for early stopping decisions

**Root Cause Hypothesis**:
- Insufficient data threshold too conservative
- Model lacks confidence with sparse observations
- No specialized "early prediction" variant

### 3. Python/ML Stack Lower Performance

**Current Performance**:
- Java Backend: RMSE 9.5, R² 0.85
- React Frontend: RMSE 10.2, R² 0.82
- **Python/ML: RMSE 11.5, R² 0.78** ⚠️

**Impact**:
- Degraded prediction quality for ML engineering roles
- Growing market segment with inferior UX

**Root Cause Hypothesis**:
- Limited training data (10 sessions vs. 35 for Java)
- Broader question variety (algorithms, theory, systems)
- Technical term dictionary incomplete for ML domain

## Proposed Interventions

## Intervention 1: Probability Calibration via Platt Scaling

### Objective
Improve pass/fail decision boundaries and reduce false negative rate for senior roles by 20% (6.0% → 4.8%).

### Approach

**What is Platt Scaling?**
- Post-processing step that calibrates raw model outputs to well-calibrated probabilities
- Fits logistic regression on model scores: `P(pass) = 1 / (1 + exp(A * score + B))`
- Adjusts decision thresholds to optimize precision/recall tradeoff

**Implementation Plan**:

1. **Data Collection** (Week 16):
   - Extract historical predictions + ground truth outcomes
   - Minimum 200 samples across all role levels
   - Stratify by role (junior/mid/senior)

2. **Calibration Training**:
   ```python
   from sklearn.calibration import CalibratedClassifierCV
   
   # Fit Platt scaling on validation set
   calibrated_model = CalibratedClassifierCV(
       base_estimator=outcome_predictor,
       method='sigmoid',  # Platt scaling
       cv=5
   )
   ```

3. **Role-Specific Thresholds**:
   - Junior: threshold = 0.50 (balanced)
   - Mid-level: threshold = 0.48 (slight recall bias)
   - Senior: threshold = 0.45 (prioritize recall, reduce false negatives)

4. **Validation Metrics**:
   - Brier score improvement (calibration quality)
   - False negative rate by role
   - Precision-recall curve analysis

### Expected Impact

| Metric | Current | Target | Delta |
|--------|---------|--------|-------|
| Senior False Negative | 6.0% | 4.8% | -20% |
| Overall Brier Score | 0.12 | 0.08 | -33% |
| Pass Probability Calibration | Moderate | Good | Improved |

### Implementation Effort
- **Engineering**: 3-4 days
- **Validation**: 2 days
- **Rollout**: 1 day
- **Total**: ~1 week

### Risks
- **Low**: Platt scaling is well-established technique
- May require retuning thresholds with real production data
- Minimal performance overhead (<5ms)

---

## Intervention 2: Context-Aware Feature Engineering

### Objective
Reduce RMSE for short interviews (3-4 questions) by 25% (15.2 → 11.4) and increase activation rate to 60%.

### Approach

**New Feature Categories**:

1. **Trajectory Features** (delta-based):
   ```
   - score_trend: linear regression slope of scores
   - vocabulary_growth: vocabulary expansion rate
   - confidence_trajectory: confidence score changes
   ```

2. **Early Signal Amplifiers**:
   ```
   - first_response_quality: strong predictor for final outcome
   - technical_term_density_early: quick signal of expertise
   - question_difficulty_match: candidate level vs. question difficulty
   ```

3. **Contextual Metadata**:
   ```
   - role_level: junior/mid/senior encoding
   - tech_stack_match: resume alignment with interview focus
   - question_category_coverage: breadth vs. depth indicator
   ```

**Implementation Plan**:

1. **Feature Development** (3 days):
   - Implement 8-10 new features
   - Add to `ResponseFeatureExtractor`
   - Update feature vector dimension (12 → 20+)

2. **Model Retraining** (2 days):
   - Retrain GBRT with expanded feature set
   - Optimize for early-stage prediction
   - Cross-validate on 3-4 question segments

3. **Activation Threshold Tuning** (1 day):
   - Lower minimum question count from 5 → 4
   - Add confidence-based override (high confidence = earlier activation)

### Expected Impact

| Metric | Current | Target | Delta |
|--------|---------|--------|-------|
| RMSE (3-4 questions) | 15.2 | 11.4 | -25% |
| Activation Rate (3-4 Qs) | 45% | 60% | +33% |
| Overall RMSE | 10.12 | 9.5 | -6% |

### Implementation Effort
- **Engineering**: 5-6 days
- **Validation**: 2 days
- **Total**: ~1.5 weeks

### Risks
- **Medium**: Feature engineering requires domain expertise
- Risk of overfitting with more features (mitigate with regularization)
- Increased latency (target: keep <10ms p95)

---

## Intervention 3: Specialized Early-Stop Heuristics

### Objective
Optimize early stopping policy to reduce average interview length by 10% while maintaining <5% premature stop rate.

### Approach

**Current Early-Stop Logic** (EarlyStoppingService):
```java
if (passProbability > 0.95) → EARLY_PASS
if (passProbability < 0.05) → EARLY_FAIL
```

**Problem**: Single global threshold ignores context.

**Proposed Policy**:

```java
public EarlyStopDecision shouldStop(
    InterviewContext context,
    PredictionResult prediction
) {
    // Role-specific thresholds
    double passThreshold = getPassThreshold(context.roleLevel());
    double failThreshold = getFailThreshold(context.roleLevel());
    
    // Question count gates
    int minQuestions = getMinQuestions(context.roleLevel());
    if (context.questionCount() < minQuestions) {
        return CONTINUE;
    }
    
    // Confidence-weighted decision
    if (prediction.confidence() > 0.90) {
        if (prediction.passProbability() > passThreshold) {
            return EARLY_PASS;
        }
        if (prediction.passProbability() < failThreshold) {
            return EARLY_FAIL;
        }
    }
    
    // Stability check (prevent flip-flop)
    if (hasStablePrediction(context, prediction)) {
        return applyStablePolicy(prediction);
    }
    
    return CONTINUE;
}
```

**Policy Matrix**:

| Role Level | Min Questions | Pass Threshold | Fail Threshold | Notes |
|-----------|--------------|----------------|----------------|-------|
| Junior | 4 | 0.92 | 0.08 | More aggressive |
| Mid-level | 5 | 0.94 | 0.06 | Balanced |
| Senior | 6 | 0.96 | 0.04 | Conservative |

**Guardrails**:
- **Stability requirement**: Prediction must be stable for 2 consecutive questions
- **Confidence gate**: Only stop if confidence >90%
- **Manual override**: Interviewer can always continue
- **Logging**: Record all early-stop decisions for audit

### Expected Impact

| Metric | Current | Target | Delta |
|--------|---------|--------|-------|
| Avg Interview Length | 7.2 questions | 6.5 questions | -10% |
| Premature Stop Rate | N/A | <5% | New metric |
| Time Savings | 0 | ~12 min/interview | New value |

### Implementation Effort
- **Engineering**: 3 days (policy logic + tests)
- **Validation**: 3 days (offline simulation)
- **Rollout**: phased over 2 weeks
- **Total**: ~2 weeks

### Risks
- **High**: Early stopping mistakes highly visible to users
- Requires careful A/B testing before full rollout
- Need strong monitoring and quick rollback capability

---

## Prioritization & Sequencing

### Phase 1: Quick Wins (Week 16)
**Priority**: P0  
**Timeline**: Days 1-5

1. **Intervention 1: Platt Scaling**
   - Fastest to implement
   - High impact on senior role accuracy
   - Low risk

**Deliverables**:
- Calibrated model with role-specific thresholds
- Validation report showing false negative reduction

### Phase 2: Foundation Enhancement (Week 16-17)
**Priority**: P1  
**Timeline**: Days 6-12

2. **Intervention 2: Context-Aware Features**
   - Enables better early predictions
   - Foundation for Intervention 3
   - Medium complexity

**Deliverables**:
- Expanded feature set (20+ dimensions)
- Retrained model with improved RMSE
- Activation rate increase validation

### Phase 3: Policy Optimization (Week 17-18)
**Priority**: P1  
**Timeline**: Days 13-20

3. **Intervention 3: Early-Stop Heuristics**
   - Highest impact on UX
   - Requires Interventions 1-2 complete
   - Needs careful validation

**Deliverables**:
- Implemented heuristic engine
- Offline simulation results
- A/B test plan for staging

---

## Success Metrics

### Week 16 Targets

| Improvement Area | Baseline | Week 16 Target | Success Criteria |
|-----------------|----------|----------------|------------------|
| Senior False Negative | 6.0% | 4.8% | ✅ Pass if <5.0% |
| Short Interview RMSE | 15.2 | 11.4 | ✅ Pass if <12.0 |
| Python/ML Performance | RMSE 11.5 | RMSE 10.5 | ✅ Pass if <11.0 |
| Early-Stop Simulation | N/A | <5% premature | ✅ Pass if validated |

### Long-term Goals (Week 17+)

- **Outcome Prediction**: RMSE <8.0 across all segments
- **Activation Rate**: >70% for all interview lengths
- **Interview Efficiency**: Average 6 questions (vs. 7.2 today)
- **User Satisfaction**: Early-stop acceptance >85%

---

## Dependencies & Prerequisites

### Data Requirements

- **Minimum 200 historical sessions** with ground truth labels
- **Stratified across**:
  - Role levels (junior/mid/senior)
  - Tech stacks (Java/React/Python/Full-stack)
  - Interview lengths (3-4, 5-7, 8-10, 11+)

### Technical Dependencies

- Offline evaluation pipeline (Task 2) for hypothesis testing
- Experiment configuration framework for A/B testing
- Monitoring instrumentation for real-time validation

### Team Coordination

- **Data Science**: Feature engineering + model retraining
- **Backend Engineering**: Service integration + deployment
- **QA**: Validation testing + regression checks
- **Product**: Early-stop policy review + UX considerations

---

## Rollout Strategy

### Staging Validation (Week 16-17)

1. **Deploy Intervention 1** (Platt Scaling)
   - Enable in staging environment
   - Monitor false negative rate for 3 days
   - Gradual rollout: 25% → 50% → 100%

2. **Deploy Intervention 2** (Context Features)
   - A/B test: baseline vs. enhanced features
   - Track RMSE improvement
   - Require minimum 100 sessions per variant

3. **Simulate Intervention 3** (Early-Stop)
   - Offline policy simulation only
   - No live early stopping yet
   - Generate recommendation report

### Production Rollout (Week 18+)

- **Week 18**: Intervention 1 to 50% production traffic
- **Week 19**: Intervention 2 to production (if validated)
- **Week 20+**: Intervention 3 A/B test (10% traffic)

### Rollback Criteria

Trigger rollback if:
- False positive rate increases >3%
- RMSE degrades >10% from baseline
- API error rate >5%
- User complaints spike

---

## Monitoring & Iteration

### Daily Metrics (During Rollout)

- RMSE by segment (role/stack/length)
- False positive/negative rates
- Activation rate changes
- Early-stop decision distribution

### Weekly Review

- Compare Week 16 targets vs. actuals
- Identify new weak segments
- Adjust hypotheses based on findings
- Plan Week 17 iteration priorities

### Experiment Log

Maintain experiment registry:
```yaml
experiments:
  - id: EXP-001
    name: "Platt Scaling - Senior Roles"
    status: in-progress
    start_date: 2026-03-16
    end_date: 2026-03-23
    success_metric: "false_negative < 5.0%"
    
  - id: EXP-002
    name: "Context-Aware Features"
    status: planned
    projected_start: 2026-03-20
```

---

## Appendix A: Feature Importance Analysis

### Current Top Features (GBRT)

| Rank | Feature | Importance | Notes |
|------|---------|-----------|-------|
| 1 | technical_term_coverage | 0.18 | Strongest signal |
| 2 | tfidf_similarity | 0.15 | Question relevance |
| 3 | response_length_log | 0.12 | Explanation depth |
| 4 | vocabulary_richness | 0.11 | Language sophistication |
| 5 | readability_score | 0.09 | Communication clarity |

**Observation**: Current top-5 features are all content-based. Adding trajectory and context features will diversify signal sources.

---

## Appendix B: Failure Mode Analysis

### Known Edge Cases

1. **Copy-paste Detection**:
   - **Issue**: Generic responses score unexpectedly high
   - **Mitigation**: Add plagiarism detection feature

2. **Overly Brief Answers**:
   - **Issue**: Very short responses cause undefined features
   - **Mitigation**: Minimum token threshold + imputation

3. **Non-English Responses**:
   - **Issue**: Feature extraction fails silently
   - **Mitigation**: Language detection + fallback

4. **Code-heavy Responses**:
   - **Issue**: NLP features optimized for prose
   - **Mitigation**: Separate code vs. text feature extractors

---

## References

- Week 16 ML Metrics Baseline
- Week 15 Regression Validation Report
- Platt, J. (1999). "Probabilistic Outputs for SVMs"
- Chen & Guestrin (2016). "XGBoost: A Scalable Tree Boosting System"
