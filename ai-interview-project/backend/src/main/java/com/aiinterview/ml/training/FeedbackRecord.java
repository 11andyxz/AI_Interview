package com.aiinterview.ml.training;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * Human feedback on AI responses for training data quality control
 */
@Entity
@Table(name = "ai_feedback")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeedbackRecord {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "interaction_id")
    private Long interactionId;
    
    @Column(name = "conversation_id")
    private String conversationId;
    
    /**
     * Feedback type: thumbs_up | thumbs_down | flag | correction
     */
    @Column(name = "feedback_type")
    private String feedbackType;
    
    /**
     * Overall rating (1-5)
     */
    private Integer rating;
    
    /**
     * Specific issues: hallucination | irrelevant | incomplete | incorrect | other
     */
    @Column(columnDefinition = "TEXT")
    private String issues;
    
    /**
     * Free-text feedback
     */
    @Column(columnDefinition = "TEXT")
    private String comment;
    
    /**
     * Suggested correction (for training)
     */
    @Column(name = "correction", columnDefinition = "TEXT")
    private String correction;
    
    /**
     * Feedback source: interviewer | candidate | admin | automated
     */
    @Column(name = "source")
    private String source;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
