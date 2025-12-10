package com.aiinterview.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/resume")
public class ResumeController {

    @GetMapping("/candidates")
    public ResponseEntity<Map<String, Object>> getAllCandidates() {
        // TODO(ML): Load from candidates.json
        List<Map<String, String>> candidates = List.of(
            Map.of("id", "candidate_001", "name", "Andy Xiong"),
            Map.of("id", "candidate_002", "name", "Yukun Song")
        );
        return ResponseEntity.ok(Map.of("candidates", candidates));
    }

    @PostMapping("/analyze")
    public ResponseEntity<Map<String, Object>> analyzeResume(@RequestBody Map<String, String> request) {
        String candidateId = request.get("candidateId");
        String positionType = request.get("positionType");
        
        // TODO(ML): Implement AI-powered resume analysis
        // 1. Load candidate resume from candidates.json by candidateId
        // 2. Use LLM to extract skills from work experience and projects
        // 3. Generate personalized interview questions based on candidate background
        // 4. Match candidate experience level (junior/mid/senior)
        // 5. Return knowledge base format similar to backend_java_mid.json
        
        // Mock response for now
        Map<String, Object> knowledgeBase = new HashMap<>();
        knowledgeBase.put("roleId", positionType != null ? positionType : "backend_java");
        knowledgeBase.put("level", "mid");
        knowledgeBase.put("skills", List.of("java_core", "spring_boot", "sql", "redis"));
        
        List<Map<String, Object>> questions = new ArrayList<>();
        questions.add(Map.of(
            "id", "q_generated_001",
            "text", "I see you worked on e-commerce platform at Tech Corp. Can you describe the microservices architecture you implemented?",
            "type", "technical",
            "difficulty", "medium",
            "skills", List.of("spring_boot", "microservices"),
            "followUps", List.of(
                "How did you handle inter-service communication?",
                "What challenges did you face during the migration?"
            )
        ));
        questions.add(Map.of(
            "id", "q_generated_002",
            "text", "You mentioned optimizing SQL queries for 50% performance improvement. Walk me through your optimization process.",
            "type", "technical",
            "difficulty", "medium",
            "skills", List.of("sql", "performance"),
            "followUps", List.of(
                "What tools did you use to identify slow queries?",
                "How did you measure the improvement?"
            )
        ));
        
        knowledgeBase.put("questions", questions);
        knowledgeBase.put("candidateId", candidateId);
        knowledgeBase.put("personalized", true);
        
        return ResponseEntity.ok(knowledgeBase);
    }
}
