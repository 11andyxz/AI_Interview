package com.aiinterview.monitoring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Email notification service for alerts and daily digests
 */
@Service
public class EmailNotificationService {
    
    private static final Logger log = LoggerFactory.getLogger(EmailNotificationService.class);
    
    @Autowired(required = false)
    private JavaMailSender mailSender;
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @Value("${monitoring.email.enabled:false}")
    private boolean emailEnabled;
    
    @Value("${monitoring.email.from:noreply@aiinterview.com}")
    private String fromEmail;
    
    @Value("${monitoring.email.to:admin@aiinterview.com}")
    private String toEmail;
    
    /**
     * Send alert email
     */
    public void sendAlertEmail(String alertName, String message, String severity) {
        if (!emailEnabled || mailSender == null) {
            log.info("[EmailNotification] Email disabled or not configured, skipping alert: {}", alertName);
            return;
        }
        
        try {
            SimpleMailMessage mailMessage = new SimpleMailMessage();
            mailMessage.setFrom(fromEmail);
            mailMessage.setTo(toEmail);
            mailMessage.setSubject(String.format("[%s] AI Interview Alert: %s", severity.toUpperCase(), alertName));
            
            String body = buildAlertEmailBody(alertName, message, severity);
            mailMessage.setText(body);
            
            mailSender.send(mailMessage);
            log.info("[EmailNotification] Alert email sent: {}", alertName);
            
        } catch (Exception e) {
            log.error("[EmailNotification] Failed to send alert email for {}", alertName, e);
        }
    }
    
    /**
     * Build alert email body
     */
    private String buildAlertEmailBody(String alertName, String message, String severity) {
        StringBuilder body = new StringBuilder();
        body.append("AI Interview Monitoring Alert\n");
        body.append("==============================\n\n");
        body.append("Alert Name: ").append(alertName).append("\n");
        body.append("Severity: ").append(severity.toUpperCase()).append("\n");
        body.append("Time: ").append(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).append("\n");
        body.append("Message: ").append(message).append("\n\n");
        body.append("-------------------------------\n");
        body.append("Please check the monitoring dashboard for more details.\n");
        body.append("Dashboard: http://localhost:3000/monitoring\n");
        
        return body.toString();
    }
    
    /**
     * Send daily digest email at 9:00 AM every day
     */
    @Scheduled(cron = "0 0 9 * * *")
    public void sendDailyDigest() {
        if (!emailEnabled || mailSender == null) {
            log.info("[EmailNotification] Email disabled, skipping daily digest");
            return;
        }
        
        log.info("[EmailNotification] Sending daily digest...");
        
        try {
            SimpleMailMessage mailMessage = new SimpleMailMessage();
            mailMessage.setFrom(fromEmail);
            mailMessage.setTo(toEmail);
            mailMessage.setSubject("AI Interview Daily Monitoring Digest - " + LocalDateTime.now().toLocalDate());
            
            String body = buildDailyDigestBody();
            mailMessage.setText(body);
            
            mailSender.send(mailMessage);
            log.info("[EmailNotification] Daily digest email sent successfully");
            
        } catch (Exception e) {
            log.error("[EmailNotification] Failed to send daily digest", e);
        }
    }
    
