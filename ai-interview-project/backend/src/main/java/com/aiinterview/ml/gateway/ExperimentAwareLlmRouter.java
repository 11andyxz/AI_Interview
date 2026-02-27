package com.aiinterview.ml.gateway;

import com.aiinterview.ml.experiment.Experiment;
import com.aiinterview.ml.experiment.ExperimentAssignment;
import com.aiinterview.ml.experiment.ExperimentRepository;
import com.aiinterview.ml.experiment.ExperimentTracker;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Experiment-aware LLM router
 * Handles experiment assignment, config resolution, and prompt version selection
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExperimentAwareLlmRouter {
    
    private final ExperimentTracker experimentTracker;
    private final ExperimentRepository experimentRepository;
    private final PromptVersionRepository promptVersionRepository;
    private final ObjectMapper objectMapper;
    
    @Value("${openai.model:gpt-4o-mini}")
    private String defaultModel;
    
    @Value("${openai.temperature:0.7}")
    private double defaultTemperature;
    
    @Value("${openai.max-tokens:1000}")
    private int defaultMaxTokens;
    
    /**
     * Route LLM request
     * 1. Check for active experiment
     * 2. Assign variant using hash-based consistent assignment
     * 3. Resolve config and return routing decision
     */
    public LlmRouteDecision route(LlmRequest request) {
        log.debug("Routing LLM request: type={}, requestId={}, sessionId={}", 
                  request.getRequestType(), request.getRequestId(), request.getSessionId());
        
        Optional<Experiment> activeExperiment = findActiveExperiment(request.getRequestType());
        
        if (activeExperiment.isEmpty()) {
            return buildDefaultDecision(request);
        }
        
        Experiment experiment = activeExperiment.get();
        
        String assignmentId = request.getRequestId() != null ? 
            request.getRequestId() : request.getSessionId();
        
        ExperimentAssignment assignment = experimentTracker.assignExperiment(
            experiment.getId(), 
            assignmentId
        );
        
        String variant = assignment.getVariant();
        
        LlmConfig config = resolveConfig(experiment.getId(), variant);
        
        String promptTemplate = getPromptTemplate(config.getPromptKey(), config.getPromptVersion());
        
        return LlmRouteDecision.builder()
            .experimentId(experiment.getId())
            .variant(variant)
            .model(config.getModel() != null ? config.getModel() : defaultModel)
            .promptTemplate(promptTemplate)
            .useRag(config.getUseRag() != null ? config.getUseRag() : false)
            .ragTopK(config.getRagTopK() != null ? config.getRagTopK() : 5)
            .temperature(config.getTemperature() != null ? config.getTemperature() : defaultTemperature)
            .maxTokens(config.getMaxTokens() != null ? config.getMaxTokens() : defaultMaxTokens)
            .promptVersion(config.getPromptVersion())
            .isExperiment(true)
            .build();
    }
    
    /**
     * Resolve experiment configuration
     * Parse JSON config string into LlmConfig object
     */
    public LlmConfig resolveConfig(String experimentId, String variant) {
        Optional<Experiment> experimentOpt = experimentRepository.findById(experimentId);
        
        if (experimentOpt.isEmpty()) {
            log.warn("Experiment not found: {}, using default config", experimentId);
            return LlmConfig.defaultConfig();
        }
        
        Experiment experiment = experimentOpt.get();
        String configJson = "variant".equals(variant) ? 
            experiment.getVariantConfig() : 
            experiment.getBaselineConfig();
        
        if (configJson == null || configJson.trim().isEmpty()) {
            log.warn("Config is empty for experiment {} variant {}, using default", 
                     experimentId, variant);
            return LlmConfig.defaultConfig();
        }
        
        try {
            Map<String, Object> configMap = objectMapper.readValue(
                configJson, 
                new TypeReference<Map<String, Object>>() {}
            );
            
            return LlmConfig.builder()
                .model((String) configMap.get("model"))
                .promptKey((String) configMap.get("promptKey"))
                .promptVersion((String) configMap.get("promptVersion"))
                .useRag((Boolean) configMap.get("useRag"))
                .ragTopK((Integer) configMap.get("ragTopK"))
                .temperature(configMap.get("temperature") != null ? 
                    ((Number) configMap.get("temperature")).doubleValue() : null)
                .maxTokens((Integer) configMap.get("maxTokens"))
                .build();
                
        } catch (Exception e) {
            log.error("Failed to parse experiment config for {} variant {}: {}", 
                      experimentId, variant, e.getMessage(), e);
            return LlmConfig.defaultConfig();
        }
    }
    
    /**
     * Get prompt template content
     */
    private String getPromptTemplate(String promptKey, String version) {
        if (promptKey == null) {
            return null;
        }
        
        try {
            Optional<PromptVersion> promptVersion;
            
            if (version != null) {
                promptVersion = promptVersionRepository.findByPromptKeyAndVersion(promptKey, version);
            } else {
                promptVersion = promptVersionRepository.findByPromptKeyAndIsActive(promptKey, true);
            }
            
            return promptVersion.map(PromptVersion::getContent).orElse(null);
            
        } catch (Exception e) {
            log.error("Failed to fetch prompt template: key={}, version={}, error={}", 
                      promptKey, version, e.getMessage());
            return null;
        }
    }
    
    /**
     * Find active experiment for request type
     */
    private Optional<Experiment> findActiveExperiment(String requestType) {
        if (requestType == null) {
            return Optional.empty();
        }
        
        List<Experiment> activeExperiments = experimentRepository.findByStatus("active");
        
        return activeExperiments.stream()
            .filter(exp -> {
                if (exp.getDescription() != null && exp.getDescription().contains("targetEndpoint:")) {
                    String endpoint = exp.getDescription()
                        .substring(exp.getDescription().indexOf("targetEndpoint:") + 15)
                        .split("\\|")[0]
                        .trim();
                    return requestType.equals(endpoint);
                }
                return false;
            })
            .findFirst();
    }
    
    /**
     * Build default routing decision when no experiment is active
     */
    private LlmRouteDecision buildDefaultDecision(LlmRequest request) {
        return LlmRouteDecision.builder()
            .experimentId(null)
            .variant(null)
            .model(defaultModel)
            .promptTemplate(null)
            .useRag(false)
            .ragTopK(5)
            .temperature(defaultTemperature)
            .maxTokens(defaultMaxTokens)
            .isExperiment(false)
            .build();
    }
}
