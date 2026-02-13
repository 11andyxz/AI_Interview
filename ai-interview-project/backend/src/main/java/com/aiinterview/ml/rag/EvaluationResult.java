package com.aiinterview.ml.rag;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Answer evaluation result
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvaluationResult {
    
    /** Overall score (0.0 to 10.0) */
    private Double score;
    
    /** Pass/fail status */
    private Boolean passed;
    
    /** Detailed feedback */
    private String feedback;
    
    /** Strengths identified */
    private List<String> strengths;
    
    /** Areas for improvement */
    private List<String> improvements;
    
    /** Score breakdown by dimension */
    private Map<String, Double> dimensionScores;
    
    /** Context used for evaluation */
    private String context;
    
    /** Confidence in evaluation (0.0 to 1.0) */
    private Double confidence;
}
