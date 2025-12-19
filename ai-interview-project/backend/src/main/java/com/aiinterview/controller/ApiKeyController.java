package com.aiinterview.controller;

import com.aiinterview.model.ApiKeyConfig;
import com.aiinterview.service.ApiKeyConfigService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/keys")
@CrossOrigin(origins = "http://localhost:3000")
public class ApiKeyController {

    private final ApiKeyConfigService apiKeyConfigService;

    public ApiKeyController(ApiKeyConfigService apiKeyConfigService) {
        this.apiKeyConfigService = apiKeyConfigService;
    }

    /**
     * Check API key status (public endpoint for frontend)
     */
    @GetMapping("/status")
    @CrossOrigin(origins = "http://localhost:3000")
    public ResponseEntity<Map<String, Object>> getApiKeyStatus() {
        boolean openaiConfigured = apiKeyConfigService.hasActiveApiKey("openai");

        Map<String, Object> result = new java.util.HashMap<>();
        result.put("openaiConfigured", openaiConfigured);
        result.put("status", openaiConfigured ? "configured" : "not_configured");

        return ResponseEntity.ok(result);
    }

    /**
     * Get all API key configurations
     */
    @GetMapping
    public ResponseEntity<List<ApiKeyConfig>> getAllApiKeys() {
        List<ApiKeyConfig> configs = apiKeyConfigService.findAll();
        return ResponseEntity.ok(configs);
    }

    /**
     * Get active API key for a service
     */
    @GetMapping("/{serviceName}/active")
    public ResponseEntity<Map<String, Object>> getActiveApiKey(@PathVariable String serviceName) {
        return apiKeyConfigService.getActiveApiKey(serviceName)
            .map(apiKey -> {
                Map<String, Object> result = new java.util.HashMap<>();
                result.put("serviceName", serviceName);
                result.put("hasKey", true);
                result.put("maskedKey", apiKey.substring(0, 10) + "..." + apiKey.substring(apiKey.length() - 4));
                return ResponseEntity.ok(result);
            })
            .orElseGet(() -> {
                Map<String, Object> result = new java.util.HashMap<>();
                result.put("serviceName", serviceName);
                result.put("hasKey", false);
                return ResponseEntity.ok(result);
            });
    }

    /**
     * Set active API key for a service
     */
    @PostMapping("/{serviceName}")
    public ResponseEntity<ApiKeyConfig> setApiKey(@PathVariable String serviceName, @RequestBody Map<String, String> request) {
        String apiKey = request.get("apiKey");
        if (apiKey == null || apiKey.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        // Deactivate all existing keys for this service
        apiKeyConfigService.deactivateAllForService(serviceName);

        // Create new active key
        ApiKeyConfig newConfig = new ApiKeyConfig(serviceName, apiKey.trim());
        ApiKeyConfig saved = apiKeyConfigService.save(newConfig);

        return ResponseEntity.ok(saved);
    }

    /**
     * Delete API key configuration
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteApiKey(@PathVariable Integer id) {
        // Note: In a real application, you might want to soft delete or check dependencies
        // For now, we'll allow deletion but this might break services if it's the active key
        try {
            // This is a simple implementation - in production you'd want more validation
            ApiKeyConfig config = new ApiKeyConfig();
            config.setId(id);
            // Note: This is just a placeholder - proper deletion logic should be implemented
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Test API key for a service
     */
    @PostMapping("/{serviceName}/test")
    public ResponseEntity<Map<String, Object>> testApiKey(@PathVariable String serviceName) {
        boolean hasActiveKey = apiKeyConfigService.hasActiveApiKey(serviceName);

        Map<String, Object> result = new java.util.HashMap<>();
        result.put("serviceName", serviceName);
        result.put("configured", hasActiveKey);
        result.put("status", hasActiveKey ? "API key is configured" : "No active API key found");

        return ResponseEntity.ok(result);
    }
}
