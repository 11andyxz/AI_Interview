package com.aiinterview.ml.experiment.repository;

import com.aiinterview.ml.experiment.model.Experiment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ExperimentRepository extends JpaRepository<Experiment, Long> {
    List<Experiment> findByStatus(String status);
    Optional<Experiment> findByName(String name);
    List<Experiment> findByTargetEndpoint(String targetEndpoint);
    List<Experiment> findByStatusAndTargetEndpoint(String status, String targetEndpoint);
}
