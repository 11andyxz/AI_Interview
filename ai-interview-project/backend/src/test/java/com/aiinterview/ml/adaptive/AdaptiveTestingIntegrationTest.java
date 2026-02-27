package com.aiinterview.ml.adaptive;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for adaptive testing system
 * Tests ability estimation, question selection, and calibration
 */
@SpringBootTest
@Transactional
public class AdaptiveTestingIntegrationTest {
    
    @Autowired
    private CandidateAbilityEstimator abilityEstimator;
    
    @Autowired
    private AdaptiveQuestionSelector questionSelector;
    
    @Autowired
    private QuestionCalibrationService calibrationService;
    
    @Autowired
    private QuestionCalibrationRepository calibrationRepository;
    
    @BeforeEach
    public void setUp() {
        calibrationRepository.deleteAll();
    }
    
    @Test
    public void testAbilityEstimationFromEmpty() {
        AbilityEstimate estimate = abilityEstimator.estimateAbility(Collections.emptyList());
        
        assertNotNull(estimate);
        assertEquals(0.0, estimate.getTheta(), 0.1, "Initial ability should be neutral");
        assertTrue(estimate.getStandardError() > 0.5, "Initial SE should be high");
        assertEquals(0, estimate.getResponsesUsed());
    }
    
    @Test
    public void testAbilityEstimationWithResponses() {
        List<ResponseRecord> responses = Arrays.asList(
            ResponseRecord.builder()
                .questionId("q1")
                .score(1.0)
                .difficultyB(0.0)
                .discriminationA(1.0)
                .build(),
            ResponseRecord.builder()
                .questionId("q2")
                .score(1.0)
                .difficultyB(0.5)
                .discriminationA(1.0)
                .build(),
            ResponseRecord.builder()
                .questionId("q3")
                .score(0.8)
                .difficultyB(1.0)
                .discriminationA(1.0)
                .build()
        );
        
        AbilityEstimate estimate = abilityEstimator.estimateAbility(responses);
        
        assertNotNull(estimate);
        assertTrue(estimate.getTheta() > 0.0, "Ability should be positive for correct responses");
        assertTrue(estimate.getStandardError() < 1.0, "SE should decrease with responses");
        assertEquals(3, estimate.getResponsesUsed());
    }
    
    @Test
    public void testIncrementalAbilityUpdate() {
        AbilityEstimate prior = AbilityEstimate.createPrior();
        
        ResponseRecord newResponse = ResponseRecord.builder()
            .questionId("q1")
            .score(1.0)
            .difficultyB(0.0)
            .discriminationA(1.0)
            .build();
        
        AbilityEstimate updated = abilityEstimator.updateAbility(prior, newResponse);
        
        assertNotNull(updated);
        assertTrue(updated.getTheta() > prior.getTheta(), 
                   "Ability should increase after correct response");
        assertTrue(updated.getStandardError() < prior.getStandardError(),
                   "SE should decrease with more data");
        assertEquals(1, updated.getResponsesUsed());
    }
    
    @Test
    public void testAbilityConvergenceWithin12Questions() {
        List<ResponseRecord> responses = new ArrayList<>();
        
        // Simulate 12 responses with varying difficulty levels and high discrimination
        // Use a mix of difficulties to provide good Fisher information
        double[] difficulties = {-1.0, -0.5, 0.0, 0.5, 1.0, 0.3, -0.3, 0.7, 0.2, -0.2, 0.4, 0.1};
        double[] scores = {1.0, 1.0, 0.9, 0.8, 0.6, 0.8, 0.9, 0.7, 0.9, 1.0, 0.8, 0.9};
        
        for (int i = 0; i < 12; i++) {
            responses.add(ResponseRecord.builder()
                .questionId("q" + i)
                .score(scores[i])
                .difficultyB(difficulties[i])
                .discriminationA(2.0)  // Higher discrimination for better precision
                .build());
        }
        
        AbilityEstimate initial = abilityEstimator.estimateAbility(responses.subList(0, 3));
        AbilityEstimate estimate = abilityEstimator.estimateAbility(responses);
        
        // Check that SE improves significantly with more questions
        assertTrue(estimate.getStandardError() < initial.getStandardError(),
                   "SE should decrease with more responses");
        assertTrue(estimate.getStandardError() < 0.5,
                   "SE should be reasonable within 12 questions with good discrimination");
        assertEquals(12, estimate.getResponsesUsed());
    }
    
