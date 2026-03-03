package com.aiinterview.ml.adaptive.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "question_difficulty_calibration",
       uniqueConstraints = @UniqueConstraint(name = "uk_question_role", columnNames = {"question_id", "role_id"}))
public class QuestionDifficultyCalibration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "question_id", nullable = false, length = 100)
    private String questionId;

    @Column(name = "role_id", nullable = false, length = 100)
    private String roleId;

    @Column(name = "difficulty_b")
    private double difficultyB = 0.0;

    @Column(name = "discrimination_a")
    private double discriminationA = 1.0;

    @Column(name = "response_count")
    private int responseCount = 0;

    @Column(name = "mean_score")
    private double meanScore = 0.0;

    @Column(name = "score_variance")
    private double scoreVariance = 0.25;

    @Column(name = "last_calibrated_at")
    private LocalDateTime lastCalibratedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        lastCalibratedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getQuestionId() { return questionId; }
    public void setQuestionId(String questionId) { this.questionId = questionId; }

    public String getRoleId() { return roleId; }
    public void setRoleId(String roleId) { this.roleId = roleId; }

    public double getDifficultyB() { return difficultyB; }
    public void setDifficultyB(double difficultyB) { this.difficultyB = difficultyB; }

    public double getDiscriminationA() { return discriminationA; }
    public void setDiscriminationA(double discriminationA) { this.discriminationA = discriminationA; }

    public int getResponseCount() { return responseCount; }
    public void setResponseCount(int responseCount) { this.responseCount = responseCount; }

    public double getMeanScore() { return meanScore; }
    public void setMeanScore(double meanScore) { this.meanScore = meanScore; }

    public double getScoreVariance() { return scoreVariance; }
    public void setScoreVariance(double scoreVariance) { this.scoreVariance = scoreVariance; }

    public LocalDateTime getLastCalibratedAt() { return lastCalibratedAt; }
    public void setLastCalibratedAt(LocalDateTime lastCalibratedAt) { this.lastCalibratedAt = lastCalibratedAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
