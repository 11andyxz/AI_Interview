package com.aiinterview.ml.prediction.repository;

import com.aiinterview.ml.prediction.entity.CandidateSkillProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CandidateSkillProfileRepository extends JpaRepository<CandidateSkillProfile, Long> {
    
    /**
     * Find profile by session ID
     */
    Optional<CandidateSkillProfile> findBySessionId(String sessionId);
    
    /**
     * Find all profiles for a user
     */
    List<CandidateSkillProfile> findByUserId(String userId);
    
    /**
     * Find profiles by user and role
     */
    List<CandidateSkillProfile> findByUserIdAndRoleId(String userId, Long roleId);
    
    /**
     * Find all early stopped sessions
     */
    @Query("SELECT p FROM CandidateSkillProfile p WHERE p.earlyStoppingTriggered = true")
    List<CandidateSkillProfile> findAllEarlyStopped();
    
    /**
     * Find early stopped sessions by reason
     */
    List<CandidateSkillProfile> findByEarlyStoppingReason(String reason);
    
    /**
     * Count early stopping occurrences
     */
    @Query("SELECT COUNT(p) FROM CandidateSkillProfile p WHERE p.earlyStoppingTriggered = true")
    long countEarlyStopped();
    
    /**
     * Get average pass probability for a user
     */
    @Query("SELECT AVG(p.passProbability) FROM CandidateSkillProfile p WHERE p.userId = :userId AND p.passProbability IS NOT NULL")
    Double getAveragePassProbability(@Param("userId") String userId);
}
