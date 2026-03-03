package com.aiinterview.ml.experiment.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "experiment")
public class Experiment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    private String description;

    @Column(nullable = false)
    private String status = "draft";

    @Column(name = "traffic_percentage")
    private double trafficPercentage = 50.0;

    @Column(name = "min_sample_size")
    private int minSampleSize = 100;

    @Column(name = "baseline_variant")
    private String baselineVariant = "baseline";

    @Column(name = "treatment_variant")
    private String treatmentVariant = "variant";

    @Column(name = "baseline_config", columnDefinition = "TEXT")
    private String baselineConfig;

    @Column(name = "treatment_config", columnDefinition = "TEXT")
    private String treatmentConfig;

    @Column(name = "target_endpoint")
    private String targetEndpoint;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public double getTrafficPercentage() { return trafficPercentage; }
    public void setTrafficPercentage(double trafficPercentage) { this.trafficPercentage = trafficPercentage; }

    public int getMinSampleSize() { return minSampleSize; }
    public void setMinSampleSize(int minSampleSize) { this.minSampleSize = minSampleSize; }

    public String getBaselineVariant() { return baselineVariant; }
    public void setBaselineVariant(String baselineVariant) { this.baselineVariant = baselineVariant; }

    public String getTreatmentVariant() { return treatmentVariant; }
    public void setTreatmentVariant(String treatmentVariant) { this.treatmentVariant = treatmentVariant; }

    public String getBaselineConfig() { return baselineConfig; }
    public void setBaselineConfig(String baselineConfig) { this.baselineConfig = baselineConfig; }

    public String getTreatmentConfig() { return treatmentConfig; }
    public void setTreatmentConfig(String treatmentConfig) { this.treatmentConfig = treatmentConfig; }

    public String getTargetEndpoint() { return targetEndpoint; }
    public void setTargetEndpoint(String targetEndpoint) { this.targetEndpoint = targetEndpoint; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }

    public LocalDateTime getEndedAt() { return endedAt; }
    public void setEndedAt(LocalDateTime endedAt) { this.endedAt = endedAt; }
}
