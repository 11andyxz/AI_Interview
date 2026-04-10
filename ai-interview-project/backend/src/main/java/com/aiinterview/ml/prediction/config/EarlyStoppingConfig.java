package com.aiinterview.ml.prediction.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for early stopping policy (Week 17 P0 Task 2).
 * Unified dual-threshold policy: 0.90 / 0.10, minimum 6 questions.
 */
@Configuration
@ConfigurationProperties(prefix = "ml.prediction.early-stopping")
public class EarlyStoppingConfig {
    
    private boolean enabled = false;
    private double passThreshold = 0.95;
    private double failThreshold = 0.05;
    private int minQuestions = 5;
    private double stabilityThreshold = 0.2;
    
    // New policy configuration (Week 17)
    private NewPolicyConfig newPolicy = new NewPolicyConfig();
    
    // Getters and setters
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    public double getPassThreshold() {
        return passThreshold;
    }
    
    public void setPassThreshold(double passThreshold) {
        this.passThreshold = passThreshold;
    }
    
    public double getFailThreshold() {
        return failThreshold;
    }
    
    public void setFailThreshold(double failThreshold) {
        this.failThreshold = failThreshold;
    }
    
    public int getMinQuestions() {
        return minQuestions;
    }
    
    public void setMinQuestions(int minQuestions) {
        this.minQuestions = minQuestions;
    }
    
    public double getStabilityThreshold() {
        return stabilityThreshold;
    }
    
    public void setStabilityThreshold(double stabilityThreshold) {
        this.stabilityThreshold = stabilityThreshold;
    }
    
    public NewPolicyConfig getNewPolicy() {
        return newPolicy;
    }
    
    public void setNewPolicy(NewPolicyConfig newPolicy) {
        this.newPolicy = newPolicy;
    }
    
    /**
     * New policy configuration (Week 17 P0 Task 2).
     * Unified dual-threshold policy: 0.90 / 0.10, minimum 6 questions.
     */
    public static class NewPolicyConfig {
        private boolean enabled = false;
        private double passThreshold = 0.90;
        private double failThreshold = 0.10;
        private int minQuestions = 6;
        
        public boolean isEnabled() {
            return enabled;
        }
        
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
        
        public double getPassThreshold() {
            return passThreshold;
        }
        
        public void setPassThreshold(double passThreshold) {
            this.passThreshold = passThreshold;
        }
        
        public double getFailThreshold() {
            return failThreshold;
        }
        
        public void setFailThreshold(double failThreshold) {
            this.failThreshold = failThreshold;
        }
        
        public int getMinQuestions() {
            return minQuestions;
        }
        
        public void setMinQuestions(int minQuestions) {
            this.minQuestions = minQuestions;
        }
    }
}
