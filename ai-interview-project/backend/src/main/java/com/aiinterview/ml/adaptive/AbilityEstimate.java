package com.aiinterview.ml.adaptive;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Ability estimate using 2PL IRT model with Bayesian updating
 * Represents candidate's latent ability (θ) with uncertainty
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AbilityEstimate {
    
    /**
     * Estimated ability level (θ) on standardized scale
     * Typically ranges from -3 to +3, with 0 as average
     */
    private double theta;
    
    /**
     * Standard error of the ability estimate
     * Lower values indicate more precise estimates
     */
    private double standardError;
    
    /**
     * Lower bound of 95% confidence interval
     */
    private double confidenceLower;
    
    /**
     * Upper bound of 95% confidence interval
     */
    private double confidenceUpper;
    
    /**
     * Number of responses used to compute this estimate
     */
    private int responsesUsed;
    
    /**
     * Create initial prior estimate (neutral ability)
     */
    public static AbilityEstimate createPrior() {
        return AbilityEstimate.builder()
            .theta(0.0)
            .standardError(1.0)
            .confidenceLower(-1.96)
            .confidenceUpper(1.96)
            .responsesUsed(0)
            .build();
    }
}
