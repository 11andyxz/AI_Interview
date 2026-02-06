package com.aiinterview.ml.validation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Aggregated results from multiple validators
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AggregatedValidationResult {
    private boolean overallPassed;
    private double averageScore;
    private List<ValidationResult> individualResults;
    
    /**
     * Get all failed validators
     */
    public List<ValidationResult> getFailures() {
        return individualResults.stream()
                .filter(r -> !r.isPassed())
                .collect(Collectors.toList());
    }
    
    /**
     * Get all warnings across validators
     */
    public List<String> getAllWarnings() {
        return individualResults.stream()
                .flatMap(r -> r.getWarnings().stream())
                .collect(Collectors.toList());
    }
    
    /**
     * Get summary message
     */
    public String getSummary() {
        long passedCount = individualResults.stream().filter(ValidationResult::isPassed).count();
        long totalCount = individualResults.size();
        
        return String.format("%d/%d validators passed (avg score: %.2f)", 
                passedCount, totalCount, averageScore);
    }
}
