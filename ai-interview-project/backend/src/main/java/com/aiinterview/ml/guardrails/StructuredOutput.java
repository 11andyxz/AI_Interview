package com.aiinterview.ml.guardrails;

import java.util.ArrayList;
import java.util.List;

public class StructuredOutput<T> {
    private T result;
    private boolean usedRepair;
    private int repairAttempts;
    private boolean usedFallback;
    private List<String> validationErrors;
    private long totalLatencyMs;
    private String rawResponse;

    public StructuredOutput() {
        this.validationErrors = new ArrayList<>();
    }

    public static <T> StructuredOutput<T> success(T result, long latencyMs) {
        StructuredOutput<T> output = new StructuredOutput<>();
        output.result = result;
        output.totalLatencyMs = latencyMs;
        return output;
    }

    public static <T> StructuredOutput<T> repairedSuccess(T result, int attempts,
                                                            List<String> initialErrors, long latencyMs) {
        StructuredOutput<T> output = new StructuredOutput<>();
        output.result = result;
        output.usedRepair = true;
        output.repairAttempts = attempts;
        output.validationErrors = initialErrors;
        output.totalLatencyMs = latencyMs;
        return output;
    }

    public static <T> StructuredOutput<T> fallback(T fallbackResult, List<String> errors, long latencyMs) {
        StructuredOutput<T> output = new StructuredOutput<>();
        output.result = fallbackResult;
        output.usedFallback = true;
        output.validationErrors = errors;
        output.totalLatencyMs = latencyMs;
        return output;
    }

    public ConformanceRecord toConformanceRecord(String endpoint, String model, String promptVersion) {
        ConformanceRecord record = new ConformanceRecord();
        record.setEndpoint(endpoint);
        record.setModel(model);
        record.setPromptVersion(promptVersion);
        record.setInitialValid(!usedRepair && !usedFallback);
        record.setRepairAttempts(repairAttempts);
        record.setFinalValid(!usedFallback);
        record.setUsedFallback(usedFallback);
        record.setValidationErrors(validationErrors);
        record.setTotalLatencyMs(totalLatencyMs);
        return record;
    }

    public T getResult() { return result; }
    public void setResult(T result) { this.result = result; }

    public boolean isUsedRepair() { return usedRepair; }
    public void setUsedRepair(boolean usedRepair) { this.usedRepair = usedRepair; }

    public int getRepairAttempts() { return repairAttempts; }
    public void setRepairAttempts(int repairAttempts) { this.repairAttempts = repairAttempts; }

    public boolean isUsedFallback() { return usedFallback; }
    public void setUsedFallback(boolean usedFallback) { this.usedFallback = usedFallback; }

    public List<String> getValidationErrors() { return validationErrors; }
    public void setValidationErrors(List<String> validationErrors) { this.validationErrors = validationErrors; }

    public long getTotalLatencyMs() { return totalLatencyMs; }
    public void setTotalLatencyMs(long totalLatencyMs) { this.totalLatencyMs = totalLatencyMs; }

    public String getRawResponse() { return rawResponse; }
    public void setRawResponse(String rawResponse) { this.rawResponse = rawResponse; }
}
