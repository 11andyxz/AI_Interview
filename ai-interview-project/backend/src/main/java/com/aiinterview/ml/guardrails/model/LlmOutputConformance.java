package com.aiinterview.ml.guardrails.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "llm_output_conformance",
       indexes = {
           @Index(name = "idx_endpoint_created", columnList = "endpoint, created_at"),
           @Index(name = "idx_model_valid", columnList = "model, initial_valid")
       })
public class LlmOutputConformance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String endpoint;

    @Column(nullable = false, length = 50)
    private String model;

    @Column(name = "prompt_version", length = 20)
    private String promptVersion;

    @Column(name = "initial_valid", nullable = false)
    private boolean initialValid;

    @Column(name = "repair_attempts")
    private int repairAttempts;

    @Column(name = "final_valid", nullable = false)
    private boolean finalValid;

    @Column(name = "used_fallback")
    private boolean usedFallback;

    @Column(name = "validation_errors", columnDefinition = "TEXT")
    private String validationErrors;

    @Column(name = "total_latency_ms")
    private long totalLatencyMs;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public String getPromptVersion() { return promptVersion; }
    public void setPromptVersion(String promptVersion) { this.promptVersion = promptVersion; }

    public boolean isInitialValid() { return initialValid; }
    public void setInitialValid(boolean initialValid) { this.initialValid = initialValid; }

    public int getRepairAttempts() { return repairAttempts; }
    public void setRepairAttempts(int repairAttempts) { this.repairAttempts = repairAttempts; }

    public boolean isFinalValid() { return finalValid; }
    public void setFinalValid(boolean finalValid) { this.finalValid = finalValid; }

    public boolean isUsedFallback() { return usedFallback; }
    public void setUsedFallback(boolean usedFallback) { this.usedFallback = usedFallback; }

    public String getValidationErrors() { return validationErrors; }
    public void setValidationErrors(String validationErrors) { this.validationErrors = validationErrors; }

    public long getTotalLatencyMs() { return totalLatencyMs; }
    public void setTotalLatencyMs(long totalLatencyMs) { this.totalLatencyMs = totalLatencyMs; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
