package com.aiinterview.ml.monitoring;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MLMetricsService.
 */
class MLMetricsServiceTest {
    
    private MLMetricsService metricsService;
    private MeterRegistry meterRegistry;
    
    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        metricsService = new MLMetricsService(meterRegistry);
    }
    
    @Test
    void testRecordQuestionRepetition() {
        // When
        metricsService.recordQuestionRepetition("technical");
        
        // Then
        double count = meterRegistry.get("ml.question.repetition")
                .tag("type", "technical")
                .counter()
                .count();
        assertEquals(1.0, count, 0.01);
    }
    
    @Test
    void testRecordEmbeddingCacheHit() {
        // When
        metricsService.recordEmbeddingCacheHit();
        metricsService.recordEmbeddingCacheHit();
        
        // Then
        double count = meterRegistry.get("ml.embedding.cache.hit")
                .counter()
                .count();
        assertEquals(2.0, count, 0.01);
    }
    
    @Test
    void testRecordEmbeddingCacheMiss() {
        // When
        metricsService.recordEmbeddingCacheMiss();
        
        // Then
        double count = meterRegistry.get("ml.embedding.cache.miss")
                .counter()
                .count();
        assertEquals(1.0, count, 0.01);
    }
    
    @Test
    void testRecordEarlyStop() {
        // When
        metricsService.recordEarlyStop("early_pass", "senior");
        
        // Then
        double count = meterRegistry.get("ml.early_stop.triggered")
                .tag("reason", "early_pass")
                .tag("role", "senior")
                .counter()
                .count();
        assertEquals(1.0, count, 0.01);
    }
    
    @Test
    void testRecordPredictionError() {
        // When
        metricsService.recordPredictionError("feature_extraction", "NullPointerException");
        
        // Then
        double count = meterRegistry.get("ml.prediction.error")
                .tag("component", "feature_extraction")
                .tag("error_type", "NullPointerException")
                .counter()
                .count();
        assertEquals(1.0, count, 0.01);
    }
    
    @Test
    void testRecordFeatureExtractionTime() {
        // When
        metricsService.recordFeatureExtractionTime(10);
        
        // Then
        double count = meterRegistry.get("ml.feature_extraction.duration")
                .timer()
                .count();
        assertEquals(1.0, count, 0.01);
    }
    
    @Test
    void testRecordTokenUsage() {
        // When
        metricsService.recordTokenUsage(150);
        metricsService.recordTokenUsage(200);
        
        // Then
        double count = meterRegistry.get("ml.token.usage")
                .summary()
                .count();
        assertEquals(2.0, count, 0.01);
    }
    
    @Test
    void testRecordEmbeddingDimensions() {
        // When
        metricsService.recordEmbeddingDimensions(1536);
        
        // Then
        double count = meterRegistry.get("ml.embedding.dimensions")
                .summary()
                .count();
        assertEquals(1.0, count, 0.01);
    }
    
    @Test
    void testTimeFeatureExtraction() {
        // When
        String result = metricsService.timeFeatureExtraction(() -> {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "test_result";
        });
        
        // Then
        assertEquals("test_result", result);
        double count = meterRegistry.get("ml.feature_extraction.duration")
                .timer()
                .count();
        assertEquals(1.0, count, 0.01);
    }
    
    @Test
    void testRegisterActiveSessionsGauge() {
        // When
        metricsService.registerActiveSessionsGauge(() -> 5);
        
        // Then
        double value = meterRegistry.get("ml.sessions.active")
                .gauge()
                .value();
        assertEquals(5.0, value, 0.01);
    }
    
    @Test
    void testRegisterModelLoadedGauge() {
        // When
        metricsService.registerModelLoadedGauge(() -> 1);
        
        // Then
        double value = meterRegistry.get("ml.model.loaded")
                .gauge()
                .value();
        assertEquals(1.0, value, 0.01);
    }
}
