package com.aiinterview.ml.embedding.service;

import com.aiinterview.ml.embedding.entity.QuestionEmbedding;
import com.aiinterview.ml.embedding.repository.QuestionEmbeddingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Maps LLM-generated questions to stable IDs and cluster assignments so that
 * TopicCoverageTracker can record organic topic_coverage rows.
 *
 * <p>Root cause of the Week 26 topic_coverage=0 blocker:
 * LlmGatewayController passed the question number (1, 2, 3, ...) as the questionId
 * to TopicCoverageTracker. No question_embedding row exists for numeric IDs like
 * "1" or "2", so the tracker silently returned without writing any coverage row.
 *
 * <p>This service resolves the blocker by:
 * <ol>
 *   <li>Computing a stable {@code "gen-" + sha256[:12]} ID from the question text.</li>
 *   <li>Checking whether a {@code question_embedding} row already exists for that ID
 *       (deduplicates repeated identical questions).</li>
 *   <li>If not found: persisting a skeleton row with the question text and stable ID
 *       immediately.</li>
 *   <li>Scheduling background embedding + cluster assignment for that row so that
 *       coverage tracking becomes active without delaying the HTTP response.</li>
 * </ol>
 *
 * <p>Cluster assignment for generated questions:
 * Nearest-neighbour cosine similarity against all {@code question_embedding} rows
 * for the same {@code role_id} that already have a {@code cluster_id}. If no
 * seeded cluster candidates exist, the generated question receives an explicit
 * generated fallback cluster so coverage writes remain non-fatal and auditable.
 *
 * <p>Failure modes: all failures are logged and non-fatal.  The caller (controller)
 * should never surface an error to the candidate because of a mapping failure.
 */
@Service
@ConditionalOnProperty(name = "ml.embedding.enabled", havingValue = "true", matchIfMissing = false)
public class GeneratedQuestionMapper {

    private static final Logger logger = LoggerFactory.getLogger(GeneratedQuestionMapper.class);
    private static final String GENERATED_ID_PREFIX = "gen-";
    private static final int HASH_HEX_LEN = 12;
    private static final int GENERATED_FALLBACK_CLUSTER_BASE = 900_000;

    private final QuestionEmbeddingRepository embeddingRepository;
    private final EmbeddingService embeddingService;

    @Autowired
    public GeneratedQuestionMapper(
            QuestionEmbeddingRepository embeddingRepository,
            EmbeddingService embeddingService) {
        this.embeddingRepository = embeddingRepository;
        this.embeddingService = embeddingService;
    }

    /**
     * Return a stable questionId for the given generated question text.
     *
     * <p>If a {@code question_embedding} row already exists for this text hash, the
     * existing row's {@code question_id} is returned immediately. Otherwise a skeleton
     * row is persisted synchronously and background embedding/cluster assignment is scheduled.
     *
     * @param questionText the full text of the LLM-generated question
     * @param roleId       numeric role ID matching {@code question_embedding.role_id}
     * @return stable questionId string (e.g. {@code "gen-a3f8d21c0b94"})
     */
    public String mapGeneratedQuestion(String questionText, Long roleId) {
        String stableId = computeStableId(questionText);

        Optional<QuestionEmbedding> existing =
                embeddingRepository.findByQuestionIdAndRoleId(stableId, roleId);
        if (existing.isPresent()) {
            logger.debug("Generated question cache hit: id={} roleId={}", stableId, roleId);
            return stableId;
        }

        // Persist skeleton row synchronously so the tracker can find it immediately.
        QuestionEmbedding row = new QuestionEmbedding();
        row.setQuestionId(stableId);
        row.setRoleId(roleId);
        row.setQuestionText(questionText);
        // cluster_id is left null until the background job assigns it.
        try {
            embeddingRepository.save(row);
            logger.info("Persisted generated-question skeleton: id={} roleId={}", stableId, roleId);
        } catch (Exception e) {
            // Unique constraint violation = concurrent insert; row already exists, safe to continue.
            logger.debug("Skeleton insert skipped (likely duplicate): id={} error={}", stableId, e.getMessage());
        }

        // Schedule background embedding + cluster assignment; failures are non-fatal.
        scheduleEmbeddingAndClusterAssignment(stableId, questionText, roleId);

        return stableId;
    }

    /**
     * Return a stable questionId and record topic coverage once a cluster is available.
     *
     * <p>New generated questions are inserted immediately, then embedding, cluster
     * assignment, and coverage recording happen in the background. Existing generated
     * questions with a cluster assignment can be recorded immediately.
     */
    public String mapGeneratedQuestionAndRecordCoverage(
            String questionText,
            Long roleId,
            String sessionId,
            TopicCoverageTracker coverageTracker) {
        String stableId = computeStableId(questionText);

        Optional<QuestionEmbedding> existing =
                embeddingRepository.findByQuestionIdAndRoleId(stableId, roleId);
        if (existing.isPresent() && existing.get().getClusterId() != null) {
            coverageTracker.recordQuestionAsked(sessionId, roleId, stableId);
            return stableId;
        }

        persistSkeletonIfNeeded(stableId, questionText, roleId, existing);
        scheduleEmbeddingAndClusterAssignment(
                stableId,
                questionText,
                roleId,
                () -> coverageTracker.recordQuestionAsked(sessionId, roleId, stableId)
        );
        return stableId;
    }

