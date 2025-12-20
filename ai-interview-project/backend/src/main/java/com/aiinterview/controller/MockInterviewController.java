package com.aiinterview.controller;

import com.aiinterview.model.MockInterview;
import com.aiinterview.model.MockInterviewMessage;
import com.aiinterview.service.MockInterviewService;
import com.aiinterview.service.ResumeService;
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

    @Autowired
    private ResumeService resumeService;
    
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
            String interviewType = (String) requestBody.getOrDefault("interviewType", "general");

            // Validate interview type
            if (!"general".equals(interviewType) && !"resume-based".equals(interviewType)) {
                return ResponseEntity.badRequest().body(Map.of("error", "Invalid interview type. Must be 'general' or 'resume-based'"));
            }

            MockInterview mockInterview;

            if ("resume-based".equals(interviewType)) {
                // For resume-based mock interviews
                Long resumeId = null;
                if (requestBody.get("resumeId") instanceof Number) {
                    resumeId = ((Number) requestBody.get("resumeId")).longValue();
                } else if (requestBody.get("resumeId") instanceof String) {
                    resumeId = Long.parseLong((String) requestBody.get("resumeId"));
                }

                if (resumeId == null) {
                    return ResponseEntity.badRequest().body(Map.of("error", "resumeId is required for resume-based mock interviews"));
                }

                // Check if resume exists and is analyzed
                var resumeOpt = resumeService.getResumeById(resumeId, userId);
                if (resumeOpt.isEmpty()) {
                    return ResponseEntity.badRequest().body(Map.of("error", "Resume not found or access denied"));
                }

                if (!Boolean.TRUE.equals(resumeOpt.get().getAnalyzed())) {
                    return ResponseEntity.badRequest().body(Map.of("error", "Resume must be analyzed before creating resume-based mock interview"));
                }

                String language = (String) requestBody.getOrDefault("language", "English");
                mockInterview = mockInterviewService.createMockInterviewFromResume(userId, resumeId, language);

            } else {
                // For general mock interviews (backward compatibility)
                String title = (String) requestBody.get("title");
                String positionType = (String) requestBody.get("positionType");
                String programmingLanguages = (String) requestBody.get("programmingLanguages");
                String language = (String) requestBody.getOrDefault("language", "English");

                mockInterview = mockInterviewService.createMockInterview(
                    userId, title, positionType, programmingLanguages, language);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("mockInterview", mockInterview);

            // Add resume analysis data for resume-based interviews
            if ("resume-based".equals(interviewType) && mockInterview.getResumeId() != null) {
                var analysisOpt = resumeService.getResumeAnalysisData(mockInterview.getResumeId(), userId);
                if (analysisOpt.isPresent()) {
                    response.put("resumeAnalysis", analysisOpt.get());
                }
            }

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

