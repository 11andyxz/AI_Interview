package com.aiinterview.util;

import org.mockito.Mockito;
import org.springframework.data.redis.core.RedisTemplate;

public class MockHelper {
    
    /**
     * Create a mock RedisTemplate for testing
     */
    @SuppressWarnings("unchecked")
    public static RedisTemplate<String, Object> createMockRedisTemplate() {
        return Mockito.mock(RedisTemplate.class);
    }
    
    /**
     * Helper to verify Redis operations
     */
    public static void verifyRedisSet(RedisTemplate<String, Object> redisTemplate, String key, Object value) {
        Mockito.verify(redisTemplate).opsForValue();
    }
    
    /**
     * Helper to verify Redis get operations
     */
    public static void verifyRedisGet(RedisTemplate<String, Object> redisTemplate, String key) {
        Mockito.verify(redisTemplate).opsForValue();
    }
    
    /**
     * Generate a mock JWT token for testing (doesn't validate, just for structure)
     */
    public static String generateMockToken(Long userId, String username) {
        // This is a simple mock token structure for testing
        // In real tests, you should use JwtService to generate actual tokens
        return "mock-token-" + userId + "-" + username;
    }
    
    /**
     * Generate a mock refresh token for testing
     */
    public static String generateMockRefreshToken(Long userId, String username) {
        return "mock-refresh-token-" + userId + "-" + username;
    }
}

