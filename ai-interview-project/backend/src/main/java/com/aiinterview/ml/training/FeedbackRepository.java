package com.aiinterview.ml.training;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for human feedback on AI responses
 */
@Repository
public interface FeedbackRepository extends JpaRepository<FeedbackRecord, Long> {
    
    List<FeedbackRecord> findByInteractionId(Long interactionId);
    
    List<FeedbackRecord> findByConversationId(String conversationId);
    
    List<FeedbackRecord> findByFeedbackType(String feedbackType);
    
    @Query("SELECT f FROM FeedbackRecord f WHERE f.createdAt BETWEEN :from AND :to")
    List<FeedbackRecord> findByDateRange(@Param("from") LocalDateTime from, 
                                         @Param("to") LocalDateTime to);
    
    @Query("SELECT COUNT(f) FROM FeedbackRecord f WHERE f.feedbackType = :type AND f.createdAt >= :since")
    Long countByTypeAndSince(@Param("type") String type, @Param("since") LocalDateTime since);
}
