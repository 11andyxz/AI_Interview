package com.aiinterview.ml.guardrails.repository;

import com.aiinterview.ml.guardrails.model.LlmOutputConformance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface LlmOutputConformanceRepository extends JpaRepository<LlmOutputConformance, Long> {

    List<LlmOutputConformance> findByEndpointAndCreatedAtAfter(String endpoint, LocalDateTime after);

    @Query("SELECT c FROM LlmOutputConformance c WHERE c.createdAt > :since")
    List<LlmOutputConformance> findRecentRecords(@Param("since") LocalDateTime since);

    @Query("SELECT DISTINCT c.endpoint FROM LlmOutputConformance c WHERE c.createdAt > :since")
    List<String> findDistinctEndpointsSince(@Param("since") LocalDateTime since);

    long countByEndpointAndCreatedAtAfter(String endpoint, LocalDateTime after);
    long countByEndpointAndInitialValidAndCreatedAtAfter(String endpoint, boolean initialValid, LocalDateTime after);
    long countByEndpointAndFinalValidAndCreatedAtAfter(String endpoint, boolean finalValid, LocalDateTime after);
    long countByEndpointAndUsedFallbackAndCreatedAtAfter(String endpoint, boolean usedFallback, LocalDateTime after);
}
