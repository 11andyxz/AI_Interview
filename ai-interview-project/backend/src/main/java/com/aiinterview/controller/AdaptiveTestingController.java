package com.aiinterview.controller;

import com.aiinterview.ml.adaptive.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Adaptive testing controller
 * Provides REST API for ML-driven adaptive interview difficulty
 */
@Slf4j
@RestController
@RequestMapping("/api/adaptive")
@RequiredArgsConstructor
public class AdaptiveTestingController {
    
    private final CandidateAbilityEstimator abilityEstimator;
    private final AdaptiveQuestionSelector questionSelector;
    private final QuestionCalibrationService calibrationService;
    private final QuestionCalibrationRepository calibrationRepository;
    
    /**
     * Estimate candidate ability from response history
     */
    @PostMapping("/estimate-ability")
    public ResponseEntity<AbilityEstimate> estimateAbility(
            @RequestBody List<ResponseRecord> responseHistory) {
        
        log.info("Estimating ability from {} responses", responseHistory.size());
        
        AbilityEstimate estimate = abilityEstimator.estimateAbility(responseHistory);
        
        return ResponseEntity.ok(estimate);
    }
    
    /**
     * Update ability estimate with new response
     */
    @PostMapping("/update-ability")
    public ResponseEntity<AbilityEstimate> updateAbility(
            @RequestBody UpdateAbilityRequest request) {
        
        log.info("Updating ability: prior theta={}, new score={}",
                 request.getPriorEstimate().getTheta(),
                 request.getNewResponse().getScore());
        
        AbilityEstimate updated = abilityEstimator.updateAbility(
            request.getPriorEstimate(),
            request.getNewResponse()
        );
        
        return ResponseEntity.ok(updated);
    }
    
    /**
     * Select next adaptive question
     */
    @PostMapping("/select-question")
    public ResponseEntity<QuestionItem> selectQuestion(
            @RequestBody SelectQuestionRequest request) {
        
        log.info("Selecting question: roleId={}, theta={}, asked={}",
                 request.getRoleId(), 
                 request.getCurrentAbility().getTheta(),
                 request.getAskedQuestionIds().size());
        
        Optional<QuestionItem> selected = questionSelector.selectNextQuestion(
            request.getInterviewId(),
            request.getRoleId(),
            request.getCurrentAbility(),
            request.getAskedQuestionIds()
        );
        
        return selected
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }
    
    /**
     * Check if testing should terminate
     */
    @PostMapping("/should-terminate")
    public ResponseEntity<TerminationDecision> shouldTerminate(
            @RequestBody TerminationCheckRequest request) {
        
        boolean shouldStop = questionSelector.shouldTerminate(
            request.getCurrentAbility(),
            request.getQuestionsAsked(),
            request.getMaxQuestions() != null ? request.getMaxQuestions() : 12
        );
        
        TerminationDecision decision = TerminationDecision.builder()
            .shouldTerminate(shouldStop)
            .standardError(request.getCurrentAbility().getStandardError())
            .questionsAsked(request.getQuestionsAsked())
            .reason(shouldStop ? 
                (request.getCurrentAbility().getStandardError() < 0.3 ? 
                    "SE threshold met" : "Max questions reached") :
                "Continue testing")
            .build();
        
        return ResponseEntity.ok(decision);
    }
    
    /**
     * Update question calibration with new response
     */
    @PostMapping("/calibrate-question")
    public ResponseEntity<Void> calibrateQuestion(
            @RequestBody CalibrationUpdateRequest request) {
        
        log.info("Updating calibration: questionId={}, roleId={}, score={}",
                 request.getQuestionId(), request.getRoleId(), request.getScore());
        
        calibrationService.updateCalibration(
            request.getQuestionId(),
            request.getRoleId(),
            request.getScore(),
            request.getCandidateAbility()
        );
        
        return ResponseEntity.ok().build();
    }
    
    /**
     * Bootstrap calibration from historical data
     */
    @PostMapping("/bootstrap-calibration")
    public ResponseEntity<Void> bootstrapCalibration(
            @RequestBody BootstrapRequest request) {
        
        log.info("Bootstrapping calibration: questionId={}, roleId={}, responses={}",
                 request.getQuestionId(), request.getRoleId(),
                 request.getHistoricalResponses().size());
        
        calibrationService.bootstrapFromHistory(
            request.getQuestionId(),
            request.getRoleId(),
            request.getHistoricalResponses()
        );
        
        return ResponseEntity.ok().build();
    }
    
    /**
     * Get calibration for question
     */
    @GetMapping("/calibration/{questionId}/{roleId}")
    public ResponseEntity<QuestionCalibration> getCalibration(
            @PathVariable String questionId,
            @PathVariable String roleId) {
        
        return calibrationRepository
            .findByQuestionIdAndRoleId(questionId, roleId)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }
    
    /**
     * Get all calibrations for role
     */
    @GetMapping("/calibrations/{roleId}")
    public ResponseEntity<List<QuestionCalibration>> getCalibrationsByRole(
            @PathVariable String roleId) {
        
        List<QuestionCalibration> calibrations = 
            calibrationRepository.findByRoleId(roleId);
        
        return ResponseEntity.ok(calibrations);
    }
    
    // Request/Response DTOs
    
    @lombok.Data
    public static class UpdateAbilityRequest {
        private AbilityEstimate priorEstimate;
        private ResponseRecord newResponse;
    }
    
    @lombok.Data
    public static class SelectQuestionRequest {
        private String interviewId;
        private String roleId;
        private AbilityEstimate currentAbility;
        private Set<String> askedQuestionIds;
    }
    
    @lombok.Data
    public static class TerminationCheckRequest {
        private AbilityEstimate currentAbility;
        private Integer questionsAsked;
        private Integer maxQuestions;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class TerminationDecision {
        private boolean shouldTerminate;
        private double standardError;
        private int questionsAsked;
        private String reason;
    }
    
    @lombok.Data
    public static class CalibrationUpdateRequest {
        private String questionId;
        private String roleId;
        private double score;
        private double candidateAbility;
    }
    
    @lombok.Data
    public static class BootstrapRequest {
        private String questionId;
        private String roleId;
        private List<QuestionCalibrationService.HistoricalResponse> historicalResponses;
    }
}
