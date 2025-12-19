package com.aiinterview.controller;

import com.aiinterview.model.MockInterview;
import com.aiinterview.model.MockInterviewMessage;
import com.aiinterview.service.MockInterviewService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/mock-interviews")
@CrossOrigin(origins = "http://localhost:3000")
public class MockInterviewController {
    
    @Autowired
    private MockInterviewService mockInterviewService;
    
    /**
     * Get all mock interviews for user
     */
    @GetMapping
    public ResponseEntity<List<MockInterview>> getMockInterviews(
            HttpServletRequest request,
            @RequestParam(required = false) String status) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }
        
        List<MockInterview> interviews = mockInterviewService.getUserMockInterviews(userId, status);
        return ResponseEntity.ok(interviews);
    }
    
    /**
     * Get mock interview by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getMockInterviewById(
            HttpServletRequest request,
            @PathVariable String id) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }
        
        return mockInterviewService.getMockInterviewById(id, userId)
            .map(mockInterview -> {
                Map<String, Object> response = new HashMap<>();
                response.put("mockInterview", mockInterview);
                List<MockInterviewMessage> messages = mockInterviewService.getMockInterviewMessages(id);
                response.put("messages", messages);
                return ResponseEntity.ok(response);
            })
            .orElseGet(() -> ResponseEntity.notFound().build());
    }
    
    /**
     * Create a new mock interview
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> createMockInterview(
            HttpServletRequest request,
            @RequestBody Map<String, Object> requestBody) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }
        
        try {
            String title = (String) requestBody.get("title");
            String positionType = (String) requestBody.get("positionType");
            String programmingLanguages = (String) requestBody.get("programmingLanguages");
            String language = (String) requestBody.getOrDefault("language", "English");
            
            MockInterview mockInterview = mockInterviewService.createMockInterview(
                userId, title, positionType, programmingLanguages, language);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("mockInterview", mockInterview);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }
    
    /**
     * Retry mock interview
     */
    @PostMapping("/{id}/retry")
    public ResponseEntity<Map<String, Object>> retryMockInterview(
            HttpServletRequest request,
            @PathVariable String id) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }
        
        try {
            MockInterview mockInterview = mockInterviewService.retryMockInterview(id, userId);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("mockInterview", mockInterview);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(400).body(Map.of("error", e.getMessage()));
        }
    }
    
    /**
     * Get hints for a question (practice mode)
     */
    @GetMapping("/{id}/hints")
    public ResponseEntity<Map<String, Object>> getHints(
            HttpServletRequest request,
            @PathVariable String id,
            @RequestParam(required = false) Long questionIndex) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }
        
        try {
            String hint = mockInterviewService.getHint(id, questionIndex != null ? questionIndex : 0L);
            Map<String, Object> response = new HashMap<>();
            response.put("hint", hint);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }
}

