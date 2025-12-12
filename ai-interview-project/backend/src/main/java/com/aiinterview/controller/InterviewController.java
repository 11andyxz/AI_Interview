package com.aiinterview.controller;

import com.aiinterview.dto.ChatRequest;
import com.aiinterview.dto.CreateInterviewRequest;
import com.aiinterview.dto.QAHistory;
import com.aiinterview.model.Candidate;
import com.aiinterview.model.Interview;
import com.aiinterview.repository.InterviewRepository;
import com.aiinterview.service.AiService;
import com.aiinterview.service.CandidateService;
import com.aiinterview.service.InterviewSessionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/interviews")
public class InterviewController {

    private final AiService aiService;
    private final InterviewRepository interviewRepository;
    private final CandidateService candidateService;
    private final InterviewSessionService interviewSessionService;
    private final ObjectMapper objectMapper;

    public InterviewController(AiService aiService,
                               InterviewRepository interviewRepository,
                               CandidateService candidateService,
                               InterviewSessionService interviewSessionService,
                               ObjectMapper objectMapper) {
        this.aiService = aiService;
        this.interviewRepository = interviewRepository;
        this.candidateService = candidateService;
        this.interviewSessionService = interviewSessionService;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public List<Interview> getAllInterviews() {
        return interviewRepository.findAll();
    }

    @PostMapping
    public ResponseEntity<?> createInterview(@RequestBody CreateInterviewRequest request) {
        if (request.getCandidateId() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "candidateId is required"));
        }

        Optional<Candidate> candidateOpt = candidateService.findById(request.getCandidateId());
        if (candidateOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "candidate not found"));
        }
        Candidate candidate = candidateOpt.get();

        Interview interview = new Interview();
        interview.setId(UUID.randomUUID().toString());
        interview.setCandidateId(candidate.getId());
        interview.setTitle(request.getPositionType());
        interview.setLanguage(request.getLanguage());
        interview.setTechStack(String.join(",", request.getProgrammingLanguages() != null ? request.getProgrammingLanguages() : List.of()));
        interview.setDate(LocalDate.now());
        interview.setStatus("In Progress");
        interview.setUseCustomKnowledge(request.isUseCustomKnowledge());
        try {
            if (request.getProgrammingLanguages() != null) {
                interview.setProgrammingLanguages(objectMapper.writeValueAsString(request.getProgrammingLanguages()));
            }
        } catch (IOException e) {
            // ignore and leave null
        }

        Interview saved = interviewRepository.save(interview);

        Map<String, Object> knowledgeBase = candidateService.buildKnowledgeBase(
            candidate,
            request.getPositionType(),
            request.getProgrammingLanguages(),
            request.getLanguage()
        );

        return ResponseEntity.ok(Map.of(
            "interview", saved,
            "knowledgeBase", knowledgeBase
        ));
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
    public Mono<String> chatWithAi(@PathVariable String id, @RequestBody ChatRequest request) {
        // Use intelligent chat with candidate context and conversation history
        return interviewSessionService.generatePersonalizedResponse(id, request);
    }

    @GetMapping("/{id}/session")
    public ResponseEntity<Map<String, Object>> getInterviewSession(@PathVariable String id) {
        Optional<Map<String, Object>> session = interviewSessionService.getInterviewSession(id);
        return session.map(ResponseEntity::ok)
                     .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/history")
    public List<QAHistory> getChatHistory(@PathVariable String id) {
        return interviewSessionService.getChatHistory(id);
    }
}
