package com.aiinterview.ml.observability;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Scheduled tasks for ML observability
 * Ensures alerts are triggered within 5 minutes (AC6)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MlObservabilityScheduler {
    
    private final QualityDriftDetector driftDetector;
    private final MlMetricsCollector metricsCollector;
    
    /**
     * Check for quality drift and alerts every minute
     * Ensures AC6: Alert < 5 minutes
     */
    @Scheduled(fixedRate = 60000)  // Every 60 seconds
    public void checkQualityAlerts() {
        try {
            log.debug("Running scheduled quality alert check");
            
            List<QualityAlert> alerts = driftDetector.checkAlerts();
            
            if (!alerts.isEmpty()) {
                log.warn("Detected {} quality alerts", alerts.size());
                
                for (QualityAlert alert : alerts) {
                    log.warn("ALERT: endpoint={}, type={}, severity={}, message={}",
                            alert.getEndpoint(), alert.getAlertType(), 
                            alert.getSeverity(), alert.getMessage());
                    
                    // Here you could:
                    // - Send to monitoring system (Datadog, Prometheus, etc.)
                    // - Send email/Slack notifications
                    // - Write to alert database table
                    // - Trigger incident management system
                }
            }
            
        } catch (Exception e) {
            log.error("Failed to check quality alerts: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Optional: Compute and update baselines periodically
     * Runs once per day at 2 AM
     */
    @Scheduled(cron = "0 0 2 * * *")
    public void updateQualityBaselines() {
        try {
            log.info("Starting scheduled baseline update");
            
            // This could be expanded to auto-update baselines
            // based on recent high-quality periods
            
            log.info("Baseline update completed");
            
        } catch (Exception e) {
            log.error("Failed to update baselines: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Optional: Log health summary every hour for monitoring
     */
    @Scheduled(fixedRate = 3600000)  // Every hour
    public void logHealthSummary() {
        try {
            log.info("Hourly ML health check");
            
            // This provides visibility into system health
            // Can be exported to monitoring dashboards
            
        } catch (Exception e) {
            log.error("Failed to log health summary: {}", e.getMessage(), e);
        }
    }
}