    @Test
    public void testQuestionCalibrationCreation() {
        String questionId = "test_q1";
        String roleId = "backend_java";
        
        calibrationService.updateCalibration(questionId, roleId, 0.8, 0.5);
        
        Optional<QuestionCalibration> calibration = 
            calibrationRepository.findByQuestionIdAndRoleId(questionId, roleId);
        
        assertTrue(calibration.isPresent());
        assertEquals(questionId, calibration.get().getQuestionId());
        assertEquals(roleId, calibration.get().getRoleId());
        assertEquals(1, calibration.get().getResponseCount());
    }
    
    @Test
    public void testIncrementalCalibrationUpdate() {
        String questionId = "test_q2";
        String roleId = "backend_java";
        
        // Add multiple responses
        calibrationService.updateCalibration(questionId, roleId, 0.6, 0.0);
        calibrationService.updateCalibration(questionId, roleId, 0.8, 0.5);
        calibrationService.updateCalibration(questionId, roleId, 0.7, 0.3);
        
        QuestionCalibration calibration = 
            calibrationRepository.findByQuestionIdAndRoleId(questionId, roleId).orElseThrow();
        
        assertEquals(3, calibration.getResponseCount());
        assertTrue(calibration.getMeanScore() > 0.6 && calibration.getMeanScore() < 0.8);
    }
    
    @Test
    public void testBootstrapFromHistory() {
        String questionId = "test_q3";
        String roleId = "backend_java";
        
        List<QuestionCalibrationService.HistoricalResponse> history = Arrays.asList(
            QuestionCalibrationService.HistoricalResponse.builder()
                .score(0.8).candidateAbility(0.5).build(),
            QuestionCalibrationService.HistoricalResponse.builder()
                .score(0.6).candidateAbility(-0.2).build(),
            QuestionCalibrationService.HistoricalResponse.builder()
                .score(0.9).candidateAbility(1.0).build(),
            QuestionCalibrationService.HistoricalResponse.builder()
                .score(0.7).candidateAbility(0.3).build()
        );
        
        calibrationService.bootstrapFromHistory(questionId, roleId, history);
        
        QuestionCalibration calibration = 
            calibrationRepository.findByQuestionIdAndRoleId(questionId, roleId).orElseThrow();
        
        assertEquals(4, calibration.getResponseCount());
        assertTrue(calibration.getDifficultyB() >= -1.0 && calibration.getDifficultyB() <= 1.0);
        assertTrue(calibration.getDiscriminationA() > 0.1 && calibration.getDiscriminationA() <= 3.0);
    }
    
    @Test
    public void testQuestionSelectionWithNoCalibrations() {
        AbilityEstimate ability = AbilityEstimate.builder()
            .theta(0.5)
            .standardError(0.5)
            .responsesUsed(3)
            .build();
        
        Optional<QuestionItem> selected = questionSelector.selectNextQuestion(
            "interview1",
            "backend_java",
            ability,
            new HashSet<>()
        );
        
        assertFalse(selected.isPresent(), "Should return empty when no calibrations exist");
    }
    
    @Test
    public void testQuestionSelectionWithCalibrations() {
        String roleId = "backend_java";
        
        // Create calibrations with different difficulties
        createCalibration("q1", roleId, -0.5, 1.2, 20);
        createCalibration("q2", roleId, 0.0, 1.0, 20);
        createCalibration("q3", roleId, 0.5, 1.5, 20);
        createCalibration("q4", roleId, 1.0, 1.3, 20);
        
        AbilityEstimate ability = AbilityEstimate.builder()
            .theta(0.3)
            .standardError(0.5)
            .responsesUsed(3)
            .build();
        
        Optional<QuestionItem> selected = questionSelector.selectNextQuestion(
            "interview1",
            roleId,
            ability,
            new HashSet<>()
        );
        
        assertTrue(selected.isPresent(), "Should select a question");
        
        // Selected question should be within ±1 difficulty of ability
        double difficultyDiff = Math.abs(selected.get().getDifficultyB() - ability.getTheta());
        assertTrue(difficultyDiff <= 1.5, 
                   "Selected question should be reasonably close to ability level");
    }
    
