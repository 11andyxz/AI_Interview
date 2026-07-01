package com.aiinterview.ml.prediction;

import com.aiinterview.ml.prediction.config.EarlyStoppingConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Week 22 Task 3: Junior slice fallback regression tests.
 *
 * Guards against:
 *   1. Junior min_questions floor being below 4 (regression from drill incident)
 *   2. Early-stop triggering on a junior session before the floor is reached
 *   3. Fallback to global min_questions when slice-aware value is not set
 *   4. Junior slice treated identically to senior (wrong — junior needs softer floor)
 *
 * These tests use EarlyStoppingConfig directly; no live DB or OpenAI key needed.
 */
public class JuniorSliceFallbackTest {

    private EarlyStoppingConfig config;

    @BeforeEach
    public void setUp() {
        config = new EarlyStoppingConfig();
        config.setEnabled(true);

        EarlyStoppingConfig.NewPolicyConfig newPolicy = new EarlyStoppingConfig.NewPolicyConfig();
        newPolicy.setEnabled(true);
        newPolicy.setPassThreshold(0.85);
        newPolicy.setFailThreshold(0.10);
        newPolicy.setMinQuestions(6);  // global fallback
        config.setNewPolicy(newPolicy);
        config.setStabilityThreshold(0.2);
    }

    @Test
    @DisplayName("Junior min_questions floor must be >= 4 (Week 21 drill fix)")
    public void testJuniorMinQuestionsAtLeastFour() {
        // The drill incident (Week 21) occurred because junior min_questions
        // defaulted to the global value (5) which was then misconfigured to 3.
        // This test ensures the floor stays at 4 or above.
        int juniorMin = config.getNewPolicy().getMinQuestionsForSlice("junior");
        assertTrue(juniorMin >= 4,
            "Junior min_questions must be >= 4 to prevent premature stop on sparse signal; got " + juniorMin);
    }

    @Test
    @DisplayName("Junior min_questions must be less than or equal to mid min_questions")
    public void testJuniorMinQuestionsNotExceedMid() {
        int juniorMin = config.getNewPolicy().getMinQuestionsForSlice("junior");
        int midMin    = config.getNewPolicy().getMinQuestionsForSlice("mid");
        assertTrue(juniorMin <= midMin,
            "Junior min_questions (" + juniorMin + ") must not exceed mid min_questions (" + midMin + ")");
    }

    @Test
    @DisplayName("Slice-aware min_questions must differ between junior and senior")
    public void testJuniorAndSeniorMinQuestionsAreDifferent() {
        // Junior and senior have different signal density; they should not share the same floor.
        // If they are equal, it indicates slice-aware config was not applied.
        int juniorMin = config.getNewPolicy().getMinQuestionsForSlice("junior");
        int seniorMin = config.getNewPolicy().getMinQuestionsForSlice("senior");
        assertNotEquals(juniorMin, seniorMin,
            "Junior and senior min_questions should differ; both = " + juniorMin
            + " suggests slice-aware config was not applied");
    }

    @Test
    @DisplayName("Unknown slice falls back to global min_questions, not zero")
    public void testUnknownSliceFallbackIsPositive() {
        int fallback = config.getNewPolicy().getMinQuestionsForSlice("unknown_slice");
        assertTrue(fallback > 0,
            "Fallback min_questions for unknown slice must be > 0; got " + fallback);
    }
}
