package com.aiinterview.monitoring;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Slack notification service for alerts
 * Sends alerts to Slack channel via webhook
 */
@Service
public class SlackNotificationService {
    
    private static final Logger log = LoggerFactory.getLogger(SlackNotificationService.class);
    
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Value("${monitoring.slack.enabled:false}")
    private boolean slackEnabled;
    
    @Value("${monitoring.slack.webhook.url:}")
    private String webhookUrl;
    
    /**
     * Send alert to Slack
     */
    public void sendAlert(String alertName, String message, String severity) {
        if (!slackEnabled || webhookUrl == null || webhookUrl.isEmpty()) {
            log.info("[SlackNotification] Slack disabled or not configured, skipping alert: {}", alertName);
            return;
        }
        
        try {
            Map<String, Object> slackMessage = buildSlackMessage(alertName, message, severity);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            String json = objectMapper.writeValueAsString(slackMessage);
            HttpEntity<String> request = new HttpEntity<>(json, headers);
            
            restTemplate.postForEntity(webhookUrl, request, String.class);
            
            log.info("[SlackNotification] Alert sent to Slack: {}", alertName);
            
        } catch (Exception e) {
            log.error("[SlackNotification] Failed to send alert to Slack: {}", alertName, e);
        }
    }
    
    /**
     * Build Slack message with rich formatting
     */
    private Map<String, Object> buildSlackMessage(String alertName, String message, String severity) {
        Map<String, Object> slackMessage = new HashMap<>();
        
        // Determine emoji and color based on severity
        String emoji;
        String color;
        
        switch (severity.toLowerCase()) {
            case "critical":
                emoji = ":rotating_light:";
                color = "#FF0000"; // Red
                break;
            case "warning":
                emoji = ":warning:";
                color = "#FFA500"; // Orange
                break;
            default:
                emoji = ":information_source:";
                color = "#0000FF"; // Blue
                break;
        }
        
        slackMessage.put("text", emoji + " AI Interview Monitoring Alert");
        
        // Create rich attachment
        Map<String, Object> attachment = new HashMap<>();
        attachment.put("color", color);
        attachment.put("title", alertName);
        attachment.put("text", message);
        attachment.put("footer", "AI Interview Monitoring");
        attachment.put("ts", System.currentTimeMillis() / 1000);
        
        // Add fields
        Map<String, Object>[] fields = new Map[2];
        
        Map<String, Object> field1 = new HashMap<>();
        field1.put("title", "Severity");
        field1.put("value", severity.toUpperCase());
        field1.put("short", true);
        fields[0] = field1;
        
        Map<String, Object> field2 = new HashMap<>();
        field2.put("title", "Time");
        field2.put("value", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        field2.put("short", true);
        fields[1] = field2;
        
        attachment.put("fields", fields);
        
        slackMessage.put("attachments", new Object[]{attachment});
        
        return slackMessage;
    }
    
    /**
     * Send daily digest to Slack
     */
    public void sendDailyDigest(Map<String, Double> metrics, int criticalAlerts, int warningAlerts) {
        if (!slackEnabled || webhookUrl == null || webhookUrl.isEmpty()) {
            log.info("[SlackNotification] Slack disabled, skipping daily digest");
            return;
        }
        
        try {
            Map<String, Object> slackMessage = new HashMap<>();
            slackMessage.put("text", ":chart_with_upwards_trend: AI Interview Daily Digest");
            
            Map<String, Object> attachment = new HashMap<>();
            attachment.put("color", "#36a64f"); // Green
            attachment.put("title", "Daily Monitoring Summary - " + LocalDateTime.now().toLocalDate());
            
            StringBuilder text = new StringBuilder();
            text.append("*Key Metrics (Last 24 Hours):*\n");
            text.append(String.format("• Total Requests: %.0f\n", metrics.getOrDefault("request_count", 0.0)));
            text.append(String.format("• Success Rate: %.2f%%\n", metrics.getOrDefault("success_rate", 0.0) * 100));
            text.append(String.format("• Validation Pass Rate: %.2f%%\n", metrics.getOrDefault("validation_pass_rate", 0.0) * 100));
            text.append(String.format("• Average Latency (P95): %.0fms\n", metrics.getOrDefault("latency_p95", 0.0)));
            text.append(String.format("• Total Cost: $%.2f\n", metrics.getOrDefault("daily_cost", 0.0)));
            text.append(String.format("\n*Alerts:* %d critical, %d warning", criticalAlerts, warningAlerts));
            
            attachment.put("text", text.toString());
            attachment.put("mrkdwn_in", new String[]{"text"});
            
            slackMessage.put("attachments", new Object[]{attachment});
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            String json = objectMapper.writeValueAsString(slackMessage);
            HttpEntity<String> request = new HttpEntity<>(json, headers);
            
            restTemplate.postForEntity(webhookUrl, request, String.class);
            
            log.info("[SlackNotification] Daily digest sent to Slack");
            
        } catch (Exception e) {
            log.error("[SlackNotification] Failed to send daily digest to Slack", e);
        }
    }
}
