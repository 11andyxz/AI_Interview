package com.aiinterview.controller;

import com.aiinterview.dto.ChatRequest;
import com.aiinterview.dto.CreateInterviewRequest;
import com.aiinterview.dto.QAHistory;
import com.aiinterview.model.Candidate;
import com.aiinterview.model.Interview;
import com.aiinterview.repository.InterviewRepository;
import com.aiinterview.service.AiService;
import com.aiinterview.service.CandidateService;
import com.aiinterview.service.InterviewService;
import com.aiinterview.service.InterviewSessionService;
import com.aiinterview.service.LlmEvaluationService;
import com.aiinterview.service.PdfReportService;
import com.aiinterview.service.ReportService;
import com.aiinterview.service.AudioService;
import com.aiinterview.service.ResumeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;

import java.io.IOException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import reactor.core.publisher.Mono;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/interviews")
public class InterviewController {

    private final AiService aiService;
    private final InterviewRepository interviewRepository;
    private final InterviewService interviewService;
    private final CandidateService candidateService;
    private final InterviewSessionService interviewSessionService;
    private final ReportService reportService;
    private final PdfReportService pdfReportService;
    private final LlmEvaluationService llmEvaluationService;
    private final AudioService audioService;
    private final ResumeService resumeService;
    private final ObjectMapper objectMapper;

    public InterviewController(AiService aiService,
                               InterviewRepository interviewRepository,
                               InterviewService interviewService,
                               CandidateService candidateService,
                               InterviewSessionService interviewSessionService,
                               ReportService reportService,
                               PdfReportService pdfReportService,
                               LlmEvaluationService llmEvaluationService,
                               AudioService audioService,
                               ResumeService resumeService,
                               ObjectMapper objectMapper) {
        this.aiService = aiService;
        this.interviewRepository = interviewRepository;
        this.interviewService = interviewService;
        this.candidateService = candidateService;
        this.interviewSessionService = interviewSessionService;
        this.reportService = reportService;
        this.pdfReportService = pdfReportService;
        this.llmEvaluationService = llmEvaluationService;
        this.audioService = audioService;
        this.resumeService = resumeService;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public ResponseEntity<?> getAllInterviews(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }

        List<Interview> interviews = interviewService.getInterviewsByUserId(userId);
        return ResponseEntity.ok(interviews);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Interview> getInterviewById(@PathVariable String id, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }

        Optional<Interview> interview = interviewService.getInterviewByIdAndUserId(id, userId);
        if (interview.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(interview.get());
    }

    @PostMapping
    public ResponseEntity<?> createInterview(@RequestBody CreateInterviewRequest request, HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }

        // Validate interview type
        String interviewType = request.getInterviewType() != null ? request.getInterviewType() : "general";
        if (!"general".equals(interviewType) && !"resume-based".equals(interviewType)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid interview type. Must be 'general' or 'resume-based'"));
        }

        // For resume-based interviews, validate resume exists and is analyzed
        if ("resume-based".equals(interviewType)) {
            if (request.getResumeId() == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "resumeId is required for resume-based interviews"));
            }

            // Check if resume exists and belongs to user
            var resumeOpt = resumeService.getResumeById(request.getResumeId(), userId);
            if (resumeOpt.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Resume not found or access denied"));
            }

