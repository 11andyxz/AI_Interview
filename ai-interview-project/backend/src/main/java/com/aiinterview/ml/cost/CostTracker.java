package com.aiinterview.ml.cost;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks AI API costs and monitors budget utilization
 * Legacy implementation - kept for reference
 * @deprecated Use com.aiinterview.ml.observability.CostTracker instead
 */
@Slf4j
@Service("legacyCostTracker")
public class CostTracker {
    
    // Cost tracking maps
    private final Map<String, List<CostEntry>> costsByScenario = new ConcurrentHashMap<>();
    private final Map<String, List<CostEntry>> costsByUser = new ConcurrentHashMap<>();
    private final Map<LocalDate, DailyCosts> dailyCosts = new ConcurrentHashMap<>();
    
    // Pricing (per 1K tokens) - estimated as of 2026
    private static final Map<String, ModelPricing> MODEL_PRICING = Map.of(
        "gpt-3.5-turbo-1106", new ModelPricing(0.001, 0.002),
        "gpt-3.5-turbo-16k", new ModelPricing(0.003, 0.004),
        "gpt-4-turbo-preview", new ModelPricing(0.01, 0.03)
    );
    
    // Budget limits
    private static final double DAILY_BUDGET = 100.0;  // $100/day
    private static final double MONTHLY_BUDGET = 2500.0;  // $2500/month
    
    @Data
    public static class ModelPricing {
        private final double inputCostPer1K;
        private final double outputCostPer1K;
    }
    
    @Data
    @Builder
    public static class CostEntry {
        private String id;
        private LocalDateTime timestamp;
        private String scenario;
        private String userId;
        private String model;
        private int inputTokens;
        private int outputTokens;
        private double cost;
        private boolean fromCache;
    }
    
    @Data
    @Builder
    public static class DailyCosts {
        private LocalDate date;
        private double totalCost;
        private int requestCount;
        private Map<String, Double> costByScenario;
        private double budgetUtilization;
    }
    
    /**
     * Record cost for an API request
     */
    public void recordCost(String scenario, String userId, String model, 
                          int inputTokens, int outputTokens, boolean fromCache) {
        double cost = fromCache ? 0.0 : calculateCost(model, inputTokens, outputTokens);
        
        CostEntry entry = CostEntry.builder()
            .id(UUID.randomUUID().toString())
            .timestamp(LocalDateTime.now())
            .scenario(scenario)
            .userId(userId)
            .model(model)
            .inputTokens(inputTokens)
            .outputTokens(outputTokens)
            .cost(cost)
            .fromCache(fromCache)
            .build();
        
        // Store in various indexes
        costsByScenario.computeIfAbsent(scenario, k -> new ArrayList<>()).add(entry);
        costsByUser.computeIfAbsent(userId, k -> new ArrayList<>()).add(entry);
        
        // Update daily costs
        LocalDate today = LocalDate.now();
        DailyCosts daily = dailyCosts.computeIfAbsent(today, k -> DailyCosts.builder()
            .date(k)
            .totalCost(0.0)
            .requestCount(0)
            .costByScenario(new ConcurrentHashMap<>())
            .budgetUtilization(0.0)
            .build());
        
        daily.setTotalCost(daily.getTotalCost() + cost);
        daily.setRequestCount(daily.getRequestCount() + 1);
        daily.getCostByScenario().merge(scenario, cost, Double::sum);
        daily.setBudgetUtilization(daily.getTotalCost() / DAILY_BUDGET);
        
        log.debug("Recorded cost: scenario={}, cost=${:.4f}, daily_total=${:.2f}", 
                 scenario, cost, daily.getTotalCost());
    }
    
    /**
     * Calculate cost based on model and token usage
     */
    public double calculateCost(String model, int inputTokens, int outputTokens) {
        ModelPricing pricing = MODEL_PRICING.getOrDefault(model, 
            new ModelPricing(0.001, 0.002)); // Default to gpt-3.5 pricing
        
        double inputCost = (inputTokens / 1000.0) * pricing.inputCostPer1K;
        double outputCost = (outputTokens / 1000.0) * pricing.outputCostPer1K;
        
        return inputCost + outputCost;
    }
    
    /**
     * Get cost statistics for today
     */
    public DailyCosts getTodayCosts() {
        return dailyCosts.getOrDefault(LocalDate.now(), DailyCosts.builder()
            .date(LocalDate.now())
            .totalCost(0.0)
            .requestCount(0)
            .costByScenario(new HashMap<>())
            .budgetUtilization(0.0)
            .build());
    }
    
    /**
     * Get cost statistics for a date range
     */
    public CostStatistics getCostStatistics(LocalDate startDate, LocalDate endDate) {
        double totalCost = 0.0;
        int totalRequests = 0;
        Map<String, Double> costByScenario = new HashMap<>();
        
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            DailyCosts daily = dailyCosts.get(date);
            if (daily != null) {
                totalCost += daily.getTotalCost();
                totalRequests += daily.getRequestCount();
                
                daily.getCostByScenario().forEach((scenario, cost) -> 
                    costByScenario.merge(scenario, cost, Double::sum));
            }
        }
        
