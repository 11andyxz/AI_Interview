package com.aiinterview.controller;

import com.aiinterview.model.UserResume;
import com.aiinterview.service.ResumeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserResumeController.class)
class UserResumeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ResumeService resumeService;

    private Long userId = 1L;
    private Long resumeId = 1L;
    private UserResume mockResume;

    @BeforeEach
    void setUp() {
        mockResume = new UserResume();
        mockResume.setId(resumeId);
        mockResume.setUserId(userId);
        mockResume.setOriginalFileName("resume.pdf");
        mockResume.setResumeText("Resume content");
    }

    @Test
    void testGetUserResumes_Success() throws Exception {
        when(resumeService.getUserResumes(userId)).thenReturn(Arrays.asList(mockResume));

        mockMvc.perform(get("/api/user/resume")
                .requestAttr("userId", userId))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$[0].id").value(resumeId))
            .andExpect(jsonPath("$[0].originalFileName").value("resume.pdf"));
    }

    @Test
    void testGetResumeById_Success() throws Exception {
        when(resumeService.getResumeById(resumeId, userId))
            .thenReturn(java.util.Optional.of(mockResume));

        mockMvc.perform(get("/api/user/resume/{id}", resumeId)
                .requestAttr("userId", userId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(resumeId));
    }

    @Test
    void testGetResumeById_NotFound() throws Exception {
        when(resumeService.getResumeById(resumeId, userId))
            .thenReturn(java.util.Optional.empty());

        mockMvc.perform(get("/api/user/resume/{id}", resumeId)
                .requestAttr("userId", userId))
            .andExpect(status().isNotFound());
    }

    @Test
    void testUploadResume_Success() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file", "resume.pdf", "application/pdf", "resume content".getBytes());

        when(resumeService.uploadResume(eq(userId), any(), eq("additional text")))
            .thenReturn(mockResume);

        mockMvc.perform(multipart("/api/user/resume")
                .file(file)
                .param("resumeText", "additional text")
                .requestAttr("userId", userId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.resume.id").value(resumeId));
    }

    @Test
    void testUploadResume_Unauthorized() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file", "resume.pdf", "application/pdf", "content".getBytes());

        mockMvc.perform(multipart("/api/user/resume")
                .file(file))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void testUpdateResume_Success() throws Exception {
        when(resumeService.updateResume(resumeId, userId, "updated content"))
            .thenReturn(mockResume);

        String requestBody = "{\"resumeText\": \"updated content\"}";

        mockMvc.perform(put("/api/user/resume/{id}", resumeId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
                .requestAttr("userId", userId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void testUpdateResume_Unauthorized() throws Exception {
        String requestBody = "{\"resumeText\": \"updated content\"}";

        mockMvc.perform(put("/api/user/resume/{id}", resumeId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void testDeleteResume_Success() throws Exception {
        when(resumeService.deleteResume(resumeId, userId)).thenReturn(true);

        mockMvc.perform(delete("/api/user/resume/{id}", resumeId)
                .requestAttr("userId", userId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void testDeleteResume_NotFound() throws Exception {
        when(resumeService.deleteResume(resumeId, userId)).thenReturn(false);

        mockMvc.perform(delete("/api/user/resume/{id}", resumeId)
                .requestAttr("userId", userId))
            .andExpect(status().isNotFound());
    }

    @Test
    void testDownloadResume_Success() throws Exception {
        when(resumeService.getResumeFilePath(resumeId, userId))
            .thenReturn(java.util.Optional.of(java.nio.file.Paths.get("test.pdf")));

        // This test would need file system mocking for complete testing
        // For now, we'll test the basic endpoint structure
        mockMvc.perform(get("/api/user/resume/{id}/download", resumeId)
                .requestAttr("userId", userId))
            .andExpect(status().isOk());
    }

    @Test
    void testDownloadResume_NotFound() throws Exception {
        when(resumeService.getResumeFilePath(resumeId, userId))
            .thenReturn(java.util.Optional.empty());

        mockMvc.perform(get("/api/user/resume/{id}/download", resumeId)
                .requestAttr("userId", userId))
            .andExpect(status().isNotFound());
    }

    @Test
    void testAnalyzeResume_Success() throws Exception {
        mockMvc.perform(post("/api/user/resume/{id}/analyze", resumeId)
                .requestAttr("userId", userId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("Resume analysis completed"));
    }

    @Test
    void testAnalyzeResume_Unauthorized() throws Exception {
        mockMvc.perform(post("/api/user/resume/{id}/analyze", resumeId))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void testUploadResume_InvalidFileType_BadRequest() throws Exception {
        MockMultipartFile invalidFile = new MockMultipartFile(
            "file", "resume.exe", "application/octet-stream", "invalid content".getBytes());

        mockMvc.perform(multipart("/api/user/resume")
                .file(invalidFile)
                .requestAttr("userId", userId))
            .andExpect(status().isBadRequest());
    }

    @Test
    void testUploadResume_EmptyFile_BadRequest() throws Exception {
        MockMultipartFile emptyFile = new MockMultipartFile(
            "file", "empty.pdf", "application/pdf", new byte[0]);

        mockMvc.perform(multipart("/api/user/resume")
                .file(emptyFile)
                .requestAttr("userId", userId))
            .andExpect(status().isBadRequest());
    }

    @Test
    void testUploadResume_FileTooLarge_BadRequest() throws Exception {
        // Create a file larger than typical limits (simulate)
        byte[] largeContent = new byte[50 * 1024 * 1024]; // 50MB
        Arrays.fill(largeContent, (byte) 'x');

        MockMultipartFile largeFile = new MockMultipartFile(
            "file", "large.pdf", "application/pdf", largeContent);

        mockMvc.perform(multipart("/api/user/resume")
                .file(largeFile)
                .requestAttr("userId", userId))
            .andExpect(status().isBadRequest());
    }

    @Test
    void testGetResume_NotFound_Returns404() throws Exception {
        when(resumeService.getResumeById(999L, userId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/user/resume/{id}", 999L)
                .requestAttr("userId", userId))
            .andExpect(status().isNotFound());
    }

    @Test
    void testAnalyzeResume_ResumeNotFound_Returns404() throws Exception {
        when(resumeService.getResumeById(999L, userId)).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/user/resume/{id}/analyze", 999L)
                .requestAttr("userId", userId))
            .andExpect(status().isNotFound());
    }

    @Test
    void testAnalyzeResume_AlreadyAnalyzed_Returns400() throws Exception {
        mockResume.setAnalyzed(true);
        when(resumeService.getResumeById(resumeId, userId)).thenReturn(Optional.of(mockResume));

        mockMvc.perform(post("/api/user/resume/{id}/analyze", resumeId)
                .requestAttr("userId", userId))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Resume already analyzed"));
    }

    @Test
    void testDownloadResume_ResumeNotFound_Returns404() throws Exception {
        when(resumeService.getResumeById(999L, userId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/user/resume/{id}/download", 999L)
                .requestAttr("userId", userId))
            .andExpect(status().isNotFound());
    }

    @Test
    void testDeleteResume_NotFound_Returns404() throws Exception {
        when(resumeService.getResumeById(999L, userId)).thenReturn(Optional.empty());

        mockMvc.perform(delete("/api/user/resume/{id}", 999L)
                .requestAttr("userId", userId))
            .andExpect(status().isNotFound());
    }

    @Test
    void testGetUserResumes_NoResumes_ReturnsEmptyArray() throws Exception {
        when(resumeService.getUserResumes(userId)).thenReturn(Arrays.asList());

        mockMvc.perform(get("/api/user/resume")
                .requestAttr("userId", userId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void testUploadResume_TextOnly_Success() throws Exception {
        MockMultipartFile textFile = new MockMultipartFile(
            "resumeText", "", "text/plain", "Resume text content".getBytes());

        when(resumeService.uploadResume(eq(userId), any(), any()))
            .thenReturn(mockResume);

        mockMvc.perform(multipart("/api/user/resume")
                .file(textFile)
                .requestAttr("userId", userId))
            .andExpect(status().isOk());
    }
}
