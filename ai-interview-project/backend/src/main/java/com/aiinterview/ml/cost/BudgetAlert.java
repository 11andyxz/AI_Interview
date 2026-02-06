package com.aiinterview.ml.cost;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Represents a budget alert
 */
@Data
@Builder
public class BudgetAlert {
    
    public enum Severity {
        INFO,
        WARNING,
        CRITICAL
    }
    
    private Severity severity;
    private String type;
    private String message;
    private double currentValue;
    private double threshold;
    private String recommendation;
    private LocalDateTime triggeredAt;
    
    public static BudgetAlert create(Severity severity, String type, String message, 
                                    double currentValue, double threshold, String recommendation) {
        return BudgetAlert.builder()
            .severity(severity)
            .type(type)
            .message(message)
            .currentValue(currentValue)
            .threshold(threshold)
            .recommendation(recommendation)
            .triggeredAt(LocalDateTime.now())
            .build();
    }
}
