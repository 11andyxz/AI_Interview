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
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@RestController
@RequestMapping("/api/user/resume")
@CrossOrigin(origins = "http://localhost:3000")
public class UserResumeController {

    private static final long MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024; // 10MB
    private static final Set<String> ALLOWED_FILE_EXTENSIONS = Set.of(".pdf", ".doc", ".docx", ".txt");
    
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
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestPart(value = "resumeText", required = false) MultipartFile resumeTextPart,
            @RequestParam(required = false, defaultValue = "false") boolean autoAnalyze,
            @RequestParam Map<String, String> formFields) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }

        try {
            String resumeText = formFields.get("resumeText");
            if (!StringUtils.hasText(resumeText) && resumeTextPart != null && !resumeTextPart.isEmpty()) {
                resumeText = new String(resumeTextPart.getBytes(), StandardCharsets.UTF_8);
            }

            String validationError = validateUploadInput(file, resumeText);
            if (validationError != null) {
                return ResponseEntity.badRequest().body(Map.of("error", validationError));
            }

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
            response.put("autoAnalyzed", autoAnalyze && Boolean.TRUE.equals(resume.getAnalyzed()));
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
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

            return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"" + resource.getFilename() + "\"")
                .body(resource);
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
            var existingResume = resumeService.getResumeById(id, userId);
            if (existingResume.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of("error", "Resume not found"));
            }
            if (Boolean.TRUE.equals(existingResume.get().getAnalyzed())) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Resume already analyzed"
                ));
            }

            resumeService.analyzeResume(id, userId);

            // Get the updated resume with analysis data
            var resumeOpt = resumeService.getResumeById(id, userId);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Resume analysis completed");

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
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().toLowerCase().contains("not found")) {
                return ResponseEntity.status(404).body(Map.of("error", "Resume not found"));
            }
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

    private String validateUploadInput(MultipartFile file, String resumeText) {
        boolean hasFile = file != null;
        boolean hasText = StringUtils.hasText(resumeText);

        if (!hasFile && !hasText) {
            return "Either resume file or resume text is required";
        }

        if (hasFile) {
            if (file.isEmpty()) {
                return "Resume file cannot be empty";
            }
            if (file.getSize() > MAX_FILE_SIZE_BYTES) {
                return "Resume file exceeds maximum size of 10MB";
            }

            String originalFilename = file.getOriginalFilename();
            if (StringUtils.hasText(originalFilename) && originalFilename.contains(".")) {
                String lowerFilename = originalFilename.toLowerCase();
                boolean extensionAllowed = ALLOWED_FILE_EXTENSIONS.stream().anyMatch(lowerFilename::endsWith);
                if (!extensionAllowed) {
                    return "Unsupported file type. Allowed types: pdf, doc, docx, txt";
                }
            }
        }

        return null;
    }
}