    private void persistSkeletonIfNeeded(
            String stableId,
            String questionText,
            Long roleId,
            Optional<QuestionEmbedding> existing) {
        if (existing.isPresent()) {
            return;
        }

        QuestionEmbedding row = new QuestionEmbedding();
        row.setQuestionId(stableId);
        row.setRoleId(roleId);
        row.setQuestionText(questionText);
        try {
            embeddingRepository.save(row);
            logger.info("Persisted generated-question skeleton: id={} roleId={}", stableId, roleId);
        } catch (Exception e) {
            logger.debug("Skeleton insert skipped (likely duplicate): id={} error={}", stableId, e.getMessage());
        }
    }

    /**
     * Compute SHA-256 of the question text and return {@code "gen-"} followed by the
     * first {@value #HASH_HEX_LEN} hex characters.  Collisions are astronomically
     * unlikely at this ID length for interview question volumes.
     */
    static String computeStableId(String questionText) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String normalized = questionText == null ? "" : questionText.strip();
            byte[] hash = digest.digest(normalized.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(GENERATED_ID_PREFIX);
            for (int i = 0; i < HASH_HEX_LEN / 2 && i < hash.length; i++) {
                sb.append(String.format("%02x", hash[i]));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is always available in the JDK; unreachable in practice.
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /**
     * Schedule embedding for the question text, then assign the nearest cluster
     * from existing role-specific embeddings via cosine similarity.
     *
     * <p>Failures are logged but never propagated. Work is explicitly scheduled
     * because Spring @Async would not apply to self-invocation from mapGeneratedQuestion().
     */
    public void scheduleEmbeddingAndClusterAssignment(
            String stableId, String questionText, Long roleId) {
        scheduleEmbeddingAndClusterAssignment(stableId, questionText, roleId, null);
    }

    private void scheduleEmbeddingAndClusterAssignment(
            String stableId, String questionText, Long roleId, Runnable afterClusterAssigned) {
        CompletableFuture.runAsync(() ->
                generateEmbeddingAndAssignCluster(stableId, questionText, roleId, afterClusterAssigned));
    }

    private void generateEmbeddingAndAssignCluster(
            String stableId, String questionText, Long roleId, Runnable afterClusterAssigned) {
        try {
            double[] embedding = embeddingService.embed(questionText).block();
            if (embedding == null) {
                logger.warn("Embedding returned null for generated question id={}", stableId);
                return;
            }

            // Assign nearest cluster via cosine similarity. If the role has no
            // clustered question-bank rows yet, use a stable generated fallback
            // cluster so coverage tracking is not blocked by missing seed data.
            Optional<Integer> nearestCluster = findNearestCluster(embedding, roleId);
            int clusterId = nearestCluster.orElseGet(() -> fallbackClusterId(roleId));

            Optional<QuestionEmbedding> rowOpt =
                    embeddingRepository.findByQuestionIdAndRoleId(stableId, roleId);
            rowOpt.ifPresent(row -> {
                row.setEmbeddingVector(embedding);
                row.setClusterId(clusterId);
                if (nearestCluster.isEmpty()) {
                    row.setClusterLabel("Generated questions fallback cluster");
                }
                embeddingRepository.save(row);
                logger.info("Embedding and cluster assigned: id={} clusterId={}", stableId,
                        clusterId);
                if (afterClusterAssigned != null) {
                    afterClusterAssigned.run();
                }
            });
        } catch (Exception e) {
            logger.warn("Async embedding/cluster assignment failed for id={}: {}", stableId, e.getMessage());
        }
    }

    /**
     * Find the nearest cluster for the given embedding vector by cosine similarity
     * against all {@code question_embedding} rows for {@code roleId} that have a
     * non-null {@code cluster_id}.
     *
     * @return the nearest cluster ID, or empty if no cluster candidates exist
     */
    private Optional<Integer> findNearestCluster(double[] queryEmbedding, Long roleId) {
        List<QuestionEmbedding> candidates = embeddingRepository.findByRoleId(roleId)
                .stream()
                .filter(qe -> qe.getClusterId() != null && qe.getEmbedding() != null)
                .toList();

        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        int bestCluster = -1;
        double bestSim = Double.NEGATIVE_INFINITY;

        for (QuestionEmbedding candidate : candidates) {
            double[] candidateVec = candidate.getEmbeddingVector();
            if (candidateVec == null) continue;
            double sim = cosineSimilarity(queryEmbedding, candidateVec);
            if (sim > bestSim) {
                bestSim = sim;
                bestCluster = candidate.getClusterId();
            }
        }

        return bestCluster >= 0 ? Optional.of(bestCluster) : Optional.empty();
    }

    private static int fallbackClusterId(Long roleId) {
        long role = roleId != null ? roleId : 0L;
        long bounded = Math.abs(role % 1_000L);
        return GENERATED_FALLBACK_CLUSTER_BASE + (int) bounded;
    }

    private static double cosineSimilarity(double[] a, double[] b) {
        int len = Math.min(a.length, b.length);
        double dot = 0.0, normA = 0.0, normB = 0.0;
        for (int i = 0; i < len; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0.0 || normB == 0.0) return 0.0;
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
