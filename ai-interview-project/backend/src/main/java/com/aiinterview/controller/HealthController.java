package com.aiinterview.controller;

import com.aiinterview.repository.UserRepository;
import com.aiinterview.service.ApiKeyConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/health")
public class HealthController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ApiKeyConfigService apiKeyConfigService;

    @GetMapping("/db")
    public ResponseEntity<Map<String, Object>> checkDatabase() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            long userCount = userRepository.count();
            response.put("status", "connected");
            response.put("database", "ai_interview");
            response.put("userCount", userCount);
            response.put("message", "Database connection successful");
            
            // Try to find test user
            boolean testUserExists = userRepository.existsByUsername("test");
            response.put("testUserExists", testUserExists);

            // Check API key configuration
            boolean openaiConfigured = apiKeyConfigService.hasActiveApiKey("openai");
            response.put("openaiConfigured", openaiConfigured);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "Database connection failed: " + e.getMessage());
            response.put("error", e.getClass().getSimpleName());
            return ResponseEntity.status(500).body(response);
        }
    }
}

