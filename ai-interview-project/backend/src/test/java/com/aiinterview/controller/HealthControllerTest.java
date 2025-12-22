package com.aiinterview.controller;

import com.aiinterview.repository.UserRepository;
import com.aiinterview.service.ApiKeyConfigService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import com.aiinterview.config.TestWebMvcConfig;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Import(TestWebMvcConfig.class)
@WebMvcTest(HealthController.class)
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private ApiKeyConfigService apiKeyConfigService;

    @MockBean
    private com.aiinterview.config.WebMvcConfig webMvcConfig;

    @MockBean
    private com.aiinterview.interceptor.AuthInterceptor authInterceptor;

    @MockBean
    private com.aiinterview.service.JwtService jwtService;

    @Test
    void testHealth_Success() throws Exception {
        mockMvc.perform(get("/api/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"))
            .andExpect(jsonPath("$.service").value("ai-interview-backend"));
    }

    @Test
    void testCheckDatabase_Success() throws Exception {
        when(userRepository.count()).thenReturn(5L);
        when(userRepository.existsByUsername("test")).thenReturn(true);
        when(apiKeyConfigService.hasActiveApiKey("openai")).thenReturn(true);

        mockMvc.perform(get("/api/health/db"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("connected"))
            .andExpect(jsonPath("$.database").value("ai_interview"))
            .andExpect(jsonPath("$.userCount").value(5))
            .andExpect(jsonPath("$.testUserExists").value(true))
            .andExpect(jsonPath("$.openaiConfigured").value(true))
            .andExpect(jsonPath("$.message").value("Database connection successful"));
    }

    @Test
    void testCheckDatabase_Error() throws Exception {
        when(userRepository.count()).thenThrow(new RuntimeException("Database connection failed"));

        mockMvc.perform(get("/api/health/db"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.status").value("error"))
            .andExpect(jsonPath("$.message").exists())
            .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void testCheckDatabase_NoTestUser() throws Exception {
        when(userRepository.count()).thenReturn(0L);
        when(userRepository.existsByUsername("test")).thenReturn(false);
        when(apiKeyConfigService.hasActiveApiKey("openai")).thenReturn(false);

        mockMvc.perform(get("/api/health/db"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("connected"))
            .andExpect(jsonPath("$.userCount").value(0))
            .andExpect(jsonPath("$.testUserExists").value(false))
            .andExpect(jsonPath("$.openaiConfigured").value(false));
    }
}

