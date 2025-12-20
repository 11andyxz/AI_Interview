package com.aiinterview.service;

import com.aiinterview.dto.ResumeAnalysisResult;
import com.aiinterview.model.KnowledgeBase;
import com.aiinterview.model.UserResume;
import com.aiinterview.repository.KnowledgeBaseRepository;
import com.aiinterview.repository.UserResumeRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    @Autowired
    private ResumeAnalysisService resumeAnalysisService;

    @Autowired
    private ObjectMapper objectMapper;

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
        resume.setFileName(uniqueFilename);
        resume.setOriginalFileName(originalFilename);
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

            // Analyze resume content using OpenAI for structured data
            ResumeAnalysisResult structuredAnalysis = resumeAnalysisService.analyzeResumeWithOpenAI(resumeText);

            // Store structured analysis data as JSON
            String analysisDataJson = objectMapper.writeValueAsString(structuredAnalysis);
            resume.setAnalysisData(analysisDataJson);

            // Generate text-based analysis for backward compatibility
            String textAnalysis = generateTextAnalysisFromStructuredData(structuredAnalysis);

            // Generate knowledge base entries based on structured analysis
            String sourceFileName = resume.getOriginalFileName() != null ? resume.getOriginalFileName() : resume.getFileName();
            generateKnowledgeBaseEntriesFromStructuredData(userId, structuredAnalysis, sourceFileName);

            // Mark resume as analyzed and store results
            resume.setAnalyzed(true);
            resume.setAnalysisResult(textAnalysis);
            resumeRepository.save(resume);

        } catch (Exception e) {
            throw new RuntimeException("Failed to analyze resume: " + e.getMessage(), e);
        }
    }

    /**
     * Get structured analysis data for a resume
     */
    public Optional<ResumeAnalysisResult> getResumeAnalysisData(Long id, Long userId) {
        Optional<UserResume> resumeOpt = getResumeById(id, userId);
        if (resumeOpt.isEmpty()) {
            return Optional.empty();
        }

        UserResume resume = resumeOpt.get();
        String analysisDataJson = resume.getAnalysisData();

        if (analysisDataJson == null || analysisDataJson.trim().isEmpty()) {
            return Optional.empty();
        }

        try {
            return Optional.of(objectMapper.readValue(analysisDataJson, ResumeAnalysisResult.class));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse analysis data: " + e.getMessage(), e);
        }
    }

    /**
     * Generate text-based analysis from structured data for backward compatibility
     */
    private String generateTextAnalysisFromStructuredData(ResumeAnalysisResult analysis) {
        StringBuilder textAnalysis = new StringBuilder();
        textAnalysis.append("Resume Analysis Results:\n\n");

        if (analysis.getTechStack() != null && !analysis.getTechStack().isEmpty()) {
            textAnalysis.append("Technical Skills Identified: ").append(String.join(", ", analysis.getTechStack())).append("\n\n");
        }

        if (analysis.getMainSkillAreas() != null && !analysis.getMainSkillAreas().isEmpty()) {
            textAnalysis.append("Experience Areas: ").append(String.join(", ", analysis.getMainSkillAreas())).append("\n\n");
        }

        textAnalysis.append("Resume Statistics:\n");
        textAnalysis.append("- Experience Level: ").append(analysis.getLevel()).append("\n");
        textAnalysis.append("- Estimated Experience Years: ").append(analysis.getExperienceYears()).append("\n");
        textAnalysis.append("- Education: ").append(analysis.getEducation()).append("\n\n");

        if (analysis.getSummary() != null && !analysis.getSummary().isEmpty()) {
            textAnalysis.append("Professional Summary: ").append(analysis.getSummary()).append("\n\n");
        }

        textAnalysis.append("Recommendations:\n");
        textAnalysis.append("- Consider highlighting key achievements and quantifiable results\n");
        textAnalysis.append("- Ensure technical skills are clearly listed and up-to-date\n");
        textAnalysis.append("- Include specific examples of project work and technologies used\n");

        return textAnalysis.toString();
    }

    /**
     * Generate knowledge base entries from structured analysis data
     */
    private void generateKnowledgeBaseEntriesFromStructuredData(Long userId, ResumeAnalysisResult analysis, String sourceFileName) {
        // Create knowledge base entries for technical skills
        if (analysis.getTechStack() != null) {
            for (String tech : analysis.getTechStack()) {
                if (tech != null && !tech.trim().isEmpty()) {
                    KnowledgeBase kb = new KnowledgeBase();
                    kb.setUserId(userId);
                    kb.setType("user");
                    kb.setCategory("skill");
                    kb.setName("Tech Skill: " + tech.trim());
                    kb.setTitle("Technical Skill: " + tech.trim());
                    kb.setDescription("Technical skill extracted from resume analysis");
                    kb.setTags("skill,technical,resume," + tech.toLowerCase().trim());
                    knowledgeBaseRepository.save(kb);
                }
            }
        }

        // Create knowledge base entries for main skill areas
        if (analysis.getMainSkillAreas() != null) {
            for (String area : analysis.getMainSkillAreas()) {
                if (area != null && !area.trim().isEmpty()) {
                    KnowledgeBase kb = new KnowledgeBase();
                    kb.setUserId(userId);
                    kb.setType("user");
                    kb.setCategory("experience");
                    kb.setName("Skill Area: " + area.trim());
                    kb.setTitle("Experience Area: " + area.trim());
                    kb.setDescription("Skill area extracted from resume analysis");
                    kb.setTags("experience,area,resume," + area.toLowerCase().trim());
                    knowledgeBaseRepository.save(kb);
                }
            }
        }

        // Create knowledge base entry for experience level
        if (analysis.getLevel() != null && !analysis.getLevel().trim().isEmpty()) {
            KnowledgeBase kb = new KnowledgeBase();
            kb.setUserId(userId);
            kb.setType("user");
            kb.setCategory("level");
            kb.setName("Experience Level: " + analysis.getLevel());
            kb.setTitle("Experience Level: " + analysis.getLevel());
            kb.setDescription("Experience level determined from resume analysis");
            kb.setTags("level,experience,resume," + analysis.getLevel().toLowerCase());
            knowledgeBaseRepository.save(kb);
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

