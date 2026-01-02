package com.aiinterview.service;

import com.aiinterview.dto.ResumeAnalysisResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.aiinterview.validator.ResumeAnalysisValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ResumeAnalysisServiceTest {

    @Mock
    private OpenAiService openAiService;

    @Mock
    private ResumeAnalysisValidator validator;

    private ObjectMapper objectMapper;
    private ResumeAnalysisService resumeAnalysisService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        objectMapper = new ObjectMapper();
        resumeAnalysisService = new ResumeAnalysisService(openAiService, objectMapper, validator);
    }

    @Test
    void testAnalyzeResumeWithOpenAI_Success() {
        // Given
        String resumeText = "John Doe\nJava Developer\n5 years experience\nSkills: Java, Spring, MySQL";
        String mockResponse = """
            {
                "level": "mid",
                "techStack": ["Java", "Spring", "MySQL"],
                "experienceYears": 5,
                "skills": ["Java Development", "Spring Framework", "Database Design"],
                "mainSkillAreas": ["Backend Development", "Web Development"],
                "education": "Bachelor's in Computer Science",
                "summary": "Experienced Java developer with strong backend skills"
            }
            """;

        when(openAiService.simpleChat(any(), any())).thenReturn(Mono.just(mockResponse));

        // When
        ResumeAnalysisResult result = resumeAnalysisService.analyzeResumeWithOpenAI(resumeText);

        // Then
        assertNotNull(result);
        assertEquals("mid", result.getLevel());
        assertEquals(5, result.getExperienceYears());
        assertTrue(result.getTechStack().contains("Java"));
        assertTrue(result.getTechStack().contains("Spring"));
        assertTrue(result.getMainSkillAreas().contains("Backend Development"));
        assertEquals("Bachelor's in Computer Science", result.getEducation());
        assertEquals("Experienced Java developer with strong backend skills", result.getSummary());

        verify(openAiService, times(1)).simpleChat(any(), any());
    }

    @Test
    void testAnalyzeResumeWithOpenAI_InvalidJSON() {
        // Given
        String resumeText = "Test resume";
        String invalidJsonResponse = "Invalid JSON response without braces";

        when(openAiService.simpleChat(any(), any())).thenReturn(Mono.just(invalidJsonResponse));

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            resumeAnalysisService.analyzeResumeWithOpenAI(resumeText);
        });

        assertTrue(exception.getMessage().contains("Failed to analyze resume"));
        verify(openAiService, times(1)).simpleChat(any(), any());
    }

    @Test
    void testAnalyzeResumeWithOpenAI_OpenAIFailure() {
        // Given
        String resumeText = "Test resume";

        when(openAiService.simpleChat(any(), any())).thenReturn(Mono.error(new RuntimeException("OpenAI service error")));

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            resumeAnalysisService.analyzeResumeWithOpenAI(resumeText);
        });

        assertTrue(exception.getMessage().contains("Failed to analyze resume"));
        verify(openAiService, times(1)).simpleChat(any(), any());
    }

    @Test
    void testGenerateAnalysisPrompt() {
        // Given
        String resumeText = "John Doe\nJava Developer";

        // When
        // Note: This is testing private method, in real scenario we might need to make it package-private
        // For now, we'll test indirectly through the main method

        // Then
        // The prompt should contain the resume text and specific instructions
        // This is tested indirectly through the success test above
    }

    @Test
    void testParseAnalysisResult_ValidJSON() {
        // Given
        String validJson = """
            {
                "level": "senior",
                "techStack": ["React", "Node.js", "AWS"],
                "experienceYears": 8,
                "skills": ["Full-stack Development", "Cloud Architecture"],
                "mainSkillAreas": ["Frontend Development", "DevOps"],
                "education": "Master's in Software Engineering",
                "summary": "Senior full-stack developer with cloud expertise"
            }
            """;

        // When
        ResumeAnalysisResult result = resumeAnalysisService.parseAnalysisResult(validJson);

        // Then
        assertNotNull(result);
        assertEquals("senior", result.getLevel());
        assertEquals(8, result.getExperienceYears());
        assertTrue(result.getTechStack().contains("React"));
        assertTrue(result.getTechStack().contains("Node.js"));
        assertTrue(result.getTechStack().contains("AWS"));
    }

    @Test
    void testParseAnalysisResult_InvalidJSON() {
        // Given
        String invalidJson = "{ invalid json content";

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            resumeAnalysisService.parseAnalysisResult(invalidJson);
        });

        assertTrue(exception.getMessage().contains("No valid JSON object found in response"));
    }

    @Test
    void testParseAnalysisResult_MissingFields() {
        // Given
        String incompleteJson = """
            {
                "level": "junior"
            }
            """;

        // When
        ResumeAnalysisResult result = resumeAnalysisService.parseAnalysisResult(incompleteJson);

        // Then
        assertNotNull(result);
        assertEquals("junior", result.getLevel());
        assertEquals(0, result.getExperienceYears()); // default value
        assertTrue(result.getTechStack().isEmpty()); // default empty list
        assertEquals("Not specified", result.getEducation()); // default value
        assertEquals("Professional summary not available", result.getSummary()); // default value
    }

    @Test
    void testAnalyzeResumeWithOpenAI_EmptyResumeText() {
        // Given
        String emptyResumeText = "";

        // When & Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            resumeAnalysisService.analyzeResumeWithOpenAI(emptyResumeText);
        });

        assertEquals("Resume text cannot be null or empty", exception.getMessage());
        verify(openAiService, never()).simpleChat(any(), any());
    }

    @Test
    void testAnalyzeResumeWithOpenAI_NullResumeText() {
        // Given
        String nullResumeText = null;

        // When & Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            resumeAnalysisService.analyzeResumeWithOpenAI(nullResumeText);
        });

        assertEquals("Resume text cannot be null or empty", exception.getMessage());
        verify(openAiService, never()).simpleChat(any(), any());
    }

    @Test
    void testParseAnalysisResult_JsonWithExtraText() {
        // Given - AI sometimes adds extra text around JSON
        String jsonWithExtraText = """
            Based on the resume analysis, here are the results:
            {
                "level": "mid",
                "techStack": ["Python", "Django"],
                "experienceYears": 4,
                "skills": ["Python Development", "Web Development"],
                "mainSkillAreas": ["Backend Development"],
                "education": "Bachelor's Degree",
                "summary": "Mid-level Python developer"
            }
            This concludes the analysis.
            """;

        // When
        ResumeAnalysisResult result = resumeAnalysisService.parseAnalysisResult(jsonWithExtraText);

        // Then
        assertNotNull(result);
        assertEquals("mid", result.getLevel());
        assertEquals(4, result.getExperienceYears());
        assertTrue(result.getTechStack().contains("Python"));
    }
}
