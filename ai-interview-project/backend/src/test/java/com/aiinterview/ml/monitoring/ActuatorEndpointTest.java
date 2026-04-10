package com.aiinterview.ml.monitoring;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test to verify monitoring components work correctly.
 * Tests monitoring implementation without requiring full Spring context.
 */
class ActuatorEndpointTest {
    
    @Test
    void testMLMetricsServiceExists() {
        MeterRegistry registry = new SimpleMeterRegistry();
        MLMetricsService service = new MLMetricsService(registry);
        
        assertNotNull(service, "MLMetricsService should be created");
        
        // Test metric recording
        service.recordQuestionRepetition("technical");
        service.recordEmbeddingCacheHit();
        service.recordFeatureExtractionTime(10);
        service.recordTokenUsage(100);
        
        // Verify metrics are registered
        assertEquals(1.0, registry.get("ml.question.repetition").counter().count());
        assertEquals(1.0, registry.get("ml.embedding.cache.hit").counter().count());
        assertEquals(1.0, registry.get("ml.feature_extraction.duration").timer().count());
        assertEquals(1.0, registry.get("ml.token.usage").summary().count());
    }
    
    @Test
    void testMLHealthIndicatorExists() {
        MLHealthIndicator indicator = new MLHealthIndicator();
        
        assertNotNull(indicator, "MLHealthIndicator should be created");
        
        // Test health check
        Health health = indicator.health();
        assertNotNull(health);
        assertEquals("UP", health.getStatus().getCode());
        
        // Verify details
        assertNotNull(health.getDetails().get("embedding_service"));
        assertNotNull(health.getDetails().get("prediction_service"));
        assertNotNull(health.getDetails().get("nlp_service"));
    }
    
    @Test
    void testAllRequiredMetricsAreDefined() {
        MeterRegistry registry = new SimpleMeterRegistry();
        MLMetricsService service = new MLMetricsService(registry);
        
        // Record one of each type to ensure they're all registered
        service.recordQuestionRepetition("test");
        service.recordEmbeddingCacheHit();
        service.recordEmbeddingCacheMiss();
        service.recordEarlyStop("early_pass", "senior");
        service.recordPredictionError("component", "error");
        service.recordFeatureExtractionTime(1);
        service.recordEmbeddingTime(1);
        service.recordOutcomePredictionTime(1);
        service.recordNLPScoringTime(1);
        service.recordTokenUsage(1);
        service.recordEmbeddingDimensions(1536);
        
        // Verify all metrics exist
        assertNotNull(registry.find("ml.question.repetition").counter());
        assertNotNull(registry.find("ml.embedding.cache.hit").counter());
        assertNotNull(registry.find("ml.embedding.cache.miss").counter());
        assertNotNull(registry.find("ml.early_stop.triggered").counter());
        assertNotNull(registry.find("ml.prediction.error").counter());
        assertNotNull(registry.find("ml.feature_extraction.duration").timer());
        assertNotNull(registry.find("ml.embedding.duration").timer());
        assertNotNull(registry.find("ml.outcome_prediction.duration").timer());
        assertNotNull(registry.find("ml.nlp_scoring.duration").timer());
        assertNotNull(registry.find("ml.token.usage").summary());
        assertNotNull(registry.find("ml.embedding.dimensions").summary());
    }
}