    /**
     * Build daily digest email body with key metrics
     */
    private String buildDailyDigestBody() {
        StringBuilder body = new StringBuilder();
        body.append("AI Interview - Daily Monitoring Digest\n");
        body.append("=========================================\n\n");
        body.append("Date: ").append(LocalDateTime.now().toLocalDate()).append("\n\n");
        
        try {
            // Get yesterday's metrics
            Map<String, Double> metrics = getDailyMetrics();
            
            body.append("Key Metrics (Last 24 Hours):\n");
            body.append("-----------------------------\n");
            body.append(String.format("Total Requests: %.0f\n", metrics.getOrDefault("request_count", 0.0)));
            body.append(String.format("Success Rate: %.2f%%\n", metrics.getOrDefault("success_rate", 0.0) * 100));
            body.append(String.format("Validation Pass Rate: %.2f%%\n", metrics.getOrDefault("validation_pass_rate", 0.0) * 100));
            body.append(String.format("Average Latency (P95): %.0fms\n", metrics.getOrDefault("latency_p95", 0.0)));
            body.append(String.format("Error Rate: %.2f%%\n", metrics.getOrDefault("error_rate", 0.0) * 100));
            body.append(String.format("Total Cost: $%.2f\n", metrics.getOrDefault("daily_cost", 0.0)));
            body.append(String.format("Quality Score: %.2f\n", metrics.getOrDefault("quality_score", 0.0)));
            body.append("\n");
            
            // Get alerts in last 24 hours
            int criticalAlerts = getAlertCount("critical", 24);
            int warningAlerts = getAlertCount("warning", 24);
            
            body.append("Alerts (Last 24 Hours):\n");
            body.append("-----------------------\n");
            body.append(String.format("Critical: %d\n", criticalAlerts));
            body.append(String.format("Warning: %d\n", warningAlerts));
            body.append("\n");
            
            // Drift detection status
            body.append("Drift Detection Status:\n");
            body.append("-----------------------\n");
            body.append("KL Divergence: ").append(String.format("%.4f", metrics.getOrDefault("kl_divergence", 0.0))).append("\n");
            body.append("Status: ").append(metrics.getOrDefault("kl_divergence", 0.0) > 0.1 ? "DRIFT DETECTED ⚠️" : "OK ✓").append("\n");
            body.append("\n");
            
            body.append("---------------------------------------\n");
            body.append("Dashboard: http://localhost:3000/monitoring\n");
            body.append("API Docs: http://localhost:8080/swagger-ui.html\n");
            
        } catch (Exception e) {
            log.error("Failed to build daily digest metrics", e);
            body.append("Error retrieving metrics. Please check the system logs.\n");
        }
        
        return body.toString();
    }
    
    /**
     * Get daily metrics from database
     */
    private Map<String, Double> getDailyMetrics() {
        Map<String, Double> metrics = new HashMap<>();
        
        try {
            // Query aggregated metrics for last 24 hours
            String sql = "SELECT metric_name, AVG(metric_value) as avg_value FROM ai_metrics_log " +
                        "WHERE timestamp >= DATE_SUB(NOW(), INTERVAL 1 DAY) " +
                        "GROUP BY metric_name";
            
            jdbcTemplate.query(sql, rs -> {
                String metricName = rs.getString("metric_name");
                double avgValue = rs.getDouble("avg_value");
                metrics.put(metricName, avgValue);
            });
            
            // Calculate request count
            String countSql = "SELECT COUNT(*) as count FROM ai_metrics_log " +
                             "WHERE metric_name = 'ai_request_count' AND timestamp >= DATE_SUB(NOW(), INTERVAL 1 DAY)";
            Integer count = jdbcTemplate.queryForObject(countSql, Integer.class);
            if (count != null) {
                metrics.put("request_count", count.doubleValue());
            }
            
        } catch (Exception e) {
            log.error("Failed to get daily metrics", e);
        }
        
        return metrics;
    }
    
    /**
     * Get alert count by severity
     */
    private int getAlertCount(String severity, int hours) {
        try {
            String sql = "SELECT COUNT(*) FROM ai_metrics_log " +
                        "WHERE metric_name = 'alert' " +
                        "AND tags LIKE ? " +
                        "AND timestamp >= DATE_SUB(NOW(), INTERVAL ? HOUR)";
            
            Integer count = jdbcTemplate.queryForObject(sql, Integer.class, "%" + severity + "%", hours);
            return count != null ? count : 0;
            
        } catch (Exception e) {
            log.error("Failed to get alert count for severity: {}", severity, e);
            return 0;
        }
    }
}
