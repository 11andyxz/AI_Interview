package com.aiinterview.ml.experiment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for Experiment entities
 */
@Repository
public interface ExperimentRepository extends JpaRepository<Experiment, String> {
    
    List<Experiment> findByStatus(String status);
    
    List<Experiment> findByType(String type);
    
    List<Experiment> findByStatusOrderByCreatedAtDesc(String status);
}
