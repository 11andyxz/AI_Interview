package com.aiinterview.ml.adaptive;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Question item with IRT parameters
 * Represents a calibrated question for adaptive testing
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionItem {
    
    /**
     * Unique question identifier
     */
    private String questionId;
    
    /**
     * Role this question is calibrated for
     */
    private String roleId;
    
    /**
     * Question difficulty parameter (b)
     * Represents ability level at which P(correct)=0.5
     */
    private double difficultyB;
    
    /**
     * Question discrimination parameter (a)
     * Represents how well question differentiates abilities
     */
    private double discriminationA;
    
    /**
     * Number of responses used for calibration
     */
    private int responseCount;
    
    /**
     * Question content/text (optional)
     */
    private String content;
    
    /**
     * Compute Fisher information at given ability level
     * Higher information = more precise measurement
     */
    public double fisherInformation(double theta) {
        double z = discriminationA * (theta - difficultyB);
        double p = 1.0 / (1.0 + Math.exp(-z));
        double q = 1.0 - p;
        return discriminationA * discriminationA * p * q;
    }
    
    /**
     * Compute probability of correct response given ability
     * Uses 2PL IRT model
     */
    public double probabilityCorrect(double theta) {
        double z = discriminationA * (theta - difficultyB);
        return 1.0 / (1.0 + Math.exp(-z));
    }
}
