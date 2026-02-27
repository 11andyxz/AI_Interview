package com.aiinterview.ml.guardrails;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for structured output guardrails
 * Tests validation, repair, conformance tracking, and monitoring
 */
@SpringBootTest
@Transactional
public class OutputGuardrailsIntegrationTest {
    
    @Autowired
    private LlmOutputConformanceRepository conformanceRepository;
    
    @Autowired
    private OutputConformanceMonitor conformanceMonitor;
    
    @BeforeEach
    public void setUp() {
        conformanceRepository.deleteAll();
    }
    
    @Test
    public void testValidatorWithValidOutput() {
        InterviewQuestionValidator validator = new InterviewQuestionValidator();
        
        String validOutput = """
            {
              "question": "What is Spring Boot?",
              "difficulty": "medium",
              "expectedAnswer": "Spring Boot is a framework...",
              "keywords": ["Spring", "Boot", "Framework"],
              "followUpQuestions": ["What are Spring Boot starters?"]
            }
            """;
        
        OutputSchemaValidator.ValidationResult result = validator.validate(validOutput);
        
        assertTrue(result.isValid(), "Valid output should pass validation");
        assertTrue(result.getErrors().isEmpty(), "No errors for valid output");
    }
    
    @Test
    public void testValidatorWithMissingFields() {
        InterviewQuestionValidator validator = new InterviewQuestionValidator();
        
        String invalidOutput = """
            {
              "question": "What is Spring Boot?"
            }
            """;
        
        OutputSchemaValidator.ValidationResult result = validator.validate(invalidOutput);
        
        assertFalse(result.isValid(), "Invalid output should fail validation");
        assertTrue(result.getErrors().size() >= 3, "Should have multiple errors");
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("difficulty")));
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("expectedAnswer")));
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("keywords")));
    }
    
    @Test
    public void testValidatorWithInvalidDifficulty() {
        InterviewQuestionValidator validator = new InterviewQuestionValidator();
        
        String invalidOutput = """
            {
              "question": "Test question",
              "difficulty": "invalid",
              "expectedAnswer": "Answer",
              "keywords": ["test"]
            }
            """;
        
        OutputSchemaValidator.ValidationResult result = validator.validate(invalidOutput);
        
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("easy, medium, hard")));
    }
    
    @Test
    public void testValidatorWithEmptyKeywords() {
        InterviewQuestionValidator validator = new InterviewQuestionValidator();
        
        String invalidOutput = """
            {
              "question": "Test question",
              "difficulty": "easy",
              "expectedAnswer": "Answer",
              "keywords": []
            }
            """;
        
        OutputSchemaValidator.ValidationResult result = validator.validate(invalidOutput);
        
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("at least one item")));
    }
    
    @Test
    public void testValidatorWithInvalidJson() {
        InterviewQuestionValidator validator = new InterviewQuestionValidator();
        
        String invalidOutput = "{ invalid json }";
        
        OutputSchemaValidator.ValidationResult result = validator.validate(invalidOutput);
        
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("Invalid JSON")));
    }
    
    @Test
    public void testConformanceTracking() {
        String endpoint = "/api/interview/generate";
        
        LlmOutputConformance record = LlmOutputConformance.builder()
            .endpoint(endpoint)
            .model("gpt-4")
            .promptVersion("v1.0")
            .initialValid(true)
            .repairAttempts(0)
            .finalValid(true)
            .usedFallback(false)
            .totalLatencyMs(500L)
            .build();
        
        LlmOutputConformance saved = conformanceRepository.save(record);
        
        assertNotNull(saved.getId());
        assertEquals(endpoint, saved.getEndpoint());
        assertTrue(saved.getInitialValid());
        assertTrue(saved.getFinalValid());
    }
    
    @Test
    public void testConformanceTrackingWithRepair() {
        String endpoint = "/api/interview/generate";
        
        LlmOutputConformance record = LlmOutputConformance.builder()
            .endpoint(endpoint)
            .model("gpt-4")
            .initialValid(false)
            .repairAttempts(2)
            .finalValid(true)
            .usedFallback(false)
            .validationErrors("Missing field: difficulty; Missing field: keywords")
            .totalLatencyMs(2500L)
            .build();
        
        LlmOutputConformance saved = conformanceRepository.save(record);
        
        assertFalse(saved.getInitialValid());
        assertEquals(2, saved.getRepairAttempts());
        assertTrue(saved.getFinalValid());
        assertFalse(saved.getUsedFallback());
    }
    
    @Test
    public void testConformanceTrackingWithFallback() {
        String endpoint = "/api/interview/generate";
        
        LlmOutputConformance record = LlmOutputConformance.builder()
            .endpoint(endpoint)
            .model("gpt-4")
            .initialValid(false)
            .repairAttempts(3)
            .finalValid(false)
            .usedFallback(true)
            .validationErrors("Multiple validation failures")
            .totalLatencyMs(4000L)
            .build();
        
        LlmOutputConformance saved = conformanceRepository.save(record);
        
        assertFalse(saved.getInitialValid());
        assertEquals(3, saved.getRepairAttempts());
        assertFalse(saved.getFinalValid());
        assertTrue(saved.getUsedFallback());
    }
    
    @Test
    public void testFirstPassRateCalculation() {
        String endpoint = "/api/test";
        LocalDateTime now = LocalDateTime.now();
        
        // Create test records: 7 valid, 3 invalid
        for (int i = 0; i < 7; i++) {
            conformanceRepository.save(LlmOutputConformance.builder()
                .endpoint(endpoint)
                .model("gpt-4")
                .initialValid(true)
                .finalValid(true)
                .repairAttempts(0)
                .usedFallback(false)
                .totalLatencyMs(500L)
                .build());
        }
        
        for (int i = 0; i < 3; i++) {
            conformanceRepository.save(LlmOutputConformance.builder()
                .endpoint(endpoint)
                .model("gpt-4")
                .initialValid(false)
                .finalValid(true)
                .repairAttempts(1)
                .usedFallback(false)
                .totalLatencyMs(1500L)
                .build());
        }
        
        Double firstPassRate = conformanceRepository.calculateFirstPassRate(
            endpoint, now.minusMinutes(5));
        
        assertNotNull(firstPassRate);
        assertEquals(0.7, firstPassRate, 0.01, "First-pass rate should be 70%");
    }
    
    @Test
    public void testRepairSuccessRateCalculation() {
        String endpoint = "/api/test";
        LocalDateTime now = LocalDateTime.now();
        
        // 4 successful repairs
        for (int i = 0; i < 4; i++) {
            conformanceRepository.save(LlmOutputConformance.builder()
                .endpoint(endpoint)
                .model("gpt-4")
                .initialValid(false)
                .repairAttempts(1)
                .finalValid(true)
                .usedFallback(false)
                .totalLatencyMs(1500L)
                .build());
        }
        
        // 1 failed repair (fallback)
        conformanceRepository.save(LlmOutputConformance.builder()
            .endpoint(endpoint)
            .model("gpt-4")
            .initialValid(false)
            .repairAttempts(3)
            .finalValid(false)
            .usedFallback(true)
            .totalLatencyMs(4000L)
            .build());
        
        Double repairSuccessRate = conformanceRepository.calculateRepairSuccessRate(
            endpoint, now.minusMinutes(5));
        
        assertNotNull(repairSuccessRate);
        assertEquals(0.8, repairSuccessRate, 0.01, "Repair success rate should be 80%");
    }
    
    @Test
    public void testFallbackRateCalculation() {
        String endpoint = "/api/test";
        LocalDateTime now = LocalDateTime.now();
        
        // 19 successful (no fallback)
        for (int i = 0; i < 19; i++) {
            conformanceRepository.save(LlmOutputConformance.builder()
                .endpoint(endpoint)
                .model("gpt-4")
                .initialValid(i < 15) // 15 first-pass, 4 repaired
                .repairAttempts(i < 15 ? 0 : 1)
                .finalValid(true)
                .usedFallback(false)
                .totalLatencyMs(i < 15 ? 500L : 1500L)
                .build());
        }
        
        // 1 fallback
        conformanceRepository.save(LlmOutputConformance.builder()
            .endpoint(endpoint)
            .model("gpt-4")
            .initialValid(false)
            .repairAttempts(3)
            .finalValid(false)
            .usedFallback(true)
            .totalLatencyMs(4000L)
            .build());
        
        Double fallbackRate = conformanceRepository.calculateFallbackRate(
            endpoint, now.minusMinutes(5));
        
        assertNotNull(fallbackRate);
        assertEquals(0.05, fallbackRate, 0.01, "Fallback rate should be 5%");
    }
    
    @Test
    public void testAverageRepairLatencyCalculation() {
        String endpoint = "/api/test";
        LocalDateTime now = LocalDateTime.now();
        
        // Create records with repair (1500ms, 2000ms, 2500ms average = 2000ms)
        conformanceRepository.save(LlmOutputConformance.builder()
            .endpoint(endpoint)
            .model("gpt-4")
            .initialValid(false)
            .repairAttempts(1)
            .finalValid(true)
            .usedFallback(false)
            .totalLatencyMs(1500L)
            .build());
        
        conformanceRepository.save(LlmOutputConformance.builder()
            .endpoint(endpoint)
            .model("gpt-4")
            .initialValid(false)
            .repairAttempts(2)
            .finalValid(true)
            .usedFallback(false)
            .totalLatencyMs(2000L)
            .build());
        
        conformanceRepository.save(LlmOutputConformance.builder()
            .endpoint(endpoint)
            .model("gpt-4")
            .initialValid(false)
            .repairAttempts(2)
            .finalValid(true)
            .usedFallback(false)
            .totalLatencyMs(2500L)
            .build());
        
        Double avgLatency = conformanceRepository.calculateAverageRepairLatency(
            endpoint, now.minusMinutes(5));
        
        assertNotNull(avgLatency);
        assertEquals(2000.0, avgLatency, 50.0, "Average repair latency should be around 2000ms");
    }
    
    @Test
    public void testConformanceMetricsCalculation() {
        String endpoint = "/api/test";
        LocalDateTime now = LocalDateTime.now();
        
        // Create diverse test data
        // 8 first-pass valid
        for (int i = 0; i < 8; i++) {
            conformanceRepository.save(LlmOutputConformance.builder()
                .endpoint(endpoint)
                .model("gpt-4")
                .initialValid(true)
                .repairAttempts(0)
                .finalValid(true)
                .usedFallback(false)
                .totalLatencyMs(500L)
                .build());
        }
        
        // 2 repaired successfully
        for (int i = 0; i < 2; i++) {
            conformanceRepository.save(LlmOutputConformance.builder()
                .endpoint(endpoint)
                .model("gpt-4")
                .initialValid(false)
                .repairAttempts(1)
                .finalValid(true)
                .usedFallback(false)
                .totalLatencyMs(2000L)
                .build());
        }
        
        OutputConformanceMonitor.ConformanceMetrics metrics = 
            conformanceMonitor.calculateMetrics(endpoint, now.minusMinutes(5));
        
        assertNotNull(metrics);
        assertEquals(endpoint, metrics.getEndpoint());
        assertEquals(10, metrics.getSampleSize());
        assertEquals(0.8, metrics.getFirstPassRate(), 0.01);
        assertEquals(1.0, metrics.getRepairSuccessRate(), 0.01);
        assertEquals(0.0, metrics.getFallbackRate(), 0.01);
        assertTrue(metrics.getAverageRepairLatency() > 0);
    }
    
    @Test
    public void testStructuredOutputSuccess() {
        StructuredOutput<String> output = StructuredOutput.success(
            "result", 1000L, "gpt-4", "v1.0");
        
        assertNotNull(output.getResult());
        assertTrue(output.isInitialValid());
        assertTrue(output.isFinalValid());
        assertFalse(output.isUsedRepair());
        assertFalse(output.isUsedFallback());
        assertEquals(0, output.getRepairAttempts());
        assertEquals(1000L, output.getTotalLatencyMs());
    }
    
    @Test
    public void testStructuredOutputFallback() {
        List<String> errors = List.of("Error 1", "Error 2");
        StructuredOutput<String> output = StructuredOutput.fallback(
            "fallback", errors, 3000L, "gpt-4", "v1.0");
        
        assertNotNull(output.getResult());
        assertFalse(output.isInitialValid());
        assertFalse(output.isFinalValid());
        assertTrue(output.isUsedFallback());
        assertEquals(2, output.getValidationErrors().size());
        assertEquals(3000L, output.getTotalLatencyMs());
    }
    
    @Test
    public void testConformanceFromStructuredOutput() {
        StructuredOutput<String> output = StructuredOutput.<String>builder()
            .result("test")
            .initialValid(false)
            .repairAttempts(2)
            .finalValid(true)
            .usedRepair(true)
            .usedFallback(false)
            .validationErrors(List.of("Error 1"))
            .totalLatencyMs(2500L)
            .model("gpt-4")
            .promptVersion("v1.0")
            .build();
        
        LlmOutputConformance conformance = 
            LlmOutputConformance.fromStructuredOutput(output, "/api/test");
        
        assertEquals("/api/test", conformance.getEndpoint());
        assertEquals("gpt-4", conformance.getModel());
        assertEquals("v1.0", conformance.getPromptVersion());
        assertFalse(conformance.getInitialValid());
        assertEquals(2, conformance.getRepairAttempts());
        assertTrue(conformance.getFinalValid());
        assertFalse(conformance.getUsedFallback());
        assertEquals(2500L, conformance.getTotalLatencyMs());
    }
}
