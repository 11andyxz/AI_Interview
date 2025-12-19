package com.aiinterview.controller;

import com.aiinterview.model.MockInterview;
import com.aiinterview.model.MockInterviewMessage;
import com.aiinterview.service.MockInterviewService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MockInterviewController.class)
class MockInterviewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MockInterviewService mockInterviewService;

    private String mockInterviewId = "mock-interview-123";
    private Long userId = 1L;
    private MockInterview mockInterview;

    @BeforeEach
    void setUp() {
        mockInterview = new MockInterview();
        mockInterview.setId(mockInterviewId);
        mockInterview.setUserId(userId);
        mockInterview.setTitle("Test Mock Interview");
        mockInterview.setPositionType("Backend Developer");
        mockInterview.setStatus("practice");
    }

    @Test
    void testGetMockInterviews_Success() throws Exception {
        when(mockInterviewService.getUserMockInterviews(userId, null))
            .thenReturn(Arrays.asList(mockInterview));

        mockMvc.perform(get("/api/mock-interviews")
                .requestAttr("userId", userId))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$[0].id").value(mockInterviewId))
            .andExpect(jsonPath("$[0].title").value("Test Mock Interview"));
    }

    @Test
    void testGetMockInterviews_WithStatusFilter() throws Exception {
        when(mockInterviewService.getUserMockInterviews(userId, "completed"))
            .thenReturn(Arrays.asList(mockInterview));

        mockMvc.perform(get("/api/mock-interviews")
                .param("status", "completed")
                .requestAttr("userId", userId))
            .andExpect(status().isOk());
    }

    @Test
    void testGetMockInterviewById_Success() throws Exception {
        MockInterviewMessage message = new MockInterviewMessage();
        message.setQuestionText("Test question");
        message.setAnswerText("Test answer");

        when(mockInterviewService.getMockInterviewById(mockInterviewId, userId))
            .thenReturn(java.util.Optional.of(mockInterview));
        when(mockInterviewService.getMockInterviewMessages(mockInterviewId))
            .thenReturn(Arrays.asList(message));

        mockMvc.perform(get("/api/mock-interviews/{id}", mockInterviewId)
                .requestAttr("userId", userId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.mockInterview.id").value(mockInterviewId))
            .andExpect(jsonPath("$.messages[0].questionText").value("Test question"));
    }

    @Test
    void testGetMockInterviewById_NotFound() throws Exception {
        when(mockInterviewService.getMockInterviewById("non-existent", userId))
            .thenReturn(java.util.Optional.empty());

        mockMvc.perform(get("/api/mock-interviews/{id}", "non-existent")
                .requestAttr("userId", userId))
            .andExpect(status().isNotFound());
    }

    @Test
    void testCreateMockInterview_Success() throws Exception {
        when(mockInterviewService.createMockInterview(any(), anyString(), anyString(), anyString(), anyString()))
            .thenReturn(mockInterview);

        String requestBody = """
            {
                "title": "Test Interview",
                "positionType": "Backend Developer",
                "programmingLanguages": "[\\"Java\\", \\"Spring\\"]",
                "language": "English"
            }
            """;

        mockMvc.perform(post("/api/mock-interviews")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
                .requestAttr("userId", userId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.mockInterview.id").value(mockInterviewId));
    }

    @Test
    void testRetryMockInterview_Success() throws Exception {
        when(mockInterviewService.retryMockInterview(mockInterviewId, userId))
            .thenReturn(mockInterview);

        mockMvc.perform(post("/api/mock-interviews/{id}/retry", mockInterviewId)
                .requestAttr("userId", userId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void testRetryMockInterview_NotFound() throws Exception {
        when(mockInterviewService.retryMockInterview("non-existent", userId))
            .thenThrow(new RuntimeException("Mock interview not found"));

        mockMvc.perform(post("/api/mock-interviews/{id}/retry", "non-existent")
                .requestAttr("userId", userId))
            .andExpect(status().isBadRequest());
    }

    @Test
    void testGetHints_Success() throws Exception {
        String expectedHint = "Consider the key concepts and best practices related to this topic.";
        when(mockInterviewService.getHint(mockInterviewId, 0L)).thenReturn(expectedHint);

        mockMvc.perform(get("/api/mock-interviews/{id}/hints", mockInterviewId)
                .requestAttr("userId", userId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.hint").value(expectedHint));
    }

    @Test
    void testGetHints_WithQuestionIndex() throws Exception {
        String expectedHint = "Think about time and space complexity.";
        when(mockInterviewService.getHint(mockInterviewId, 1L)).thenReturn(expectedHint);

        mockMvc.perform(get("/api/mock-interviews/{id}/hints", mockInterviewId)
                .param("questionIndex", "1")
                .requestAttr("userId", userId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.hint").value(expectedHint));
    }

    @Test
    void testGetHints_ServiceError() throws Exception {
        when(mockInterviewService.getHint(mockInterviewId, 0L))
            .thenThrow(new RuntimeException("Service error"));

        mockMvc.perform(get("/api/mock-interviews/{id}/hints", mockInterviewId)
                .requestAttr("userId", userId))
            .andExpect(status().isInternalServerError());
    }

    @Test
    void testGetHints_Unauthorized() throws Exception {
        mockMvc.perform(get("/api/mock-interviews/{id}/hints", mockInterviewId))
            .andExpect(status().isUnauthorized());
    }
}
