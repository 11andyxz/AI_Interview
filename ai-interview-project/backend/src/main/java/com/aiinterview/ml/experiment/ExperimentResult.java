package com.aiinterview.ml.experiment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Statistical evaluation result for an experiment
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExperimentResult {
    
    /** Experiment ID */
    private String experimentId;
    
    /** Experiment name */
    private String experimentName;
    
    /** Number of samples collected for baseline */
    private Integer baselineSamples;
    
    /** Number of samples collected for variant */
    private Integer variantSamples;
    
    /** Baseline metrics (average) */
    private Map<String, Double> baselineMetrics;
    
    /** Variant metrics (average) */
    private Map<String, Double> variantMetrics;
    
    /** Statistical significance (p-value) */
    private Double pValue;
    
    /** Is difference statistically significant? */
    private Boolean isSignificant;
    
    /** Winning variant: baseline, variant, or inconclusive */
    private String winner;
    
    /** Percentage improvement over baseline */
    private Double improvement;
    
    /** Recommendation: rollout, rollback, or continue */
    private String recommendation;
    
    /** Detailed explanation */
    private String explanation;
}
