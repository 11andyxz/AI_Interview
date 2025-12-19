package com.aiinterview.controller;

import com.aiinterview.service.UserProfileService;
import com.aiinterview.service.UserPreferencesService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/user")
@CrossOrigin(origins = "http://localhost:3000")
public class UserController {
    
    @Autowired
    private UserProfileService userProfileService;

    @Autowired
    private UserPreferencesService userPreferencesService;
    
    /**
     * Get user profile
     */
    @GetMapping("/profile")
    public ResponseEntity<Map<String, Object>> getUserProfile(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }
        
        try {
            Map<String, Object> profile = userProfileService.getUserProfile(userId);
            return ResponseEntity.ok(profile);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }
    
    /**
     * Update user profile
     */
    @PutMapping("/profile")
    public ResponseEntity<Map<String, Object>> updateUserProfile(
            HttpServletRequest request,
            @RequestBody Map<String, Object> updates) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }
        
        try {
            var updatedUser = userProfileService.updateUserProfile(userId, updates);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("user", updatedUser);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(400).body(Map.of("error", e.getMessage()));
        }
    }
    
    /**
     * Get user points
     */
    @GetMapping("/points")
    public ResponseEntity<Map<String, Object>> getUserPoints(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }
        
        try {
            Integer points = userProfileService.getUserPoints(userId);
            Map<String, Object> response = new HashMap<>();
            response.put("points", points);
            response.put("isUnlimited", points != null && points == Integer.MAX_VALUE);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }
    
    /**
     * Get subscription status
     */
    @GetMapping("/subscription-status")
    public ResponseEntity<Map<String, Object>> getSubscriptionStatus(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }

        try {
            Map<String, Object> status = userProfileService.getSubscriptionStatus(userId);
            return ResponseEntity.ok(status);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get user statistics for dashboard
     */
    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> getUserStatistics(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }

        try {
            Map<String, Object> statistics = userProfileService.getUserStatistics(userId);
            return ResponseEntity.ok(statistics);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get user preferences
     */
    @GetMapping("/preferences")
    public ResponseEntity<Map<String, Object>> getUserPreferences(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }

        try {
            var preferences = userPreferencesService.getUserPreferences(userId);
            return ResponseEntity.ok(Map.of("preferences", preferences));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Update user preferences
     */
    @PutMapping("/preferences")
    public ResponseEntity<Map<String, Object>> updateUserPreferences(
            HttpServletRequest request,
            @RequestBody Map<String, Object> updates) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }

        try {
            var preferences = userPreferencesService.updateUserPreferences(userId, updates);
            return ResponseEntity.ok(Map.of("preferences", preferences, "message", "Preferences updated successfully"));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Reset user preferences to defaults
     */
    @PostMapping("/preferences/reset")
    public ResponseEntity<Map<String, Object>> resetUserPreferences(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }

        try {
            var preferences = userPreferencesService.resetUserPreferences(userId);
            return ResponseEntity.ok(Map.of("preferences", preferences, "message", "Preferences reset to defaults"));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }
}

