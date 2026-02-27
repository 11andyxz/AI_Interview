package com.aiinterview.ml.gateway;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for prompt version management
 */
@Repository
public interface PromptVersionRepository extends JpaRepository<PromptVersion, Long> {
    
    /**
     * Find prompt by key and version
     */
    Optional<PromptVersion> findByPromptKeyAndVersion(String promptKey, String version);
    
    /**
     * Find active prompt by key
     */
    Optional<PromptVersion> findByPromptKeyAndIsActive(String promptKey, Boolean isActive);
    
    /**
     * Find all versions for a prompt key
     */
    List<PromptVersion> findByPromptKey(String promptKey);
}
