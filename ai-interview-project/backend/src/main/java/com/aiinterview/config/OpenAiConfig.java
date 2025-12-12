package com.aiinterview.config;

import com.aiinterview.service.ApiKeyConfigService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class OpenAiConfig {

    @Value("${openai.api.url}")
    private String apiUrl;

    private final ApiKeyConfigService apiKeyConfigService;

    public OpenAiConfig(ApiKeyConfigService apiKeyConfigService) {
        this.apiKeyConfigService = apiKeyConfigService;
    }

    @Bean
    public WebClient openAiWebClient() {
        // Get API key from database
        String apiKey = apiKeyConfigService.getActiveApiKey("openai")
            .orElseThrow(() -> new IllegalStateException("OpenAI API key not found in database"));

        return WebClient.builder()
                .baseUrl(apiUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }
}

