package com.aiinterview.service;

import com.aiinterview.model.KnowledgeBase;
import com.aiinterview.model.UserResume;
import com.aiinterview.repository.KnowledgeBaseRepository;
import com.aiinterview.repository.UserResumeRepository;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ResumeService {
    
    @Autowired
    private UserResumeRepository resumeRepository;

    @Autowired
    private KnowledgeBaseRepository knowledgeBaseRepository;

    @Autowired
    private AiService aiService;
    
    private static final String UPLOAD_DIR = "uploads/resumes/";
    
    /**
     * Get all resumes for a user
     */
    public List<UserResume> getUserResumes(Long userId) {
        return resumeRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }
    
    /**
     * Get resume by ID
     */
    public Optional<UserResume> getResumeById(Long id, Long userId) {
        return resumeRepository.findByIdAndUserId(id, userId);
    }
    
    /**
     * Upload and save resume file
     */
    public UserResume uploadResume(Long userId, MultipartFile file, String resumeText) throws IOException {
        // Create upload directory if not exists
        Path uploadPath = Paths.get(UPLOAD_DIR);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }
        
        // Generate unique filename
        String originalFilename = file.getOriginalFilename();
        String fileExtension = originalFilename != null && originalFilename.contains(".") 
            ? originalFilename.substring(originalFilename.lastIndexOf(".")) 
            : "";
        String uniqueFilename = UUID.randomUUID().toString() + fileExtension;
        Path filePath = uploadPath.resolve(uniqueFilename);
        
        // Save file
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
        
        // Create resume record
        UserResume resume = new UserResume();
        resume.setUserId(userId);
        resume.setFileName(originalFilename);
        resume.setFilePath(filePath.toString());
        resume.setFileSize(file.getSize());
        resume.setFileType(file.getContentType());
        resume.setResumeText(resumeText);
        resume.setAnalyzed(false);
        
        return resumeRepository.save(resume);
    }
    
    /**
     * Update resume
     */
    public UserResume updateResume(Long id, Long userId, String resumeText) {
        Optional<UserResume> resumeOpt = getResumeById(id, userId);
        if (resumeOpt.isEmpty()) {
            throw new RuntimeException("Resume not found");
        }
        
        UserResume resume = resumeOpt.get();
        if (resumeText != null) {
            resume.setResumeText(resumeText);
        }
        return resumeRepository.save(resume);
    }
    
    /**
     * Delete resume
     */
    public boolean deleteResume(Long id, Long userId) {
        Optional<UserResume> resumeOpt = getResumeById(id, userId);
        if (resumeOpt.isEmpty()) {
            return false;
        }
        
        UserResume resume = resumeOpt.get();
        // Delete file
        try {
            Path filePath = Paths.get(resume.getFilePath());
            if (Files.exists(filePath)) {
                Files.delete(filePath);
            }
        } catch (IOException e) {
            // Log error but continue with database deletion
            System.err.println("Error deleting file: " + e.getMessage());
        }
        
        resumeRepository.delete(resume);
        return true;
    }
    
    /**
     * Get resume file path for download
     */
    public Optional<Path> getResumeFilePath(Long id, Long userId) {
        Optional<UserResume> resumeOpt = getResumeById(id, userId);
        if (resumeOpt.isEmpty()) {
            return Optional.empty();
        }
        
        Path filePath = Paths.get(resumeOpt.get().getFilePath());
        if (Files.exists(filePath)) {
            return Optional.of(filePath);
        }
        return Optional.empty();
    }
    
    /**
     * Analyze resume and generate knowledge base entries
     */
    public void analyzeResume(Long id, Long userId) {
        Optional<UserResume> resumeOpt = getResumeById(id, userId);
        if (resumeOpt.isEmpty()) {
            throw new RuntimeException("Resume not found");
        }

        UserResume resume = resumeOpt.get();

        try {
            // Extract text from resume file
            String resumeText = extractResumeText(resume);

            // Analyze resume content using AI
            String analysis = analyzeResumeContent(resumeText);

            // Generate knowledge base entries based on analysis
            String sourceFileName = resume.getOriginalFileName() != null ? resume.getOriginalFileName() : resume.getFileName();
            generateKnowledgeBaseEntries(userId, analysis, sourceFileName);

            // Mark resume as analyzed
            resume.setAnalyzed(true);
            resume.setAnalysisResult(analysis);
            resumeRepository.save(resume);

        } catch (Exception e) {
            throw new RuntimeException("Failed to analyze resume: " + e.getMessage(), e);
        }
    }

    /**
     * Extract text content from resume file
     */
    private String extractResumeText(UserResume resume) throws IOException {
        Path filePath = Paths.get(resume.getFilePath());

        if (!Files.exists(filePath)) {
            // If file doesn't exist, use the stored text content
            return resume.getResumeText() != null ? resume.getResumeText() : "";
        }

        String fileName = (resume.getOriginalFileName() != null ? resume.getOriginalFileName() : resume.getFileName()).toLowerCase();

        if (fileName.endsWith(".pdf")) {
            // Extract text from PDF
            try (PDDocument document = Loader.loadPDF(Files.readAllBytes(filePath))) {
                PDFTextStripper stripper = new PDFTextStripper();
                return stripper.getText(document);
            }
        } else if (fileName.endsWith(".txt")) {
            // Read plain text file
            return Files.readString(filePath);
        } else {
            // For other formats, try to use stored text or return empty
            return resume.getResumeText() != null ? resume.getResumeText() : "";
        }
    }

    /**
     * Analyze resume content using AI service
     */
    private String analyzeResumeContent(String resumeText) {
        if (resumeText == null || resumeText.trim().isEmpty()) {
            return "No resume content available for analysis.";
        }

        // Use AI service to analyze the resume
        // This is a simplified implementation - in a real system, you might want to use
        // more sophisticated NLP analysis or external AI services
        return aiService.analyzeResumeContent(resumeText);
    }

    /**
     * Generate knowledge base entries from resume analysis
     */
    private void generateKnowledgeBaseEntries(Long userId, String analysis, String sourceFileName) {
        // Extract key skills and experiences from analysis
        // This is a simplified implementation - in practice, you might use NLP to extract entities

        String[] skills = extractSkillsFromAnalysis(analysis);
        String[] experiences = extractExperiencesFromAnalysis(analysis);

        // Create knowledge base entries for skills
        for (String skill : skills) {
            if (!skill.trim().isEmpty()) {
                KnowledgeBase kb = new KnowledgeBase();
                kb.setUserId(userId);
                kb.setType("user");
                kb.setCategory("skill");
                kb.setTitle("Skill: " + skill.trim());
                kb.setContent("Extracted from resume analysis of " + sourceFileName);
                kb.setTags("skill,resume," + skill.toLowerCase().trim());
                knowledgeBaseRepository.save(kb);
            }
        }

        // Create knowledge base entries for experiences
        for (String experience : experiences) {
            if (!experience.trim().isEmpty()) {
                KnowledgeBase kb = new KnowledgeBase();
                kb.setUserId(userId);
                kb.setType("user");
                kb.setCategory("experience");
                kb.setTitle("Experience: " + experience.trim());
                kb.setContent("Extracted from resume analysis of " + sourceFileName);
                kb.setTags("experience,resume," + experience.toLowerCase().trim());
                knowledgeBaseRepository.save(kb);
            }
        }
    }

    /**
     * Extract skills from analysis text (simplified implementation)
     */
    private String[] extractSkillsFromAnalysis(String analysis) {
        // This is a very basic implementation
        // In a real system, you'd use NLP or pattern matching
        String[] commonSkills = {
            "Java", "Python", "JavaScript", "React", "Spring", "SQL", "Git",
            "Docker", "Kubernetes", "AWS", "Azure", "Linux", "Agile", "Scrum"
        };

        return java.util.Arrays.stream(commonSkills)
            .filter(skill -> analysis.toLowerCase().contains(skill.toLowerCase()))
            .toArray(String[]::new);
    }

    /**
     * Extract experiences from analysis text (simplified implementation)
     */
    private String[] extractExperiencesFromAnalysis(String analysis) {
        // This is a very basic implementation
        // In a real system, you'd use more sophisticated NLP
        String[] commonExperiences = {
            "Backend Development", "Frontend Development", "Full Stack Development",
            "API Development", "Database Design", "System Architecture", "Team Leadership"
        };

        return java.util.Arrays.stream(commonExperiences)
            .filter(exp -> analysis.toLowerCase().contains(exp.toLowerCase()))
            .toArray(String[]::new);
    }

    /**
     * Mark resume as analyzed (legacy method for backward compatibility)
     */
    public void markAsAnalyzed(Long id, Long userId) {
        analyzeResume(id, userId);
    }
}

