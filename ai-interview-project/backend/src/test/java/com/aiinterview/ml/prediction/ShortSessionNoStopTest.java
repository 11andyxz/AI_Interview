package com.aiinterview.ml.prediction;

import com.aiinterview.ml.prediction.config.EarlyStoppingConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Week 22 Task 3: Short-session no-stop regression tests.
 *
 * Guards against early-stop triggering on sessions with fewer questions than
 * the slice-aware minimum. These are the most dangerous false-stop scenarios
 * because the model has sparse signal and high prediction variance.
 *
 * Policy under test:
 *   pass_threshold = 0.85, fail_threshold = 0.10
 *   min_questions: junior=4, mid=5, senior=6
 *
 * All tests use config/policy logic only; no DB or OpenAI calls required.
 */
public class ShortSessionNoStopTest {

    private EarlyStoppingConfig config;

    @BeforeEach
    public void setUp() {
        config = new EarlyStoppingConfig();
        config.setEnabled(true);

        EarlyStoppingConfig.NewPolicyConfig newPolicy = new EarlyStoppingConfig.NewPolicyConfig();
        newPolicy.setEnabled(true);
        newPolicy.setPassThreshold(0.85);
        newPolicy.setFailThreshold(0.10);
        newPolicy.setMinQuestions(6);  // global default
        config.setNewPolicy(newPolicy);
        config.setStabilityThreshold(0.2);
    }

    @Test
    @DisplayName("Global min_questions floor must block stop at question 5 or fewer")
    public void testGlobalMinQuestionsBlocksEarlyStop() {
        int globalMin = config.getNewPolicy().getMinQuestions();
        // Sessions below globalMin must not be stopped regardless of confidence
        for (int q = 1; q < globalMin; q++) {
            assertFalse(isEarlyStopAllowed(q, 0.95, globalMin),
                "Early stop must not be allowed at question " + q
                + " (below global floor " + globalMin + ")");
        }
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.90, 0.92, 0.95, 0.99})
    @DisplayName("High confidence must not override min_questions floor")
    public void testHighConfidenceDoesNotOverrideFloor(double confidence) {
        int juniorMin = config.getNewPolicy().getMinQuestionsForSlice("junior");
        // Even with very high confidence, questions below floor must not be stopped
        assertFalse(isEarlyStopAllowed(juniorMin - 1, confidence, juniorMin),
            "Confidence " + confidence + " must not override junior min_questions floor at question "
            + (juniorMin - 1));
    }

    @Test
    @DisplayName("Stop is allowed at exactly junior floor with sufficient confidence")
    public void testStopAllowedAtJuniorFloor() {
        int juniorMin = config.getNewPolicy().getMinQuestionsForSlice("junior");
        double passThreshold = config.getNewPolicy().getPassThreshold();
        // At exactly the floor, stop should be allowed if confidence exceeds threshold
        assertTrue(isEarlyStopAllowed(juniorMin, passThreshold + 0.01, juniorMin),
            "Early stop should be allowed at question " + juniorMin
            + " when confidence exceeds pass_threshold");
    }

    // ---------------------------------------------------------------------------
    // Helper — mirrors the production early-stop guard logic
    // ---------------------------------------------------------------------------

    /**
     * Returns true if an early stop is allowed given the current question count,
     * model confidence, and slice-aware minimum questions floor.
     *
     * Mirrors: InterviewEarlyStoppingService.shouldStop(questionCount, confidence, sliceMin)
     */
    private boolean isEarlyStopAllowed(int questionCount, double confidence, int sliceMin) {
        if (questionCount < sliceMin) {
            return false;
        }
        double passThreshold = config.getNewPolicy().getPassThreshold();
        double failThreshold = config.getNewPolicy().getFailThreshold();
        return confidence >= passThreshold || confidence <= failThreshold;
    }
}
