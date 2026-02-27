package com.aiinterview.ml.observability;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for evaluation metrics
 */
@Repository
public interface EvaluationMetricRepository extends JpaRepository<EvaluationMetric, String> {
    
    List<EvaluationMetric> findByEndpointAndCreatedAtBetween(
        String endpoint,
        LocalDateTime start,
        LocalDateTime end
    );
    
    List<EvaluationMetric> findByEvaluationTypeAndCreatedAtBetween(
        String evaluationType,
        LocalDateTime start,
        LocalDateTime end
    );
    
    @Query("SELECT AVG(e.qualityScore) FROM EvaluationMetric e " +
           "WHERE e.endpoint = :endpoint " +
           "AND e.evaluationType = :type " +
           "AND e.createdAt BETWEEN :start AND :end " +
           "AND e.qualityScore IS NOT NULL")
    Double getAverageQuality(
        @Param("endpoint") String endpoint,
        @Param("type") String evaluationType,
        @Param("start") LocalDateTime start,
        @Param("end") LocalDateTime end
    );
    
    @Query("SELECT COUNT(e) FROM EvaluationMetric e " +
           "WHERE e.endpoint = :endpoint " +
           "AND e.evaluationType = :type " +
           "AND e.passed = true " +
           "AND e.createdAt BETWEEN :start AND :end")
    Long countPassed(
        @Param("endpoint") String endpoint,
        @Param("type") String evaluationType,
        @Param("start") LocalDateTime start,
        @Param("end") LocalDateTime end
    );
    
    @Query("SELECT COUNT(e) FROM EvaluationMetric e " +
           "WHERE e.endpoint = :endpoint " +
           "AND e.evaluationType = :type " +
           "AND e.createdAt BETWEEN :start AND :end")
    Long countTotal(
        @Param("endpoint") String endpoint,
        @Param("type") String evaluationType,
        @Param("start") LocalDateTime start,
        @Param("end") LocalDateTime end
    );
}
