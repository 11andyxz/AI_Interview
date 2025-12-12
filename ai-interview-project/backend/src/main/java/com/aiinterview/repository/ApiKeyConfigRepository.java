package com.aiinterview.repository;

import com.aiinterview.model.ApiKeyConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ApiKeyConfigRepository extends JpaRepository<ApiKeyConfig, Integer> {

    /**
     * Find active API key by service name
     */
    Optional<ApiKeyConfig> findByServiceNameAndIsActive(String serviceName, Boolean isActive);

    /**
     * Check if active API key exists for service
     */
    boolean existsByServiceNameAndIsActive(String serviceName, Boolean isActive);
}
