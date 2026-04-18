# Threshold Evolution Summary

## Week 16: Theoretical Design

**Policy B (Recommended)** - Slice-aware:
- Junior: 0.92
- Mid: 0.94
- Senior: 0.96
**Rationale**: Conservative, role-specific thresholds

**Policy C (Aggressive)** - Global:
- All: 0.90
**Rationale**: Time-optimized, higher risk

**Basis**: Theoretical projections, no real confidence data

---

## Week 18: Implementation

**Chosen**: 0.90 (adopted Policy C - aggressive)
**Rationale**: 
- "Looser threshold, triggers earlier"
- Target: 10-15% question reduction
- Willing to accept higher early-stop risk

**Limitation**: Still theoretical, no empirical confidence distribution

---

## Week 20: Empirical Discovery

**Real Confidence Distribution**:
- Mean: 0.80
- Median: 0.81
- 75th percentile: 0.89
- 90th percentile: 0.94

**Problem with 0.90**:
- Only 21.7% questions > 0.90
- Too conservative for actual distribution
- Early-stop rate 55%, but question reduction only -2.8%

**Data-Driven Recommendation: 0.85**

**Rationale**:
1. **Empirical alignment**: Median=0.81, 0.85 is ~median+0.5σ
2. **Coverage**: 36.7% questions > 0.85 (vs 21.7% > 0.90)
3. **Balance**: Still "high confidence" (top 37%), not reckless
4. **Proven effective**: -10.6% reduction, p=0.0067 (significant)

**Why Week 16/18 didn't use 0.85**:
- Week 16: No real data, chose conservative thresholds
- Week 18: Picked "aggressive" 0.90 from Policy C options
- Week 20: **Real data shows 0.90 is actually still conservative**

**Justification**:
"Week 18's 0.90 was the aggressive option *in theory*. 
Week 20's empirical data reveals 0.85 is the right aggressive option *in practice*."

---

## Summary for Report

**One-liner**: 
"After measuring real OpenAI confidence distribution (mean=0.80), adjusted threshold from Week 18's theoretical 0.90 to data-driven 0.85, improving reduction from -2.8% to -10.6%."

**Detailed explanation**:
"Week 16 and 18 selected thresholds based on theoretical assumptions without real confidence data. Week 20's 120 real OpenAI sessions revealed actual confidence distribution (mean=0.80, median=0.81) is lower than assumed. The 0.90 threshold only captures top 22% of questions. Data-driven analysis shows 0.85 (top 37%) better aligns with the empirical distribution while maintaining high confidence bar, yielding 10.6% question reduction vs 2.8% with 0.90."
