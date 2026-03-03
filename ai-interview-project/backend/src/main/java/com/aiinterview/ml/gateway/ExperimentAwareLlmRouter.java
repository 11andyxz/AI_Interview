package com.aiinterview.ml.gateway;

import com.aiinterview.ml.experiment.ExperimentTracker;
import com.aiinterview.ml.experiment.model.Experiment;
import com.aiinterview.ml.gateway.model.PromptVersion;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class ExperimentAwareLlmRouter {

    private static final Logger logger = LoggerFactory.getLogger(ExperimentAwareLlmRouter.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final ExperimentTracker experimentTracker;
    private final PromptVersionService promptVersionService;

    @Value("${openai.model:gpt-3.5-turbo}")
    private String defaultModel;

    @Value("${openai.temperature:0.7}")
    private double defaultTemperature;

    public ExperimentAwareLlmRouter(ExperimentTracker experimentTracker,
                                     PromptVersionService promptVersionService) {
        this.experimentTracker = experimentTracker;
        this.promptVersionService = promptVersionService;
    }

    /**
     * Route an LLM request through experiment-aware logic.
     * If an active experiment exists for this endpoint, assign the session to a variant
     * and resolve the appropriate config. Otherwise return default config.
     */
    public LlmRouteDecision route(LlmRequest request) {
        Optional<Experiment> experimentOpt = experimentTracker.getActiveExperiment(request.getEndpoint());

        if (experimentOpt.isEmpty()) {
            return LlmRouteDecision.defaultRoute(defaultModel, defaultTemperature);
        }

        Experiment experiment = experimentOpt.get();
        String variant = experimentTracker.assignVariant(experiment, request.getSessionId());

        return resolveConfig(experiment, variant);
    }

    /**
     * Resolve LLM configuration for a specific experiment variant.
     * Parses the experiment's JSON config (baseline or treatment) and resolves prompt versions.
     */
    public LlmRouteDecision resolveConfig(Experiment experiment, String variant) {
        LlmRouteDecision decision = new LlmRouteDecision();
        decision.setExperimentId(String.valueOf(experiment.getId()));
        decision.setVariant(variant);

        String configJson = variant.equals(experiment.getBaselineVariant())
                ? experiment.getBaselineConfig()
                : experiment.getTreatmentConfig();

        if (configJson != null) {
            try {
                JsonNode config = objectMapper.readTree(configJson);
                decision.setModel(config.has("model") ? config.get("model").asText() : defaultModel);
                decision.setTemperature(config.has("temperature") ? config.get("temperature").asDouble() : defaultTemperature);
                decision.setUseRag(config.has("useRag") && config.get("useRag").asBoolean());
                decision.setRagTopK(config.has("ragTopK") ? config.get("ragTopK").asInt() : 5);

                if (config.has("promptKey") && config.has("promptVersion")) {
                    String promptKey = config.get("promptKey").asText();
                    String promptVersion = config.get("promptVersion").asText();
                    promptVersionService.getPromptByVersion(promptKey, promptVersion)
                            .ifPresent(pv -> decision.setPromptTemplate(pv.getContent()));
                }
            } catch (Exception e) {
                logger.warn("Failed to parse experiment config for variant {}: {}", variant, e.getMessage());
                decision.setModel(defaultModel);
                decision.setTemperature(defaultTemperature);
            }
        } else {
            decision.setModel(defaultModel);
            decision.setTemperature(defaultTemperature);
        }

        return decision;
    }
}
