package com.aiinterview.ml.nlp.repository;

import com.aiinterview.ml.nlp.entity.ResponseFeatureCache;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ResponseFeatureCacheRepository extends JpaRepository<ResponseFeatureCache, Long> {
    
    /**
     * Find cache entry by session and question
     */
    Optional<ResponseFeatureCache> findBySessionIdAndQuestionId(String sessionId, String questionId);
    
    /**
     * Find all cache entries for a session
     */
    List<ResponseFeatureCache> findBySessionId(String sessionId);
    
    /**
     * Find all entries with ground truth LLM scores for training
     */
    @Query("SELECT rfc FROM ResponseFeatureCache rfc WHERE rfc.llmScore IS NOT NULL")
    List<ResponseFeatureCache> findAllWithLlmScores();
    
    /**
     * Find entries with ground truth scores created after a specific date
     */
    @Query("SELECT rfc FROM ResponseFeatureCache rfc WHERE rfc.llmScore IS NOT NULL AND rfc.createdAt >= :since")
    List<ResponseFeatureCache> findNewTrainingData(@Param("since") LocalDateTime since);
    
    /**
     * Count entries with prediction errors above threshold (for monitoring)
     */
    @Query("SELECT COUNT(rfc) FROM ResponseFeatureCache rfc WHERE rfc.predictionError IS NOT NULL AND rfc.predictionError > :threshold")
    long countHighErrorPredictions(@Param("threshold") Double threshold);
    
    /**
     * Get average prediction error for a specific model version
     */
    @Query("SELECT AVG(rfc.predictionError) FROM ResponseFeatureCache rfc WHERE rfc.modelVersion = :version AND rfc.predictionError IS NOT NULL")
    Double getAveragePredictionError(@Param("version") String version);
    
    /**
     * Delete old cache entries (for cleanup)
     */
    void deleteByCreatedAtBefore(LocalDateTime before);
}
