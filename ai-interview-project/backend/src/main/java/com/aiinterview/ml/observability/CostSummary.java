package com.aiinterview.ml.observability;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.Map;

/**
 * Cost summary for a time period
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CostSummary {
    
    private LocalDate date;
    
    private Double totalCost;
    
    private Integer totalCalls;
    
    private Long totalTokens;
    
    private Double avgCostPerCall;
    
    private Double avgCostPer1KTokens;
    
    private Map<String, Double> costByEndpoint;
    
    private Map<String, Double> costByModel;
    
    private Double budgetLimit;
    
    private Double budgetUtilization;
    
    /**
     * Check if budget is exceeded
     */
    public boolean isBudgetExceeded() {
        return budgetLimit != null && totalCost != null && totalCost > budgetLimit;
    }
    
    /**
     * Check if approaching budget (>80%)
     */
    public boolean isApproachingBudget() {
        return budgetLimit != null && budgetUtilization != null && budgetUtilization > 0.8;
    }
    
    /**
     * Get projected monthly cost
     */
    public double getProjectedMonthlyCost() {
        if (totalCost == null) {
            return 0.0;
        }
        return totalCost * 30;
    }
}
