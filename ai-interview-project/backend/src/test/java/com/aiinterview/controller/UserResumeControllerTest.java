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
}
