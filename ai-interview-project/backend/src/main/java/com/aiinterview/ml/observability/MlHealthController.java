package com.aiinterview.ml.observability;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * REST API for ML observability and health monitoring
 * Provides 6 endpoints for real-time quality, cost, and drift tracking
 */
@Slf4j
@RestController
@RequestMapping("/api/ml/health")
@RequiredArgsConstructor
public class MlHealthController {
    
    private final MlMetricsCollector metricsCollector;
    private final QualityDriftDetector driftDetector;
    private final CostTracker costTracker;
    private final OnlineQualitySampler qualitySampler;
    
    /**
     * GET /api/ml/health/summary
     * Overall health dashboard with status for all endpoints
     */
    @GetMapping("/summary")
    public ResponseEntity<MlHealthSummary> getHealthSummary(
        @RequestParam(defaultValue = "24") int hours
    ) {
        try {
            MlHealthSummary summary = metricsCollector.getHealthSummary(Duration.ofHours(hours));
            return ResponseEntity.ok(summary);
        } catch (Exception e) {
            log.error("Error getting health summary", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * GET /api/ml/health/quality-trends
     * Quality trends over time (daily aggregation)
     */
    @GetMapping("/quality-trends")
    public ResponseEntity<List<QualityTrend>> getQualityTrends(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
        @RequestParam(required = false) String endpoint
    ) {
        try {
            List<QualityTrend> trends = metricsCollector.getQualityTrends(startDate, endDate);
            
            // Filter by endpoint if specified
            if (endpoint != null && !endpoint.isEmpty()) {
                trends = trends.stream()
                    .filter(t -> endpoint.equals(t.getEndpoint()))
                    .toList();
            }
            
            return ResponseEntity.ok(trends);
        } catch (Exception e) {
            log.error("Error getting quality trends", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * GET /api/ml/health/latency-trends
     * Latency statistics by endpoint
     */
    @GetMapping("/latency-trends")
    public ResponseEntity<Map<String, LatencyStats>> getLatencyTrends(
        @RequestParam(defaultValue = "24") int hours
    ) {
        try {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime start = now.minusHours(hours);
            
            // Get health summary which includes latency
            MlHealthSummary summary = metricsCollector.getHealthSummary(Duration.ofHours(hours));
            
            Map<String, LatencyStats> latencyStats = new java.util.HashMap<>();
            summary.getEndpointHealth().forEach((endpoint, health) -> {
                LatencyStats stats = LatencyStats.builder()
                    .endpoint(endpoint)
                    .avgLatencyMs(health.getAvgLatency() != null ? health.getAvgLatency().doubleValue() : 0.0)
                    .totalCalls(health.getCallCount() != null ? health.getCallCount().longValue() : 0L)
                    .periodStart(start)
                    .periodEnd(now)
                    .build();
                latencyStats.put(endpoint, stats);
            });
            
            return ResponseEntity.ok(latencyStats);
        } catch (Exception e) {
            log.error("Error getting latency trends", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * GET /api/ml/health/cost-report
     * Cost breakdown and budget tracking
     */
    @GetMapping("/cost-report")
    public ResponseEntity<CostReport> getCostReport(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
        @RequestParam(defaultValue = "daily") String period  // daily, monthly
    ) {
        try {
            CostSummary costSummary;
            
            if ("monthly".equals(period)) {
                LocalDate targetDate = date != null ? date : LocalDate.now();
                costSummary = costTracker.getMonthlyCost(
                    targetDate.getYear(), 
                    targetDate.getMonthValue()
                );
            } else {
                LocalDate targetDate = date != null ? date : LocalDate.now();
                costSummary = costTracker.getDailyCost(targetDate);
            }
            
            // Get cost trends
            List<CostSummary> trends = costTracker.getCostTrend(7);
            
            // Get top cost endpoints
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime start = now.minusDays(7);
            Map<String, Double> topEndpoints = costTracker.getTopCostEndpoints(5, start, now);
            Map<String, Double> topModels = costTracker.getTopCostModels(5, start, now);
            
            // Get cost breakdowns
            CostBreakdown modelBreakdown = costTracker.getCostByModel();
            CostBreakdown endpointBreakdown = costTracker.getCostByEndpoint();
            
            CostReport report = CostReport.builder()
                .summary(costSummary)
                .trends(trends)
                .topEndpoints(topEndpoints)
                .topModels(topModels)
                .modelBreakdown(modelBreakdown)
                .endpointBreakdown(endpointBreakdown)
                .budgetExceeded(costTracker.isBudgetExceeded())
                .approachingBudget(costTracker.isApproachingBudget())
                .projectedMonthlyCost(costTracker.getProjectedMonthlyCost())
                .build();
            
            return ResponseEntity.ok(report);
        } catch (Exception e) {
            log.error("Error getting cost report", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * GET /api/ml/health/alerts
     * Active quality and cost alerts
     */
    @GetMapping("/alerts")
    public ResponseEntity<AlertsResponse> getAlerts(
        @RequestParam(defaultValue = "false") boolean includeAcknowledged
    ) {
        try {
            List<QualityAlert> alerts = driftDetector.checkAlerts();
            
            // Add cost alert if needed
            costTracker.checkCostAlert().ifPresent(alerts::add);
            
            // Filter acknowledged if requested
            if (!includeAcknowledged) {
                alerts = alerts.stream()
                    .filter(a -> !a.isAcknowledged())
                    .toList();
            }
            
            // Count by severity
            long criticalCount = alerts.stream().filter(QualityAlert::isCritical).count();
            long warningCount = alerts.stream().filter(a -> "warning".equals(a.getSeverity())).count();
            long infoCount = alerts.stream().filter(a -> "info".equals(a.getSeverity())).count();
            
            AlertsResponse response = AlertsResponse.builder()
                .alerts(alerts)
                .totalAlerts(alerts.size())
                .criticalAlerts((int) criticalCount)
                .warningAlerts((int) warningCount)
                .infoAlerts((int) infoCount)
                .retrievedAt(LocalDateTime.now())
                .build();
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error getting alerts", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * GET /api/ml/health/drift-report
     * Statistical drift detection report
     */
    @GetMapping("/drift-report")
    public ResponseEntity<Map<String, DriftReport>> getDriftReport(
        @RequestParam(required = false) String endpoint,
        @RequestParam(defaultValue = "1") int hours
    ) {
        try {
            Map<String, DriftReport> reports = new java.util.HashMap<>();
            
            if (endpoint != null && !endpoint.isEmpty()) {
                // Single endpoint
                DriftReport report = driftDetector.detectDrift(endpoint, Duration.ofHours(hours));
                reports.put(endpoint, report);
            } else {
                // All endpoints
                MlHealthSummary summary = metricsCollector.getHealthSummary(Duration.ofHours(hours));
                for (String ep : summary.getEndpointHealth().keySet()) {
                    DriftReport report = driftDetector.detectDrift(ep, Duration.ofHours(hours));
                    reports.put(ep, report);
                }
            }
            
            return ResponseEntity.ok(reports);
        } catch (Exception e) {
            log.error("Error getting drift report", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    // DTOs
    
    @lombok.Data
    @lombok.Builder
    public static class LatencyStats {
        private String endpoint;
        private Double avgLatencyMs;
        private Long totalCalls;
        private LocalDateTime periodStart;
        private LocalDateTime periodEnd;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class CostReport {
        private CostSummary summary;
        private List<CostSummary> trends;
        private Map<String, Double> topEndpoints;
        private Map<String, Double> topModels;
        private CostBreakdown modelBreakdown;
        private CostBreakdown endpointBreakdown;
        private boolean budgetExceeded;
        private boolean approachingBudget;
        private double projectedMonthlyCost;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class AlertsResponse {
        private List<QualityAlert> alerts;
        private int totalAlerts;
        private int criticalAlerts;
        private int warningAlerts;
        private int infoAlerts;
        private LocalDateTime retrievedAt;
    }
}
