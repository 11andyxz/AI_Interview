package com.aiinterview.controller;

import com.aiinterview.model.Candidate;
import com.aiinterview.service.CandidateService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/resume")
@CrossOrigin(origins = "http://localhost:3000")
public class CandidateController {
    
    @Autowired
    private CandidateService candidateService;
    
    /**
     * Get all candidates
     * This endpoint matches the frontend expectation: /api/resume/candidates
     */
    @GetMapping("/candidates")
    public ResponseEntity<Map<String, Object>> getCandidates(HttpServletRequest request) {
        // Note: Candidates are public data, no authentication required for now
        // If you want to add authentication, uncomment below:
        // Long userId = (Long) request.getAttribute("userId");
        // if (userId == null) {
        //     return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        // }
        
        try {
            List<Candidate> candidates = candidateService.findAll();
            Map<String, Object> response = new HashMap<>();
            response.put("candidates", candidates);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }
    
    /**
     * Get candidate by ID
     */
    @GetMapping("/candidates/{id}")
    public ResponseEntity<Map<String, Object>> getCandidateById(@PathVariable Integer id) {
        try {
            var candidateOpt = candidateService.findById(id);
            if (candidateOpt.isPresent()) {
                Map<String, Object> response = new HashMap<>();
                response.put("candidate", candidateOpt.get());
                return ResponseEntity.ok(response);
            }
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }
}