            // Check if resume is analyzed
            var resume = resumeOpt.get();
            if (!Boolean.TRUE.equals(resume.getAnalyzed())) {
                return ResponseEntity.badRequest().body(Map.of("error", "Resume must be analyzed before creating resume-based interview"));
            }
        } else {
            // For general interviews, candidateId is still required
            if (request.getCandidateId() == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "candidateId is required for general interviews"));
            }
        }

        // Set default values if not provided
        if (request.getLanguage() == null) {
            request.setLanguage("English");
        }

        try {
            // Create interview using service
            Interview saved = interviewService.createInterview(request, userId);

            Map<String, Object> response = new HashMap<>();
            response.put("interview", saved);

            // Build knowledge base based on interview type
            if ("resume-based".equals(interviewType) && request.getResumeId() != null) {
                // For resume-based interviews, build knowledge base from resume analysis
                var analysisOpt = resumeService.getResumeAnalysisData(request.getResumeId(), userId);
                if (analysisOpt.isPresent()) {
                    Map<String, Object> knowledgeBase = Map.of(
                        "type", "resume-based",
                        "resumeAnalysis", analysisOpt.get(),
                        "techStack", analysisOpt.get().getTechStack(),
                        "experienceLevel", analysisOpt.get().getLevel()
                    );
                    response.put("knowledgeBase", knowledgeBase);
                }
            } else {
                // For general interviews, use candidate-based knowledge base
                Optional<Candidate> candidateOpt = candidateService.findById(request.getCandidateId());
                if (candidateOpt.isPresent()) {
                    Map<String, Object> knowledgeBase = candidateService.buildKnowledgeBase(
                        candidateOpt.get(),
                        request.getPositionType(),
                        request.getProgrammingLanguages(),
                        request.getLanguage()
                    );
                    response.put("knowledgeBase", knowledgeBase);
                }
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Failed to create interview: " + e.getMessage()));
        }
    }

    @PostMapping("/start")
    public Interview startAiInterview(@RequestBody String jobRole) {
        // Call AI Service to prepare session context (simulated)
        // In a real app, we would save these questions to the database linked to the interview ID
        List<String> questions = aiService.generateInterviewQuestions(jobRole);
        System.out.println("Generated questions for " + jobRole + ": " + questions);
        
        // Return a new interview object
        Interview newInterview = new Interview(
            UUID.randomUUID().toString(),
            jobRole != null && !jobRole.isEmpty() ? jobRole : "New AI Interview",
            "English",
            "React, Java, Spring", // This could also be dynamic based on role
            LocalDate.now(),
            "In Progress"
        );
        
        return newInterview;
    }
    
    @PostMapping("/{id}/chat")
    public ResponseEntity<?> chatWithAi(@PathVariable String id, @RequestBody ChatRequest request, HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }

        ResponseEntity<?> ownershipCheck = checkInterviewOwnership(id, userId);
        if (ownershipCheck != null) {
            return ownershipCheck;
        }

        // Use intelligent chat with candidate context and conversation history
        return ResponseEntity.ok(interviewSessionService.generatePersonalizedResponse(id, request));
    }

    /**
     * Check if user owns the interview
     */
    private ResponseEntity<?> checkInterviewOwnership(String interviewId, Long userId) {
        if (!interviewService.isInterviewOwnedByUser(interviewId, userId)) {
            return ResponseEntity.status(403).body(Map.of("error", "Access denied"));
        }

        return null; // null means ownership check passed
    }

    @GetMapping("/{id}/session")
    public ResponseEntity<?> getInterviewSession(@PathVariable String id, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }

        ResponseEntity<?> ownershipCheck = checkInterviewOwnership(id, userId);
        if (ownershipCheck != null) {
            return ownershipCheck;
        }

        Optional<Map<String, Object>> session = interviewSessionService.getInterviewSession(id);
        return session.map(ResponseEntity::ok)
                     .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/history")
    public ResponseEntity<?> getChatHistory(@PathVariable String id, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }

        ResponseEntity<?> ownershipCheck = checkInterviewOwnership(id, userId);
        if (ownershipCheck != null) {
            return ownershipCheck;
        }

        List<QAHistory> history = interviewSessionService.getChatHistory(id);
        return ResponseEntity.ok(history);
    }
    
    /**
     * End interview and generate report
     */
    @PostMapping("/{id}/end")
    public ResponseEntity<?> endInterview(@PathVariable String id, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }

        ResponseEntity<?> ownershipCheck = checkInterviewOwnership(id, userId);
        if (ownershipCheck != null) {
            return ownershipCheck;
        }

        Optional<Interview> interviewOpt = interviewRepository.findById(id);
        if (interviewOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        Interview interview = interviewOpt.get();
        
        // Update interview status and end time
        interview.setStatus("Completed");
        if (interview.getStartedAt() == null) {
            interview.setStartedAt(interview.getCreatedAt());
        }
        interview.setEndedAt(java.time.LocalDateTime.now());
        if (interview.getStartedAt() != null) {
            long duration = java.time.Duration.between(interview.getStartedAt(), interview.getEndedAt()).getSeconds();
            interview.setDurationSeconds((int) duration);
        }
        interviewRepository.save(interview);
        
        // Generate evaluation for all Q&A pairs
        List<QAHistory> history = interviewSessionService.getChatHistory(id);
        evaluateAllAnswers(id, history, interview.getTitle());
        
        // Generate report
        Map<String, Object> report = reportService.generateReport(id);
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Interview completed successfully");
        response.put("report", report);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Get interview report
     */
    @GetMapping("/{id}/report")
    public ResponseEntity<?> getInterviewReport(@PathVariable String id, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }

        ResponseEntity<?> ownershipCheck = checkInterviewOwnership(id, userId);
        if (ownershipCheck != null) {
            return ownershipCheck;
        }

        try {
            Map<String, Object> report = reportService.generateReport(id);
            return ResponseEntity.ok(report);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }
    
    /**
     * Get interview report as JSON
     */
    @GetMapping("/{id}/report/json")
    public ResponseEntity<?> getInterviewReportJson(@PathVariable String id, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }

        ResponseEntity<?> ownershipCheck = checkInterviewOwnership(id, userId);
        if (ownershipCheck != null) {
            return ownershipCheck;
        }

        try {
            Map<String, Object> report = reportService.generateReport(id);
            return ResponseEntity.ok(report);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }
    
    /**
     * Download interview report as PDF
     */
    @GetMapping("/{id}/report/download")
    public ResponseEntity<?> downloadInterviewReport(@PathVariable String id, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }

        ResponseEntity<?> ownershipCheck = checkInterviewOwnership(id, userId);
        if (ownershipCheck != null) {
            return ownershipCheck;
        }

        try {
            byte[] pdfBytes = pdfReportService.generatePdfReport(id);
            return ResponseEntity.ok()
                .header("Content-Type", "application/pdf")
                .header("Content-Disposition", "attachment; filename=\"interview-report-" + id + ".pdf\"")
                .body(pdfBytes);
        } catch (RuntimeException | IOException e) {
            return ResponseEntity.notFound().build();
        }
    }
    
    /**
     * Update interview information
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> updateInterview(
            @PathVariable String id,
            @RequestBody Map<String, Object> updates,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }

        ResponseEntity<?> ownershipCheck = checkInterviewOwnership(id, userId);
        if (ownershipCheck != null) {
            return ownershipCheck;
        }

        Optional<Interview> interviewOpt = interviewRepository.findById(id);
        if (interviewOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        Interview interview = interviewOpt.get();
        
        if (updates.containsKey("title")) {
            interview.setTitle((String) updates.get("title"));
        }
        if (updates.containsKey("status")) {
            interview.setStatus((String) updates.get("status"));
        }
        if (updates.containsKey("language")) {
            interview.setLanguage((String) updates.get("language"));
        }
        if (updates.containsKey("techStack")) {
            interview.setTechStack((String) updates.get("techStack"));
        }
        
        Interview updated = interviewRepository.save(interview);
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("interview", updated);
        return ResponseEntity.ok(response);
    }
    
    /**
     * Delete interview
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteInterview(@PathVariable String id, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }

        ResponseEntity<?> ownershipCheck = checkInterviewOwnership(id, userId);
        if (ownershipCheck != null) {
            return ownershipCheck;
        }

        Optional<Interview> interviewOpt = interviewRepository.findById(id);
        if (interviewOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        interviewRepository.delete(interviewOpt.get());
        return ResponseEntity.ok(Map.of("success", true, "message", "Interview deleted successfully"));
    }

    /**
     * Compare multiple interviews for progress analysis
     */
    @PostMapping("/compare")
    public ResponseEntity<Map<String, Object>> compareInterviews(@RequestBody Map<String, Object> request) {
        @SuppressWarnings("unchecked")
        List<String> interviewIds = (List<String>) request.get("interviewIds");

        if (interviewIds == null || interviewIds.size() < 2) {
            return ResponseEntity.badRequest().body(Map.of("error", "At least 2 interview IDs required"));
        }

        try {
            Map<String, Object> comparisonResult = interviewSessionService.compareInterviews(interviewIds);
            return ResponseEntity.ok(comparisonResult);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Evaluate all Q&A pairs in an interview
     */
    private void evaluateAllAnswers(String interviewId, List<QAHistory> history, String roleId) {
        if (history.isEmpty()) {
            return;
        }

        // Extract role and level from roleId (e.g., "backend_java_mid" -> role: "backend_java", level: "mid")
        String role = roleId;
        String level = "mid"; // default

        if (roleId != null && roleId.contains("_")) {
            String[] parts = roleId.split("_");
            if (parts.length >= 3) {
                level = parts[parts.length - 1]; // last part is level
                role = roleId.substring(0, roleId.lastIndexOf("_" + level)); // everything before level
            }
        }

        final String finalRole = role;
        final String finalLevel = level;

        // Evaluate each Q&A pair asynchronously
        List<CompletableFuture<Void>> evaluationTasks = history.stream()
            .filter(qa -> qa.getAnswerText() != null && !qa.getAnswerText().trim().isEmpty())
            .map(qa -> CompletableFuture.runAsync(() -> {
                try {
                    llmEvaluationService.evaluateAnswer(
                        qa.getQuestionText(),
                        qa.getAnswerText(),
                        finalRole,
                        finalLevel
                    )
                    .doOnNext(evaluationResult -> {
                        // Update QA history with evaluation results
                        qa.setScore(evaluationResult.getScore());
                        qa.setDetailedScores(evaluationResult.getDetailedScores());
                        qa.setStrengths(evaluationResult.getStrengths());
                        qa.setImprovements(evaluationResult.getImprovements());
                        qa.setFollowUpQuestions(evaluationResult.getFollowUpQuestions());
                        qa.setRubricLevel(evaluationResult.getRubricLevel());

                        // Persist evaluation results to database
                        interviewSessionService.updateEvaluationResults(
                            interviewId,
                            qa.getQuestionText(),
                            qa.getAnswerText(),
                            evaluationResult.getScore(),
                            evaluationResult.getDetailedScores(),
                            evaluationResult.getStrengths(),
                            evaluationResult.getImprovements(),
                            evaluationResult.getFollowUpQuestions(),
                            evaluationResult.getRubricLevel()
                        );
                    })
                    .subscribe();
                } catch (Exception e) {
                    System.err.println("Failed to evaluate answer for interview " + interviewId + ": " + e.getMessage());
                    // Continue with other evaluations even if one fails
                }
            }))
            .toList();

        // Wait for all evaluations to complete (with timeout)
        try {
            CompletableFuture.allOf(evaluationTasks.toArray(new CompletableFuture[0]))
                .get(30, java.util.concurrent.TimeUnit.SECONDS); // 30 second timeout
        } catch (Exception e) {
            System.err.println("Evaluation timeout or error for interview " + interviewId + ": " + e.getMessage());
        }
    }

    /**
     * Upload audio recording for an interview
     */
    @PostMapping("/{id}/recording")
    public ResponseEntity<Map<String, Object>> uploadRecording(
            @PathVariable String id,
            @RequestParam("audio") MultipartFile file,
            @RequestAttribute Long userId) {
        try {
            var recording = audioService.saveAudioFile(file, id, userId);
            return ResponseEntity.ok(Map.of(
                "recording", recording,
                "message", "Recording uploaded successfully"
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Upload audio blob from browser recording
     */
    @PostMapping("/{id}/recording/blob")
    public ResponseEntity<Map<String, Object>> uploadRecordingBlob(
            @PathVariable String id,
            @RequestBody Map<String, Object> request,
            @RequestAttribute Long userId) {
        try {
            String filename = (String) request.get("filename");
            String audioData = (String) request.get("audioData"); // base64 encoded
            Integer durationSeconds = (Integer) request.get("durationSeconds");

            // Decode base64 audio data
            byte[] audioBytes = java.util.Base64.getDecoder().decode(audioData);

            var recording = audioService.saveAudioBlob(audioBytes, id, userId, filename, durationSeconds);
            return ResponseEntity.ok(Map.of(
                "recording", recording,
                "message", "Recording uploaded successfully"
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get recordings for an interview
     */
    @GetMapping("/{id}/recordings")
    public ResponseEntity<List<com.aiinterview.model.InterviewRecording>> getRecordings(
            @PathVariable String id,
            @RequestAttribute Long userId) {
        try {
            var recordings = audioService.getRecordingsForInterview(id);
            return ResponseEntity.ok(recordings);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(null);
        }
    }

    /**
     * Download recording file
     */
    @GetMapping("/recording/{recordingId}/download")
    public ResponseEntity<byte[]> downloadRecording(
            @PathVariable Long recordingId,
            @RequestAttribute Long userId) {
        try {
            var recording = audioService.getRecordingById(recordingId);
            if (recording.isEmpty() || !recording.get().getUserId().equals(userId)) {
                return ResponseEntity.notFound().build();
            }

            byte[] audioData = audioService.getAudioFile(recordingId);
            return ResponseEntity.ok()
                .header("Content-Type", "audio/webm")
                .header("Content-Disposition", "attachment; filename=\"" + recording.get().getOriginalFilename() + "\"")
                .body(audioData);
        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * Delete recording
     */
    @DeleteMapping("/recording/{recordingId}")
    public ResponseEntity<Map<String, Object>> deleteRecording(
            @PathVariable Long recordingId,
            @RequestAttribute Long userId) {
        try {
            audioService.deleteRecording(recordingId, userId);
            return ResponseEntity.ok(Map.of("message", "Recording deleted successfully"));
        } catch (Exception e) {
            return ResponseEntity.status(400).body(Map.of("error", e.getMessage()));
        }
    }
}
