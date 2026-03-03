package com.aiinterview.ml.adaptive;

public class AbilityEstimate {
    private double theta;
    private double standardError;
    private double confidenceLower;
    private double confidenceUpper;
    private int responsesUsed;

    public AbilityEstimate() {
        this.theta = 0.0;
        this.standardError = 3.0;
        this.responsesUsed = 0;
    }

    public AbilityEstimate(double theta, double standardError, int responsesUsed) {
        this.theta = theta;
        this.standardError = standardError;
        this.confidenceLower = theta - 1.96 * standardError;
        this.confidenceUpper = theta + 1.96 * standardError;
        this.responsesUsed = responsesUsed;
    }

    /**
     * Prior ability estimate before any responses (standard normal prior).
     */
    public static AbilityEstimate prior() {
        return new AbilityEstimate(0.0, 1.0, 0);
    }

    public double getTheta() { return theta; }
    public void setTheta(double theta) {
        this.theta = theta;
        this.confidenceLower = theta - 1.96 * standardError;
        this.confidenceUpper = theta + 1.96 * standardError;
    }

    public double getStandardError() { return standardError; }
    public void setStandardError(double standardError) {
        this.standardError = standardError;
        this.confidenceLower = theta - 1.96 * standardError;
        this.confidenceUpper = theta + 1.96 * standardError;
    }

    public double getConfidenceLower() { return confidenceLower; }
    public double getConfidenceUpper() { return confidenceUpper; }

    public int getResponsesUsed() { return responsesUsed; }
    public void setResponsesUsed(int responsesUsed) { this.responsesUsed = responsesUsed; }
}
