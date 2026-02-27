package com.aiinterview.ml.experiment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for ExperimentMetric entities
 */
@Repository
public interface ExperimentMetricRepository extends JpaRepository<ExperimentMetric, Long> {
    
    List<ExperimentMetric> findByExperimentId(String experimentId);
    
    List<ExperimentMetric> findByExperimentIdAndVariant(String experimentId, String variant);
    
    @Query("SELECT COUNT(em) FROM ExperimentMetric em WHERE em.experimentId = :experimentId AND em.variant = :variant")
    long countByExperimentIdAndVariant(@Param("experimentId") String experimentId, @Param("variant") String variant);
    
    @Query("SELECT AVG(em.qualityScore) FROM ExperimentMetric em WHERE em.experimentId = :experimentId AND em.variant = :variant AND em.qualityScore IS NOT NULL")
    Double avgQualityScoreByExperimentIdAndVariant(@Param("experimentId") String experimentId, @Param("variant") String variant);
    
    @Query("SELECT AVG(em.latencyMs) FROM ExperimentMetric em WHERE em.experimentId = :experimentId AND em.variant = :variant AND em.latencyMs IS NOT NULL")
    Double avgLatencyByExperimentIdAndVariant(@Param("experimentId") String experimentId, @Param("variant") String variant);
    
    @Query("SELECT AVG(em.costUsd) FROM ExperimentMetric em WHERE em.experimentId = :experimentId AND em.variant = :variant AND em.costUsd IS NOT NULL")
    Double avgCostByExperimentIdAndVariant(@Param("experimentId") String experimentId, @Param("variant") String variant);
}
