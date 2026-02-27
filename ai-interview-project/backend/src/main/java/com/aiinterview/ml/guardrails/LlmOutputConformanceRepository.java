package com.aiinterview.ml.guardrails;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for LLM output conformance tracking
 */
@Repository
public interface LlmOutputConformanceRepository extends JpaRepository<LlmOutputConformance, Long> {
    
    /**
     * Find by endpoint and time range
     */
    List<LlmOutputConformance> findByEndpointAndCreatedAtBetween(
        String endpoint, 
        LocalDateTime start, 
        LocalDateTime end
    );
    
    /**
     * Find by model and initial validity
     */
    List<LlmOutputConformance> findByModelAndInitialValid(
        String model, 
        Boolean initialValid
    );
    
    /**
     * Calculate first-pass validation rate for endpoint
     */
    @Query("SELECT AVG(CASE WHEN c.initialValid = true THEN 1.0 ELSE 0.0 END) " +
           "FROM LlmOutputConformance c " +
           "WHERE c.endpoint = :endpoint AND c.createdAt >= :since")
    Double calculateFirstPassRate(
        @Param("endpoint") String endpoint, 
        @Param("since") LocalDateTime since
    );
    
    /**
     * Calculate repair success rate
     */
    @Query("SELECT AVG(CASE WHEN c.finalValid = true AND c.repairAttempts > 0 THEN 1.0 ELSE 0.0 END) " +
           "FROM LlmOutputConformance c " +
           "WHERE c.endpoint = :endpoint AND c.repairAttempts > 0 AND c.createdAt >= :since")
    Double calculateRepairSuccessRate(
        @Param("endpoint") String endpoint, 
        @Param("since") LocalDateTime since
    );
    
    /**
     * Calculate fallback rate
     */
    @Query("SELECT AVG(CASE WHEN c.usedFallback = true THEN 1.0 ELSE 0.0 END) " +
           "FROM LlmOutputConformance c " +
           "WHERE c.endpoint = :endpoint AND c.createdAt >= :since")
    Double calculateFallbackRate(
        @Param("endpoint") String endpoint, 
        @Param("since") LocalDateTime since
    );
    
    /**
     * Calculate average repair overhead
     */
    @Query("SELECT AVG(c.totalLatencyMs) " +
           "FROM LlmOutputConformance c " +
           "WHERE c.endpoint = :endpoint AND c.repairAttempts > 0 AND c.createdAt >= :since")
    Double calculateAverageRepairLatency(
        @Param("endpoint") String endpoint, 
        @Param("since") LocalDateTime since
    );
    
    /**
     * Get recent conformance records
     */
    @Query("SELECT c FROM LlmOutputConformance c " +
           "WHERE c.endpoint = :endpoint AND c.createdAt >= :since " +
           "ORDER BY c.createdAt DESC")
    List<LlmOutputConformance> findRecentByEndpoint(
        @Param("endpoint") String endpoint, 
        @Param("since") LocalDateTime since
    );
}
