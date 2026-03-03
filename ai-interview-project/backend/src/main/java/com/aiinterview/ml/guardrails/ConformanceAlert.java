package com.aiinterview.ml.guardrails;

import java.time.LocalDateTime;

public class ConformanceAlert {
    private String endpoint;
    private String severity;
    private String message;
    private double currentRate;
    private double threshold;
    private LocalDateTime detectedAt;

    public ConformanceAlert() {
        this.detectedAt = LocalDateTime.now();
    }

    public ConformanceAlert(String endpoint, String severity, String message,
                             double currentRate, double threshold) {
        this.endpoint = endpoint;
        this.severity = severity;
        this.message = message;
        this.currentRate = currentRate;
        this.threshold = threshold;
        this.detectedAt = LocalDateTime.now();
    }

    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }

    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public double getCurrentRate() { return currentRate; }
    public void setCurrentRate(double currentRate) { this.currentRate = currentRate; }

    public double getThreshold() { return threshold; }
    public void setThreshold(double threshold) { this.threshold = threshold; }

    public LocalDateTime getDetectedAt() { return detectedAt; }
    public void setDetectedAt(LocalDateTime detectedAt) { this.detectedAt = detectedAt; }
}
