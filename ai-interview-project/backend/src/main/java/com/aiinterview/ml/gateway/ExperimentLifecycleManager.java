package com.aiinterview.ml.gateway;

import com.aiinterview.ml.experiment.Experiment;
import com.aiinterview.ml.experiment.ExperimentMetric;
import com.aiinterview.ml.experiment.ExperimentMetricRepository;
import com.aiinterview.ml.experiment.ExperimentRepository;
import com.aiinterview.ml.experiment.ExperimentResult;
import com.aiinterview.ml.experiment.ExperimentTracker;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Experiment lifecycle manager
 * Handles experiment launch, monitoring, auto-evaluation and rollback
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExperimentLifecycleManager {
    
    private final ExperimentRepository experimentRepository;
    private final ExperimentTracker experimentTracker;
    private final ExperimentMetricRepository metricRepository;
    private final ObjectMapper objectMapper;
    
    private static final int DEFAULT_MIN_SAMPLE_SIZE = 100;
    private static final double DEFAULT_ROLLBACK_P_VALUE = 0.05;
    private static final int MIN_WAIT_MINUTES_FOR_ROLLBACK = 5;
    
    /**
     * Launch experiment from template
     */
    @Transactional
    public Experiment launchExperiment(ExperimentTemplate template) {
        log.info("Launching experiment from template: type={}, name={}", 
                 template.getTemplateType(), template.getName());
        
        try {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("templateType", template.getTemplateType());
            metadata.put("targetEndpoint", template.getTargetEndpoint());
            if (template.getMetadata() != null) {
                metadata.putAll(template.getMetadata());
            }
            
            String experimentId = "exp_" + UUID.randomUUID().toString().substring(0, 8);
            
            String description = template.getName();
            if (template.getTargetEndpoint() != null) {
                description += " | targetEndpoint:" + template.getTargetEndpoint();
            }
            
            Experiment experiment = Experiment.builder()
                .id(experimentId)
                .name(template.getName())
                .type(template.getTemplateType())
                .status("active")
                .baselineConfig(objectMapper.writeValueAsString(template.getBaselineConfig()))
                .variantConfig(objectMapper.writeValueAsString(template.getVariantConfig()))
                .trafficSplit(template.getTrafficSplit() != null ? template.getTrafficSplit() : 0.5)
                .minSampleSize(template.getMinSampleSize() != null ? 
                               template.getMinSampleSize() : DEFAULT_MIN_SAMPLE_SIZE)
                .significanceThreshold(template.getRollbackPValue() != null ?
                                        template.getRollbackPValue() : DEFAULT_ROLLBACK_P_VALUE)
                .description(description)
                .startedAt(LocalDateTime.now())
                .build();
            
            experiment = experimentRepository.save(experiment);
            
            log.info("Experiment launched: id={}, name={}, split={}", 
                     experiment.getId(), experiment.getName(), experiment.getTrafficSplit());
            
            return experiment;
            
        } catch (Exception e) {
            log.error("Failed to launch experiment: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to launch experiment", e);
        }
    }
    
    /**
     * Check experiment progress periodically (every 5 minutes)
     * - Auto-evaluate experiments that reached minimum sample size
     * - Detect quality degradation and trigger auto-rollback
     */
    @Scheduled(fixedRate = 300000)
    public void checkExperimentProgress() {
        log.debug("Checking experiment progress...");
        
        List<Experiment> activeExperiments = experimentRepository.findByStatus("active");
        
        if (activeExperiments.isEmpty()) {
            log.debug("No active experiments to check");
            return;
        }
        
        log.info("Checking {} active experiments", activeExperiments.size());
        
        for (Experiment experiment : activeExperiments) {
            try {
                checkSingleExperiment(experiment);
            } catch (Exception e) {
                log.error("Error checking experiment {}: {}", 
                         experiment.getId(), e.getMessage(), e);
            }
        }
    }
    
    /**
     * Check single experiment
     */
    private void checkSingleExperiment(Experiment experiment) {
        String experimentId = experiment.getId();
        
        if (experiment.getStartedAt() != null) {
            Duration runDuration = Duration.between(experiment.getStartedAt(), LocalDateTime.now());
            if (runDuration.toMinutes() < MIN_WAIT_MINUTES_FOR_ROLLBACK) {
                log.debug("Experiment {} too recent ({} min), skipping evaluation", 
                         experimentId, runDuration.toMinutes());
                return;
            }
        }
        
        long baselineCount = metricRepository.countByExperimentIdAndVariant(experimentId, "baseline");
        long variantCount = metricRepository.countByExperimentIdAndVariant(experimentId, "variant");
        
        int minSize = experiment.getMinSampleSize() != null ? experiment.getMinSampleSize() : DEFAULT_MIN_SAMPLE_SIZE;
        
        log.debug("Experiment {} samples: baseline={}, variant={}, minSize={}", 
                 experimentId, baselineCount, variantCount, minSize);
        
        if (baselineCount < minSize || variantCount < minSize) {
            log.debug("Experiment {} has insufficient samples", experimentId);
            return;
        }
        
        ExperimentResult result = experimentTracker.evaluateExperiment(experimentId);
        
        if (result == null) {
            log.warn("Failed to compute result for experiment {}", experimentId);
            return;
        }
        
        Double pValue = result.getPValue();
        Double improvement = result.getImprovement();
        double threshold = experiment.getSignificanceThreshold() != null ? 
            experiment.getSignificanceThreshold() : DEFAULT_ROLLBACK_P_VALUE;
        
        log.info("Experiment {} evaluation: pValue={}, improvement={}, threshold={}", 
                 experimentId, pValue, improvement, threshold);
        
        if (pValue != null && improvement != null && pValue < threshold && improvement < 0) {
            log.warn("Auto-rollback triggered for experiment {}: significant quality degradation (p={}, improvement={})", 
                     experimentId, pValue, improvement);
            rollbackExperiment(experimentId, "Auto-rollback: significant quality degradation");
        } else {
            log.info("Experiment {} continues: no rollback condition met", experimentId);
        }
    }
    
    /**
     * Rollback experiment
     */
    @Transactional
    private void rollbackExperiment(String experimentId, String reason) {
        experimentRepository.findById(experimentId).ifPresent(experiment -> {
            experiment.setStatus("rolled_back");
            experiment.setCompletedAt(LocalDateTime.now());
            experiment.setResultSummary(reason);
            experimentRepository.save(experiment);
            log.info("Experiment {} rolled back: {}", experimentId, reason);
        });
    }
    
    /**
     * Conclude experiment manually
     */
    @Transactional
    public void concludeExperiment(String experimentId) {
        log.info("Concluding experiment: {}", experimentId);
        
        experimentRepository.findById(experimentId).ifPresent(experiment -> {
            experiment.setStatus("concluded");
            experiment.setCompletedAt(LocalDateTime.now());
            experimentRepository.save(experiment);
            log.info("Experiment {} concluded", experimentId);
        });
    }
    
    /**
     * Generate experiment report
     */
    public ExperimentReport generateReport(String experimentId) {
        log.info("Generating report for experiment: {}", experimentId);
        
        Experiment experiment = experimentRepository.findById(experimentId)
            .orElseThrow(() -> new RuntimeException("Experiment not found: " + experimentId));
        
        List<ExperimentMetric> baselineMetrics = metricRepository
            .findByExperimentIdAndVariant(experimentId, "baseline");
        List<ExperimentMetric> variantMetrics = metricRepository
            .findByExperimentIdAndVariant(experimentId, "variant");
        
        ExperimentReport.MetricsSummary baselineSummary = computeMetricsSummary(baselineMetrics);
        ExperimentReport.MetricsSummary variantSummary = computeMetricsSummary(variantMetrics);
        
        ExperimentResult result = experimentTracker.evaluateExperiment(experimentId);
        
        ExperimentReport.StatisticalSignificance significance = null;
        if (result != null && result.getPValue() != null) {
            significance = ExperimentReport.StatisticalSignificance.builder()
                .pValue(result.getPValue())
                .effectSize(result.getImprovement() != null ? result.getImprovement() : 0.0)
                .confidenceInterval95Lower(0.0)
                .confidenceInterval95Upper(0.0)
                .isSignificant(result.getPValue() < 0.05)
                .build();
        }
        
        String recommendation = generateRecommendation(result, experiment);
        String recommendationReason = generateRecommendationReason(result, experiment);
        
        return ExperimentReport.builder()
            .experimentId(experimentId)
            .experimentName(experiment.getName())
            .status(experiment.getStatus())
            .startTime(experiment.getStartedAt())
            .endTime(experiment.getCompletedAt())
            .baselineMetrics(baselineSummary)
            .variantMetrics(variantSummary)
            .significance(significance)
            .recommendation(recommendation)
            .recommendationReason(recommendationReason)
            .build();
    }
    
    /**
     * Compute metrics summary
     */
    private ExperimentReport.MetricsSummary computeMetricsSummary(List<ExperimentMetric> metrics) {
        if (metrics.isEmpty()) {
            return ExperimentReport.MetricsSummary.builder()
                .sampleSize(0)
                .meanQuality(0.0)
                .meanLatency(0.0)
                .meanCost(0.0)
                .stdDevQuality(0.0)
                .stdDevLatency(0.0)
                .build();
        }
        
        double meanQuality = metrics.stream()
            .mapToDouble(ExperimentMetric::getQualityScore)
            .average()
            .orElse(0.0);
        
        double meanLatency = metrics.stream()
            .mapToDouble(ExperimentMetric::getLatencyMs)
            .average()
            .orElse(0.0);
        
        double meanCost = metrics.stream()
            .mapToDouble(ExperimentMetric::getCostUsd)
            .average()
            .orElse(0.0);
        
        double variance = metrics.stream()
            .mapToDouble(m -> Math.pow(m.getQualityScore() - meanQuality, 2))
            .average()
            .orElse(0.0);
        
        double stdDevQuality = Math.sqrt(variance);
        
        double varianceLatency = metrics.stream()
            .mapToDouble(m -> Math.pow(m.getLatencyMs() - meanLatency, 2))
            .average()
            .orElse(0.0);
        
        double stdDevLatency = Math.sqrt(varianceLatency);
        
        return ExperimentReport.MetricsSummary.builder()
            .sampleSize(metrics.size())
            .meanQuality(meanQuality)
            .meanLatency(meanLatency)
            .meanCost(meanCost)
            .stdDevQuality(stdDevQuality)
            .stdDevLatency(stdDevLatency)
            .build();
    }
    
    /**
     * Generate recommendation
     */
    private String generateRecommendation(ExperimentResult result, Experiment experiment) {
        if (result == null || result.getPValue() == null) {
            return "inconclusive";
        }
        
        if ("rolled_back".equals(experiment.getStatus())) {
            return "rollback";
        }
        
        if (result.getPValue() < 0.05) {
            if (result.getImprovement() != null && result.getImprovement() > 0) {
                return "promote";
            } else {
                return "rollback";
            }
        }
        
        return "continue";
    }
    
    /**
     * Generate recommendation reason
     */
    private String generateRecommendationReason(ExperimentResult result, Experiment experiment) {
        if (result == null || result.getPValue() == null) {
            return "Insufficient data for evaluation";
        }
        
        if ("rolled_back".equals(experiment.getStatus())) {
            return experiment.getResultSummary() != null ? 
                experiment.getResultSummary() : "Experiment was rolled back";
        }
        
        if (result.getPValue() < 0.05) {
            if (result.getImprovement() != null && result.getImprovement() > 0) {
                return String.format("Statistically significant improvement (p=%.4f, improvement=%.2f%%)", 
                    result.getPValue(), result.getImprovement());
            } else {
                return String.format("Statistically significant degradation (p=%.4f, improvement=%.2f%%)", 
                    result.getPValue(), result.getImprovement() != null ? result.getImprovement() : 0.0);
            }
        }
        
        return "No statistically significant difference detected";
    }
}
