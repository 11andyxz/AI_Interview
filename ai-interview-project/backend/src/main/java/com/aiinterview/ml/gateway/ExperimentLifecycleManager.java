package com.aiinterview.ml.gateway;

import com.aiinterview.ml.experiment.ExperimentTracker;
import com.aiinterview.ml.experiment.model.Experiment;
import com.aiinterview.ml.experiment.model.ExperimentMetric;
import com.aiinterview.ml.experiment.repository.ExperimentMetricRepository;
import com.aiinterview.ml.experiment.repository.ExperimentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class ExperimentLifecycleManager {

    private static final Logger logger = LoggerFactory.getLogger(ExperimentLifecycleManager.class);
    private static final double SIGNIFICANCE_THRESHOLD = 0.05;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final ExperimentRepository experimentRepository;
    private final ExperimentMetricRepository metricRepository;
    private final ExperimentTracker experimentTracker;
    private final PromptVersionService promptVersionService;

    public ExperimentLifecycleManager(ExperimentRepository experimentRepository,
                                       ExperimentMetricRepository metricRepository,
                                       ExperimentTracker experimentTracker,
                                       PromptVersionService promptVersionService) {
        this.experimentRepository = experimentRepository;
        this.metricRepository = metricRepository;
        this.experimentTracker = experimentTracker;
        this.promptVersionService = promptVersionService;
    }

    /**
     * Launch an experiment from a template configuration.
     */
    public Experiment launchExperiment(ExperimentTemplate template) {
        Experiment experiment = new Experiment();
        experiment.setName(template.getName());
        experiment.setDescription(template.getDescription());
        experiment.setTargetEndpoint(template.getTargetEndpoint());
        experiment.setTrafficPercentage(template.getTrafficPercentage());
        experiment.setMinSampleSize(template.getMinSampleSize());

        try {
            if (template.getBaselinePromptKey() != null) {
                experiment.setBaselineConfig(objectMapper.writeValueAsString(Map.of(
                        "promptKey", template.getBaselinePromptKey(),
                        "promptVersion", template.getBaselinePromptVersion() != null ? template.getBaselinePromptVersion() : "v1.0",
                        "model", template.getBaselineModel() != null ? template.getBaselineModel() : "gpt-3.5-turbo"
                )));
            }
            if (template.getTreatmentPromptKey() != null || template.getTreatmentModel() != null) {
                experiment.setTreatmentConfig(objectMapper.writeValueAsString(Map.of(
                        "promptKey", template.getTreatmentPromptKey() != null ? template.getTreatmentPromptKey() : "",
                        "promptVersion", template.getTreatmentPromptVersion() != null ? template.getTreatmentPromptVersion() : "v1.1",
                        "model", template.getTreatmentModel() != null ? template.getTreatmentModel() : "gpt-3.5-turbo"
                )));
            }
        } catch (Exception e) {
            logger.error("Failed to serialize experiment config: {}", e.getMessage());
        }

        experiment.setStatus("running");
        experiment.setStartedAt(LocalDateTime.now());
        return experimentRepository.save(experiment);
    }

    /**
     * Scheduled check every 5 minutes: evaluate running experiments and auto-conclude.
     */
    @Scheduled(fixedRate = 300000)
    public void checkExperimentProgress() {
        List<Experiment> running = experimentRepository.findByStatus("running");
        for (Experiment experiment : running) {
            try {
                if (experimentTracker.hasMinSampleSize(experiment.getId())) {
                    evaluateAndConclude(experiment);
                }
            } catch (Exception e) {
                logger.error("Error checking experiment {}: {}", experiment.getName(), e.getMessage());
            }
        }
    }

    private void evaluateAndConclude(Experiment experiment) {
        double pValue = experimentTracker.welchTTest(experiment.getId());
        ExperimentReport report = generateReport(experiment.getId());

        if (pValue < SIGNIFICANCE_THRESHOLD) {
            if (report.getTreatmentMeanQuality() > report.getBaselineMeanQuality()) {
                logger.info("Experiment {} — treatment wins (p={:.4f}), promoting", experiment.getName(), pValue);
                concludeExperiment(experiment.getId(), "promoted");
            } else {
                logger.info("Experiment {} — baseline wins (p={:.4f}), rolling back", experiment.getName(), pValue);
                concludeExperiment(experiment.getId(), "rolled_back");
            }
        } else {
            logger.info("Experiment {} — no significant difference (p={:.4f}), continuing", experiment.getName(), pValue);
        }
    }

    public void concludeExperiment(Long experimentId, String outcome) {
        experimentTracker.stopExperiment(experimentId, outcome);
    }

    public ExperimentReport generateReport(Long experimentId) {
        Experiment experiment = experimentRepository.findById(experimentId).orElseThrow();
        List<ExperimentMetric> baselineMetrics =
                metricRepository.findByExperimentIdAndVariant(experimentId, experiment.getBaselineVariant());
        List<ExperimentMetric> treatmentMetrics =
                metricRepository.findByExperimentIdAndVariant(experimentId, experiment.getTreatmentVariant());

        double baselineMean = baselineMetrics.stream().mapToDouble(ExperimentMetric::getQualityScore).average().orElse(0);
        double treatmentMean = treatmentMetrics.stream().mapToDouble(ExperimentMetric::getQualityScore).average().orElse(0);
        double pValue = experimentTracker.welchTTest(experimentId);

        ExperimentReport report = new ExperimentReport();
        report.setExperimentId(experimentId);
        report.setExperimentName(experiment.getName());
        report.setStatus(experiment.getStatus());
        report.setBaselineSampleSize(baselineMetrics.size());
        report.setTreatmentSampleSize(treatmentMetrics.size());
        report.setBaselineMeanQuality(baselineMean);
        report.setTreatmentMeanQuality(treatmentMean);
        report.setQualityDelta(treatmentMean - baselineMean);
        report.setpValue(pValue);
        report.setStatisticallySignificant(pValue < SIGNIFICANCE_THRESHOLD);

        if (pValue >= SIGNIFICANCE_THRESHOLD) {
            report.setRecommendation("No significant difference. Continue collecting data or end experiment.");
        } else if (treatmentMean > baselineMean) {
            report.setRecommendation("Treatment is significantly better. Recommend promoting treatment.");
        } else {
            report.setRecommendation("Baseline is significantly better. Recommend rolling back treatment.");
        }

        return report;
    }
}
