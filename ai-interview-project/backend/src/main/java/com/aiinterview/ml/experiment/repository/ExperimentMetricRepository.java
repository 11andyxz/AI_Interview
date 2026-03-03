package com.aiinterview.ml.experiment.repository;

import com.aiinterview.ml.experiment.model.ExperimentMetric;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExperimentMetricRepository extends JpaRepository<ExperimentMetric, Long> {
    List<ExperimentMetric> findByExperimentId(Long experimentId);
    List<ExperimentMetric> findByExperimentIdAndVariant(Long experimentId, String variant);
    long countByExperimentIdAndVariant(Long experimentId, String variant);
}
