package com.aiinterview.ml.guardrails;

public class ConformanceSummary {
    private String endpoint;
    private long totalCalls;
    private long initialValidCount;
    private long finalValidCount;
    private long fallbackCount;
    private double initialValidRate;
    private double finalValidRate;
    private double repairSuccessRate;
    private double fallbackRate;
    private double avgLatencyMs;

    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }

    public long getTotalCalls() { return totalCalls; }
    public void setTotalCalls(long totalCalls) { this.totalCalls = totalCalls; }

    public long getInitialValidCount() { return initialValidCount; }
    public void setInitialValidCount(long initialValidCount) { this.initialValidCount = initialValidCount; }

    public long getFinalValidCount() { return finalValidCount; }
    public void setFinalValidCount(long finalValidCount) { this.finalValidCount = finalValidCount; }

    public long getFallbackCount() { return fallbackCount; }
    public void setFallbackCount(long fallbackCount) { this.fallbackCount = fallbackCount; }

    public double getInitialValidRate() { return initialValidRate; }
    public void setInitialValidRate(double initialValidRate) { this.initialValidRate = initialValidRate; }

    public double getFinalValidRate() { return finalValidRate; }
    public void setFinalValidRate(double finalValidRate) { this.finalValidRate = finalValidRate; }

    public double getRepairSuccessRate() { return repairSuccessRate; }
    public void setRepairSuccessRate(double repairSuccessRate) { this.repairSuccessRate = repairSuccessRate; }

    public double getFallbackRate() { return fallbackRate; }
    public void setFallbackRate(double fallbackRate) { this.fallbackRate = fallbackRate; }

    public double getAvgLatencyMs() { return avgLatencyMs; }
    public void setAvgLatencyMs(double avgLatencyMs) { this.avgLatencyMs = avgLatencyMs; }
}
