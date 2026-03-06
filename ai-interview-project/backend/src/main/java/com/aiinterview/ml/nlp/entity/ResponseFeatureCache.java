package com.aiinterview.ml.nlp.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Stores extracted NLP features and ML scores for candidate responses.
 * Used to cache predictions and reduce LLM API calls.
 */
@Entity
@Table(name = "response_feature_cache",
       indexes = {
           @Index(name = "idx_session_question", columnList = "session_id,question_id"),
           @Index(name = "idx_created_at", columnList = "created_at")
       })
public class ResponseFeatureCache {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * Interview session identifier
     */
    @Column(name = "session_id", nullable = false, length = 100)
    private String sessionId;
    
    /**
     * Question identifier
     */
    @Column(name = "question_id", nullable = false, length = 100)
    private String questionId;
    
    /**
     * Candidate's full response text
     */
    @Column(name = "response_text", nullable = false, columnDefinition = "TEXT")
    private String responseText;
    
    /**
     * Extracted feature vector stored as JSON array
     * Contains 20+ numerical features from ResponseFeatureExtractor
     */
    @Column(name = "feature_vector", nullable = false, columnDefinition = "JSON")
    private String featureVector;
    
    /**
     * Score predicted by GBRT model (0-100)
     */
    @Column(name = "predicted_score")
    private Double predictedScore;
    
    /**
     * Ground truth score from LLM evaluation (0-100)
     */
    @Column(name = "llm_score")
    private Double llmScore;
    
    /**
     * Absolute prediction error: |predicted - llm_score|
     */
    @Column(name = "prediction_error")
    private Double predictionError;
    
    /**
     * Feature extraction latency in milliseconds
     */
    @Column(name = "feature_extraction_time_ms")
    private Integer featureExtractionTimeMs;
    
    /**
     * Model version identifier for tracking model updates
     */
    @Column(name = "model_version", length = 50)
    private String modelVersion;
    
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
    
    public String getQuestionId() {
        return questionId;
    }
    
    public void setQuestionId(String questionId) {
        this.questionId = questionId;
    }
    
    public String getResponseText() {
        return responseText;
    }
    
    public void setResponseText(String responseText) {
        this.responseText = responseText;
    }
    
    public String getFeatureVector() {
        return featureVector;
    }
    
    public void setFeatureVector(String featureVector) {
        this.featureVector = featureVector;
    }
    
    public Double getPredictedScore() {
        return predictedScore;
    }
    
    public void setPredictedScore(Double predictedScore) {
        this.predictedScore = predictedScore;
    }
    
    public Double getLlmScore() {
        return llmScore;
    }
    
    public void setLlmScore(Double llmScore) {
        this.llmScore = llmScore;
    }
    
    public Double getPredictionError() {
        return predictionError;
    }
    
    public void setPredictionError(Double predictionError) {
        this.predictionError = predictionError;
    }
    
    public Integer getFeatureExtractionTimeMs() {
        return featureExtractionTimeMs;
    }
    
    public void setFeatureExtractionTimeMs(Integer featureExtractionTimeMs) {
        this.featureExtractionTimeMs = featureExtractionTimeMs;
    }
    
    public String getModelVersion() {
        return modelVersion;
    }
    
    public void setModelVersion(String modelVersion) {
        this.modelVersion = modelVersion;
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
