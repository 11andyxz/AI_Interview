package com.aiinterview.ml.gateway.repository;

import com.aiinterview.ml.gateway.model.PromptVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PromptVersionRepository extends JpaRepository<PromptVersion, Long> {
    Optional<PromptVersion> findByPromptKeyAndIsActiveTrue(String promptKey);
    Optional<PromptVersion> findByPromptKeyAndVersion(String promptKey, String version);
    List<PromptVersion> findByPromptKeyOrderByCreatedAtDesc(String promptKey);
    List<PromptVersion> findByIsActiveTrue();
}
