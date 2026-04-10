package com.aiinterview.ml.prediction.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Tracks candidate's skill profile and performance trends throughout the interview.
 * Used for outcome prediction and knowledge gap detection.
 */
@Entity
@Table(name = "candidate_skill_profile",
       uniqueConstraints = @UniqueConstraint(name = "uk_session", columnNames = {"session_id"}),
       indexes = {
           @Index(name = "idx_user_role", columnList = "user_id,role_id"),
           @Index(name = "idx_updated", columnList = "updated_at")
       })
public class CandidateSkillProfile {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * Interview session identifier
     */
    @Column(name = "session_id", nullable = false, length = 100)
    private String sessionId;
    
    @Column(name = "user_id", length = 100)
    private String userId;
    
    @Column(name = "role_id")
    private Long roleId;
    
    /**
     * Cumulative score across all answered questions
     */
    @Column(name = "cumulative_score", nullable = false)
    private Double cumulativeScore = 0.0;
    
    /**
     * Number of questions answered so far
     */
    @Column(name = "question_count", nullable = false)
    private Integer questionCount = 0;
    
    /**
     * Recent score history stored as JSON array
     * Format: [score1, score2, score3, ...]
     */
    @Column(name = "score_trend", columnDefinition = "JSON")
    private String scoreTrend;
    
    /**
     * Mean of recent scores
     */
    @Column(name = "score_mean")
    private Double scoreMean;
    
    /**
     * Standard deviation of recent scores
     */
    @Column(name = "score_std")
    private Double scoreStd;
    
    /**
     * Confidence level in predictions (0-1)
     */
    @Column(name = "confidence")
    private Double confidence;
    
    /**
     * Predicted final score for the interview
     */
    @Column(name = "predicted_final_score")
    private Double predictedFinalScore;
    
    /**
     * Probability of passing the interview (0-1)
     */
    @Column(name = "pass_probability")
    private Double passProbability;
    
    /**
     * Score stability metric (lower = more stable)
     */
    @Column(name = "score_stability")
    private Double scoreStability;
    
    /**
     * Whether early stopping was triggered
     */
    @Column(name = "early_stopping_triggered")
    private Boolean earlyStoppingTriggered = false;
    
    /**
     * Reason for early stopping (early_pass, early_fail, null)
     */
    @Column(name = "early_stopping_reason", length = 50)
    private String earlyStoppingReason;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }
    
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
    
    // Getters and setters
    
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getSessionId() {
        return sessionId;
    }
    
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }
    
    public String getUserId() {
        return userId;
    }
    
    public void setUserId(String userId) {
        this.userId = userId;
    }
    
    public Long getRoleId() {
        return roleId;
    }
    
    public void setRoleId(Long roleId) {
        this.roleId = roleId;
    }
    
    public Double getCumulativeScore() {
        return cumulativeScore;
    }
    
    public void setCumulativeScore(Double cumulativeScore) {
        this.cumulativeScore = cumulativeScore;
    }
    
    public Integer getQuestionCount() {
        return questionCount;
    }
    
    public void setQuestionCount(Integer questionCount) {
        this.questionCount = questionCount;
    }
    
    public String getScoreTrend() {
        return scoreTrend;
    }
    
    public void setScoreTrend(String scoreTrend) {
        this.scoreTrend = scoreTrend;
    }
    
    public Double getScoreMean() {
        return scoreMean;
    }
    
    public void setScoreMean(Double scoreMean) {
        this.scoreMean = scoreMean;
    }
    
    public Double getScoreStd() {
        return scoreStd;
    }
    
    public void setScoreStd(Double scoreStd) {
        this.scoreStd = scoreStd;
    }
    
    public Double getConfidence() {
        return confidence;
    }
    
    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }
    
    public Double getPredictedFinalScore() {
        return predictedFinalScore;
    }
    
    public void setPredictedFinalScore(Double predictedFinalScore) {
        this.predictedFinalScore = predictedFinalScore;
    }
    
    public Double getPassProbability() {
        return passProbability;
    }
    
    public void setPassProbability(Double passProbability) {
        this.passProbability = passProbability;
    }
    
    public Double getScoreStability() {
        return scoreStability;
    }
    
    public void setScoreStability(Double scoreStability) {
        this.scoreStability = scoreStability;
    }
    
    public Boolean getEarlyStoppingTriggered() {
        return earlyStoppingTriggered;
    }
    
    public void setEarlyStoppingTriggered(Boolean earlyStoppingTriggered) {
        this.earlyStoppingTriggered = earlyStoppingTriggered;
    }
    
    public String getEarlyStoppingReason() {
        return earlyStoppingReason;
    }
    
    public void setEarlyStoppingReason(String earlyStoppingReason) {
        this.earlyStoppingReason = earlyStoppingReason;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
    
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
