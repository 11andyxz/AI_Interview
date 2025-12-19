package com.aiinterview.util;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Mock configuration for external services in tests
 */
@TestConfiguration
public class MockExternalServiceConfig {
    
    @Bean
    @Primary
    public WebClient mockOpenAiWebClient() {
        // Return a mock WebClient for OpenAI service
        // In actual tests, use MockWebServer or WireMock
        return WebClient.builder().baseUrl("http://localhost:9999").build();
    }
}

