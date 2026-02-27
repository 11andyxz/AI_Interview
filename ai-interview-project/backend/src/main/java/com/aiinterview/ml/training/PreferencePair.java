package com.aiinterview.ml.training;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Preference pair for RLHF/DPO training
 * Contains a question with two responses: chosen (better) and rejected (worse)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PreferencePair {
    
    private Long pairId;
    
    private String conversationId;
    
    /**
     * The question/prompt
     */
    private String prompt;
    
    /**
     * Context (resume, previous Q&A)
     */
    private String context;
    
    /**
     * Preferred response (higher quality)
     */
    private Response chosen;
    
    /**
     * Rejected response (lower quality)
     */
    private Response rejected;
    
    /**
     * Quality margin (chosen_score - rejected_score)
     */
    private Double margin;
    
    private String role;
    
    private String difficulty;
    
    private LocalDateTime createdAt;
    
    /**
     * Single response with metadata
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private String content;
        private Double qualityScore;
        private String modelVersion;
        private Boolean validationPass;
    }
}
