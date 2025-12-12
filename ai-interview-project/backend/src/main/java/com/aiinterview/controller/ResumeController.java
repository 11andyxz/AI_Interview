package com.aiinterview.controller;

import com.aiinterview.service.CandidateService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/resume")
public class ResumeController {

    private final CandidateService candidateService;

    public ResumeController(CandidateService candidateService) {
        this.candidateService = candidateService;
    }

    @GetMapping("/candidates")
    public ResponseEntity<Map<String, Object>> getAllCandidates() {
        List<Map<String, Object>> candidates = candidateService.findAll().stream()
            .map(c -> Map.<String, Object>of("id", c.getId(), "name", c.getName()))
            .collect(Collectors.toList());
        return ResponseEntity.ok(Map.of("candidates", candidates));
    }

    @PostMapping("/analyze")
    public ResponseEntity<Map<String, Object>> analyzeResume(@RequestBody Map<String, Object> request) {
        Object cid = request.get("candidateId");
        if (cid == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "candidateId is required"));
        }
        Integer candidateId = Integer.parseInt(String.valueOf(cid));
        String positionType = (String) request.getOrDefault("positionType", "Unknown");
        @SuppressWarnings("unchecked")
        List<String> programmingLanguages = (List<String>) request.getOrDefault("programmingLanguages", List.of());
        String language = (String) request.getOrDefault("language", "English");

        return candidateService.findById(candidateId)
            .map(candidate -> ResponseEntity.ok(
                candidateService.buildKnowledgeBase(candidate, positionType, programmingLanguages, language)
            ))
            .orElseGet(() -> ResponseEntity.badRequest().body(Map.of("error", "candidate not found")));
    }
}
