package com.aiinterview.ml.guardrails;

import com.aiinterview.ml.guardrails.model.LlmOutputConformance;
import com.aiinterview.ml.guardrails.repository.LlmOutputConformanceRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class OutputConformanceTracker {

    private static final Logger logger = LoggerFactory.getLogger(OutputConformanceTracker.class);
    private static final double INITIAL_VALID_RATE_THRESHOLD = 0.70;
    private static final double FALLBACK_RATE_THRESHOLD = 0.10;

    private final LlmOutputConformanceRepository repository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OutputConformanceTracker(LlmOutputConformanceRepository repository) {
        this.repository = repository;
    }

    public void recordConformance(ConformanceRecord record) {
        LlmOutputConformance entity = new LlmOutputConformance();
        entity.setEndpoint(record.getEndpoint());
        entity.setModel(record.getModel() != null ? record.getModel() : "unknown");
        entity.setPromptVersion(record.getPromptVersion());
        entity.setInitialValid(record.isInitialValid());
        entity.setRepairAttempts(record.getRepairAttempts());
        entity.setFinalValid(record.isFinalValid());
        entity.setUsedFallback(record.isUsedFallback());
        entity.setTotalLatencyMs(record.getTotalLatencyMs());

        if (record.getValidationErrors() != null && !record.getValidationErrors().isEmpty()) {
            try {
                entity.setValidationErrors(objectMapper.writeValueAsString(record.getValidationErrors()));
            } catch (Exception e) {
                entity.setValidationErrors(String.join("; ", record.getValidationErrors()));
            }
        }

        repository.save(entity);
    }

    public ConformanceSummary getConformanceRate(String endpoint, Duration window) {
        LocalDateTime since = LocalDateTime.now().minus(window);

        long total = repository.countByEndpointAndCreatedAtAfter(endpoint, since);
        if (total == 0) {
            ConformanceSummary summary = new ConformanceSummary();
            summary.setEndpoint(endpoint);
            return summary;
        }

        long initialValid = repository.countByEndpointAndInitialValidAndCreatedAtAfter(endpoint, true, since);
        long finalValid = repository.countByEndpointAndFinalValidAndCreatedAtAfter(endpoint, true, since);
        long fallback = repository.countByEndpointAndUsedFallbackAndCreatedAtAfter(endpoint, true, since);

        long needsRepair = total - initialValid;
        long repairSuccess = finalValid - initialValid;

        ConformanceSummary summary = new ConformanceSummary();
        summary.setEndpoint(endpoint);
        summary.setTotalCalls(total);
        summary.setInitialValidCount(initialValid);
        summary.setFinalValidCount(finalValid);
        summary.setFallbackCount(fallback);
        summary.setInitialValidRate((double) initialValid / total);
        summary.setFinalValidRate((double) finalValid / total);
        summary.setRepairSuccessRate(needsRepair > 0 ? (double) repairSuccess / needsRepair : 1.0);
        summary.setFallbackRate((double) fallback / total);

        List<LlmOutputConformance> records = repository.findByEndpointAndCreatedAtAfter(endpoint, since);
        summary.setAvgLatencyMs(records.stream().mapToLong(LlmOutputConformance::getTotalLatencyMs).average().orElse(0));

        return summary;
    }

    /**
     * Scheduled check every 15 minutes for conformance degradation.
     */
    @Scheduled(fixedRate = 900000)
    public void monitorConformance() {
        List<ConformanceAlert> alerts = checkConformanceAlerts();
        for (ConformanceAlert alert : alerts) {
            logger.warn("Conformance alert [{}]: {} — {} (current: {:.2f}%, threshold: {:.2f}%)",
                    alert.getSeverity(), alert.getEndpoint(), alert.getMessage(),
                    alert.getCurrentRate() * 100, alert.getThreshold() * 100);
        }
    }

    public List<ConformanceAlert> checkConformanceAlerts() {
        List<ConformanceAlert> alerts = new ArrayList<>();
        LocalDateTime since = LocalDateTime.now().minusMinutes(15);
        List<String> endpoints = repository.findDistinctEndpointsSince(since);

        for (String endpoint : endpoints) {
            ConformanceSummary summary = getConformanceRate(endpoint, Duration.ofMinutes(15));

            if (summary.getTotalCalls() < 5) continue;

            if (summary.getInitialValidRate() < INITIAL_VALID_RATE_THRESHOLD) {
                alerts.add(new ConformanceAlert(
                        endpoint, "warning",
                        "Initial validation rate below threshold",
                        summary.getInitialValidRate(), INITIAL_VALID_RATE_THRESHOLD));
            }

            if (summary.getFallbackRate() > FALLBACK_RATE_THRESHOLD) {
                alerts.add(new ConformanceAlert(
                        endpoint, "critical",
                        "Fallback rate exceeds threshold",
                        summary.getFallbackRate(), FALLBACK_RATE_THRESHOLD));
            }
        }

        return alerts;
    }
}
