package com.aiinterview.config;

import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Test configuration to mock Redis and other external dependencies
 */
@TestConfiguration
public class TestWebMvcConfig {
    
    @Bean
    @Primary
    public RedisConnectionFactory testRedisConnectionFactory() {
        // Return a mock RedisConnectionFactory for tests
        return Mockito.mock(RedisConnectionFactory.class);
    }
    
    @Bean
    @Primary
    @SuppressWarnings("unchecked")
    public RedisTemplate<String, Object> testRedisTemplate() {
        // Return a mock RedisTemplate for tests
        RedisTemplate<String, Object> mockTemplate = Mockito.mock(RedisTemplate.class);
        ValueOperations<String, Object> mockValueOps = Mockito.mock(ValueOperations.class);
        Mockito.when(mockTemplate.opsForValue()).thenReturn(mockValueOps);
        return mockTemplate;
    }
    
    @Bean
    @Primary
    public WebClient testOpenAiWebClient() {
        // Return a mock WebClient for tests
        return WebClient.builder().baseUrl("http://localhost:9999").build();
    }
}

