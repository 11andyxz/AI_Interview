package com.aiinterview.controller;

import com.aiinterview.model.UserNote;
import com.aiinterview.service.NoteService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notes")
@CrossOrigin(origins = "http://localhost:3000")
public class NoteController {
    
    @Autowired
    private NoteService noteService;
    
    /**
     * Get all notes for user
     */
    @GetMapping
    public ResponseEntity<List<UserNote>> getNotes(
            HttpServletRequest request,
            @RequestParam(required = false) String type) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }
        
        List<UserNote> notes = noteService.getUserNotes(userId, type);
        return ResponseEntity.ok(notes);
    }
    
    /**
     * Get notes for a specific interview
     */
    @GetMapping("/interview/{interviewId}")
    public ResponseEntity<List<UserNote>> getInterviewNotes(
            HttpServletRequest request,
            @PathVariable String interviewId) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }
        
        List<UserNote> notes = noteService.getInterviewNotes(userId, interviewId);
        return ResponseEntity.ok(notes);
    }
    
    /**
     * Get note by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<UserNote> getNoteById(
            HttpServletRequest request,
            @PathVariable Long id) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }
        
        return noteService.getNoteById(id, userId)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }
    
    /**
     * Create a new note
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> createNote(
            HttpServletRequest request,
            @RequestBody Map<String, Object> requestBody) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }
        
        try {
            String type = (String) requestBody.getOrDefault("type", "general");
            String title = (String) requestBody.get("title");
            String content = (String) requestBody.get("content");
            String interviewId = (String) requestBody.get("interviewId");
            
            if (title == null || title.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Title is required"));
            }
            
            UserNote note = noteService.createNote(userId, type, title, content, interviewId);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("note", note);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }
    
    /**
     * Update a note
     */
    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateNote(
            HttpServletRequest request,
            @PathVariable Long id,
            @RequestBody Map<String, Object> requestBody) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }
        
        try {
            String title = (String) requestBody.get("title");
            String content = (String) requestBody.get("content");
            
            UserNote note = noteService.updateNote(id, userId, title, content);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("note", note);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(400).body(Map.of("error", e.getMessage()));
        }
    }
    
    /**
     * Delete a note
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteNote(
            HttpServletRequest request,
            @PathVariable Long id) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }
        
        boolean deleted = noteService.deleteNote(id, userId);
        if (deleted) {
            return ResponseEntity.ok(Map.of("success", true));
        }
        return ResponseEntity.notFound().build();
    }
}

