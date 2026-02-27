package com.aiinterview.ml.adaptive;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response record for IRT calibration
 * Represents a single candidate response to a question
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResponseRecord {
    
    /**
     * Unique question identifier
     */
    private String questionId;
    
    /**
     * Normalized score (0.0 to 1.0)
     * Represents correctness/quality of response
     */
    private double score;
    
    /**
     * Question difficulty parameter (b)
     * Higher values indicate harder questions
     */
    private double difficultyB;
    
    /**
     * Question discrimination parameter (a)
     * Higher values indicate better ability differentiation
     */
    private double discriminationA;
    
    /**
     * Optional: raw score before normalization
     */
    private Double rawScore;
}
