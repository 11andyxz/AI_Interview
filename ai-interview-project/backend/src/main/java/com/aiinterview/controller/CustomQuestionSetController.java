package com.aiinterview.controller;

import com.aiinterview.model.CustomQuestionSet;
import com.aiinterview.service.CustomQuestionSetService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/question-sets")
@CrossOrigin(origins = "http://localhost:3000")
public class CustomQuestionSetController {

    @Autowired
    private CustomQuestionSetService questionSetService;

    /**
     * Get all question sets available to user
     */
    @GetMapping
    public ResponseEntity<List<CustomQuestionSet>> getQuestionSets(@RequestAttribute Long userId) {
        try {
            List<CustomQuestionSet> questionSets = questionSetService.getUserQuestionSets(userId);
            return ResponseEntity.ok(questionSets);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(null);
        }
    }

    /**
     * Get user's owned question sets only
     */
    @GetMapping("/my")
    public ResponseEntity<List<CustomQuestionSet>> getUserQuestionSets(@RequestAttribute Long userId) {
        try {
            List<CustomQuestionSet> questionSets = questionSetService.getUserOwnedQuestionSets(userId);
            return ResponseEntity.ok(questionSets);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(null);
        }
    }

    /**
     * Get question set by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<CustomQuestionSet> getQuestionSet(@PathVariable Long id) {
        try {
            var questionSet = questionSetService.getQuestionSetById(id);
            return questionSet.map(ResponseEntity::ok)
                             .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * Create a new question set
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> createQuestionSet(
            @RequestAttribute Long userId,
            @RequestBody CustomQuestionSet questionSet) {
        try {
            questionSet.setUserId(userId);
            CustomQuestionSet created = questionSetService.createQuestionSet(questionSet);
            return ResponseEntity.ok(Map.of("questionSet", created, "message", "Question set created successfully"));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Update an existing question set
     */
    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateQuestionSet(
            @PathVariable Long id,
            @RequestAttribute Long userId,
            @RequestBody Map<String, Object> updates) {
        try {
            CustomQuestionSet updated = questionSetService.updateQuestionSet(id, updates);
            return ResponseEntity.ok(Map.of("questionSet", updated, "message", "Question set updated successfully"));
        } catch (Exception e) {
            return ResponseEntity.status(400).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Delete a question set
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteQuestionSet(
            @PathVariable Long id,
            @RequestAttribute Long userId) {
        try {
            questionSetService.deleteQuestionSet(id, userId);
            return ResponseEntity.ok(Map.of("message", "Question set deleted successfully"));
        } catch (Exception e) {
            return ResponseEntity.status(400).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Use a question set (increment usage count)
     */
    @PostMapping("/{id}/use")
    public ResponseEntity<Map<String, Object>> useQuestionSet(@PathVariable Long id) {
        try {
            questionSetService.incrementUsageCount(id);
            return ResponseEntity.ok(Map.of("message", "Question set usage recorded"));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Add questions to an existing question set
     */
    @PostMapping("/{id}/questions")
    public ResponseEntity<Map<String, Object>> addQuestionsToSet(
            @PathVariable Long id,
            @RequestBody Map<String, Object> request) {
        try {
            @SuppressWarnings("unchecked")
            List<String> questions = (List<String>) request.get("questions");
            CustomQuestionSet updated = questionSetService.addQuestionsToSet(id, questions);
            return ResponseEntity.ok(Map.of("questionSet", updated, "message", "Questions added successfully"));
        } catch (Exception e) {
            return ResponseEntity.status(400).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Remove questions from a question set
     */
    @DeleteMapping("/{id}/questions")
    public ResponseEntity<Map<String, Object>> removeQuestionsFromSet(
            @PathVariable Long id,
            @RequestBody Map<String, Object> request) {
        try {
            @SuppressWarnings("unchecked")
            List<String> questions = (List<String>) request.get("questions");
            CustomQuestionSet updated = questionSetService.removeQuestionsFromSet(id, questions);
            return ResponseEntity.ok(Map.of("questionSet", updated, "message", "Questions removed successfully"));
        } catch (Exception e) {
            return ResponseEntity.status(400).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get popular question sets
     */
    @GetMapping("/popular")
    public ResponseEntity<List<CustomQuestionSet>> getPopularQuestionSets(
            @RequestParam(defaultValue = "10") int limit) {
        try {
            List<CustomQuestionSet> questionSets = questionSetService.getPopularQuestionSets(limit);
            return ResponseEntity.ok(questionSets);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(null);
        }
    }

    /**
     * Search question sets by criteria
     */
    @GetMapping("/search")
    public ResponseEntity<List<CustomQuestionSet>> searchQuestionSets(
            @RequestParam(required = false) String techStack,
            @RequestParam(required = false) String level) {
        try {
            List<CustomQuestionSet> questionSets = questionSetService.searchQuestionSets(techStack, level);
            return ResponseEntity.ok(questionSets);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(null);
        }
    }
}
