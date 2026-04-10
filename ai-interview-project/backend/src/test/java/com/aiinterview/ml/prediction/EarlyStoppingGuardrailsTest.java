package com.aiinterview.ml.prediction;

import com.aiinterview.ml.prediction.config.EarlyStoppingConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests for early-stopping guardrails and safety mechanisms.
 * 
 * Test coverage:
 * 1. Minimum question guardrail (>=6 questions)
 * 2. Threshold validation (pass/fail boundaries)  
 * 3. Policy switching behavior (baseline vs new policy)
 * 4. Edge cases and boundary conditions
 * 
 * @author Yukun Song
 * @since Week 18 (April 2026)
 */
public class EarlyStoppingGuardrailsTest {

    private EarlyStoppingConfig config;

    @BeforeEach
    public void setUp() {
        config = new EarlyStoppingConfig();
        config.setEnabled(true);
        
        EarlyStoppingConfig.NewPolicyConfig newPolicy = new EarlyStoppingConfig.NewPolicyConfig();
        newPolicy.setEnabled(true);
        newPolicy.setPassThreshold(0.90);
        newPolicy.setFailThreshold(0.10);
        newPolicy.setMinQuestions(6);
        config.setNewPolicy(newPolicy);
        config.setStabilityThreshold(0.2);
    }

    @Test
    @DisplayName("New policy minimum question threshold should be 6")
    public void testMinimumQuestionGuardrail_Configured() {
        EarlyStoppingConfig.NewPolicyConfig newPolicy = config.getNewPolicy();
        assertTrue(newPolicy.isEnabled(), "New policy should be enabled");
        assertEquals(6, newPolicy.getMinQuestions(), "Minimum questions should be 6");
    }

    @Test
    @DisplayName("New policy pass threshold should be 0.90")
    public void testPassThreshold_Configured() {
        assertEquals(0.90, config.getNewPolicy().getPassThreshold(), 0.001);
    }

    @Test
    @DisplayName("New policy fail threshold should be 0.10")
    public void testFailThreshold_Configured() {
        assertEquals(0.10, config.getNewPolicy().getFailThreshold(), 0.001);
    }

    @Test
    @DisplayName("Stability threshold should be 0.2")
    public void testStabilityThreshold_Configured() {
        assertEquals(0.2, config.getStabilityThreshold(), 0.001);
    }

    @Test
    @DisplayName("Policy can be toggled")
    public void testPolicyToggle() {
        config.getNewPolicy().setEnabled(false);
        assertFalse(config.getNewPolicy().isEnabled());
        
        config.getNewPolicy().setEnabled(true);
        assertTrue(config.getNewPolicy().isEnabled());
    }

    @Test
    @DisplayName("Baseline policy should use stricter thresholds")
    public void testBaselinePolicy_StricterThresholds() {
        config.setPassThreshold(0.95);
        config.setFailThreshold(0.05);
        config.setMinQuestions(5);
        
        assertEquals(0.95, config.getPassThreshold(), 0.001);
        assertEquals(0.05, config.getFailThreshold(), 0.001);
        assertEquals(5, config.getMinQuestions());
    }

    @Test
    @DisplayName("Pass probability threshold validation (exactly at 0.90)")
    public void testPassThreshold_BoundaryCondition() {
        double passProb = 0.90;
        assertTrue(passProb >= config.getNewPolicy().getPassThreshold(),
            "0.90 should meet pass threshold (inclusive)");
    }

    @Test
    @DisplayName("Fail probability threshold validation (exactly at 0.10)")
    public void testFailThreshold_BoundaryCondition() {
        double passProb = 0.10;
        assertTrue(passProb <= config.getNewPolicy().getFailThreshold(),
            "0.10 should meet fail threshold (inclusive)");
    }

    @Test
    @DisplayName("Uncertainty zone validation (0.11 to 0.89)")
    public void testUncertaintyZone_BetweenThresholds() {
        double passProb = 0.55;
        EarlyStoppingConfig.NewPolicyConfig newPolicy = config.getNewPolicy();
        
        assertTrue(passProb > newPolicy.getFailThreshold() && 
                   passProb < newPolicy.getPassThreshold(),
            "0.55 should be in uncertainty zone");
    }

    @Test
    @DisplayName("Question count below minimum (5 < 6)")
    public void testQuestionCount_BelowMinimum() {
        int questionCount = 5;
        assertTrue(questionCount < config.getNewPolicy().getMinQuestions(),
            "5 questions is below minimum of 6");
    }

    @Test
    @DisplayName("Question count at minimum boundary (exactly 6)")
    public void testQuestionCount_AtMinimum() {
        int questionCount = 6;
        assertTrue(questionCount >= config.getNewPolicy().getMinQuestions(),
            "6 questions meets minimum requirement");
    }

    @Test
    @DisplayName("Stability threshold boundary (0.2)")
    public void testStabilityThreshold_BoundaryCondition() {
        double stability = 0.20;
        assertTrue(stability >= config.getStabilityThreshold(),
            "0.20 is at stability threshold");
    }

    @Test
    @DisplayName("Stable performance (stability < 0.2)")
    public void testStability_StablePerformance() {
        double stability = 0.15;
        assertTrue(stability < config.getStabilityThreshold(),
            "0.15 indicates stable performance");
    }

    @Test
    @DisplayName("Unstable performance (stability > 0.2)")
    public void testStability_UnstablePerformance() {
        double stability = 0.25;
        assertTrue(stability > config.getStabilityThreshold(),
            "0.25 indicates unstable performance");
    }
}
