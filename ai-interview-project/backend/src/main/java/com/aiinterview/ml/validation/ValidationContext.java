package com.aiinterview.ml.validation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Context information for validation
 * Provides additional data needed by validators
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidationContext {
    // Request type
    private RequestType requestType;
    
    // Target difficulty level (for questions)
    private Integer difficultyLevel;
    
    // Target role/position
    private String targetRole;
    
    // Target tech stack
    private String[] techStack;
    
    // Model information
    private String modelVersion;
    
    // Interview type
    private String interviewType; // "technical", "behavioral", "system_design"
    
    // User information
    private Long userId;
    private String userRole;
    
    // Additional metadata
    private Map<String, Object> metadata;
    
    // Lombok will generate getRequestType() that returns RequestType enum
    // We also provide a helper method for String representation
    public String getRequestTypeName() {
        return requestType != null ? requestType.name() : null;
    }
    
    public String getRole() {
        return targetRole != null ? targetRole : userRole;
    }
    
    public enum RequestType {
        RESUME_ANALYSIS,
        QUESTION_GENERATION,
        ANSWER_EVALUATION,
        FOLLOW_UP_QUESTION,
        INTERVIEW_REPORT
    }
}
