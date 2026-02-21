package com.aiinterview.ml.observability;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for LLM call metrics
 */
@Repository
public interface LlmCallMetricRepository extends JpaRepository<LlmCallMetric, String> {
    
    List<LlmCallMetric> findByEndpointAndCreatedAtBetween(
        String endpoint, 
        LocalDateTime start, 
        LocalDateTime end
    );
    
    List<LlmCallMetric> findByModelAndCreatedAtBetween(
        String model, 
        LocalDateTime start, 
        LocalDateTime end
    );
    
    List<LlmCallMetric> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);
    
    @Query("SELECT AVG(m.qualityScore) FROM LlmCallMetric m " +
           "WHERE m.endpoint = :endpoint " +
           "AND m.createdAt BETWEEN :start AND :end " +
           "AND m.qualityScore IS NOT NULL")
    Double getAverageQuality(
        @Param("endpoint") String endpoint,
        @Param("start") LocalDateTime start,
        @Param("end") LocalDateTime end
    );
    
    @Query("SELECT AVG(m.latencyMs) FROM LlmCallMetric m " +
           "WHERE m.endpoint = :endpoint " +
           "AND m.createdAt BETWEEN :start AND :end")
    Double getAverageLatency(
        @Param("endpoint") String endpoint,
        @Param("start") LocalDateTime start,
        @Param("end") LocalDateTime end
    );
    
    @Query("SELECT SUM(m.costUsd) FROM LlmCallMetric m " +
           "WHERE m.createdAt BETWEEN :start AND :end")
    Double getTotalCost(
        @Param("start") LocalDateTime start,
        @Param("end") LocalDateTime end
    );
    
    @Query("SELECT COUNT(m) FROM LlmCallMetric m " +
           "WHERE m.endpoint = :endpoint " +
           "AND m.validationPassed = false " +
           "AND m.createdAt BETWEEN :start AND :end")
    Long countFailures(
        @Param("endpoint") String endpoint,
        @Param("start") LocalDateTime start,
        @Param("end") LocalDateTime end
    );
    
    @Query("SELECT DISTINCT m.endpoint FROM LlmCallMetric m")
    List<String> findAllEndpoints();
    
    @Query("SELECT DISTINCT m.model FROM LlmCallMetric m")
    List<String> findAllModels();
    
    long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);
    
    @Query("SELECT SUM(m.inputTokens + m.outputTokens) FROM LlmCallMetric m " +
           "WHERE m.createdAt BETWEEN :start AND :end")
    Long getTotalTokens(
        @Param("start") LocalDateTime start,
        @Param("end") LocalDateTime end
    );
    
    @Query("SELECT m.endpoint, SUM(m.costUsd) FROM LlmCallMetric m " +
           "WHERE m.createdAt BETWEEN :start AND :end " +
           "GROUP BY m.endpoint")
    List<Object[]> getCostByEndpoint(
        @Param("start") LocalDateTime start,
        @Param("end") LocalDateTime end
    );
    
    @Query("SELECT m.model, SUM(m.costUsd) FROM LlmCallMetric m " +
           "WHERE m.createdAt BETWEEN :start AND :end " +
           "GROUP BY m.model")
    List<Object[]> getCostByModel(
        @Param("start") LocalDateTime start,
        @Param("end") LocalDateTime end
    );
}
