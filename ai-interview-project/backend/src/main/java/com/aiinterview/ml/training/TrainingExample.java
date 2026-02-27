package com.aiinterview.ml.training;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Training example for supervised fine-tuning
 * Represents a high-quality QA interaction suitable for model training
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrainingExample {
    
    private Long interactionId;
    
    private String conversationId;
    
    private String role;
    
    private String difficulty;
    
    /**
     * Input messages (conversation history)
     */
    private List<Message> messages;
    
    /**
     * Expected completion (for instruction tuning)
     */
    private String completion;
    
    /**
     * Quality score (0-100)
     */
    private Double qualityScore;
    
    /**
     * Validation pass status
     */
    private Boolean validationPass;
    
    /**
     * Metadata for filtering
     */
    private String category;
    
    private String skill;
    
    private LocalDateTime createdAt;
    
    /**
     * Individual message in conversation
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Message {
        private String role;  // system | user | assistant
        private String content;
    }
}
