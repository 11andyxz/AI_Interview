package com.aiinterview.config;

import com.aiinterview.service.ApiKeyConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * OpenAI Configuration - Configures WebClient for OpenAI API integration.
 * API key is loaded from database via ApiKeyConfigService.
 */
@Configuration
public class OpenAiConfig {

    private static final Logger logger = LoggerFactory.getLogger(OpenAiConfig.class);
    private static final String DUMMY_API_KEY = "sk-dummy-key-for-testing";

    @Value("${openai.api.url}")
    private String apiUrl;

    private final ApiKeyConfigService apiKeyConfigService;

    public OpenAiConfig(ApiKeyConfigService apiKeyConfigService) {
        this.apiKeyConfigService = apiKeyConfigService;
    }

    @Bean
    public WebClient openAiWebClient() {
        // Get API key from database, use dummy if not found
        String apiKey = apiKeyConfigService.getActiveApiKey("openai")
            .orElse(DUMMY_API_KEY);

        if (DUMMY_API_KEY.equals(apiKey)) {
            logger.warn("⚠️  WARNING: Using dummy OpenAI API key. Please configure a real API key in database.");
            logger.warn("⚠️  Add API key via: POST /api/admin/api-keys with service_name='openai'");
        } else {
            logger.info("✓ OpenAI API key loaded successfully from database");
        }

        return WebClient.builder()
                .baseUrl(apiUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }
    
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}

