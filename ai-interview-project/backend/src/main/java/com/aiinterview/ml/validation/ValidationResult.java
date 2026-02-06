package com.aiinterview.ml.validation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Result of output validation
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidationResult {
    private boolean passed;
    private double score; // 0.0 to 1.0
    private String validatorName;
    private String message;
    
    @Builder.Default
    private List<String> details = new ArrayList<>();
    
    @Builder.Default
    private List<String> warnings = new ArrayList<>();
    
    /**
     * Create a passing result
     */
    public static ValidationResult pass(String validatorName, double score, String message) {
        return ValidationResult.builder()
                .passed(true)
                .validatorName(validatorName)
                .score(score)
                .message(message)
                .build();
    }
    
    /**
     * Create a failing result
     */
    public static ValidationResult fail(String validatorName, double score, String message) {
        return ValidationResult.builder()
                .passed(false)
                .validatorName(validatorName)
                .score(score)
                .message(message)
                .build();
    }
    
    public void addDetail(String detail) {
        this.details.add(detail);
    }
    
    public void addWarning(String warning) {
        this.warnings.add(warning);
    }
}
