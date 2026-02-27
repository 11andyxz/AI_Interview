package com.aiinterview.model;

import java.util.Collections;
import java.util.List;

/**
 * Type alias for InterviewMessage - used for training data collection
 * This allows Week 8 training code to reference interview interactions
 * while maintaining compatibility with existing data model
 */
public class InterviewInteraction extends InterviewMessage {
    
    /**
     * Get quality score for training data filtering
     * Uses evaluation_score from parent InterviewMessage
     */
    public Double getQualityScore() {
        return getEvaluationScore();
    }
    
    /**
     * Check if interaction has been validated
     * An interaction is considered validated if it has an evaluation score
     */
    public boolean isValidated() {
        return getEvaluationScore() != null && getEvaluationScore() > 0;
    }
    
    /**
     * Get answer text (from user message)
     */
    public String getAnswerText() {
        return getUserMessage();
    }
    
    /**
     * Get question text (from AI message)
     */
    public String getQuestionText() {
        return getAiMessage();
    }
    
    // Week 8 training compatibility methods - provide defaults
    // These fields don't exist in the database, but are expected by TrainingDataCollector
    
    /**
     * Get conversation ID (use interview_id from parent)
     */
    public String getConversationId() {
        return getInterviewId();
    }
    
    /**
     * Get role - default to empty string
     * In production, this should be fetched from interview session context
     */
    public String getRole() {
        return "";
    }
    
    /**
     * Get difficulty level (use evaluation rubric level from parent)
     */
    public String getDifficulty() {
        String rubric = getEvaluationRubricLevel();
        return rubric != null ? rubric : "MEDIUM";
    }
    
    /**
     * Get category - default to empty string
     * In production, this should be fetched from question metadata
     */
    public String getCategory() {
        return "";
    }
    
    /**
     * Get skill - default to empty string
     * In production, this should be fetched from question metadata
     */
    public String getSkill() {
        return "";
    }
    
    /**
     * Check if interaction is flagged - default to false
     */
    public Boolean getFlagged() {
        return false;
    }
    
    /**
     * Alias for getQuestionText (compatibility)
     */
    public String getQuestion() {
        return getQuestionText();
    }
    
    /**
     * Alias for getAnswerText (compatibility)
     */
    public String getAnswer() {
        return getAnswerText();
    }
    
    /**
     * Get validation errors - default to empty list
     * InterviewMessage doesn't track validation errors, assume valid
     */
    public List<String> getValidationErrors() {
        return Collections.emptyList();
    }
    
    /**
     * Get resume context - default to empty string
     * In production, this should be fetched from candidate profile
     */
    public String getResumeContext() {
        return "";
    }
    
    /**
     * Get model version - default to "gpt-4o-mini"
     * In production, this should be tracked in LLM call metadata
     */
    public String getModelVersion() {
        return "gpt-4o-mini";
    }
}
