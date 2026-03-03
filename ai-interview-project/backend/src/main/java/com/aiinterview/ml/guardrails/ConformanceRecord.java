package com.aiinterview.ml.guardrails;

import java.util.List;

public class ConformanceRecord {
    private String endpoint;
    private String model;
    private String promptVersion;
    private boolean initialValid;
    private int repairAttempts;
    private boolean finalValid;
    private boolean usedFallback;
    private List<String> validationErrors;
    private long totalLatencyMs;

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

    public List<String> getValidationErrors() { return validationErrors; }
    public void setValidationErrors(List<String> validationErrors) { this.validationErrors = validationErrors; }

    public long getTotalLatencyMs() { return totalLatencyMs; }
    public void setTotalLatencyMs(long totalLatencyMs) { this.totalLatencyMs = totalLatencyMs; }
}
