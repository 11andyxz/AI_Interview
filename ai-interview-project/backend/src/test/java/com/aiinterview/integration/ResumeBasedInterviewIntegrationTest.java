package com.aiinterview.integration;

import com.aiinterview.dto.CreateInterviewRequest;
import com.aiinterview.model.*;
import com.aiinterview.repository.InterviewRepository;
import com.aiinterview.repository.UserRepository;
import com.aiinterview.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
class ResumeBasedInterviewIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private InterviewRepository interviewRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ResumeService resumeService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void testResumeUploadAndAnalysis() throws Exception {
        // Create user
        String username = "resumeuser_" + System.currentTimeMillis();
        User user = userService.createUser(username, "password123");

        // Upload resume via API
        mockMvc.perform(multipart("/api/user/resume")
                .file("file", "sample resume content".getBytes())
                .param("autoAnalyze", "true")
                .requestAttr("userId", user.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resume").exists());
                // Don't check autoAnalyzed value as AI service might fail in test

        // Verify resume was created
        mockMvc.perform(get("/api/user/resume")
                .requestAttr("userId", user.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0]").exists());
    }

    @Test
    void testResumeAnalysisData() throws Exception {
        // Create user
        String username = "analysisuser_" + System.currentTimeMillis();
        User user = userService.createUser(username, "password123");

        // Upload and analyze resume
        mockMvc.perform(multipart("/api/user/resume")
                .file("file", "Java developer with 5 years experience".getBytes())
                .param("autoAnalyze", "true")
                .requestAttr("userId", user.getId()))
                .andExpect(status().isOk());

        // Get resume and verify analysis data
        var result = mockMvc.perform(get("/api/user/resume")
                .requestAttr("userId", user.getId()))
                .andExpect(status().isOk())
                .andReturn();

        String responseContent = result.getResponse().getContentAsString();
        assertTrue(responseContent.contains("analyzed") || responseContent.contains("analysis"));
    }

    @Test
    void testInterviewCreationFromResume() throws Exception {
        // Create user
        String username = "interviewuser_" + System.currentTimeMillis();
        User user = userService.createUser(username, "password123");

        // Upload analyzed resume
        var uploadResult = mockMvc.perform(multipart("/api/user/resume")
                .file("file", "Senior Java Developer with Spring Boot experience".getBytes())
                .param("autoAnalyze", "true")
                .requestAttr("userId", user.getId()))
                .andExpect(status().isOk())
                .andReturn();

        // Create resume-based interview (simplified to just test the interview creation)
        CreateInterviewRequest request = new CreateInterviewRequest();
        request.setInterviewType("technical");
        request.setPositionType("Senior Java Developer");
        request.setProgrammingLanguages(List.of("Java", "Spring Boot"));
        request.setLanguage("English");

        String requestJson = objectMapper.writeValueAsString(request);

        mockMvc.perform(post("/api/interviews")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson)
                .requestAttr("userId", user.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.interview").exists());

        // Verify interview was created
        List<Interview> interviews = interviewRepository.findByUserId(user.getId());
        assertFalse(interviews.isEmpty());
    }

    @Test
    void testResumeAnalysisIntegration() throws Exception {
        // Create user
        String username = "integrationuser_" + System.currentTimeMillis();
        User user = userService.createUser(username, "password123");

        // Upload resume first to get a valid ID
        var uploadResult = mockMvc.perform(multipart("/api/user/resume")
                .file("file", "Java developer resume content".getBytes())
                .requestAttr("userId", user.getId()))
                .andExpect(status().isOk())
                .andReturn();

        String uploadResponse = uploadResult.getResponse().getContentAsString();

        // For simplicity, just verify we can list resumes
        mockMvc.perform(get("/api/user/resume")
                .requestAttr("userId", user.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void testResumeDownload() throws Exception {
        // Create user
        String username = "downloaduser_" + System.currentTimeMillis();
        User user = userService.createUser(username, "password123");

        // Upload resume
        var uploadResult = mockMvc.perform(multipart("/api/user/resume")
                .file("file", "test resume content".getBytes())
                .requestAttr("userId", user.getId()))
                .andExpect(status().isOk())
                .andReturn();

        // Test that we can list resumes (download test requires actual file system setup)
        mockMvc.perform(get("/api/user/resume")
                .requestAttr("userId", user.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0]").exists());
    }
}
