package com.aiinterview.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import redis.embedded.RedisServer;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;

/**
 * Test configuration for Embedded Redis
 * Automatically starts Redis server for integration tests
 */
@TestConfiguration
public class EmbeddedRedisConfig {

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    private RedisServer redisServer;

    @PostConstruct
    public void startRedis() throws IOException {
        try {
            redisServer = RedisServer.builder()
                .port(redisPort)
                .setting("maxmemory 128M")
                .build();
            redisServer.start();
            System.out.println("✓ Embedded Redis started on port " + redisPort);
        } catch (Exception e) {
            System.err.println("⚠ Failed to start embedded Redis: " + e.getMessage());
            System.err.println("⚠ Tests requiring Redis will fail");
        }
    }

    @PreDestroy
    public void stopRedis() {
        if (redisServer != null && redisServer.isActive()) {
            redisServer.stop();
            System.out.println("✓ Embedded Redis stopped");
        }
    }
}
