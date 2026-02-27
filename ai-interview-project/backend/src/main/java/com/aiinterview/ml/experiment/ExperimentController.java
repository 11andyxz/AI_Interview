package com.aiinterview.ml.experiment;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST API for experiment tracking
 */
@Slf4j
@RestController
@RequestMapping("/api/ml/experiments")
public class ExperimentController {
    
    @Autowired
    private ExperimentTracker experimentTracker;
    
    @Autowired
    private ExperimentRepository experimentRepository;
    
    /**
     * Create a new experiment
     */
    @PostMapping
    public ResponseEntity<Experiment> createExperiment(@RequestBody Experiment experiment) {
        Experiment created = experimentTracker.createExperiment(experiment);
        return ResponseEntity.ok(created);
    }
    
    /**
     * Start an experiment
     */
    @PostMapping("/{experimentId}/start")
    public ResponseEntity<Void> startExperiment(@PathVariable String experimentId) {
        experimentTracker.startExperiment(experimentId);
        return ResponseEntity.ok().build();
    }
    
    /**
     * Get experiment assignment for a request
     */
    @GetMapping("/{experimentId}/assign")
    public ResponseEntity<ExperimentAssignment> assignExperiment(
            @PathVariable String experimentId,
            @RequestParam String requestId) {
        
        ExperimentAssignment assignment = experimentTracker.assignExperiment(experimentId, requestId);
        return ResponseEntity.ok(assignment);
    }
    
    /**
     * Log experiment metrics
     */
    @PostMapping("/{experimentId}/log")
    public ResponseEntity<Void> logMetrics(
            @PathVariable String experimentId,
            @RequestParam String requestId,
            @RequestBody Map<String, Double> metrics) {
        
        experimentTracker.logResult(experimentId, requestId, metrics);
        return ResponseEntity.ok().build();
    }
    
    /**
     * Evaluate experiment results
     */
    @GetMapping("/{experimentId}/evaluate")
    public ResponseEntity<ExperimentResult> evaluateExperiment(@PathVariable String experimentId) {
        ExperimentResult result = experimentTracker.evaluateExperiment(experimentId);
        return ResponseEntity.ok(result);
    }
    
    /**
     * Check and perform auto-rollback
     */
    @PostMapping("/{experimentId}/check-rollback")
    public ResponseEntity<Map<String, Boolean>> checkRollback(@PathVariable String experimentId) {
        boolean rolledBack = experimentTracker.checkAndRollback(experimentId);
        return ResponseEntity.ok(Map.of("rolled_back", rolledBack));
    }
    
    /**
     * Complete an experiment
     */
    @PostMapping("/{experimentId}/complete")
    public ResponseEntity<Void> completeExperiment(
            @PathVariable String experimentId,
            @RequestParam String winner) {
        
        experimentTracker.completeExperiment(experimentId, winner);
        return ResponseEntity.ok().build();
    }
    
    /**
     * Get all active experiments
     */
    @GetMapping("/active")
    public ResponseEntity<List<Experiment>> getActiveExperiments() {
        List<Experiment> experiments = experimentTracker.getActiveExperiments();
        return ResponseEntity.ok(experiments);
    }
    
    /**
     * Get all experiments
     */
    @GetMapping
    public ResponseEntity<List<Experiment>> getAllExperiments() {
        List<Experiment> experiments = experimentRepository.findAll();
        return ResponseEntity.ok(experiments);
    }
    
    /**
     * Get experiment by ID
     */
    @GetMapping("/{experimentId}")
    public ResponseEntity<Experiment> getExperiment(@PathVariable String experimentId) {
        return experimentRepository.findById(experimentId)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }
}
