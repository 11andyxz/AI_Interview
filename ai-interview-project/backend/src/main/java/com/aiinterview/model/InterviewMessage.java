package com.aiinterview.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "interview_message")
public class InterviewMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "interview_id", nullable = false, length = 36)
    private String interviewId;

    @Column(name = "user_message", columnDefinition = "TEXT")
    private String userMessage;

    @Column(name = "ai_message", columnDefinition = "TEXT")
    private String aiMessage;

    @Column(name = "message_type", length = 20)
    private String messageType = "chat";

    // Evaluation fields
    @Column(name = "evaluation_score")
    private Double evaluationScore;

    @Column(name = "evaluation_rubric_level", length = 20)
    private String evaluationRubricLevel;

    @Column(name = "technical_accuracy")
    private Integer technicalAccuracy;

    @Column(name = "depth_score")
    private Integer depthScore;

    @Column(name = "experience_score")
    private Integer experienceScore;

    @Column(name = "communication_score")
    private Integer communicationScore;

    @Column(name = "evaluation_strengths", columnDefinition = "TEXT")
    private String evaluationStrengths; // JSON array

    @Column(name = "evaluation_improvements", columnDefinition = "TEXT")
    private String evaluationImprovements; // JSON array

    @Column(name = "follow_up_questions", columnDefinition = "TEXT")
    private String followUpQuestions; // JSON array

    @Column(name = "evaluation_completed_at")
    private LocalDateTime evaluationCompletedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getInterviewId() {
        return interviewId;
    }

    public void setInterviewId(String interviewId) {
        this.interviewId = interviewId;
    }

    public String getUserMessage() {
        return userMessage;
    }

    public void setUserMessage(String userMessage) {
        this.userMessage = userMessage;
    }

    public String getAiMessage() {
        return aiMessage;
    }

    public void setAiMessage(String aiMessage) {
        this.aiMessage = aiMessage;
    }

    public String getMessageType() {
        return messageType;
    }

    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public Double getEvaluationScore() {
        return evaluationScore;
    }

    public void setEvaluationScore(Double evaluationScore) {
        this.evaluationScore = evaluationScore;
    }

    public String getEvaluationRubricLevel() {
        return evaluationRubricLevel;
    }

    public void setEvaluationRubricLevel(String evaluationRubricLevel) {
        this.evaluationRubricLevel = evaluationRubricLevel;
    }

    public Integer getTechnicalAccuracy() {
        return technicalAccuracy;
    }

    public void setTechnicalAccuracy(Integer technicalAccuracy) {
        this.technicalAccuracy = technicalAccuracy;
    }

    public Integer getDepthScore() {
        return depthScore;
    }

    public void setDepthScore(Integer depthScore) {
        this.depthScore = depthScore;
    }

    public Integer getExperienceScore() {
        return experienceScore;
    }

    public void setExperienceScore(Integer experienceScore) {
        this.experienceScore = experienceScore;
    }

    public Integer getCommunicationScore() {
        return communicationScore;
    }

    public void setCommunicationScore(Integer communicationScore) {
        this.communicationScore = communicationScore;
    }

    public String getEvaluationStrengths() {
        return evaluationStrengths;
    }

    public void setEvaluationStrengths(String evaluationStrengths) {
        this.evaluationStrengths = evaluationStrengths;
    }

    public String getEvaluationImprovements() {
        return evaluationImprovements;
    }

    public void setEvaluationImprovements(String evaluationImprovements) {
        this.evaluationImprovements = evaluationImprovements;
    }

    public String getFollowUpQuestions() {
        return followUpQuestions;
    }

    public void setFollowUpQuestions(String followUpQuestions) {
        this.followUpQuestions = followUpQuestions;
    }

    public LocalDateTime getEvaluationCompletedAt() {
        return evaluationCompletedAt;
    }

    public void setEvaluationCompletedAt(LocalDateTime evaluationCompletedAt) {
        this.evaluationCompletedAt = evaluationCompletedAt;
    }
}

