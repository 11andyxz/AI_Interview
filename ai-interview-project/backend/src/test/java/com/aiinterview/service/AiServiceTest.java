package com.aiinterview.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class AiServiceTest {
    
    @InjectMocks
    private AiService aiService;
    
    @Test
    void testGenerateInterviewQuestions_JavaBackend() {
        List<String> questions = aiService.generateInterviewQuestions("Backend Java Developer");
        
        assertNotNull(questions);
        assertFalse(questions.isEmpty());
        assertTrue(questions.stream().anyMatch(q -> q.contains("Java") || q.contains("JDK") || q.contains("JVM")));
    }
    
    @Test
    void testGenerateInterviewQuestions_FrontendReact() {
        List<String> questions = aiService.generateInterviewQuestions("Frontend React Developer");
        
        assertNotNull(questions);
        assertFalse(questions.isEmpty());
        assertTrue(questions.stream().anyMatch(q -> q.contains("React") || q.contains("Virtual DOM")));
    }
    
    @Test
    void testGenerateInterviewQuestions_PythonAI() {
        List<String> questions = aiService.generateInterviewQuestions("Python AI Engineer");
        
        assertNotNull(questions);
        assertFalse(questions.isEmpty());
        assertTrue(questions.stream().anyMatch(q -> q.contains("Python") || q.contains("decorator")));
    }
    
    @Test
    void testGenerateInterviewQuestions_GeneralRole() {
        List<String> questions = aiService.generateInterviewQuestions("Product Manager");
        
        assertNotNull(questions);
        assertFalse(questions.isEmpty());
        // Should return general behavioral questions
        assertTrue(questions.stream().anyMatch(q -> q.contains("yourself") || q.contains("achievement")));
    }
    
    @Test
    void testGenerateInterviewQuestions_NullRole() {
        List<String> questions = aiService.generateInterviewQuestions(null);
        
        assertNotNull(questions);
        assertFalse(questions.isEmpty());
    }
    
    @Test
    void testGenerateAiResponse() {
        String response = aiService.generateAiResponse("Test message");
        
        assertNotNull(response);
        assertFalse(response.isEmpty());
    }
    
    @Test
    void testAnalyzeVoiceResponse() {
        byte[] audioData = "test audio data".getBytes();
        String result = aiService.analyzeVoiceResponse(audioData);
        
        assertNotNull(result);
        assertTrue(result.contains("coming soon") || result.contains("Voice analysis"));
    }
    
    @Test
    void testGenerateResumeSummary() {
        String resumeText = "Java developer with 5 years experience in Spring Boot";
        String jobDescription = "Backend Java Developer position";
        
        String summary = aiService.generateResumeSummary(resumeText, jobDescription);
        
        assertNotNull(summary);
        assertFalse(summary.isEmpty());
    }
    
    @Test
    void testAnalyzeResumeContent_WithSkills() {
        String resumeText = "Java developer with experience in Spring Boot, React, and MySQL. " +
                           "5 years of backend development experience.";
        
        String analysis = aiService.analyzeResumeContent(resumeText);
        
        assertNotNull(analysis);
        assertTrue(analysis.contains("Resume Analysis Results"));
        assertTrue(analysis.contains("Java") || analysis.contains("Spring"));
    }
    
    @Test
    void testAnalyzeResumeContent_Empty() {
        String analysis = aiService.analyzeResumeContent("");
        
        assertNotNull(analysis);
        assertTrue(analysis.contains("No resume content"));
    }
    
    @Test
    void testAnalyzeResumeContent_Null() {
        String analysis = aiService.analyzeResumeContent(null);
        
        assertNotNull(analysis);
        assertTrue(analysis.contains("No resume content"));
    }
    
    @Test
    void testAnalyzeResumeContent_WithSeniorKeywords() {
        String resumeText = "Senior Java Architect with 10+ years of experience. " +
                           "Lead developer with expertise in Java, Spring, Docker, Kubernetes, AWS, Azure.";
        
        String analysis = aiService.analyzeResumeContent(resumeText);
        
        assertNotNull(analysis);
        // Should detect senior level based on keywords
        assertTrue(analysis.contains("Senior") || analysis.contains("Senior Level"));
    }
}

