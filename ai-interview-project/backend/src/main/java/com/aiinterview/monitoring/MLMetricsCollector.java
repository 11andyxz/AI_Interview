package com.aiinterview.monitoring;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Collects and persists ML metrics to database
 */
@Service
public class MLMetricsCollector {
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    /**
     * Record a metric value to database
     */
    public void recordMetric(String metricName, double value, String modelVersion, String endpoint, Map<String, Object> tags) {
        String sql = "INSERT INTO ai_metrics_log (metric_name, metric_value, model_version, endpoint, tags) VALUES (?, ?, ?, ?, ?)";
        String tagsJson = tags != null ? convertToJson(tags) : "{}";
        jdbcTemplate.update(sql, metricName, value, modelVersion, endpoint, tagsJson);
    }
    
    /**
     * Record request metrics (latency, tokens, cost)
     */
    public void recordRequest(String endpoint, String modelVersion, long latencyMs, int tokens, boolean success) {
        Map<String, Object> tags = new HashMap<>();
        tags.put("status", success ? "success" : "failure");
        
        recordMetric("ai_request_count", 1, modelVersion, endpoint, tags);
        recordMetric("ai_request_latency_ms", latencyMs, modelVersion, endpoint, tags);
        recordMetric("ai_token_usage", tokens, modelVersion, endpoint, tags);
        
        // Calculate cost based on model pricing
        double cost = calculateCost(modelVersion, tokens);
        recordMetric("ai_cost_per_request", cost, modelVersion, endpoint, tags);
    }
    
    /**
     * Record validation result
     */
    public void recordValidation(String endpoint, String modelVersion, boolean passed) {
        Map<String, Object> tags = new HashMap<>();
        tags.put("result", passed ? "pass" : "fail");
        recordMetric("ai_validation_pass_rate", passed ? 1.0 : 0.0, modelVersion, endpoint, tags);
    }
    
    /**
     * Record retry event
     */
    public void recordRetry(String endpoint, String modelVersion, String reason) {
        Map<String, Object> tags = new HashMap<>();
        tags.put("reason", reason);
        recordMetric("ai_retry_count", 1, modelVersion, endpoint, tags);
    }
    
    /**
     * Record fallback activation
     */
    public void recordFallback(String endpoint, String modelVersion) {
        Map<String, Object> tags = new HashMap<>();
        recordMetric("ai_fallback_triggered", 1, modelVersion, endpoint, tags);
    }
    
    /**
     * Record quality score
     */
    public void recordQualityScore(String endpoint, String modelVersion, double score) {
        Map<String, Object> tags = new HashMap<>();
        recordMetric("ai_quality_score", score, modelVersion, endpoint, tags);
    }
    
    /**
     * Get aggregated metrics for dashboard
     */
    public Map<String, Object> getAggregatedMetrics(int minutesBack) {
        Map<String, Object> metrics = new HashMap<>();
        LocalDateTime since = LocalDateTime.now().minusMinutes(minutesBack);
        
        // Request count
        metrics.put("ai_request_count", getSum("ai_request_count", since));
        
        // Latency percentiles (simplified - using avg, min, max as approximation)
        metrics.put("ai_request_latency_p50", getAverage("ai_request_latency_ms", since));
        metrics.put("ai_request_latency_p95", getPercentile("ai_request_latency_ms", since, 0.95));
        metrics.put("ai_request_latency_p99", getPercentile("ai_request_latency_ms", since, 0.99));
        
        // Token usage
        metrics.put("ai_token_usage", getSum("ai_token_usage", since));
        
        // Validation pass rate
        metrics.put("ai_validation_pass_rate", getAverage("ai_validation_pass_rate", since));
        
        // Retry count
        metrics.put("ai_retry_count", getSum("ai_retry_count", since));
        
        // Fallback count
        metrics.put("ai_fallback_triggered", getSum("ai_fallback_triggered", since));
        
        // Quality score
        metrics.put("ai_quality_score", getAverage("ai_quality_score", since));
        
        // Cost metrics
        metrics.put("ai_cost_per_request", getAverage("ai_cost_per_request", since));
        
        // Derived metrics
        double totalRequests = (double) metrics.get("ai_request_count");
        metrics.put("error_rate", totalRequests > 0 ? 1 - (double) metrics.get("ai_validation_pass_rate") : 0);
        metrics.put("success_rate", metrics.get("ai_validation_pass_rate"));
        metrics.put("tokens_per_hour", (double) metrics.get("ai_token_usage") * 60 / minutesBack);
        metrics.put("daily_cost", (double) metrics.get("ai_cost_per_request") * totalRequests * 24 * 60 / minutesBack);
        
        metrics.put("timestamp", LocalDateTime.now());
        return metrics;
    }
    
    private double getSum(String metricName, LocalDateTime since) {
        String sql = "SELECT COALESCE(SUM(metric_value), 0) FROM ai_metrics_log WHERE metric_name = ? AND timestamp >= ?";
        return jdbcTemplate.queryForObject(sql, Double.class, metricName, since);
    }
    
    private double getAverage(String metricName, LocalDateTime since) {
        String sql = "SELECT COALESCE(AVG(metric_value), 0) FROM ai_metrics_log WHERE metric_name = ? AND timestamp >= ?";
        return jdbcTemplate.queryForObject(sql, Double.class, metricName, since);
    }
    
    private double getPercentile(String metricName, LocalDateTime since, double percentile) {
        // Simplified percentile calculation (actual implementation would use window functions)
        String sql = "SELECT metric_value FROM ai_metrics_log WHERE metric_name = ? AND timestamp >= ? ORDER BY metric_value";
        List<Double> values = jdbcTemplate.queryForList(sql, Double.class, metricName, since);
        if (values.isEmpty()) return 0;
        int index = (int) (values.size() * percentile);
        return values.get(Math.min(index, values.size() - 1));
    }
    
    private double calculateCost(String modelVersion, int tokens) {
        // Simplified cost calculation (USD per 1K tokens)
        double costPer1KTokens = modelVersion.contains("gpt-4") ? 0.03 : 0.002;
        return (tokens / 1000.0) * costPer1KTokens;
    }
    
    private String convertToJson(Map<String, Object> map) {
        // Simple JSON conversion (production should use Jackson)
        StringBuilder json = new StringBuilder("{");
        int i = 0;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (i > 0) json.append(",");
            json.append("\"").append(entry.getKey()).append("\":\"").append(entry.getValue()).append("\"");
            i++;
        }
        json.append("}");
        return json.toString();
    }
}
