package com.aiinterview.controller;

import com.aiinterview.interceptor.AuthInterceptor;
import com.aiinterview.service.SkillTrackingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import jakarta.servlet.http.HttpServletRequest;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SkillController.class)
class SkillControllerTest {
    
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SkillTrackingService skillTrackingService;

    @MockBean
    private AuthInterceptor authInterceptor;
    
    private Map<String, Object> testProgress;
    private Map<String, Object> testRecommendations;
    private Map<String, Object> testTrends;
    
    @BeforeEach
    void setUp() {
        testProgress = new HashMap<>();
        testProgress.put("totalSkills", 10);
        testProgress.put("masteredSkills", 5);
        testProgress.put("inProgressSkills", 3);
        
        testRecommendations = new HashMap<>();
        testRecommendations.put("recommendedSkills", java.util.Arrays.asList("Java", "Spring Boot"));
        
        testTrends = new HashMap<>();
        testTrends.put("improvementRate", 15.5);
        testTrends.put("trend", "increasing");
    }
    
    @Test
    void testGetSkillProgress() throws Exception {
        when(skillTrackingService.getSkillProgress(100L)).thenReturn(testProgress);
        when(authInterceptor.preHandle(any(), any(), any())).then(invocation -> {
            HttpServletRequest request = invocation.getArgument(0);
            request.setAttribute("userId", 100L);
            return true;
        });

        mockMvc.perform(get("/api/skills/progress")
                .header("Authorization", "Bearer valid.jwt.token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSkills").value(10))
                .andExpect(jsonPath("$.masteredSkills").value(5));

        verify(skillTrackingService).getSkillProgress(100L);
    }
    
    @Test
    void testGetSkillRecommendations() throws Exception {
        when(skillTrackingService.getSkillRecommendations(100L)).thenReturn(testRecommendations);
        when(authInterceptor.preHandle(any(), any(), any())).then(invocation -> {
            HttpServletRequest request = invocation.getArgument(0);
            request.setAttribute("userId", 100L);
            return true;
        });

        mockMvc.perform(get("/api/skills/recommendations")
                .header("Authorization", "Bearer valid.jwt.token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendedSkills").isArray());

        verify(skillTrackingService).getSkillRecommendations(100L);
    }
    
    @Test
    void testGetSkillTrends() throws Exception {
        when(skillTrackingService.getSkillTrends(100L)).thenReturn(testTrends);
        when(authInterceptor.preHandle(any(), any(), any())).then(invocation -> {
            HttpServletRequest request = invocation.getArgument(0);
            request.setAttribute("userId", 100L);
            return true;
        });

        mockMvc.perform(get("/api/skills/trends")
                .header("Authorization", "Bearer valid.jwt.token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.improvementRate").value(15.5))
                .andExpect(jsonPath("$.trend").value("increasing"));

        verify(skillTrackingService).getSkillTrends(100L);
    }
    
    @Test
    void testGetSkillProgress_Error() throws Exception {
        when(skillTrackingService.getSkillProgress(100L))
            .thenThrow(new RuntimeException("Service error"));
        when(authInterceptor.preHandle(any(), any(), any())).then(invocation -> {
            HttpServletRequest request = invocation.getArgument(0);
            request.setAttribute("userId", 100L);
            return true;
        });

        mockMvc.perform(get("/api/skills/progress")
                .header("Authorization", "Bearer valid.jwt.token"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").exists());

        verify(skillTrackingService).getSkillProgress(100L);
    }
}

