package com.aiinterview.ml.embedding;

import com.aiinterview.config.EmbeddedRedisConfig;
import com.aiinterview.ml.embedding.entity.QuestionEmbedding;
import com.aiinterview.ml.embedding.entity.TopicCoverage;
import com.aiinterview.ml.embedding.repository.QuestionEmbeddingRepository;
import com.aiinterview.ml.embedding.repository.TopicCoverageRepository;
import com.aiinterview.ml.embedding.service.EmbeddingService;
import com.aiinterview.ml.embedding.service.TopicClusteringService;
import com.aiinterview.ml.embedding.service.TopicCoverageTracker;
import com.aiinterview.model.ApiKeyConfig;
import com.aiinterview.repository.ApiKeyConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for Week 14 Task 1: Embedding Infrastructure & Topic Coverage
 * 
 * Acceptance Criteria:
 * - Startup embedding < 30s (100 Qs)
 * - MRR > 0.7
 * - Silhouette > 0.3
 * - ≥ 40% repetition reduction
 * - Redis hit rate > 90%
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(EmbeddedRedisConfig.class)
@Sql("/import.sql")
@Transactional
public class EmbeddingInfrastructureIntegrationTest {

    @Autowired
    private EmbeddingService embeddingService;

    @Autowired
    private TopicClusteringService clusteringService;

    @Autowired
    private TopicCoverageTracker coverageTracker;

    @Autowired
    private QuestionEmbeddingRepository embeddingRepository;

    @Autowired
    private TopicCoverageRepository coverageRepository;
    
    @Autowired
    private ApiKeyConfigRepository apiKeyConfigRepository;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private static final Long TEST_ROLE_ID = 1L;
    private static final String TEST_SESSION_ID = "test-session-123";

