package com.aiinterview.ml.embedding.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Tracks topic coverage metrics for interview sessions.
 * Records which topic clusters have been covered and computes diversity metrics.
 */
@Entity
@Table(name = "topic_coverage", indexes = {
    @Index(name = "idx_session_role", columnList = "session_id,role_id")
})
public class TopicCoverage {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "session_id", nullable = false, length = 100)
    private String sessionId;
    
    @Column(name = "role_id", nullable = false)
    private Long roleId;
    
    /**
     * Map of cluster_id -> question_count
     * Stored as JSON for flexible cluster tracking
     */
    @Column(name = "cluster_distribution", columnDefinition = "JSON")
    private String clusterDistribution;
    
    /**
     * Total number of questions asked in this session
     */
    @Column(name = "total_questions")
    private Integer totalQuestions;
    
    /**
     * Number of unique clusters covered
     */
    @Column(name = "clusters_covered")
    private Integer clustersCovered;
    
    /**
     * Total number of clusters available for this role
     */
    @Column(name = "total_clusters")
    private Integer totalClusters;
    
    /**
     * Coverage ratio: clusters_covered / total_clusters
     */
    @Column(name = "coverage_ratio")
    private Double coverageRatio;
    
    /**
     * Shannon entropy: H = -Σ(p_i * log(p_i))
     * Measures topic diversity. Higher = more diverse.
     */
    @Column(name = "shannon_entropy")
    private Double shannonEntropy;
    
    /**
     * Maximum possible entropy for uniform distribution
     * log(total_clusters)
     */
    @Column(name = "max_entropy")
    private Double maxEntropy;
    
    /**
     * Normalized entropy: shannon_entropy / max_entropy
     * Range [0, 1], higher = more uniform distribution
     */
    @Column(name = "normalized_entropy")
    private Double normalizedEntropy;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
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
    
    public Long getRoleId() {
        return roleId;
    }
    
    public void setRoleId(Long roleId) {
        this.roleId = roleId;
    }
    
    public String getClusterDistribution() {
        return clusterDistribution;
    }
    
    public void setClusterDistribution(String clusterDistribution) {
        this.clusterDistribution = clusterDistribution;
    }
    
    public Integer getTotalQuestions() {
        return totalQuestions;
    }
    
    public void setTotalQuestions(Integer totalQuestions) {
        this.totalQuestions = totalQuestions;
    }
    
    public Integer getClustersCovered() {
        return clustersCovered;
    }
    
    public void setClustersCovered(Integer clustersCovered) {
        this.clustersCovered = clustersCovered;
    }
    
    public Integer getTotalClusters() {
        return totalClusters;
    }
    
    public void setTotalClusters(Integer totalClusters) {
        this.totalClusters = totalClusters;
    }
    
    public Double getCoverageRatio() {
        return coverageRatio;
    }
    
    public void setCoverageRatio(Double coverageRatio) {
        this.coverageRatio = coverageRatio;
    }
    
    public Double getShannonEntropy() {
        return shannonEntropy;
    }
    
    public void setShannonEntropy(Double shannonEntropy) {
        this.shannonEntropy = shannonEntropy;
    }
    
    public Double getMaxEntropy() {
        return maxEntropy;
    }
    
    public void setMaxEntropy(Double maxEntropy) {
        this.maxEntropy = maxEntropy;
    }
    
    public Double getNormalizedEntropy() {
        return normalizedEntropy;
    }
    
    public void setNormalizedEntropy(Double normalizedEntropy) {
        this.normalizedEntropy = normalizedEntropy;
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
