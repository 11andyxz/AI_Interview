package com.aiinterview.ml.observability;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Cost breakdown by model or endpoint
 * Used for cost attribution and optimization
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CostBreakdown {
    
    private Double totalCost;
    
    private Long totalCalls;
    
    private Long totalTokens;
    
    private Map<String, Double> costByCategory;  // endpoint or model
    
    private Map<String, Long> callsByCategory;
    
    private Map<String, Long> tokensByCategory;
    
    private String breakdownType;  // "model" or "endpoint"
    
    private LocalDateTime periodStart;
    
    private LocalDateTime periodEnd;
    
    /**
     * Get cost for a specific category
     */
    public Double getCostFor(String category) {
        return costByCategory != null ? costByCategory.getOrDefault(category, 0.0) : 0.0;
    }
    
    /**
     * Get top N cost categories
     */
    public Map<String, Double> getTopCostCategories(int n) {
        if (costByCategory == null) {
            return Map.of();
        }
        
        return costByCategory.entrySet().stream()
            .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
            .limit(n)
            .collect(java.util.stream.Collectors.toMap(
                Map.Entry::getKey,
                Map.Entry::getValue,
                (e1, e2) -> e1,
                java.util.LinkedHashMap::new
            ));
    }
    
    /**
     * Calculate average cost per call
     */
    public Double getAvgCostPerCall() {
        return totalCalls != null && totalCalls > 0 ? totalCost / totalCalls : 0.0;
    }
    
    /**
     * Calculate average cost per 1K tokens
     */
    public Double getAvgCostPer1KTokens() {
        return totalTokens != null && totalTokens > 0 ? (totalCost / totalTokens) * 1000 : 0.0;
    }
}
