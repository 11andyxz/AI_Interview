package com.aiinterview.ml.adaptive;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Adaptive question selector using Fisher Information maximization
 * Implements CAT (Computerized Adaptive Testing) strategy
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdaptiveQuestionSelector {
    
    private final QuestionCalibrationRepository calibrationRepository;
    
    private static final double SE_THRESHOLD = 0.3;
    private static final int MIN_QUESTIONS = 8;
    private static final int MAX_QUESTIONS = 12;
    private static final double DIFFICULTY_TOLERANCE = 1.0;
    
    /**
     * Select next question using Fisher Information maximization
     * Chooses question that provides most information at current ability estimate
     */
    public Optional<QuestionItem> selectNextQuestion(
            String interviewId,
            String roleId,
            AbilityEstimate currentAbility,
            Set<String> askedQuestionIds) {
        
        log.debug("Selecting next question: roleId={}, theta={}, SE={}, asked={}",
                  roleId, currentAbility.getTheta(), 
                  currentAbility.getStandardError(), askedQuestionIds.size());
        
        // Load all calibrated questions for role
        List<QuestionCalibration> calibrations = 
            calibrationRepository.findByRoleId(roleId);
        
        if (calibrations.isEmpty()) {
            log.warn("No calibrated questions found for role: {}", roleId);
            return Optional.empty();
        }
        
        // Convert to QuestionItems and filter out asked questions
        List<QuestionItem> availableQuestions = calibrations.stream()
            .filter(cal -> !askedQuestionIds.contains(cal.getQuestionId()))
            .filter(cal -> cal.getResponseCount() >= 5) // Minimum calibration data
            .map(this::toQuestionItem)
            .collect(Collectors.toList());
        
        if (availableQuestions.isEmpty()) {
            log.warn("No available questions remaining for role: {}", roleId);
            return fallbackSelection(calibrations, askedQuestionIds);
        }
        
        // Find question that maximizes Fisher Information at current ability
        QuestionItem bestQuestion = null;
        double maxInformation = Double.NEGATIVE_INFINITY;
        
        for (QuestionItem question : availableQuestions) {
            // Prefer questions within ±1 difficulty of current ability
            double difficultyDiff = Math.abs(question.getDifficultyB() - currentAbility.getTheta());
            
            double information = question.fisherInformation(currentAbility.getTheta());
            
            // Bonus for questions near target difficulty
            if (difficultyDiff <= DIFFICULTY_TOLERANCE) {
                information *= 1.2;
            }
            
            if (information > maxInformation) {
                maxInformation = information;
                bestQuestion = question;
            }
        }
        
        if (bestQuestion != null) {
            log.info("Selected question: id={}, difficulty={}, discrimination={}, information={}",
                     bestQuestion.getQuestionId(), bestQuestion.getDifficultyB(),
                     bestQuestion.getDiscriminationA(), maxInformation);
        }
        
        return Optional.ofNullable(bestQuestion);
    }
    
    /**
     * Determine if adaptive testing should terminate
     * Stops when SE is low enough or max questions reached
     */
    public boolean shouldTerminate(
            AbilityEstimate estimate,
            int questionsAsked,
            int maxQuestions) {
        
        // Always ask minimum questions
        if (questionsAsked < MIN_QUESTIONS) {
            log.debug("Continue testing: below minimum questions ({} < {})",
                      questionsAsked, MIN_QUESTIONS);
            return false;
        }
        
        // Stop if SE is low enough
        if (estimate.getStandardError() < SE_THRESHOLD) {
            log.info("Terminate testing: SE threshold met ({} < {})",
                     estimate.getStandardError(), SE_THRESHOLD);
            return true;
        }
        
        // Stop if max questions reached
        if (questionsAsked >= maxQuestions) {
            log.info("Terminate testing: max questions reached ({} >= {})",
                     questionsAsked, maxQuestions);
            return true;
        }
        
        log.debug("Continue testing: SE={}, questions={}/{}", 
                  estimate.getStandardError(), questionsAsked, maxQuestions);
        return false;
    }
    
    /**
     * Fallback selection when no calibrated questions available
     * Uses questions with default parameters or insufficient calibration
     */
    private Optional<QuestionItem> fallbackSelection(
            List<QuestionCalibration> allCalibrations,
            Set<String> askedQuestionIds) {
        
        log.warn("Using fallback selection strategy");
        
        // Try questions with any calibration data
        Optional<QuestionCalibration> fallback = allCalibrations.stream()
            .filter(cal -> !askedQuestionIds.contains(cal.getQuestionId()))
            .min(Comparator.comparingInt(QuestionCalibration::getResponseCount));
        
        return fallback.map(this::toQuestionItem);
    }
    
    /**
     * Convert calibration entity to question item
     */
    private QuestionItem toQuestionItem(QuestionCalibration calibration) {
        return QuestionItem.builder()
            .questionId(calibration.getQuestionId())
            .roleId(calibration.getRoleId())
            .difficultyB(calibration.getDifficultyB())
            .discriminationA(calibration.getDiscriminationA())
            .responseCount(calibration.getResponseCount())
            .build();
    }
}
