package com.aiinterview.controller;

import com.aiinterview.model.KnowledgeBase;
import com.aiinterview.service.KnowledgeBaseService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/knowledge-base")
@CrossOrigin(origins = "http://localhost:3000")
public class KnowledgeBaseController {
    
    @Autowired
    private KnowledgeBaseService knowledgeBaseService;
    
    /**
     * Get all knowledge bases
     */
    @GetMapping
    public ResponseEntity<List<KnowledgeBase>> getKnowledgeBases(
            HttpServletRequest request,
            @RequestParam(required = false) String type) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }
        
        List<KnowledgeBase> knowledgeBases = knowledgeBaseService.getKnowledgeBases(userId, type);
        return ResponseEntity.ok(knowledgeBases);
    }
    
    /**
     * Get system knowledge bases
     */
    @GetMapping("/system")
    public ResponseEntity<List<KnowledgeBase>> getSystemKnowledgeBases() {
        List<KnowledgeBase> knowledgeBases = knowledgeBaseService.getSystemKnowledgeBases();
        return ResponseEntity.ok(knowledgeBases);
    }
    
    /**
     * Get knowledge base by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<KnowledgeBase> getKnowledgeBaseById(
            HttpServletRequest request,
            @PathVariable Long id) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }
        
        return knowledgeBaseService.getKnowledgeBaseById(id, userId)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }
    
    /**
     * Create user knowledge base
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> createKnowledgeBase(
            HttpServletRequest request,
            @RequestBody Map<String, Object> requestBody) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }
        
        try {
            String name = (String) requestBody.get("name");
            String description = (String) requestBody.get("description");
            String content = (String) requestBody.get("content");
            
            if (name == null || name.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Name is required"));
            }
            
            KnowledgeBase kb = knowledgeBaseService.createKnowledgeBase(userId, name, description, content);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("knowledgeBase", kb);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }
    
    /**
     * Update knowledge base
     */
    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateKnowledgeBase(
            HttpServletRequest request,
            @PathVariable Long id,
            @RequestBody Map<String, Object> requestBody) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }
        
        try {
            String name = (String) requestBody.get("name");
            String description = (String) requestBody.get("description");
            String content = (String) requestBody.get("content");
            
            KnowledgeBase kb = knowledgeBaseService.updateKnowledgeBase(id, userId, name, description, content);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("knowledgeBase", kb);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(400).body(Map.of("error", e.getMessage()));
        }
    }
    
    /**
     * Delete knowledge base
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteKnowledgeBase(
            HttpServletRequest request,
            @PathVariable Long id) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }
        
        try {
            boolean deleted = knowledgeBaseService.deleteKnowledgeBase(id, userId);
            if (deleted) {
                return ResponseEntity.ok(Map.of("success", true));
            }
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.status(400).body(Map.of("error", e.getMessage()));
        }
    }
}

