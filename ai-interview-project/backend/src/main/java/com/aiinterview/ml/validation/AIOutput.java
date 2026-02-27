package com.aiinterview.ml.validation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Wrapper for AI-generated output
 * Contains both raw response and parsed structured data
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AIOutput {
    // Raw response from AI
    private String rawResponse;
    
    // Parsed JSON structure (if applicable)
    private Map<String, Object> parsedData;
    
    // Metadata
    private String model;
    private int tokensUsed;
    private long latencyMs;
    private double cost;
    
    // Request context
    private String prompt;
    private ValidationContext.RequestType requestType;
    
    /**
     * Check if output was successfully parsed
     */
    public boolean isParsed() {
        return parsedData != null && !parsedData.isEmpty();
    }
    
    /**
     * Get a field from parsed data
     */
    public Object getField(String fieldName) {
        if (parsedData == null) {
            return null;
        }
        return parsedData.get(fieldName);
    }
    
    /**
     * Check if a required field exists
     */
    public boolean hasField(String fieldName) {
        return parsedData != null && parsedData.containsKey(fieldName);
    }
    
    // Helper methods for QualityMonitor
    
    @SuppressWarnings("unchecked")
    public Map<String, Object> getCandidateInfo() {
        Object field = getField("candidateInfo");
        return field instanceof Map ? (Map<String, Object>) field : null;
    }
    
    @SuppressWarnings("unchecked")
    public Map<String, Object> getSkills() {
        Object field = getField("skills");
        return field instanceof Map ? (Map<String, Object>) field : null;
    }
    
    @SuppressWarnings("unchecked")
    public Map<String, Object> getExperience() {
        Object field = getField("experience");
        return field instanceof Map ? (Map<String, Object>) field : null;
    }
    
    @SuppressWarnings("unchecked")
    public Map<String, Object> getAssessment() {
        Object field = getField("assessment");
        return field instanceof Map ? (Map<String, Object>) field : null;
    }
    
    @SuppressWarnings("unchecked")
    public java.util.List<String> getInterviewQuestions() {
        Object field = getField("interviewQuestions");
        if (field instanceof java.util.List) {
            return (java.util.List<String>) field;
        }
        return null;
    }
    
    public String getFeedback() {
        Object field = getField("feedback");
        return field != null ? field.toString() : null;
    }
    
    public Double getScore() {
        Object field = getField("score");
        if (field instanceof Number) {
            return ((Number) field).doubleValue();
        }
        return null;
    }
    
    @SuppressWarnings("unchecked")
    public java.util.List<String> getStrengths() {
        Object field = getField("strengths");
        if (field instanceof java.util.List) {
            return (java.util.List<String>) field;
        }
        return null;
    }
    
    @SuppressWarnings("unchecked")
    public java.util.List<String> getImprovements() {
        Object field = getField("improvements");
        if (field instanceof java.util.List) {
            return (java.util.List<String>) field;
        }
        return null;
    }
}