    @Test
    public void testQuestionSelectionExcludesAsked() {
        String roleId = "backend_java";
        
        createCalibration("q1", roleId, 0.0, 1.0, 20);
        createCalibration("q2", roleId, 0.2, 1.0, 20);
        
        AbilityEstimate ability = AbilityEstimate.builder()
            .theta(0.0)
            .standardError(0.5)
            .build();
        
        Set<String> asked = new HashSet<>();
        asked.add("q1");
        
        Optional<QuestionItem> selected = questionSelector.selectNextQuestion(
            "interview1",
            roleId,
            ability,
            asked
        );
        
        assertTrue(selected.isPresent());
        assertEquals("q2", selected.get().getQuestionId(), "Should not select already asked question");
    }
    
    @Test
    public void testTerminationWithLowSE() {
        AbilityEstimate estimate = AbilityEstimate.builder()
            .theta(0.5)
            .standardError(0.25)
            .responsesUsed(8)
            .build();
        
        boolean shouldStop = questionSelector.shouldTerminate(estimate, 8, 12);
        
        assertTrue(shouldStop, "Should terminate when SE < 0.3 and min questions met");
    }
    
    @Test
    public void testNoTerminationBelowMinQuestions() {
        AbilityEstimate estimate = AbilityEstimate.builder()
            .theta(0.5)
            .standardError(0.2)
            .responsesUsed(5)
            .build();
        
        boolean shouldStop = questionSelector.shouldTerminate(estimate, 5, 12);
        
        assertFalse(shouldStop, "Should not terminate below minimum 8 questions");
    }
    
    @Test
    public void testTerminationAtMaxQuestions() {
        AbilityEstimate estimate = AbilityEstimate.builder()
            .theta(0.5)
            .standardError(0.8)
            .responsesUsed(12)
            .build();
        
        boolean shouldStop = questionSelector.shouldTerminate(estimate, 12, 12);
        
        assertTrue(shouldStop, "Should terminate at max questions even with high SE");
    }
    
    @Test
    public void testFisherInformation() {
        QuestionItem question = QuestionItem.builder()
            .questionId("q1")
            .difficultyB(0.5)
            .discriminationA(1.5)
            .build();
        
        double info = question.fisherInformation(0.5);
        
        assertTrue(info > 0, "Fisher information should be positive");
        assertTrue(info > 0.3, "Information should be high when theta = difficulty");
    }
    
    @Test
    public void testProbabilityCorrect() {
        QuestionItem question = QuestionItem.builder()
            .questionId("q1")
            .difficultyB(0.0)
            .discriminationA(1.0)
            .build();
        
        double p = question.probabilityCorrect(0.0);
        
        assertEquals(0.5, p, 0.01, "Probability should be 0.5 when ability = difficulty");
        
        double pHigh = question.probabilityCorrect(2.0);
        assertTrue(pHigh > 0.8, "High ability should have high probability");
        
        double pLow = question.probabilityCorrect(-2.0);
        assertTrue(pLow < 0.2, "Low ability should have low probability");
    }
    
    private void createCalibration(String questionId, String roleId, 
                                   double difficulty, double discrimination, int responses) {
        QuestionCalibration calibration = QuestionCalibration.builder()
            .questionId(questionId)
            .roleId(roleId)
            .difficultyB(difficulty)
            .discriminationA(discrimination)
            .responseCount(responses)
            .meanScore(0.7)
            .scoreVariance(0.2)
            .build();
        
        calibrationRepository.save(calibration);
    }
}
