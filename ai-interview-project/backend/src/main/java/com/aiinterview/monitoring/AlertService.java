package com.aiinterview.monitoring;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Alert evaluation and notification service
 * Checks metrics against alert rules with duration thresholds per task requirements
 */
@Service
public class AlertService {
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @Autowired
    private MLMetricsCollector metricsCollector;
    
    @Autowired(required = false)
    private EmailNotificationService emailService;
    
    @Autowired(required = false)
    private SlackNotificationService slackService;
    
    private List<Map<String, Object>> alertRules = new ArrayList<>();
    
    // Track alert states: alertName -> {startTime, lastValue}
    private Map<String, Map<String, Object>> alertStates = new ConcurrentHashMap<>();
    
    // Track baseline for comparison
    private Map<String, Double> baselineMetrics = new ConcurrentHashMap<>();
    
    public AlertService() {
        loadAlertRules();
    }
    
    /**
     * Load alert rules from alerts.yml
     */
    private void loadAlertRules() {
        try {
            Yaml yaml = new Yaml();
            InputStream inputStream = this.getClass().getResourceAsStream("/alerts.yml");
            if (inputStream != null) {
                Map<String, Object> config = yaml.load(inputStream);
                Map<String, Object> alerts = (Map<String, Object>) config.get("alerts");
                
                if (alerts.containsKey("critical")) {
                    alertRules.addAll((List<Map<String, Object>>) alerts.get("critical"));
                }
                if (alerts.containsKey("warning")) {
                    alertRules.addAll((List<Map<String, Object>>) alerts.get("warning"));
                }
            }
        } catch (Exception e) {
            System.err.println("[AlertService] Failed to load alerts.yml: " + e.getMessage());
        }
    }
    
    /**
     * Evaluate all alert rules (runs every 1 minute per task requirements)
     */
    @Scheduled(fixedRate = 60000)
    public void evaluateAlerts() {
        Map<String, Object> metrics = metricsCollector.getAggregatedMetrics(60);
        
        // Update baseline every hour
        if (baselineMetrics.isEmpty()) {
            updateBaseline();
        }
        
        for (Map<String, Object> rule : alertRules) {
            String alertName = (String) rule.get("name");
            String metricName = (String) rule.get("metric");
            String condition = (String) rule.get("condition");
            Object threshold = rule.get("threshold");
            int durationMinutes = rule.get("duration_minutes") != null ? 
                ((Number) rule.get("duration_minutes")).intValue() : 0;
            String severity = (String) rule.get("severity");
            String description = (String) rule.get("description");
            
            if (!metrics.containsKey(metricName)) continue;
            
            double metricValue = ((Number) metrics.get(metricName)).doubleValue();
            boolean thresholdExceeded = checkThreshold(condition, metricValue, threshold, metricName);
            
            if (thresholdExceeded) {
                handleThresholdExceeded(alertName, metricValue, durationMinutes, description, severity);
            } else {
                // Clear alert state if condition no longer met
                alertStates.remove(alertName);
            }
        }
    }
    
    private boolean checkThreshold(String condition, double metricValue, Object threshold, String metricName) {
        double thresholdValue = ((Number) threshold).doubleValue();
        
        switch (condition) {
            case "less_than":
                return metricValue < thresholdValue;
            case "greater_than":
                return metricValue > thresholdValue;
            case "cost_spike_3x":
                // Check if current cost > 3x baseline average
                Double baselineCost = baselineMetrics.get("daily_cost");
                return baselineCost != null && metricValue > (baselineCost * thresholdValue);
            case "increased_50_percent":
                // Check if retry count increased >50% from baseline
                Double baselineRetry = baselineMetrics.get("ai_retry_count");
                return baselineRetry != null && metricValue > (baselineRetry * thresholdValue);
            default:
                return false;
        }
    }
    
    private void handleThresholdExceeded(String alertName, double value, int durationMinutes, 
                                         String description, String severity) {
        LocalDateTime now = LocalDateTime.now();
        
        if (!alertStates.containsKey(alertName)) {
            // First time threshold exceeded - record start time
            Map<String, Object> state = new HashMap<>();
            state.put("startTime", now);
            state.put("lastValue", value);
            alertStates.put(alertName, state);
            return;
        }
        
        // Check if duration threshold met
        Map<String, Object> state = alertStates.get(alertName);
        LocalDateTime startTime = (LocalDateTime) state.get("startTime");
        long minutesExceeded = java.time.Duration.between(startTime, now).toMinutes();
        
        if (minutesExceeded >= durationMinutes) {
            // Duration threshold met - send alert (only once)
            if (!state.containsKey("alerted")) {
                sendAlert(alertName, description + " (value: " + value + ", duration: " + minutesExceeded + " min)", severity);
                state.put("alerted", true);
            }
        }
        
        state.put("lastValue", value);
    }
    
