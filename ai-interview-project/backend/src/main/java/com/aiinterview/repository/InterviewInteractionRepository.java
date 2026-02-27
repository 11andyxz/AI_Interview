package com.aiinterview.repository;

import com.aiinterview.model.InterviewMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for InterviewMessage entities - used for training data collection
 * Queries interview_message table for training data from production interviews
 */
@Repository
public interface InterviewInteractionRepository extends JpaRepository<InterviewMessage, Long> {
    
    /**
     * Find interactions within date range with minimum quality score
     */
    @Query("SELECT i FROM InterviewMessage i WHERE " +
           "i.createdAt >= :startDate AND i.createdAt <= :endDate AND " +
           "i.evaluationScore >= :minScore")
    List<InterviewMessage> findByDateRangeAndMinScore(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate,
        @Param("minScore") Double minScore
    );
    
    /**
     * Find interactions by created_at range
     */
    @Query("SELECT i FROM InterviewMessage i WHERE " +
           "i.createdAt >= :startDate AND i.createdAt <= :endDate")
    List<InterviewMessage> findByCreatedAtBetween(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );
    
    /**
     * Find validated interactions (those with evaluation scores)
     */
    @Query("SELECT i FROM InterviewMessage i WHERE " +
           "i.evaluationScore IS NOT NULL AND i.evaluationScore > 0")
    List<InterviewMessage> findValidatedInteractions();
    
    /**
     * Find interactions by interview ID
     */
    List<InterviewMessage> findByInterviewId(String interviewId);
    
    /**
     * Count interactions with quality score above threshold
     */
    @Query("SELECT COUNT(i) FROM InterviewMessage i WHERE i.evaluationScore >= :threshold")
    long countHighQualityInteractions(@Param("threshold") Double threshold);
}
