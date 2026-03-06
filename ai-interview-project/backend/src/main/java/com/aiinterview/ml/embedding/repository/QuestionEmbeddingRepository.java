package com.aiinterview.ml.embedding.repository;

import com.aiinterview.ml.embedding.entity.QuestionEmbedding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface QuestionEmbeddingRepository extends JpaRepository<QuestionEmbedding, Long> {
    
    /**
     * Find embedding by question ID and role ID
     */
    Optional<QuestionEmbedding> findByQuestionIdAndRoleId(String questionId, Long roleId);
    
    /**
     * Find all distinct cluster IDs for a role
     */
    @Query("SELECT DISTINCT qe.clusterId FROM QuestionEmbedding qe WHERE qe.roleId = :roleId AND qe.clusterId IS NOT NULL")
    List<Integer> findDistinctClusterIdsByRoleId(@Param("roleId") Long roleId);
    
    /**
     * Count questions in a specific cluster
     */
    long countByRoleIdAndClusterId(Long roleId, Integer clusterId);
    
    /**
     * Find all embeddings by role ID
     */
    List<QuestionEmbedding> findByRoleId(Long roleId);
    
    /**
     * Find embeddings by question IDs and role ID (for batch operations)
     */
    List<QuestionEmbedding> findByQuestionIdInAndRoleId(List<String> questionIds, Long roleId);
    
    /**
     * Find all embeddings for a specific cluster
     */
    List<QuestionEmbedding> findByRoleIdAndClusterId(Long roleId, Integer clusterId);
}
