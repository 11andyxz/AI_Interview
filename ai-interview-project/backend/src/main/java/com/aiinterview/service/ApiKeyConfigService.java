package com.aiinterview.service;

import com.aiinterview.model.ApiKeyConfig;
import com.aiinterview.repository.ApiKeyConfigRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class ApiKeyConfigService {

    @Autowired
    private ApiKeyConfigRepository apiKeyConfigRepository;

    /**
     * Get active API key for a service
     */
    public Optional<String> getActiveApiKey(String serviceName) {
        return apiKeyConfigRepository.findByServiceNameAndIsActive(serviceName, true)
            .map(ApiKeyConfig::getApiKey);
    }

    /**
     * Get active API key config for a service
     */
    public Optional<ApiKeyConfig> getActiveApiKeyConfig(String serviceName) {
        return apiKeyConfigRepository.findByServiceNameAndIsActive(serviceName, true);
    }

    /**
     * Check if active API key exists for a service
     */
    public boolean hasActiveApiKey(String serviceName) {
        return apiKeyConfigRepository.existsByServiceNameAndIsActive(serviceName, true);
    }

    /**
     * Save or update API key config
     */
    public ApiKeyConfig save(ApiKeyConfig apiKeyConfig) {
        return apiKeyConfigRepository.save(apiKeyConfig);
    }

    /**
     * Get all API key configs
     */
    public List<ApiKeyConfig> findAll() {
        return apiKeyConfigRepository.findAll();
    }

    /**
     * Deactivate all API keys for a service (before setting a new active one)
     */
    public void deactivateAllForService(String serviceName) {
        List<ApiKeyConfig> configs = apiKeyConfigRepository.findAll();
        configs.stream()
            .filter(config -> serviceName.equals(config.getServiceName()) && config.getIsActive())
            .forEach(config -> {
                config.setIsActive(false);
                apiKeyConfigRepository.save(config);
            });
    }
}
