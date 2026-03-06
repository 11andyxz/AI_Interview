package com.aiinterview.ml.embedding.repository;

import com.aiinterview.ml.embedding.entity.TopicCoverage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TopicCoverageRepository extends JpaRepository<TopicCoverage, Long> {
    
    /**
     * Find topic coverage by session ID and role ID
     */
    Optional<TopicCoverage> findBySessionIdAndRoleId(String sessionId, Long roleId);
    
    /**
     * Check if coverage exists for a session
     */
    boolean existsBySessionIdAndRoleId(String sessionId, Long roleId);
}
