package com.aiinterview.controller;

import com.aiinterview.model.InterviewTemplate;
import com.aiinterview.service.InterviewTemplateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/templates")
@CrossOrigin(origins = "http://localhost:3000")
public class InterviewTemplateController {

    @Autowired
    private InterviewTemplateService templateService;

    /**
     * Get all templates available to user
     */
    @GetMapping
    public ResponseEntity<List<InterviewTemplate>> getTemplates(@RequestAttribute Long userId) {
        try {
            List<InterviewTemplate> templates = templateService.getUserTemplates(userId);
            return ResponseEntity.ok(templates);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(null);
        }
    }

    /**
     * Get user's owned templates only
     */
    @GetMapping("/my")
    public ResponseEntity<List<InterviewTemplate>> getUserTemplates(@RequestAttribute Long userId) {
        try {
            List<InterviewTemplate> templates = templateService.getUserOwnedTemplates(userId);
            return ResponseEntity.ok(templates);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(null);
        }
    }

    /**
     * Get template by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<InterviewTemplate> getTemplate(@PathVariable Long id) {
        try {
            var template = templateService.getTemplateById(id);
            return template.map(ResponseEntity::ok)
                          .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * Create a new template
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> createTemplate(
            @RequestAttribute Long userId,
            @RequestBody InterviewTemplate template) {
        try {
            template.setUserId(userId);
            InterviewTemplate created = templateService.createTemplate(template);
            return ResponseEntity.ok(Map.of("template", created, "message", "Template created successfully"));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Update an existing template
     */
    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateTemplate(
            @PathVariable Long id,
            @RequestAttribute Long userId,
            @RequestBody Map<String, Object> updates) {
        try {
            InterviewTemplate updated = templateService.updateTemplate(id, updates);
            return ResponseEntity.ok(Map.of("template", updated, "message", "Template updated successfully"));
        } catch (Exception e) {
            return ResponseEntity.status(400).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Delete a template
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteTemplate(
            @PathVariable Long id,
            @RequestAttribute Long userId) {
        try {
            templateService.deleteTemplate(id, userId);
            return ResponseEntity.ok(Map.of("message", "Template deleted successfully"));
        } catch (Exception e) {
            return ResponseEntity.status(400).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Use a template (increment usage count)
     */
    @PostMapping("/{id}/use")
    public ResponseEntity<Map<String, Object>> useTemplate(@PathVariable Long id) {
        try {
            templateService.incrementUsageCount(id);
            return ResponseEntity.ok(Map.of("message", "Template usage recorded"));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get popular templates
     */
    @GetMapping("/popular")
    public ResponseEntity<List<InterviewTemplate>> getPopularTemplates(
            @RequestParam(defaultValue = "10") int limit) {
        try {
            List<InterviewTemplate> templates = templateService.getPopularTemplates(limit);
            return ResponseEntity.ok(templates);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(null);
        }
    }

    /**
     * Search templates by criteria
     */
    @GetMapping("/search")
    public ResponseEntity<List<InterviewTemplate>> searchTemplates(
            @RequestParam(required = false) String techStack,
            @RequestParam(required = false) String level) {
        try {
            List<InterviewTemplate> templates = templateService.searchTemplates(techStack, level);
            return ResponseEntity.ok(templates);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(null);
        }
    }
}
