package com.aiinterview.controller;

import com.aiinterview.service.SkillTrackingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/skills")
@CrossOrigin(origins = "http://localhost:3000")
public class SkillController {

    @Autowired
    private SkillTrackingService skillTrackingService;

    /**
     * Get skill progress for the current user
     */
    @GetMapping("/progress")
    public ResponseEntity<Map<String, Object>> getSkillProgress(@RequestAttribute Long userId) {
        try {
            Map<String, Object> progress = skillTrackingService.getSkillProgress(userId);
            return ResponseEntity.ok(progress);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get skill recommendations for the current user
     */
    @GetMapping("/recommendations")
    public ResponseEntity<Map<String, Object>> getSkillRecommendations(@RequestAttribute Long userId) {
        try {
            Map<String, Object> recommendations = skillTrackingService.getSkillRecommendations(userId);
            return ResponseEntity.ok(recommendations);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get skill improvement trends
     */
    @GetMapping("/trends")
    public ResponseEntity<Map<String, Object>> getSkillTrends(@RequestAttribute Long userId) {
        try {
            Map<String, Object> trends = skillTrackingService.getSkillTrends(userId);
            return ResponseEntity.ok(trends);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }
}
