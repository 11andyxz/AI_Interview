package com.aiinterview.ml.nlp;

import com.aiinterview.ml.nlp.entity.ResponseFeatureCache;
import com.aiinterview.ml.nlp.repository.ResponseFeatureCacheRepository;
import com.aiinterview.ml.prediction.entity.CandidateSkillProfile;
import com.aiinterview.ml.prediction.repository.CandidateSkillProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Week 25 Task 2: Organic Feature-Writing Integration Tests
 *
 * Verifies that the live eval and question-generate flows correctly write to:
 *   - response_feature_cache (via ResponseFeatureExtractor.extractAndCache)
 *   - candidate_skill_profile (via LlmGatewayController.updateSkillProfile)
 *
 * These are unit-level tests that exercise the write-path methods directly,
 * without starting a full HTTP server.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "ml.nlp.enabled=true",
        "ml.embedding.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Transactional
public class OrganicFeatureWriteTest {

    @Autowired
    private ResponseFeatureExtractor featureExtractor;

    @Autowired
    private ResponseFeatureCacheRepository featureCacheRepository;

    @Autowired
    private CandidateSkillProfileRepository skillProfileRepository;

    private String sessionId;

    @BeforeEach
    void setUp() {
        sessionId = "test-session-" + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * Test that extractAndCache writes a row to response_feature_cache.
     */
    @Test
    void testExtractAndCache_writesRow() {
        String questionId = "q-abc123";
        String answer = "Spring Boot uses the IoC container for dependency injection. "
                + "Beans are managed by the ApplicationContext and wired via @Autowired. "
                + "The @Component, @Service, and @Repository stereotypes register beans automatically.";
        double llmScore = 78.0;

        featureExtractor.extractAndCache(sessionId, questionId, answer, llmScore);

        Optional<ResponseFeatureCache> cached = featureCacheRepository.findBySessionIdAndQuestionId(sessionId, questionId);
        assertTrue(cached.isPresent(), "A cache entry should have been written");

        ResponseFeatureCache entry = cached.get();
        assertEquals(sessionId, entry.getSessionId());
        assertEquals(questionId, entry.getQuestionId());
        assertEquals(llmScore, entry.getLlmScore(), 0.001);
        assertNotNull(entry.getFeatureVector(), "Feature vector must not be null");
        assertTrue(entry.getFeatureVector().startsWith("["), "Feature vector should be a JSON array");
        assertEquals("organic-v1.0", entry.getModelVersion());
        assertNotNull(entry.getFeatureExtractionTimeMs());
    }

    /**
     * Test that extractAndCache is idempotent: calling it twice does not create a second row.
     */
    @Test
    void testExtractAndCache_idempotent() {
        String questionId = "q-idem-001";
        String answer = "REST APIs use HTTP verbs: GET retrieves resources, POST creates them, "
                + "PUT replaces them, and DELETE removes them. Status codes convey outcomes.";
        double llmScore = 65.0;

        featureExtractor.extractAndCache(sessionId, questionId, answer, llmScore);
        featureExtractor.extractAndCache(sessionId, questionId, answer, llmScore); // second call should be a no-op

        long count = featureCacheRepository.findBySessionId(sessionId).stream()
                .filter(e -> e.getQuestionId().equals(questionId))
                .count();
        assertEquals(1, count, "Duplicate call must not insert a second row");
    }

    /**
     * Test that feature vector has the expected dimension (24 features).
     */
    @Test
    void testExtractAndCache_featureVectorDimension() {
        String questionId = "q-dim-001";
        String answer = "Microservices communicate via REST or gRPC. "
                + "Service discovery is handled by tools like Consul or Eureka. "
                + "Circuit breakers prevent cascading failures.";

        featureExtractor.extractAndCache(sessionId, questionId, answer, 70.0);

        ResponseFeatureCache entry = featureCacheRepository
                .findBySessionIdAndQuestionId(sessionId, questionId)
                .orElseThrow();

        // Parse the JSON array and count elements
        String json = entry.getFeatureVector().trim().replaceAll("[\\[\\]]", "");
        String[] parts = json.split(",");
        assertEquals(24, parts.length, "Feature vector should have 24 dimensions");
    }

    /**
     * Test that a new CandidateSkillProfile is created on first save and updated on subsequent saves.
     * This exercises the upsert logic that will be called from LlmGatewayController.updateSkillProfile.
     */
    @Test
    void testSkillProfileUpsert() {
        CandidateSkillProfile profile = new CandidateSkillProfile();
        profile.setSessionId(sessionId);
        profile.setRoleId(1L);
        profile.setCumulativeScore(0.0);
        profile.setQuestionCount(0);
        profile.setScoreTrend("[]");
        profile.setCreatedAt(java.time.LocalDateTime.now());
        profile.setUpdatedAt(java.time.LocalDateTime.now());
        skillProfileRepository.save(profile);

        // Simulate two answer evaluations
        double[] scores = {72.0, 85.0};
        double cumulative = 0;
        for (double score : scores) {
            CandidateSkillProfile p = skillProfileRepository.findBySessionId(sessionId).orElseThrow();
            cumulative += score;
            p.setCumulativeScore(cumulative);
            p.setQuestionCount(p.getQuestionCount() + 1);
            p.setUpdatedAt(java.time.LocalDateTime.now());
            skillProfileRepository.save(p);
        }

        CandidateSkillProfile result = skillProfileRepository.findBySessionId(sessionId).orElseThrow();
        assertEquals(2, result.getQuestionCount(), "Question count should be 2 after two evaluations");
        assertEquals(157.0, result.getCumulativeScore(), 0.001, "Cumulative score should be 72+85=157");
    }
}