        double avgCostPerRequest = totalRequests > 0 ? totalCost / totalRequests : 0.0;
        
        return CostStatistics.builder()
            .startDate(startDate)
            .endDate(endDate)
            .totalCost(totalCost)
            .totalRequests(totalRequests)
            .avgCostPerRequest(avgCostPerRequest)
            .costByScenario(costByScenario)
            .build();
    }
    
    /**
     * Get costs for a specific scenario
     */
    public ScenarioCostStats getScenarioCosts(String scenario) {
        List<CostEntry> entries = costsByScenario.getOrDefault(scenario, List.of());
        
        if (entries.isEmpty()) {
            return ScenarioCostStats.builder()
                .scenario(scenario)
                .requestCount(0)
                .totalCost(0.0)
                .avgCost(0.0)
                .build();
        }
        
        double totalCost = entries.stream().mapToDouble(CostEntry::getCost).sum();
        int requestCount = entries.size();
        double avgCost = totalCost / requestCount;
        
        long cacheHits = entries.stream().filter(CostEntry::isFromCache).count();
        double cacheHitRate = (double) cacheHits / requestCount;
        
        return ScenarioCostStats.builder()
            .scenario(scenario)
            .requestCount(requestCount)
            .totalCost(totalCost)
            .avgCost(avgCost)
            .cacheHitRate(cacheHitRate)
            .build();
    }
    
    /**
     * Check if budget alert should be triggered
     */
    public List<BudgetAlert> checkBudgetAlerts() {
        List<BudgetAlert> alerts = new ArrayList<>();
        DailyCosts today = getTodayCosts();
        
        // Critical: Daily budget exceeded
        if (today.getTotalCost() > DAILY_BUDGET) {
            alerts.add(BudgetAlert.builder()
                .severity(BudgetAlert.Severity.CRITICAL)
                .type("DAILY_BUDGET_EXCEEDED")
                .message(String.format("Daily budget exceeded: $%.2f / $%.2f", 
                        today.getTotalCost(), DAILY_BUDGET))
                .currentValue(today.getTotalCost())
                .threshold(DAILY_BUDGET)
                .recommendation("Enable aggressive caching or reduce request volume")
                .build());
        }
        
        // Warning: 80% budget utilization
        if (today.getBudgetUtilization() >= 0.8 && today.getBudgetUtilization() < 1.0) {
            alerts.add(BudgetAlert.builder()
                .severity(BudgetAlert.Severity.WARNING)
                .type("BUDGET_80_PERCENT")
                .message(String.format("Budget 80%% utilized: $%.2f / $%.2f", 
                        today.getTotalCost(), DAILY_BUDGET))
                .currentValue(today.getTotalCost())
                .threshold(DAILY_BUDGET * 0.8)
                .recommendation("Monitor closely, consider caching optimizations")
                .build());
        }
        
        // Critical: Cost spike detected (200% of average)
        double avgDailyCost = calculateAvgDailyCost();
        if (avgDailyCost > 0 && today.getTotalCost() > avgDailyCost * 2.0) {
            alerts.add(BudgetAlert.builder()
                .severity(BudgetAlert.Severity.CRITICAL)
                .type("COST_SPIKE_DETECTED")
                .message(String.format("Cost spike: $%.2f (avg: $%.2f)", 
                        today.getTotalCost(), avgDailyCost))
                .currentValue(today.getTotalCost())
                .threshold(avgDailyCost * 2.0)
                .recommendation("Investigate unusual activity, check for bugs")
                .build());
        }
        
        return alerts;
    }
    
    private double calculateAvgDailyCost() {
        List<DailyCosts> last7Days = dailyCosts.values().stream()
            .filter(d -> d.getDate().isAfter(LocalDate.now().minusDays(8)))
            .toList();
        
        if (last7Days.isEmpty()) {
            return 0.0;
        }
        
        return last7Days.stream()
            .mapToDouble(DailyCosts::getTotalCost)
            .average()
            .orElse(0.0);
    }
    
    /**
     * Calculate cost per quality point
     */
    public double calculateCostPerQualityPoint(String scenario, double avgQualityScore) {
        ScenarioCostStats stats = getScenarioCosts(scenario);
        if (avgQualityScore == 0.0) {
            return 0.0;
        }
        return stats.getAvgCost() / avgQualityScore;
    }
    
    @Data
    @Builder
    public static class CostStatistics {
        private LocalDate startDate;
        private LocalDate endDate;
        private double totalCost;
        private int totalRequests;
        private double avgCostPerRequest;
        private Map<String, Double> costByScenario;
    }
    
    @Data
    @Builder
    public static class ScenarioCostStats {
        private String scenario;
        private int requestCount;
        private double totalCost;
        private double avgCost;
        private double cacheHitRate;
    }
}
