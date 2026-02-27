package com.aiinterview.ml.adaptive;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Question difficulty calibration entity for IRT
 * Stores 2PL model parameters calibrated from historical responses
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "question_difficulty_calibration")
public class QuestionCalibration {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "question_id", nullable = false, length = 100)
    private String questionId;
    
    @Column(name = "role_id", nullable = false, length = 100)
    private String roleId;
    
    /**
     * Difficulty parameter (b) in 2PL IRT model
     * Represents ability level at which P(correct) = 0.5
     */
    @Column(name = "difficulty_b")
    private Double difficultyB;
    
    /**
     * Discrimination parameter (a) in 2PL IRT model
     * Represents how well question differentiates abilities
     */
    @Column(name = "discrimination_a")
    private Double discriminationA;
    
    /**
     * Number of responses used for calibration
     */
    @Column(name = "response_count")
    private Integer responseCount;
    
    /**
     * Mean score across all responses
     */
    @Column(name = "mean_score")
    private Double meanScore;
    
    /**
     * Score variance
     */
    @Column(name = "score_variance")
    private Double scoreVariance;
    
    @Column(name = "last_calibrated_at")
    private LocalDateTime lastCalibratedAt;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        
        // Set defaults if not provided
        if (difficultyB == null) {
            difficultyB = 0.0;
        }
        if (discriminationA == null) {
            discriminationA = 1.0;
        }
        if (responseCount == null) {
            responseCount = 0;
        }
        if (meanScore == null) {
            meanScore = 0.0;
        }
        if (scoreVariance == null) {
            scoreVariance = 0.25;
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