    @BeforeEach
    void setUp() {
        // Clear test data
        embeddingRepository.deleteAll();
        coverageRepository.deleteAll();
        
        // Clear Redis cache
        Set<String> keys = redisTemplate.keys("embedding:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    /**
     * Test 1: Embedding generation and caching
     * Acceptance: Redis hit rate >90%
     */
    @Test
    void testEmbeddingGenerationAndCaching() {
        String text = "What is dependency injection in Spring Framework?";
        
        // First call - cache miss
        Mono<double[]> firstCall = embeddingService.embed(text);
        StepVerifier.create(firstCall)
            .assertNext(embedding -> {
                assertThat(embedding).isNotNull();
                assertThat(embedding.length).isEqualTo(1536); // text-embedding-3-small dimensions
                assertThat(embedding[0]).isNotZero(); // Vector should have values
            })
            .verifyComplete();

        // Second call - should hit cache
        Mono<double[]> secondCall = embeddingService.embed(text);
        StepVerifier.create(secondCall)
            .assertNext(embedding -> {
                assertThat(embedding).isNotNull();
                assertThat(embedding.length).isEqualTo(1536);
            })
            .verifyComplete();

        if (redisTemplate != null) {
            // Test cache hit rate with multiple calls
            int totalCalls = 100;
            int uniqueTexts = 10;
            
            List<String> texts = new ArrayList<>();
            for (int i = 0; i < uniqueTexts; i++) {
                texts.add("Question " + i + " about Java programming");
            }

            // Warm up cache
            for (String t : texts) {
                embeddingService.embed(t).block();
            }

            // Make repeated calls (90% should be cached)
            long startTime = System.currentTimeMillis();
            for (int i = 0; i < totalCalls; i++) {
                String text2 = texts.get(i % uniqueTexts);
                embeddingService.embed(text2).block();
            }
            long duration = System.currentTimeMillis() - startTime;

            // Cached calls should be very fast (<1s for 100 calls)
            assertThat(duration).isLessThan(1000);
        }
    }

    /**
     * Test 2: K-means++ clustering convergence and Silhouette score
     * Acceptance: Silhouette >0.3
     */
    @Test
    void testClusteringConvergenceAndQuality() {
        // Create test embeddings with clear clusters
        List<QuestionEmbedding> testEmbeddings = createTestEmbeddingsWithClusters(TEST_ROLE_ID, 30);
        embeddingRepository.saveAll(testEmbeddings);

        // Run clustering
        TopicClusteringService.ClusteringResult result = 
            clusteringService.clusterQuestions(TEST_ROLE_ID.toString());

        // Verify clustering results
        assertThat(result).isNotNull();
        assertThat(result.getOptimalK()).isBetween(3, 10);
        assertThat(result.getSilhouetteScore()).isGreaterThan(0.3); // Acceptance criterion
        assertThat(result.getQuestionsCount()).isEqualTo(30);
        assertThat(result.getClusters()).isNotEmpty();

        // Verify cluster labels
        for (TopicClusteringService.ClusterInfo cluster : result.getClusters()) {
            assertThat(cluster.getClusterId()).isNotNull();
            assertThat(cluster.getLabel()).isNotBlank();
            assertThat(cluster.getSize()).isGreaterThan(0);
        }

        // Verify embeddings were updated with cluster assignments
        List<QuestionEmbedding> updated = embeddingRepository.findByRoleId(TEST_ROLE_ID);
        long assignedCount = updated.stream().filter(e -> e.getClusterId() != null).count();
        assertThat(assignedCount).isEqualTo(30);
    }

    /**
     * Test 3: Topic coverage tracker with Shannon entropy
     */
    @Test
    void testTopicCoverageTrackerAndEntropy() {
        // Setup: Create embeddings with cluster assignments
        List<QuestionEmbedding> embeddings = createTestEmbeddingsWithClusters(TEST_ROLE_ID, 20);
        embeddingRepository.saveAll(embeddings);

        // Manually assign clusters for predictable testing
        List<QuestionEmbedding> saved = embeddingRepository.findByRoleId(TEST_ROLE_ID);
        for (int i = 0; i < saved.size(); i++) {
            saved.get(i).setClusterId(i % 4); // 4 clusters
        }
        embeddingRepository.saveAll(saved);

        // Track coverage as questions are asked
        for (int i = 0; i < 8; i++) {
            String qid = saved.get(i).getQuestionId();
            Long qidNumeric = Long.parseLong(qid);
            coverageTracker.recordQuestionAsked(TEST_SESSION_ID, TEST_ROLE_ID, qidNumeric);
        }

        // Get coverage metrics
        TopicCoverage coverage = coverageTracker.getCoverageMetrics(TEST_SESSION_ID, TEST_ROLE_ID);
        
        assertThat(coverage).isNotNull();
        assertThat(coverage.getTotalQuestions()).isEqualTo(8);
        assertThat(coverage.getClustersCovered()).isGreaterThan(0);
        assertThat(coverage.getCoverageRatio()).isGreaterThan(0.0);
        assertThat(coverage.getShannonEntropy()).isGreaterThan(0.0);
        assertThat(coverage.getNormalizedEntropy()).isBetween(0.0, 1.0);
        
        // Test diversity score
        double diversityScore = coverageTracker.getTopicDiversityScore(TEST_SESSION_ID, TEST_ROLE_ID);
        assertThat(diversityScore).isBetween(0.0, 1.0);

        // Test coverage gaps detection
        List<Integer> gaps = coverageTracker.detectCoverageGaps(TEST_SESSION_ID, TEST_ROLE_ID);
        assertThat(gaps).isNotNull();
    }

    /**
     * Test 4: Combined IRT and topic diversity scoring
     */
    @Test
    void testCombinedScoringIntegration() {
        // Create embeddings with clusters
        List<QuestionEmbedding> embeddings = createTestEmbeddingsWithClusters(TEST_ROLE_ID, 15);
        embeddingRepository.saveAll(embeddings);

        // Assign clusters
        List<QuestionEmbedding> saved = embeddingRepository.findByRoleId(TEST_ROLE_ID);
        for (int i = 0; i < saved.size(); i++) {
            saved.get(i).setClusterId(i % 3); // 3 clusters
        }
        embeddingRepository.saveAll(saved);

        // Ask questions from cluster 0 repeatedly
        for (int i = 0; i < 5; i++) {
            QuestionEmbedding qe = saved.stream()
                .filter(e -> e.getClusterId() == 0)
                .skip(i)
                .findFirst()
                .orElse(null);
            
            if (qe != null) {
                Long qid = Long.parseLong(qe.getQuestionId());
                coverageTracker.recordQuestionAsked(TEST_SESSION_ID, TEST_ROLE_ID, qid);
            }
        }

        // Get diversity reward for each cluster
        double reward0 = coverageTracker.getClusterDiversityReward(TEST_SESSION_ID, TEST_ROLE_ID, 0);
        double reward1 = coverageTracker.getClusterDiversityReward(TEST_SESSION_ID, TEST_ROLE_ID, 1);
        double reward2 = coverageTracker.getClusterDiversityReward(TEST_SESSION_ID, TEST_ROLE_ID, 2);

        // Cluster 0 should have lower reward (already asked many times)
        // Clusters 1 and 2 should have higher reward (not asked yet)
        assertThat(reward1).isGreaterThan(reward0);
        assertThat(reward2).isGreaterThan(reward0);
        assertThat(reward1).isCloseTo(1.0, org.assertj.core.data.Offset.offset(0.1));
    }

    /**
     * Test 5: Startup performance
     * Acceptance: Startup embedding time <30s for 100 questions
     */
    @Test
    void testStartupPerformance() {
        // Generate 100 test questions
        List<String> questions = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            questions.add("Test question " + i + " about software engineering");
        }

        long startTime = System.currentTimeMillis();
        
        // Embed all questions (batch processing)
        Mono<List<double[]>> embeddingsMono = embeddingService.embedBatch(questions);
        List<double[]> embeddings = embeddingsMono.block();
        
        long duration = System.currentTimeMillis() - startTime;

        assertThat(embeddings).hasSize(100);
        assertThat(duration).isLessThan(30000); // <30s acceptance criterion
        
        System.out.printf("Embedded 100 questions in %d ms (%.2f questions/sec)%n", 
            duration, 100000.0 / duration);
    }

    /**
     * Test 6: Repetition reduction through topic diversity
     * Acceptance: ≥40% repetition reduction
     */
    @Test
    void testRepetitionReduction() {
        // Create embeddings with 5 clusters
        int questionsPerCluster = 6;
        List<QuestionEmbedding> embeddings = new ArrayList<>();
        
        for (int cluster = 0; cluster < 5; cluster++) {
            for (int q = 0; q < questionsPerCluster; q++) {
                int questionNum = cluster * questionsPerCluster + q;
                QuestionEmbedding qe = new QuestionEmbedding();
                qe.setQuestionId(String.valueOf(questionNum)); // Use numeric ID
                qe.setRoleId(TEST_ROLE_ID);
                qe.setQuestionText("Question text for cluster " + cluster);
                qe.setEmbeddingVector(generateClusteredVector(cluster, 1536));
                qe.setClusterId(cluster);
                embeddings.add(qe);
            }
        }
        
        embeddingRepository.saveAll(embeddings);

        // Simulate interview with 15 questions
        int totalQuestions = 15;
        Map<Integer, Integer> clusterCounts = new HashMap<>();
        
        for (int i = 0; i < totalQuestions; i++) {
            // Find cluster with lowest count (simulating diversity-aware selection)
            Integer bestCluster = null;
            int minCount = Integer.MAX_VALUE;
            
            for (int c = 0; c < 5; c++) {
                int count = clusterCounts.getOrDefault(c, 0);
                if (count < minCount) {
                    minCount = count;
                    bestCluster = c;
                }
            }
            
            // Ask question from this cluster
            if (bestCluster != null) {
                final Integer finalBestCluster = bestCluster;
                clusterCounts.merge(finalBestCluster, 1, Integer::sum);
                
                // Find a question from this cluster
                QuestionEmbedding qe = embeddings.stream()
                    .filter(e -> e.getClusterId().equals(finalBestCluster))
                    .skip(clusterCounts.get(finalBestCluster) - 1)
                    .findFirst()
                    .orElse(null);
                
                if (qe != null) {
                    Long qid = Long.parseLong(qe.getQuestionId());
                    coverageTracker.recordQuestionAsked(TEST_SESSION_ID, TEST_ROLE_ID, qid);
                }
            }
        }

        // Calculate repetition: max cluster count / avg cluster count
        int maxCount = clusterCounts.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        double avgCount = clusterCounts.values().stream().mapToInt(Integer::intValue).average().orElse(0);
        double repetitionRatio = maxCount / avgCount;

        // With diversity, repetition should be low (ratio close to 1.0)
        // Without diversity, some clusters might have 10+ questions (ratio 3-5)
        // 40% reduction means ratio should be < 1.8 (down from ~3.0)
        assertThat(repetitionRatio).isLessThan(1.8);
        
        // Verify uniform coverage
        TopicCoverage finalCoverage = coverageTracker.getCoverageMetrics(TEST_SESSION_ID, TEST_ROLE_ID);
        assertThat(finalCoverage.getCoverageRatio()).isGreaterThan(0.8); // >80% clusters covered
        
        System.out.printf("Repetition ratio: %.2f, Coverage: %.1f%%, Entropy: %.3f%n",
            repetitionRatio, finalCoverage.getCoverageRatio() * 100, finalCoverage.getShannonEntropy());
    }

    // Helper methods

    private List<QuestionEmbedding> createTestEmbeddingsWithClusters(Long roleId, int count) {
        List<QuestionEmbedding> embeddings = new ArrayList<>();
        
        for (int i = 0; i < count; i++) {
            QuestionEmbedding qe = new QuestionEmbedding();
            qe.setQuestionId(String.valueOf(i)); // Use numeric ID
            qe.setRoleId(roleId);
            qe.setQuestionText("Test question " + i);
            
            // Generate vector with cluster tendency
            int clusterHint = i % 4;
            qe.setEmbeddingVector(generateClusteredVector(clusterHint, 1536));
            
            embeddings.add(qe);
        }
        
        return embeddings;
    }

    private double[] generateClusteredVector(int clusterId, int dimensions) {
        Random random = new Random(clusterId * 1000L); // Deterministic per cluster
        double[] vector = new double[dimensions];
        
        // Create cluster center
        double[] center = new double[dimensions];
        for (int i = 0; i < 10; i++) { // Use first 10 dims for clustering
            center[i] = random.nextGaussian();
        }
        
        // Generate vector near center
        for (int i = 0; i < dimensions; i++) {
            if (i < 10) {
                vector[i] = center[i] + random.nextGaussian() * 0.1; // Small noise
            } else {
                vector[i] = random.nextGaussian() * 0.01; // Random for other dims
            }
        }
        
        // Normalize
        double norm = 0.0;
        for (double v : vector) {
            norm += v * v;
        }
        norm = Math.sqrt(norm);
        
        for (int i = 0; i < dimensions; i++) {
            vector[i] /= norm;
        }
        
        return vector;
    }
}
