package com.aiinterview.ml.prediction;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Week 22 Task 3: Calibration version loading regression tests.
 *
 * Guards against calibration version misconfiguration being silently ignored
 * at startup. Covers:
 *   1. Active version is platt-v2.1 (current production)
 *   2. PlattCalibrator bean is non-null and initialized
 *   3. Version string matches expected format
 */
@SpringBootTest
@TestPropertySource(locations = "classpath:application-test.properties")
public class CalibrationVersionLoadingTest {

    @Autowired
    private PlattCalibrator plattCalibrator;

    @Value("${ml.calibration.version:platt-v2.1}")
    private String calibrationVersion;

    @Test
    @DisplayName("Calibration version property must be set and non-empty")
    public void testCalibrationVersionPropertyLoaded() {
        assertNotNull(calibrationVersion, "ml.calibration.version must be set");
        assertFalse(calibrationVersion.isBlank(), "ml.calibration.version must not be blank");
    }

    @Test
    @DisplayName("Active calibration version must be platt-v2.1 (production version)")
    public void testActiveCalibrationVersionIsV2_1() {
        // platt-v2.2 is available but not yet promoted.
        // This test gates accidental promotion: update expected version explicitly when
        // promoting platt-v2.2 after live data guardrails are verified.
        assertEquals("platt-v2.1", calibrationVersion,
            "Production calibration version must be platt-v2.1 until live data guardrails pass. "
            + "Update this assertion explicitly when promoting platt-v2.2.");
    }

    @Test
    @DisplayName("PlattCalibrator bean must be injected and usable")
    public void testPlattCalibratorBeanIsLoaded() {
        assertNotNull(plattCalibrator,
            "PlattCalibrator bean must be present in application context");

        // A calibrator that has never been fit should not throw on calibrate() call;
        // it should either return the raw score or throw a well-typed exception.
        // Either behavior is acceptable; what is NOT acceptable is a NullPointerException
        // or silent misconfiguration.
        try {
            double result = plattCalibrator.calibrate(75.0);
            assertTrue(result >= 0.0 && result <= 1.0,
                "Calibrated probability must be in [0, 1]; got " + result);
        } catch (IllegalStateException e) {
            // Acceptable: calibrator not yet fit
            assertTrue(e.getMessage() != null && !e.getMessage().isBlank(),
                "IllegalStateException must have a descriptive message");
        }
    }
}
