package com.aiinterview.controller;

import com.aiinterview.model.UserResume;
import com.aiinterview.service.ResumeService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/user/resume")
@CrossOrigin(origins = "http://localhost:3000")
public class UserResumeController {
    
    @Autowired
    private ResumeService resumeService;
    
    /**
     * Get all resumes for user
     */
    @GetMapping
    public ResponseEntity<List<UserResume>> getUserResumes(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }
        
        List<UserResume> resumes = resumeService.getUserResumes(userId);
        return ResponseEntity.ok(resumes);
    }
    
    /**
     * Get resume by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<UserResume> getResumeById(
            HttpServletRequest request,
            @PathVariable Long id) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }
        
        return resumeService.getResumeById(id, userId)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }
    
    /**
     * Upload resume file
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> uploadResume(
            HttpServletRequest request,
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String resumeText,
            @RequestParam(required = false, defaultValue = "false") boolean autoAnalyze) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }

        try {
            UserResume resume = resumeService.uploadResume(userId, file, resumeText);

            // Auto-analyze if requested
            if (autoAnalyze) {
                try {
                    resumeService.analyzeResume(resume.getId(), userId);
                    // Re-fetch resume to get updated analysis data
                    resume = resumeService.getResumeById(resume.getId(), userId).orElse(resume);
                } catch (Exception e) {
                    // Log error but don't fail the upload
                    System.err.println("Auto-analysis failed for resume " + resume.getId() + ": " + e.getMessage());
                }
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("resume", resume);
            response.put("autoAnalyzed", autoAnalyze && resume.getAnalyzed());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }
    
    /**
     * Update resume
     */
    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateResume(
            HttpServletRequest request,
            @PathVariable Long id,
            @RequestBody Map<String, Object> requestBody) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }
        
        try {
            String resumeText = (String) requestBody.get("resumeText");
            UserResume resume = resumeService.updateResume(id, userId, resumeText);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("resume", resume);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(400).body(Map.of("error", e.getMessage()));
        }
    }
    
    /**
     * Delete resume
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteResume(
            HttpServletRequest request,
            @PathVariable Long id) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }
        
        boolean deleted = resumeService.deleteResume(id, userId);
        if (deleted) {
            return ResponseEntity.ok(Map.of("success", true));
        }
        return ResponseEntity.notFound().build();
    }
    
    /**
     * Download resume file
     */
    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> downloadResume(
            HttpServletRequest request,
            @PathVariable Long id) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }
        
        try {
            Optional<Path> filePathOpt = resumeService.getResumeFilePath(id, userId);
            if (filePathOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            
            Path filePath = filePathOpt.get();
            Resource resource = new UrlResource(filePath.toUri());
            
            if (resource.exists() && resource.isReadable()) {
                return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION, 
                        "attachment; filename=\"" + resource.getFilename() + "\"")
                    .body(resource);
            }
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
    }
    
    /**
     * Analyze resume using OpenAI (generate structured analysis and knowledge base)
     */
    @PostMapping("/{id}/analyze")
    public ResponseEntity<Map<String, Object>> analyzeResume(
            HttpServletRequest request,
            @PathVariable Long id) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }

        try {
            resumeService.analyzeResume(id, userId);

            // Get the updated resume with analysis data
            var resumeOpt = resumeService.getResumeById(id, userId);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Resume analysis completed successfully");

            if (resumeOpt.isPresent()) {
                UserResume resume = resumeOpt.get();
                response.put("resume", resume);

                // Include analysis data if available
                var analysisOpt = resumeService.getResumeAnalysisData(id, userId);
                if (analysisOpt.isPresent()) {
                    response.put("analysisData", analysisOpt.get());
                }
            }

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get resume analysis data
     */
    @GetMapping("/{id}/analysis")
    public ResponseEntity<Map<String, Object>> getResumeAnalysis(
            HttpServletRequest request,
            @PathVariable Long id) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }

        try {
            var resumeOpt = resumeService.getResumeById(id, userId);
            if (resumeOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            UserResume resume = resumeOpt.get();
            Map<String, Object> response = new HashMap<>();
            response.put("resumeId", id);
            response.put("analyzed", resume.getAnalyzed());
            response.put("analysisResult", resume.getAnalysisResult());

            // Include structured analysis data if available
            var analysisOpt = resumeService.getResumeAnalysisData(id, userId);
            if (analysisOpt.isPresent()) {
                response.put("analysisData", analysisOpt.get());
            } else if (!resume.getAnalyzed()) {
                response.put("message", "Resume has not been analyzed yet");
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }
}

