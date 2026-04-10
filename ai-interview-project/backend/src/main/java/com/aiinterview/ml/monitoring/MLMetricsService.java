package com.aiinterview.ml.monitoring;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Centralized ML metrics tracking service.
 * Provides custom metrics for ML operations using Micrometer.
 */
@Service
public class MLMetricsService {
    
    private final MeterRegistry meterRegistry;
    
    // Counters
    private final Counter questionRepetitionCounter;
    private final Counter embeddingCacheHitCounter;
    private final Counter embeddingCacheMissCounter;
    private final Counter earlyStopTriggerCounter;
    private final Counter predictionErrorCounter;
    
    // Timers (for latency tracking with automatic percentiles)
    private final Timer featureExtractionTimer;
    private final Timer embeddingTimer;
    private final Timer outcomePredictionTimer;
    private final Timer nlpScoringTimer;
    
    // Distribution summaries (for token/size metrics)
    private final DistributionSummary tokenUsageSummary;
    private final DistributionSummary embeddingDimensionSummary;
    
    public MLMetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        
        // Initialize counters
        this.questionRepetitionCounter = Counter.builder("ml.question.repetition")
                .description("Count of repeated questions detected")
                .tag("severity", "warning")
                .register(meterRegistry);
        
        this.embeddingCacheHitCounter = Counter.builder("ml.embedding.cache.hit")
                .description("Embedding cache hits")
                .register(meterRegistry);
        
        this.embeddingCacheMissCounter = Counter.builder("ml.embedding.cache.miss")
                .description("Embedding cache misses")
                .register(meterRegistry);
        
        this.earlyStopTriggerCounter = Counter.builder("ml.early_stop.triggered")
                .description("Early stopping triggers")
                .register(meterRegistry);
        
        this.predictionErrorCounter = Counter.builder("ml.prediction.error")
                .description("Prediction errors")
                .register(meterRegistry);
        
        // Initialize timers with percentiles
        this.featureExtractionTimer = Timer.builder("ml.feature_extraction.duration")
                .description("Feature extraction latency")
                .publishPercentiles(0.5, 0.90, 0.95, 0.99)
                .register(meterRegistry);
        
        this.embeddingTimer = Timer.builder("ml.embedding.duration")
                .description("Embedding generation latency")
                .publishPercentiles(0.5, 0.90, 0.95, 0.99)
                .register(meterRegistry);
        
        this.outcomePredictionTimer = Timer.builder("ml.outcome_prediction.duration")
                .description("Outcome prediction latency")
                .publishPercentiles(0.5, 0.90, 0.95, 0.99)
                .register(meterRegistry);
        
        this.nlpScoringTimer = Timer.builder("ml.nlp_scoring.duration")
                .description("NLP scoring latency")
                .publishPercentiles(0.5, 0.90, 0.95, 0.99)
                .register(meterRegistry);
        
        // Initialize distribution summaries
        this.tokenUsageSummary = DistributionSummary.builder("ml.token.usage")
                .description("Token usage per request")
                .baseUnit("tokens")
                .publishPercentiles(0.5, 0.90, 0.95, 0.99)
                .register(meterRegistry);
        
        this.embeddingDimensionSummary = DistributionSummary.builder("ml.embedding.dimensions")
                .description("Embedding vector size")
                .baseUnit("dimensions")
                .register(meterRegistry);
    }
    
    // Counter methods
    
    public void recordQuestionRepetition(String questionType) {
        Counter.builder("ml.question.repetition")
                .tag("type", questionType)
                .register(meterRegistry)
                .increment();
    }
    
    public void recordEmbeddingCacheHit() {
        embeddingCacheHitCounter.increment();
    }
    
    public void recordEmbeddingCacheMiss() {
        embeddingCacheMissCounter.increment();
    }
    
    public void recordEarlyStop(String reason, String role) {
        Counter.builder("ml.early_stop.triggered")
                .tag("reason", reason)
                .tag("role", role)
                .register(meterRegistry)
                .increment();
    }
    
    public void recordPredictionError(String component, String errorType) {
        Counter.builder("ml.prediction.error")
                .tag("component", component)
                .tag("error_type", errorType)
                .register(meterRegistry)
                .increment();
    }
    
    // Timer methods (with direct recording)
    
    public void recordFeatureExtractionTime(long milliseconds) {
        featureExtractionTimer.record(milliseconds, TimeUnit.MILLISECONDS);
    }
    
    public void recordEmbeddingTime(long milliseconds) {
        embeddingTimer.record(milliseconds, TimeUnit.MILLISECONDS);
    }
    
    public void recordOutcomePredictionTime(long milliseconds) {
        outcomePredictionTimer.record(milliseconds, TimeUnit.MILLISECONDS);
    }
    
    public void recordNLPScoringTime(long milliseconds) {
        nlpScoringTimer.record(milliseconds, TimeUnit.MILLISECONDS);
    }
    
    // Timer helper for automatic timing
    public <T> T timeFeatureExtraction(Supplier<T> operation) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            return operation.get();
        } finally {
            sample.stop(featureExtractionTimer);
        }
    }
    
    public <T> T timeEmbedding(Supplier<T> operation) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            return operation.get();
        } finally {
            sample.stop(embeddingTimer);
        }
    }
    
    public <T> T timeOutcomePrediction(Supplier<T> operation) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            return operation.get();
        } finally {
            sample.stop(outcomePredictionTimer);
        }
    }
    
    // Distribution summary methods
    
    public void recordTokenUsage(int tokens) {
        tokenUsageSummary.record(tokens);
    }
    
    public void recordEmbeddingDimensions(int dimensions) {
        embeddingDimensionSummary.record(dimensions);
    }
    
    // Gauge registration (for current state metrics)
    
    public void registerActiveSessionsGauge(Supplier<Number> valueSupplier) {
        io.micrometer.core.instrument.Gauge.builder("ml.sessions.active", valueSupplier, 
                        supplier -> supplier.get().doubleValue())
                .description("Number of active ML-enabled sessions")
                .register(meterRegistry);
    }
    
    public void registerModelLoadedGauge(Supplier<Number> valueSupplier) {
        io.micrometer.core.instrument.Gauge.builder("ml.model.loaded", valueSupplier,
                        supplier -> supplier.get().doubleValue())
                .description("Model loaded status (1=loaded, 0=not loaded)")
                .register(meterRegistry);
    }
}