    private void updateBaseline() {
        try {
            // Calculate 30-day baseline averages
            String sql = "SELECT metric_name, AVG(metric_value) as avg_value FROM ai_metrics_log " +
                        "WHERE timestamp >= DATE_SUB(NOW(), INTERVAL 30 DAY) " +
                        "GROUP BY metric_name";
            jdbcTemplate.query(sql, (rs) -> {
                baselineMetrics.put(rs.getString("metric_name"), rs.getDouble("avg_value"));
            });
            System.out.println("[AlertService] Baseline metrics updated: " + baselineMetrics.size() + " metrics");
        } catch (Exception e) {
            System.err.println("[AlertService] Failed to update baseline: " + e.getMessage());
        }
    }
    
    /**
     * Send alert notification
     */
    public void sendAlert(String alertName, String message, String severity) {
        System.out.println(String.format("[ALERT %s] %s - %s", severity.toUpperCase(), alertName, message));
        
        // Log to database for tracking
        String sql = "INSERT INTO ai_metrics_log (metric_name, metric_value, endpoint, tags) VALUES (?, ?, ?, ?)";
        String tags = String.format("{\"severity\":\"%s\",\"message\":\"%s\"}", severity, message);
        jdbcTemplate.update(sql, "alert_triggered", 1.0, alertName, tags);
        
        // Send email notification
        if (emailService != null) {
            try {
                emailService.sendAlertEmail(alertName, message, severity);
            } catch (Exception e) {
                System.err.println("[AlertService] Failed to send email: " + e.getMessage());
            }
        }
        
        // Send Slack notification
        if (slackService != null) {
            try {
                slackService.sendAlert(alertName, message, severity);
            } catch (Exception e) {
                System.err.println("[AlertService] Failed to send Slack notification: " + e.getMessage());
            }
        }
    }
    
    /**
     * Get recent alerts (for dashboard)
     */
    public List<Map<String, Object>> getRecentAlerts(int hours) {
        String sql = "SELECT metric_name as alert_name, tags, timestamp FROM ai_metrics_log " +
                    "WHERE metric_name = 'alert_triggered' AND timestamp >= DATE_SUB(NOW(), INTERVAL ? HOUR) " +
                    "ORDER BY timestamp DESC LIMIT 50";
        
        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            Map<String, Object> alert = new HashMap<>();
            alert.put("name", rs.getString("alert_name"));
            alert.put("tags", rs.getString("tags"));
            alert.put("timestamp", rs.getTimestamp("timestamp").toLocalDateTime());
            return alert;
        }, hours);
    }
    
    /**
     * Generate daily digest
     */
    @Scheduled(cron = "0 0 8 * * *") // Every day at 8 AM
    public void generateDailyDigest() {
        Map<String, Object> metrics = metricsCollector.getAggregatedMetrics(1440); // Last 24 hours
        
        StringBuilder digest = new StringBuilder();
        digest.append("=== ML Observability Daily Digest ===\n");
        digest.append("Date: ").append(LocalDateTime.now()).append("\n\n");
        digest.append("Total Requests: ").append(metrics.get("ai_request_count")).append("\n");
        digest.append("Success Rate: ").append(String.format("%.2f%%", (double) metrics.get("success_rate") * 100)).append("\n");
        digest.append("Avg Quality Score: ").append(String.format("%.2f", metrics.get("ai_quality_score"))).append("\n");
        digest.append("Daily Cost: $").append(String.format("%.2f", metrics.get("daily_cost"))).append("\n");
        digest.append("P95 Latency: ").append(String.format("%.2fs", (double) metrics.get("ai_request_latency_p95") / 1000)).append("\n");
        
        List<Map<String, Object>> alerts = getRecentAlerts(24);
        digest.append("\nAlerts (24h): ").append(alerts.size()).append("\n");
        
        System.out.println(digest.toString());
        
        // In production: send via email
    }
}
