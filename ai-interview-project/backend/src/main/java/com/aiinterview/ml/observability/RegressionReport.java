package com.aiinterview.ml.observability;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Regression test report for golden dataset validation
 * Aggregates results across all endpoints
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegressionReport {
    
    /**
     * Overall regression test status
     */
    private boolean passed;
    
    /**
     * Total number of test cases across all endpoints
     */
    private int totalTests;
    
    /**
     * Number of passed tests
     */
    private int passedTests;
    
    /**
     * Number of failed tests
     */
    private int failedTests;
    
    /**
     * Average quality score across all tests
     */
    private double avgQuality;
    
    /**
     * Reports for each endpoint
     */
    private Map<String, EndpointRegressionResult> endpointResults;
    
    /**
     * Execution timestamp
     */
    private LocalDateTime executedAt;
    
    /**
     * Execution duration in milliseconds
     */
    private long durationMs;
    
    /**
     * List of failed test cases
     */
    private List<FailedTestCase> failures;
    
    /**
     * Get overall pass rate
     */
    public double getPassRate() {
        return totalTests > 0 ? (double) passedTests / totalTests * 100 : 0.0;
    }
    
    /**
     * Check if all tests passed
     */
    public boolean allPassed() {
        return totalTests > 0 && failedTests == 0;
    }
    
    /**
     * Get summary message
     */
    public String getSummary() {
        return String.format("Regression Test: %d/%d passed (%.1f%%), executed in %dms",
                           passedTests, totalTests, getPassRate(), durationMs);
    }
    
    /**
     * Per-endpoint regression result
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EndpointRegressionResult {
        private String endpoint;
        private int totalTests;
        private int passedTests;
        private int failedTests;
        private double avgQuality;
        private boolean passed;
        
        public double getPassRate() {
            return totalTests > 0 ? (double) passedTests / totalTests * 100 : 0.0;
        }
    }
    
    /**
     * Failed test case details
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FailedTestCase {
        private String endpoint;
        private String testCaseId;
        private double expectedQuality;
        private double actualQuality;
        private String reason;
        private LocalDateTime failedAt;
    }
}
