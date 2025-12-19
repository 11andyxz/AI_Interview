package com.aiinterview.integration;

import com.aiinterview.util.TestDataCleaner;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

/**
 * Base class for integration tests
 * Provides common setup and teardown functionality
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(locations = "classpath:application-test.properties")
@Transactional
public abstract class BaseIntegrationTest {
    
    @Autowired
    protected TestDataCleaner testDataCleaner;
    
    @BeforeEach
    void setUp() {
        // Clean test data before each test
        if (testDataCleaner != null) {
            try {
                testDataCleaner.cleanAllTestData();
            } catch (Exception e) {
                // Ignore cleanup errors in tests
            }
        }
    }
}

