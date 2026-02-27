package com.aiinterview.ml.experiments;

import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Routes requests to different prompt variants based on A/B test configuration
 */
@Slf4j
@Component
public class PromptRouter {
    
    private final Map<String, Experiment> experiments = new ConcurrentHashMap<>();
    private final Random random = new Random();
    
    /**
     * Register an experiment for a scenario
     */
    public void registerExperiment(String scenario, Experiment experiment) {
        if (!experiment.isValid()) {
            throw new IllegalArgumentException("Invalid experiment configuration: " + experiment.getName());
        }
        experiments.put(scenario, experiment);
        log.info("Registered experiment {} for scenario {}", experiment.getName(), scenario);
    }
    
    /**
     * Route request to a variant based on experiment configuration
     * 
     * @param scenario The use case (e.g., "resume_analysis")
     * @param userId User identifier for consistent bucketing
     * @return The variant name to use (e.g., "v1.2_few_shot")
     */
    public String routeToVariant(String scenario, String userId) {
        Experiment experiment = experiments.get(scenario);
        
        // No active experiment - use default
        if (experiment == null || !experiment.isActive()) {
            return getDefaultVariant(scenario);
        }
        
        // Use consistent hashing for stable user-to-variant assignment
        int hash = (userId + scenario).hashCode();
        double bucket = Math.abs(hash % 10000) / 10000.0;
        
        // Assign to variant based on traffic percentages
        double cumulative = 0.0;
        for (Map.Entry<String, Double> entry : experiment.getVariants().entrySet()) {
            cumulative += entry.getValue();
            if (bucket < cumulative) {
                log.debug("User {} routed to variant {} for scenario {}", 
                         userId, entry.getKey(), scenario);
                return entry.getKey();
            }
        }
        
        // Fallback to first variant (shouldn't happen if experiment is valid)
        String fallback = experiment.getVariants().keySet().iterator().next();
        log.warn("Routing fallback for user {} in scenario {}, using {}", 
                userId, scenario, fallback);
        return fallback;
    }
    
    /**
     * Get default variant when no experiment is running
     */
    private String getDefaultVariant(String scenario) {
        return switch (scenario) {
            case "resume_analysis" -> "v1.0_baseline";
            case "question_generation" -> "v2.0_baseline";
            case "answer_evaluation" -> "v3.0_baseline";
            default -> {
                log.warn("Unknown scenario: {}, using generic default", scenario);
                yield "baseline";
            }
        };
    }
    
    /**
     * Get current experiment for a scenario
     */
    public Optional<Experiment> getExperiment(String scenario) {
        return Optional.ofNullable(experiments.get(scenario));
    }
    
    /**
     * Pause an experiment
     */
    public void pauseExperiment(String scenario) {
        Experiment experiment = experiments.get(scenario);
        if (experiment != null) {
            experiment.setStatus(Experiment.ExperimentStatus.PAUSED);
            log.info("Paused experiment {} for scenario {}", experiment.getName(), scenario);
        }
    }
    
    /**
     * Resume an experiment
     */
    public void resumeExperiment(String scenario) {
        Experiment experiment = experiments.get(scenario);
        if (experiment != null) {
            experiment.setStatus(Experiment.ExperimentStatus.ACTIVE);
            log.info("Resumed experiment {} for scenario {}", experiment.getName(), scenario);
        }
    }
    
    /**
     * Stop an experiment and remove it
     */
    public void stopExperiment(String scenario) {
        Experiment experiment = experiments.remove(scenario);
        if (experiment != null) {
            experiment.setStatus(Experiment.ExperimentStatus.COMPLETED);
            log.info("Stopped experiment {} for scenario {}", experiment.getName(), scenario);
        }
    }
    
    /**
     * Builder for fluent configuration
     */
    public static class Builder {
        private final PromptRouter router = new PromptRouter();
        private String currentScenario;
        private final Map<String, Double> variants = new HashMap<>();
        private String metric = "quality_score";
        private int minimumSamples = 100;
        
        public Builder experiment(String scenario) {
            this.currentScenario = scenario;
            this.variants.clear();
            return this;
        }
        
        public Builder variant(String name, double trafficPercentage) {
            this.variants.put(name, trafficPercentage);
            return this;
        }
        
        public Builder metric(String metricName) {
            this.metric = metricName;
            return this;
        }
        
        public Builder minimumSamples(int samples) {
            this.minimumSamples = samples;
            return this;
        }
        
        public PromptRouter build() {
            if (currentScenario != null && !variants.isEmpty()) {
                Experiment experiment = Experiment.builder()
                    .name(currentScenario + "_experiment")
                    .scenario(currentScenario)
                    .variants(new HashMap<>(variants))
                    .primaryMetric(metric)
                    .minimumSamples(minimumSamples)
                    .status(Experiment.ExperimentStatus.ACTIVE)
                    .build();
                    
                router.registerExperiment(currentScenario, experiment);
            }
            return router;
        }
    }
    
    public static Builder builder() {
        return new Builder();
    }
}
