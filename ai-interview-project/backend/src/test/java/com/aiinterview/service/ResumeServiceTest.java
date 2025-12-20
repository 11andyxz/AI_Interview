package com.aiinterview.service;

import com.aiinterview.dto.ResumeAnalysisResult;
import com.aiinterview.model.KnowledgeBase;
import com.aiinterview.model.UserResume;
import com.aiinterview.repository.KnowledgeBaseRepository;
import com.aiinterview.repository.UserResumeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ResumeServiceTest {

    @Mock
    private UserResumeRepository resumeRepository;

    @Mock
    private KnowledgeBaseRepository knowledgeBaseRepository;

    @Mock
    private AiService aiService;

    @Mock
    private ResumeAnalysisService resumeAnalysisService;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private MultipartFile mockFile;

    @InjectMocks
    private ResumeService resumeService;

    private UserResume testResume;
    private Long userId = 1L;
    private Long resumeId = 1L;

    @BeforeEach
    void setUp() {
        testResume = new UserResume();
        testResume.setId(resumeId);
        testResume.setUserId(userId);
        testResume.setOriginalFileName("test-resume.pdf");
        testResume.setFilePath("/uploads/resumes/test-resume.pdf");
        testResume.setResumeText("Java developer with Spring experience");
    }

    @Test
    void testGetUserResumes() {
        when(resumeRepository.findByUserIdOrderByCreatedAtDesc(userId))
            .thenReturn(Arrays.asList(testResume));

        List<UserResume> result = resumeService.getUserResumes(userId);

        assertEquals(1, result.size());
        assertEquals(testResume, result.get(0));
        verify(resumeRepository).findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Test
    void testGetResumeById_Success() {
        when(resumeRepository.findByIdAndUserId(resumeId, userId)).thenReturn(Optional.of(testResume));

        Optional<UserResume> result = resumeService.getResumeById(resumeId, userId);

        assertTrue(result.isPresent());
        assertEquals(testResume, result.get());
    }

    @Test
    void testGetResumeById_WrongUser() {
        // When userId doesn't match, repository returns empty
        when(resumeRepository.findByIdAndUserId(resumeId, userId)).thenReturn(Optional.empty());

        Optional<UserResume> result = resumeService.getResumeById(resumeId, userId);

        assertFalse(result.isPresent());
    }

    @Test
    void testUploadResume() throws IOException {
        Path tempDir = Files.createTempDirectory("test-uploads");
        try {
            when(mockFile.getOriginalFilename()).thenReturn("resume.pdf");
            when(mockFile.getSize()).thenReturn(1000L);
            when(mockFile.getContentType()).thenReturn("application/pdf");
            when(mockFile.getInputStream()).thenReturn(
                new java.io.ByteArrayInputStream("PDF content".getBytes())
            );
            when(resumeRepository.save(any(UserResume.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

            UserResume result = resumeService.uploadResume(userId, mockFile, "Additional text");

            assertNotNull(result);
            assertEquals(userId, result.getUserId());
            assertEquals("resume.pdf", result.getOriginalFileName());
            assertEquals("Additional text", result.getResumeText());
            verify(resumeRepository).save(any(UserResume.class));
        } finally {
            // Clean up temp directory
            try {
                Files.walk(tempDir)
                    .sorted((a, b) -> b.compareTo(a))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            // Ignore
                        }
                    });
            } catch (IOException e) {
                // Ignore cleanup error
            }
        }
    }

    @Test
    void testUploadResume_IOException() throws IOException {
        when(mockFile.getOriginalFilename()).thenReturn("resume.pdf");
        when(mockFile.getInputStream()).thenThrow(new IOException("File read error"));

        assertThrows(IOException.class, () ->
            resumeService.uploadResume(userId, mockFile, null));
    }

    @Test
    void testUpdateResume() {
        when(resumeRepository.findByIdAndUserId(resumeId, userId)).thenReturn(Optional.of(testResume));
        when(resumeRepository.save(any(UserResume.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        UserResume result = resumeService.updateResume(resumeId, userId, "Updated content");

        assertNotNull(result);
        assertEquals("Updated content", result.getResumeText());
        verify(resumeRepository).save(any(UserResume.class));
    }

    @Test
    void testUpdateResume_NotFound() {
        when(resumeRepository.findByIdAndUserId(resumeId, userId)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () ->
            resumeService.updateResume(resumeId, userId, "Updated content"));
    }

    @Test
    void testDeleteResume_Success() {
        when(resumeRepository.findByIdAndUserId(resumeId, userId)).thenReturn(Optional.of(testResume));

        boolean result = resumeService.deleteResume(resumeId, userId);

        assertTrue(result);
        verify(resumeRepository).delete(testResume);
    }

    @Test
    void testDeleteResume_NotFound() {
        when(resumeRepository.findByIdAndUserId(resumeId, userId)).thenReturn(Optional.empty());

        boolean result = resumeService.deleteResume(resumeId, userId);

        assertFalse(result);
        verify(resumeRepository, never()).delete(any());
    }

    @Test
    void testAnalyzeResume_Success() throws IOException {
        // Mock file existence
        Path mockPath = Paths.get(testResume.getFilePath());
        try {
            Files.createDirectories(mockPath.getParent());
            Files.writeString(mockPath, "Java Spring Developer Resume Content");
        } catch (IOException e) {
            // Ignore for test
        }

        // Mock ResumeAnalysisService
        ResumeAnalysisResult mockAnalysis = new ResumeAnalysisResult();
        mockAnalysis.setLevel("mid");
        mockAnalysis.setTechStack(Arrays.asList("Java", "Spring"));
        mockAnalysis.setExperienceYears(5);
        mockAnalysis.setMainSkillAreas(Arrays.asList("Backend Development"));

        when(resumeRepository.findByIdAndUserId(resumeId, userId)).thenReturn(Optional.of(testResume));
        when(resumeAnalysisService.analyzeResumeWithOpenAI(anyString())).thenReturn(mockAnalysis);
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"level\":\"mid\",\"techStack\":[\"Java\",\"Spring\"]}");
        when(knowledgeBaseRepository.save(any(KnowledgeBase.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        resumeService.analyzeResume(resumeId, userId);

        verify(resumeAnalysisService).analyzeResumeWithOpenAI(anyString());
        verify(knowledgeBaseRepository, atLeastOnce()).save(any(KnowledgeBase.class));
        verify(resumeRepository).save(testResume);

        assertTrue(testResume.getAnalyzed());
        assertNotNull(testResume.getAnalysisResult());
        assertNotNull(testResume.getAnalysisData());

        // Clean up
        try {
            Files.deleteIfExists(mockPath);
        } catch (IOException e) {
            // Ignore cleanup error
        }
    }

    @Test
    void testAnalyzeResume_ResumeNotFound() {
        when(resumeRepository.findByIdAndUserId(resumeId, userId)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () ->
            resumeService.analyzeResume(resumeId, userId));
    }

    @Test
    void testAnalyzeResume_TextExtractionError() {
        testResume.setFilePath("/nonexistent/path.pdf");
        when(resumeRepository.findByIdAndUserId(resumeId, userId)).thenReturn(Optional.of(testResume));

        assertThrows(RuntimeException.class, () ->
            resumeService.analyzeResume(resumeId, userId));
    }

    // Note: Private methods are tested indirectly through public methods like analyzeResume()

    @Test
    void testGetResumeFilePath_Success() throws IOException {
        Path tempDir = Files.createTempDirectory("test-resumes");
        Path filePath = tempDir.resolve("test.pdf");

        try {
            Files.writeString(filePath, "test content");

            testResume.setFilePath(filePath.toString());
            when(resumeRepository.findByIdAndUserId(resumeId, userId)).thenReturn(Optional.of(testResume));

            Optional<Path> result = resumeService.getResumeFilePath(resumeId, userId);

            assertTrue(result.isPresent());
            assertEquals(filePath, result.get());

        } finally {
            // Clean up
            Files.deleteIfExists(filePath);
            Files.deleteIfExists(tempDir);
        }
    }

    @Test
    void testGetResumeFilePath_FileNotFound() {
        testResume.setFilePath("/nonexistent/path.pdf");
        when(resumeRepository.findByIdAndUserId(resumeId, userId)).thenReturn(Optional.of(testResume));

        Optional<Path> result = resumeService.getResumeFilePath(resumeId, userId);

        assertFalse(result.isPresent());
    }

    @Test
    void testMarkAsAnalyzed() throws Exception {
        // Mock ResumeAnalysisService for analyzeResume call
        ResumeAnalysisResult mockAnalysis = new ResumeAnalysisResult();
        mockAnalysis.setLevel("mid");
        mockAnalysis.setTechStack(Arrays.asList("Java", "Spring"));
        mockAnalysis.setExperienceYears(5);
        mockAnalysis.setMainSkillAreas(Arrays.asList("Backend Development"));

        when(resumeRepository.findByIdAndUserId(resumeId, userId)).thenReturn(Optional.of(testResume));
        when(resumeAnalysisService.analyzeResumeWithOpenAI(anyString())).thenReturn(mockAnalysis);
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"level\":\"mid\",\"techStack\":[\"Java\",\"Spring\"]}");
        when(knowledgeBaseRepository.save(any(KnowledgeBase.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(resumeRepository.save(any(UserResume.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        resumeService.markAsAnalyzed(resumeId, userId);

        assertTrue(testResume.getAnalyzed());
        verify(resumeRepository).save(testResume);
    }
}
