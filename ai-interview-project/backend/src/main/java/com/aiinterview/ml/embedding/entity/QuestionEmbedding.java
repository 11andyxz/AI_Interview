package com.aiinterview.ml.embedding.entity;

import jakarta.persistence.*;
import java.nio.ByteBuffer;
import java.time.LocalDateTime;

/**
 * Stores semantic embeddings for interview questions.
 * Embeddings are generated via OpenAI API and used for topic clustering.
 */
@Entity
@Table(name = "question_embedding", 
       uniqueConstraints = @UniqueConstraint(name = "uk_question_role_emb", columnNames = {"question_id", "role_id"}),
       indexes = {
           @Index(name = "idx_cluster", columnList = "cluster_id"),
           @Index(name = "idx_role_cluster", columnList = "role_id,cluster_id")
       })
public class QuestionEmbedding {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * Question identifier (matches QuestionItem.id from knowledge base)
     * Stored as String to support various ID formats
     */
    @Column(name = "question_id", nullable = false, length = 100)
    private String questionId;
    
    @Column(name = "role_id", nullable = false)
    private Long roleId;
    
    /**
     * Full question text (for reference and TF-IDF computation)
     */
    @Column(name = "question_text", columnDefinition = "TEXT")
    private String questionText;
    
    /**
     * Embedding vector stored as BLOB
     * OpenAI text-embedding-3-small produces 1536-dimensional vectors
     */
    @Lob
    @Column(name = "embedding", columnDefinition = "BLOB")
    private byte[] embedding;
    
    /**
     * Cluster ID assigned by K-means++ clustering
     */
    @Column(name = "cluster_id")
    private Integer clusterId;
    
    /**
     * Human-readable cluster label (e.g., "Database, SQL, Schema")
     */
    @Column(name = "cluster_label", length = 200)
    private String clusterLabel;
    
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
    
    public String getQuestionId() {
        return questionId;
    }
    
    public void setQuestionId(String questionId) {
        this.questionId = questionId;
    }
    
    public Long getRoleId() {
        return roleId;
    }
    
    public void setRoleId(Long roleId) {
        this.roleId = roleId;
    }
    
    public String getQuestionText() {
        return questionText;
    }
    
    public void setQuestionText(String questionText) {
        this.questionText = questionText;
    }
    
    public byte[] getEmbedding() {
        return embedding;
    }
    
    public void setEmbedding(byte[] embedding) {
        this.embedding = embedding;
    }
    
    public Integer getClusterId() {
        return clusterId;
    }
    
    public void setClusterId(Integer clusterId) {
        this.clusterId = clusterId;
    }
    
    public String getClusterLabel() {
        return clusterLabel;
    }
    
    public void setClusterLabel(String clusterLabel) {
        this.clusterLabel = clusterLabel;
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
    
    /**
     * Convert byte array to double array for computation.
     * Embedding is stored as sequence of 8-byte doubles in network byte order.
     */
    public double[] getEmbeddingVector() {
        if (embedding == null || embedding.length == 0) {
            return new double[0];
        }
        
        ByteBuffer buffer = ByteBuffer.wrap(embedding);
        int dimensions = embedding.length / 8;
        double[] vector = new double[dimensions];
        
        for (int i = 0; i < dimensions; i++) {
            vector[i] = buffer.getDouble();
        }
        
        return vector;
    }
    
    /**
     * Convert double array to byte array for storage.
     */
    public void setEmbeddingVector(double[] vector) {
        if (vector == null || vector.length == 0) {
            this.embedding = new byte[0];
            return;
        }
        
        ByteBuffer buffer = ByteBuffer.allocate(vector.length * 8);
        for (double value : vector) {
            buffer.putDouble(value);
        }
        
        this.embedding = buffer.array();
    }
}
