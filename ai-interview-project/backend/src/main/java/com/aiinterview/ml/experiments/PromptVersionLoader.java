package com.aiinterview.ml.experiments;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads and manages prompt versions from JSON files
 */
@Slf4j
@Service
public class PromptVersionLoader {
    
    private final ObjectMapper objectMapper;
    private final Map<String, PromptVersion> promptVersions = new ConcurrentHashMap<>();
    private final PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
    
    public PromptVersionLoader() {
        this.objectMapper = new ObjectMapper();
        // Configure ObjectMapper to handle both snake_case and camelCase
        this.objectMapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }
    
    @Data
    public static class PromptVersion {
        private String version;
        private String description;
        private String createdAt;
        private String systemPrompt;
        private String userPromptTemplate;
        private PromptParameters parameters;
    }
    
    @Data
    public static class PromptParameters {
        private double temperature;
        private int maxTokens;
        private double topP;
        private double frequencyPenalty;
        private double presencePenalty;
    }
    
    @PostConstruct
    public void loadPromptVersions() {
        try {
            // Load all prompt version JSON files
            Resource[] resources = resolver.getResources(
                "classpath:prompts/versions/**/*.json"
            );
            
            for (Resource resource : resources) {
                if (resource.getFilename() != null && 
                    !resource.getFilename().equals("metadata.json")) {
                    loadPromptVersion(resource);
                }
            }
            
            log.info("Loaded {} prompt versions", promptVersions.size());
        } catch (IOException e) {
            log.error("Failed to load prompt versions", e);
        }
    }
    
    private void loadPromptVersion(Resource resource) {
        try {
            JsonNode json = objectMapper.readTree(resource.getInputStream());
            PromptVersion version = objectMapper.treeToValue(json, PromptVersion.class);
            
            // Extract scenario and version from file path
            String filename = resource.getFilename();
            String path = resource.getURL().getPath();
            String scenario = extractScenario(path);
            String key = scenario + "/" + version.getVersion();
            
            promptVersions.put(key, version);
            log.debug("Loaded prompt version: {}", key);
        } catch (IOException e) {
            log.error("Failed to load prompt version from {}", resource.getFilename(), e);
        }
    }
    
    private String extractScenario(String path) {
        // Extract scenario from path like .../prompts/versions/resume_analysis/v1.0_baseline.json
        String[] parts = path.split("/prompts/versions/");
        if (parts.length > 1) {
            String[] subParts = parts[1].split("/");
            if (subParts.length > 0) {
                return subParts[0];
            }
        }
        return "unknown";
    }
    
    /**
     * Get a specific prompt version
     */
    public PromptVersion getVersion(String scenario, String version) {
        String key = scenario + "/" + version;
        PromptVersion promptVersion = promptVersions.get(key);
        
        if (promptVersion == null) {
            log.warn("Prompt version not found: {}, using default", key);
            return getDefaultVersion(scenario);
        }
        
        return promptVersion;
    }
    
    /**
     * Get default version for a scenario
     */
    private PromptVersion getDefaultVersion(String scenario) {
        String defaultVersion = switch (scenario) {
            case "resume_analysis" -> "v1.0_baseline";
            case "question_generation" -> "v2.0_baseline";
            case "answer_evaluation" -> "v3.0_baseline";
            default -> null;
        };
        
        if (defaultVersion != null) {
            return promptVersions.get(scenario + "/" + defaultVersion);
        }
        
        // Ultimate fallback
        return createFallbackVersion();
    }
    
    private PromptVersion createFallbackVersion() {
        PromptVersion fallback = new PromptVersion();
        fallback.setVersion("fallback");
        fallback.setSystemPrompt("You are a helpful AI assistant.");
        fallback.setUserPromptTemplate("{input}");
        
        PromptParameters params = new PromptParameters();
        params.setTemperature(0.7);
        params.setMaxTokens(500);
        params.setTopP(1.0);
        params.setFrequencyPenalty(0.0);
        params.setPresencePenalty(0.0);
        fallback.setParameters(params);
        
        return fallback;
    }
    
    /**
     * Reload all prompt versions (useful for hot-reload in development)
     */
    public void reload() {
        promptVersions.clear();
        loadPromptVersions();
        log.info("Reloaded all prompt versions");
    }
    
    /**
     * Get all loaded versions for a scenario
     */
    public Map<String, PromptVersion> getVersionsForScenario(String scenario) {
        String prefix = scenario + "/";
        Map<String, PromptVersion> scenarioVersions = new ConcurrentHashMap<>();
        
        promptVersions.forEach((key, value) -> {
            if (key.startsWith(prefix)) {
                scenarioVersions.put(key, value);
            }
        });
        
        return scenarioVersions;
    }
}
