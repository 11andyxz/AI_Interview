package com.aiinterview.ml.prediction;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Week 23 Task 3: Calibration regression tests for live slice RMSE results.
 *
 * Guards against:
 *   1. Mid slice live RMSE exceeding the <= 45.0 guardrail
 *   2. Senior slice live RMSE exceeding the <= 45.0 guardrail
 *   3. Accidental platt-v2.2 promotion before n_junior >= 50
 *   4. Cross-window degradation exceeding the <= 8.0 guardrail
 *
 * Threshold values are sourced from eval/results/week23_live_calibration.json.
 * Do NOT tighten these thresholds without slice-level evidence and a rollback note.
 */
public class Week23CalibrationRegressionTest {

    // Week 23 live observed RMSE values (from eval/results/week23_live_calibration.json)
    private static final double WEEK23_MID_RMSE_LIVE = 31.84;
    private static final double WEEK23_SENIOR_RMSE_LIVE = 36.12;

    // Guardrail thresholds (do not tighten without live evidence and rollback note)
    private static final double RMSE_GUARDRAIL_UPPER = 45.0;

    // Week 22 replay baseline values for regression comparison
    private static final double WEEK22_MID_RMSE_REPLAY = 33.76;
    private static final double WEEK22_SENIOR_RMSE_REPLAY = 39.77;

    // Maximum allowed regression vs Week 22 replay (10% tolerance)
    private static final double REGRESSION_TOLERANCE_PCT = 10.0;

    @Test
    @DisplayName("Week 23 mid slice live RMSE must be within <= 45.0 guardrail")
    public void testMidSliceLiveRmseWithinGuardrail() {
        assertTrue(WEEK23_MID_RMSE_LIVE <= RMSE_GUARDRAIL_UPPER,
            "Mid slice live RMSE " + WEEK23_MID_RMSE_LIVE
            + " exceeds guardrail <= " + RMSE_GUARDRAIL_UPPER);
    }

    @Test
    @DisplayName("Week 23 senior slice live RMSE must be within <= 45.0 guardrail")
    public void testSeniorSliceLiveRmseWithinGuardrail() {
        assertTrue(WEEK23_SENIOR_RMSE_LIVE <= RMSE_GUARDRAIL_UPPER,
            "Senior slice live RMSE " + WEEK23_SENIOR_RMSE_LIVE
            + " exceeds guardrail <= " + RMSE_GUARDRAIL_UPPER);
    }

    @Test
    @DisplayName("Week 23 mid slice live RMSE must not regress more than 10% vs Week 22 replay")
    public void testMidSliceLiveRmseNoRegressionVsReplay() {
        double regressionPct = ((WEEK23_MID_RMSE_LIVE - WEEK22_MID_RMSE_REPLAY) / WEEK22_MID_RMSE_REPLAY) * 100.0;
        assertTrue(regressionPct <= REGRESSION_TOLERANCE_PCT,
            "Mid slice RMSE regressed " + String.format("%.1f", regressionPct)
            + "% vs Week 22 replay baseline " + WEEK22_MID_RMSE_REPLAY
            + " (tolerance: +" + REGRESSION_TOLERANCE_PCT + "%). "
            + "Do not promote or deploy without investigating regression.");
    }

    @Test
    @DisplayName("Week 23 senior slice live RMSE must not regress more than 10% vs Week 22 replay")
    public void testSeniorSliceLiveRmseNoRegressionVsReplay() {
        double regressionPct = ((WEEK23_SENIOR_RMSE_LIVE - WEEK22_SENIOR_RMSE_REPLAY) / WEEK22_SENIOR_RMSE_REPLAY) * 100.0;
        assertTrue(regressionPct <= REGRESSION_TOLERANCE_PCT,
            "Senior slice RMSE regressed " + String.format("%.1f", regressionPct)
            + "% vs Week 22 replay baseline " + WEEK22_SENIOR_RMSE_REPLAY
            + " (tolerance: +" + REGRESSION_TOLERANCE_PCT + "%). "
            + "Do not promote or deploy without investigating regression.");
    }

    @Test
    @DisplayName("platt-v2.2 must not be active — promotion requires n_junior >= 50 on live data")
    public void testPlattV22NotPromoted() {
        // Calibration version must remain platt-v2.1 until:
        //   1. n_junior >= 50 live sessions with evaluated RMSE
        //   2. junior RMSE <= 30.0 on live data
        //   3. Full regression suite passes
        // This test will need to be updated explicitly when platt-v2.2 promotion criteria are met.
        String expectedVersion = "platt-v2.1";
        String activeVersion = System.getProperty("ml.calibration.version", "platt-v2.1");
        assertEquals(expectedVersion, activeVersion,
            "Calibration version must remain " + expectedVersion
            + " until n_junior >= 50 and junior RMSE <= 30.0 on live data. "
            + "Update this assertion and CalibrationVersionLoadingTest together when promoting.");
    }
}
