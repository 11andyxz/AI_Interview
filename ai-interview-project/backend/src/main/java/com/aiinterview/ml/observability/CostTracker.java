package com.aiinterview.ml.observability;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Tracks LLM API costs and manages budgets
 * Provides cost breakdowns by endpoint, model, and time period
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CostTracker {
    
    private final LlmCallMetricRepository metricRepository;
    
    // Budget limits per month
    private final Map<String, Double> monthlyBudgetLimits = new ConcurrentHashMap<>();
    
    // Default budget: $1000/month
    private static final double DEFAULT_MONTHLY_BUDGET = 1000.0;
    
    /**
     * Get daily cost summary
     */
    public CostSummary getDailyCost(LocalDate date) {
        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = startOfDay.plusDays(1);
        
        return computeCostSummary(startOfDay, endOfDay, date, getMonthlyBudgetLimit());
    }
    
    /**
     * Get monthly cost summary
     */
    public CostSummary getMonthlyCost(int year, int month) {
        LocalDate firstDayOfMonth = LocalDate.of(year, month, 1);
        LocalDate lastDayOfMonth = firstDayOfMonth.with(TemporalAdjusters.lastDayOfMonth());
        
        LocalDateTime startOfMonth = firstDayOfMonth.atStartOfDay();
        LocalDateTime endOfMonth = lastDayOfMonth.atTime(23, 59, 59);
        
        return computeCostSummary(startOfMonth, endOfMonth, firstDayOfMonth, getMonthlyBudgetLimit());
    }
    
    /**
     * Get monthly cost summary (YearMonth overload)
     */
    public CostSummary getMonthlyCost(YearMonth month) {
        return getMonthlyCost(month.getYear(), month.getMonthValue());
    }
    
    /**
     * Get cost for custom date range
     */
    public CostSummary getCostForRange(LocalDateTime start, LocalDateTime end) {
        return computeCostSummary(start, end, start.toLocalDate(), getMonthlyBudgetLimit());
    }
    
    /**
     * Get cost breakdown by endpoint
     */
    public Map<String, Double> getCostByEndpoint(LocalDateTime start, LocalDateTime end) {
        List<Object[]> results = metricRepository.getCostByEndpoint(start, end);
        
        return results.stream()
            .collect(Collectors.toMap(
                row -> (String) row[0],
                row -> ((Number) row[1]).doubleValue(),
                Double::sum,
                LinkedHashMap::new
            ));
    }
    
    /**
     * Get cost breakdown by model
     */
    public Map<String, Double> getCostByModel(LocalDateTime start, LocalDateTime end) {
        List<Object[]> results = metricRepository.getCostByModel(start, end);
        
        return results.stream()
            .collect(Collectors.toMap(
                row -> (String) row[0],
                row -> ((Number) row[1]).doubleValue(),
                Double::sum,
                LinkedHashMap::new
            ));
    }
    
    /**
     * Set monthly budget limit
     * Alias for spec compliance: setBudgetLimit(double monthlyLimitUsd)
     */
    public void setBudgetLimit(double monthlyLimitUsd) {
        String currentMonth = LocalDate.now().getYear() + "-" + LocalDate.now().getMonthValue();
        monthlyBudgetLimits.put(currentMonth, monthlyLimitUsd);
        log.info("Set monthly budget limit: ${}", monthlyLimitUsd);
    }
    
    /**
     * Set monthly budget limit (alternative method name)
     */
    public void setMonthlyBudgetLimit(double limit) {
        setBudgetLimit(limit);
    }
    
    /**
     * Get monthly budget limit
     */
    public double getMonthlyBudgetLimit() {
        String currentMonth = LocalDate.now().getYear() + "-" + LocalDate.now().getMonthValue();
        return monthlyBudgetLimits.getOrDefault(currentMonth, DEFAULT_MONTHLY_BUDGET);
    }
    
    /**
     * Check if budget is exceeded
     */
    public boolean isBudgetExceeded() {
        LocalDate now = LocalDate.now();
        CostSummary monthlyCost = getMonthlyCost(now.getYear(), now.getMonthValue());
        return monthlyCost.isBudgetExceeded();
    }
    
    /**
     * Check if approaching budget (>80%)
     */
    public boolean isApproachingBudget() {
        LocalDate now = LocalDate.now();
        CostSummary monthlyCost = getMonthlyCost(now.getYear(), now.getMonthValue());
        return monthlyCost.isApproachingBudget();
    }
    
    /**
     * Get projected monthly cost based on current usage
     */
    public double getProjectedMonthlyCost() {
        LocalDate now = LocalDate.now();
        CostSummary monthlyCost = getMonthlyCost(now.getYear(), now.getMonthValue());
        return monthlyCost.getProjectedMonthlyCost();
    }
    
    /**
     * Compute cost summary for a time period
     */
    private CostSummary computeCostSummary(LocalDateTime start, LocalDateTime end, 
                                          LocalDate date, double budgetLimit) {
        Double totalCost = metricRepository.getTotalCost(start, end);
        if (totalCost == null) {
            totalCost = 0.0;
        }
        
        // Get call count
        long totalCalls = metricRepository.countByCreatedAtBetween(start, end);
        
        // Get total tokens
        long totalTokens = metricRepository.getTotalTokens(start, end);
        
        // Get cost breakdowns
        Map<String, Double> costByEndpoint = getCostByEndpoint(start, end);
        Map<String, Double> costByModel = getCostByModel(start, end);
        
        // Calculate averages
        double avgCostPerCall = totalCalls > 0 ? totalCost / totalCalls : 0.0;
        double avgCostPer1KTokens = totalTokens > 0 ? (totalCost / totalTokens) * 1000 : 0.0;
        
        // Calculate budget utilization
        double budgetUtilization = budgetLimit > 0 ? (totalCost / budgetLimit) * 100 : 0.0;
        
        return CostSummary.builder()
            .date(date)
            .totalCost(totalCost)
            .totalCalls((int) totalCalls)
            .totalTokens(totalTokens)
            .avgCostPerCall(avgCostPerCall)
            .avgCostPer1KTokens(avgCostPer1KTokens)
            .costByEndpoint(costByEndpoint)
            .costByModel(costByModel)
            .budgetLimit(budgetLimit)
            .budgetUtilization(budgetUtilization)
            .build();
    }
    
    /**
     * Generate cost alert if needed
     */
    public Optional<QualityAlert> checkCostAlert() {
        if (isApproachingBudget()) {
            LocalDate now = LocalDate.now();
            CostSummary monthlyCost = getMonthlyCost(now.getYear(), now.getMonthValue());
            
            String severity = monthlyCost.isBudgetExceeded() ? "critical" : "warning";
            String message = monthlyCost.isBudgetExceeded() 
                ? String.format("Monthly budget exceeded: $%.2f / $%.2f", 
                               monthlyCost.getTotalCost(), monthlyCost.getBudgetLimit())
                : String.format("Approaching monthly budget: $%.2f / $%.2f (%.1f%%)", 
                               monthlyCost.getTotalCost(), monthlyCost.getBudgetLimit(), 
                               monthlyCost.getBudgetUtilization());
            
            QualityAlert alert = QualityAlert.builder()
                .id(UUID.randomUUID().toString())
                .endpoint("all")
                .alertType("cost_spike")
                .severity(severity)
                .message(message)
                .currentValue(monthlyCost.getTotalCost())
                .threshold(monthlyCost.getBudgetLimit())
                .triggeredAt(LocalDateTime.now())
                .acknowledged(false)
                .build();
            
            return Optional.of(alert);
        }
        
        return Optional.empty();
    }
    
    /**
     * Get cost trend (daily costs for last N days)
     */
    public List<CostSummary> getCostTrend(int days) {
        List<CostSummary> trend = new ArrayList<>();
        LocalDate today = LocalDate.now();
        
        for (int i = days - 1; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            CostSummary summary = getDailyCost(date);
            trend.add(summary);
        }
        
        return trend;
    }
    
    /**
     * Get top cost endpoints
     */
    public Map<String, Double> getTopCostEndpoints(int limit, LocalDateTime start, LocalDateTime end) {
        Map<String, Double> costByEndpoint = getCostByEndpoint(start, end);
        
        return costByEndpoint.entrySet().stream()
            .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
            .limit(limit)
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                Map.Entry::getValue,
                (e1, e2) -> e1,
                LinkedHashMap::new
            ));
    }
    
    /**
     * Get top cost models
     */
    public Map<String, Double> getTopCostModels(int limit, LocalDateTime start, LocalDateTime end) {
        Map<String, Double> costByModel = getCostByModel(start, end);
        
        return costByModel.entrySet().stream()
            .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
            .limit(limit)
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                Map.Entry::getValue,
                (e1, e2) -> e1,
                LinkedHashMap::new
            ));
    }
    
    /**
     * Get cost breakdown by model
     */
    public CostBreakdown getCostByModel() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startOfMonth = now.with(TemporalAdjusters.firstDayOfMonth()).toLocalDate().atStartOfDay();
        
        Map<String, Double> costByModel = getCostByModel(startOfMonth, now);
        Map<String, Long> callsByModel = new HashMap<>();
        Map<String, Long> tokensByModel = new HashMap<>();
        
        // Get call and token counts per model
        List<String> models = metricRepository.findAllModels();
        for (String model : models) {
            List<LlmCallMetric> metrics = metricRepository.findByModelAndCreatedAtBetween(model, startOfMonth, now);
            callsByModel.put(model, (long) metrics.size());
            long tokens = metrics.stream()
                .mapToLong(m -> m.getInputTokens() + m.getOutputTokens())
                .sum();
            tokensByModel.put(model, tokens);
        }
        
        double totalCost = costByModel.values().stream().mapToDouble(Double::doubleValue).sum();
        long totalCalls = callsByModel.values().stream().mapToLong(Long::longValue).sum();
        long totalTokens = tokensByModel.values().stream().mapToLong(Long::longValue).sum();
        
        return CostBreakdown.builder()
            .totalCost(totalCost)
            .totalCalls(totalCalls)
            .totalTokens(totalTokens)
            .costByCategory(costByModel)
            .callsByCategory(callsByModel)
            .tokensByCategory(tokensByModel)
            .breakdownType("model")
            .periodStart(startOfMonth)
            .periodEnd(now)
            .build();
    }
    
    /**
     * Get cost breakdown by endpoint
     */
    public CostBreakdown getCostByEndpoint() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startOfMonth = now.with(TemporalAdjusters.firstDayOfMonth()).toLocalDate().atStartOfDay();
        
        Map<String, Double> costByEndpoint = getCostByEndpoint(startOfMonth, now);
        Map<String, Long> callsByEndpoint = new HashMap<>();
        Map<String, Long> tokensByEndpoint = new HashMap<>();
        
        // Get call and token counts per endpoint
        List<String> endpoints = metricRepository.findAllEndpoints();
        for (String endpoint : endpoints) {
            List<LlmCallMetric> metrics = metricRepository.findByEndpointAndCreatedAtBetween(endpoint, startOfMonth, now);
            callsByEndpoint.put(endpoint, (long) metrics.size());
            long tokens = metrics.stream()
                .mapToLong(m -> m.getInputTokens() + m.getOutputTokens())
                .sum();
            tokensByEndpoint.put(endpoint, tokens);
        }
        
        double totalCost = costByEndpoint.values().stream().mapToDouble(Double::doubleValue).sum();
        long totalCalls = callsByEndpoint.values().stream().mapToLong(Long::longValue).sum();
        long totalTokens = tokensByEndpoint.values().stream().mapToLong(Long::longValue).sum();
        
        return CostBreakdown.builder()
            .totalCost(totalCost)
            .totalCalls(totalCalls)
            .totalTokens(totalTokens)
            .costByCategory(costByEndpoint)
            .callsByCategory(callsByEndpoint)
            .tokensByCategory(tokensByEndpoint)
            .breakdownType("endpoint")
            .periodStart(startOfMonth)
            .periodEnd(now)
            .build();
    }
}
