package com.aiinterview.ml.prediction;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import org.junit.jupiter.api.Disabled;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Week 23 Task 3: Calibration regression tests for live slice RMSE results.
 *
 * Guards against:
 *   1. Accidental platt-v2.2 promotion before n_junior >= 50
 *
 * HOLD — slice RMSE tests disabled: live calibration run (2026-05-15) found only 2 mid
 * sessions and 0 junior/senior sessions. Slice-level RMSE is not computable until
 * sufficient live treatment sessions accumulate (requires Stage A GO + n_treatment >= 20
 * per slice). Re-enable tests and populate constants once live slice data is available.
 *
 * Threshold values are sourced from eval/results/week23_live_calibration.json.
 */
public class Week23CalibrationRegressionTest {

    // Guardrail thresholds (do not tighten without live evidence and rollback note)
    private static final double RMSE_GUARDRAIL_UPPER = 45.0;

    @Test
    @Disabled("HOLD — Week 23 live data has n_mid_sessions=2 only; slice RMSE not computable. "
            + "Re-enable when Stage A GO and n_treatment >= 20 per slice.")
    @DisplayName("Week 23 mid slice live RMSE must be within <= 45.0 guardrail")
    public void testMidSliceLiveRmseWithinGuardrail() {
        // TODO: populate WEEK23_MID_RMSE_LIVE from eval/results/week23_live_calibration.json
        // when live slice data is available.
        fail("No live mid slice RMSE available — insufficient sample size.");
    }

    @Test
    @Disabled("HOLD — Week 23 live data has no senior sessions; slice RMSE not computable. "
            + "Re-enable when Stage A GO and n_treatment >= 20 per slice.")
    @DisplayName("Week 23 senior slice live RMSE must be within <= 45.0 guardrail")
    public void testSeniorSliceLiveRmseWithinGuardrail() {
        // TODO: populate WEEK23_SENIOR_RMSE_LIVE from eval/results/week23_live_calibration.json
        // when live slice data is available.
        fail("No live senior slice RMSE available — insufficient sample size.");
    }

    @Test
    @Disabled("HOLD — no live mid slice RMSE; regression vs Week 22 replay not evaluable.")
    @DisplayName("Week 23 mid slice live RMSE must not regress more than 10% vs Week 22 replay")
    public void testMidSliceLiveRmseNoRegressionVsReplay() {
        fail("No live mid slice RMSE available — insufficient sample size.");
    }

    @Test
    @Disabled("HOLD — no live senior slice RMSE; regression vs Week 22 replay not evaluable.")
    @DisplayName("Week 23 senior slice live RMSE must not regress more than 10% vs Week 22 replay")
    public void testSeniorSliceLiveRmseNoRegressionVsReplay() {
        fail("No live senior slice RMSE available — insufficient sample size.");
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
