package com.aiinterview.controller;

import com.aiinterview.model.ApiKeyConfig;
import com.aiinterview.service.ApiKeyConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ApiKeyController.class)
class ApiKeyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ApiKeyConfigService apiKeyConfigService;

    @Autowired
    private ObjectMapper objectMapper;

    private ApiKeyConfig testApiKeyConfig;
    private String serviceName = "openai";
    private String testApiKey = "sk-test12345678901234567890123456789012345678901234567890";

    @BeforeEach
    void setUp() {
        testApiKeyConfig = new ApiKeyConfig();
        testApiKeyConfig.setId(1);
        testApiKeyConfig.setServiceName(serviceName);
        testApiKeyConfig.setApiKey(testApiKey);
        testApiKeyConfig.setIsActive(true);
        testApiKeyConfig.setCreatedAt(LocalDateTime.now());
        testApiKeyConfig.setUpdatedAt(LocalDateTime.now());
    }

    @Test
    void testGetApiKeyStatus_Configured() throws Exception {
        when(apiKeyConfigService.hasActiveApiKey(serviceName)).thenReturn(true);

        mockMvc.perform(get("/api/admin/keys/status"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.openaiConfigured").value(true))
            .andExpect(jsonPath("$.status").value("configured"));
    }

    @Test
    void testGetApiKeyStatus_NotConfigured() throws Exception {
        when(apiKeyConfigService.hasActiveApiKey(serviceName)).thenReturn(false);

        mockMvc.perform(get("/api/admin/keys/status"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.openaiConfigured").value(false))
            .andExpect(jsonPath("$.status").value("not_configured"));
    }

    @Test
    void testGetAllApiKeys_Success() throws Exception {
        ApiKeyConfig config2 = new ApiKeyConfig();
        config2.setId(2);
        config2.setServiceName("anthropic");
        config2.setApiKey("sk-ant-test");
        config2.setIsActive(false);

        when(apiKeyConfigService.findAll())
            .thenReturn(Arrays.asList(testApiKeyConfig, config2));

        mockMvc.perform(get("/api/admin/keys"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(1))
            .andExpect(jsonPath("$[0].serviceName").value(serviceName))
            .andExpect(jsonPath("$[1].serviceName").value("anthropic"));
    }

    @Test
    void testGetAllApiKeys_Empty() throws Exception {
        when(apiKeyConfigService.findAll())
            .thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/admin/keys"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void testGetActiveApiKey_HasKey() throws Exception {
        when(apiKeyConfigService.getActiveApiKey(serviceName))
            .thenReturn(Optional.of(testApiKey));

        mockMvc.perform(get("/api/admin/keys/{serviceName}/active", serviceName))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.serviceName").value(serviceName))
            .andExpect(jsonPath("$.hasKey").value(true))
            .andExpect(jsonPath("$.maskedKey").exists());
    }

    @Test
    void testGetActiveApiKey_NoKey() throws Exception {
        when(apiKeyConfigService.getActiveApiKey(serviceName))
            .thenReturn(Optional.empty());

        mockMvc.perform(get("/api/admin/keys/{serviceName}/active", serviceName))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.serviceName").value(serviceName))
            .andExpect(jsonPath("$.hasKey").value(false));
    }

    @Test
    void testSetApiKey_Success() throws Exception {
        Map<String, String> request = new HashMap<>();
        request.put("apiKey", testApiKey);

        when(apiKeyConfigService.save(any(ApiKeyConfig.class)))
            .thenReturn(testApiKeyConfig);

        mockMvc.perform(post("/api/admin/keys/{serviceName}", serviceName)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.serviceName").value(serviceName))
            .andExpect(jsonPath("$.isActive").value(true));
    }

    @Test
    void testSetApiKey_EmptyKey() throws Exception {
        Map<String, String> request = new HashMap<>();
        request.put("apiKey", "");

        mockMvc.perform(post("/api/admin/keys/{serviceName}", serviceName)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void testSetApiKey_MissingKey() throws Exception {
        Map<String, String> request = new HashMap<>();

        mockMvc.perform(post("/api/admin/keys/{serviceName}", serviceName)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void testDeleteApiKey_Success() throws Exception {
        Integer id = 1;
        // The controller implementation is a placeholder, so we just test the endpoint exists
        mockMvc.perform(delete("/api/admin/keys/{id}", id))
            .andExpect(status().isOk());
    }

    @Test
    void testTestApiKey_Configured() throws Exception {
        when(apiKeyConfigService.hasActiveApiKey(serviceName)).thenReturn(true);

        mockMvc.perform(post("/api/admin/keys/{serviceName}/test", serviceName))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.serviceName").value(serviceName))
            .andExpect(jsonPath("$.configured").value(true))
            .andExpect(jsonPath("$.status").value("API key is configured"));
    }

    @Test
    void testTestApiKey_NotConfigured() throws Exception {
        when(apiKeyConfigService.hasActiveApiKey(serviceName)).thenReturn(false);

        mockMvc.perform(post("/api/admin/keys/{serviceName}/test", serviceName))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.serviceName").value(serviceName))
            .andExpect(jsonPath("$.configured").value(false))
            .andExpect(jsonPath("$.status").value("No active API key found"));
    }
}

