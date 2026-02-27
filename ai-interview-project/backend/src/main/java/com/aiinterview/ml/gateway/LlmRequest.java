package com.aiinterview.ml.gateway;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * LLM request representation for routing
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LlmRequest {
    
    /**
     * Request type: question_generation, answer_evaluation, etc.
     */
    private String requestType;
    
    /**
     * Unique request ID for consistent experiment assignment
     */
    private String requestId;
    
    /**
     * Session ID for user-level consistency
     */
    private String sessionId;
    
    /**
     * Request payload
     */
    private Map<String, Object> payload;
}
