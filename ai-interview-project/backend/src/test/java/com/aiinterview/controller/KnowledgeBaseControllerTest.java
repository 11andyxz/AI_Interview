package com.aiinterview.controller;

import com.aiinterview.model.KnowledgeBase;
import com.aiinterview.service.KnowledgeBaseService;
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

@WebMvcTest(KnowledgeBaseController.class)
class KnowledgeBaseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private KnowledgeBaseService knowledgeBaseService;

    @Autowired
    private ObjectMapper objectMapper;

    private Long userId = 1L;
    private Long knowledgeBaseId = 1L;
    private KnowledgeBase testKnowledgeBase;
    private KnowledgeBase systemKnowledgeBase;

    @BeforeEach
    void setUp() {
        testKnowledgeBase = new KnowledgeBase();
        testKnowledgeBase.setId(knowledgeBaseId);
        testKnowledgeBase.setUserId(userId);
        testKnowledgeBase.setType("user");
        testKnowledgeBase.setName("Test Knowledge Base");
        testKnowledgeBase.setDescription("Test description");
        testKnowledgeBase.setContent("{\"skills\": [\"Java\", \"Spring\"]}");
        testKnowledgeBase.setIsActive(true);
        testKnowledgeBase.setCreatedAt(LocalDateTime.now());

        systemKnowledgeBase = new KnowledgeBase();
        systemKnowledgeBase.setId(2L);
        systemKnowledgeBase.setUserId(null);
        systemKnowledgeBase.setType("system");
        systemKnowledgeBase.setName("System Knowledge Base");
        systemKnowledgeBase.setDescription("System description");
        systemKnowledgeBase.setContent("{\"skills\": [\"Python\", \"Django\"]}");
        systemKnowledgeBase.setIsActive(true);
        systemKnowledgeBase.setCreatedAt(LocalDateTime.now());
    }

    @Test
    void testGetKnowledgeBases_All() throws Exception {
        when(knowledgeBaseService.getKnowledgeBases(userId, null))
            .thenReturn(Arrays.asList(testKnowledgeBase, systemKnowledgeBase));

        mockMvc.perform(get("/api/knowledge-base")
                .header("Authorization", "Bearer valid-token")
                .requestAttr("userId", userId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(knowledgeBaseId))
            .andExpect(jsonPath("$[0].name").value("Test Knowledge Base"))
            .andExpect(jsonPath("$[1].type").value("system"));
    }

    @Test
    void testGetKnowledgeBases_ByType_User() throws Exception {
        when(knowledgeBaseService.getKnowledgeBases(userId, "user"))
            .thenReturn(Arrays.asList(testKnowledgeBase));

        mockMvc.perform(get("/api/knowledge-base")
                .param("type", "user")
                .header("Authorization", "Bearer valid-token")
                .requestAttr("userId", userId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].type").value("user"));
    }

    @Test
    void testGetKnowledgeBases_ByType_System() throws Exception {
        when(knowledgeBaseService.getKnowledgeBases(userId, "system"))
            .thenReturn(Arrays.asList(systemKnowledgeBase));

        mockMvc.perform(get("/api/knowledge-base")
                .param("type", "system")
                .header("Authorization", "Bearer valid-token")
                .requestAttr("userId", userId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].type").value("system"));
    }

    @Test
    void testGetKnowledgeBases_Unauthorized() throws Exception {
        mockMvc.perform(get("/api/knowledge-base"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void testGetSystemKnowledgeBases_Success() throws Exception {
        when(knowledgeBaseService.getSystemKnowledgeBases())
            .thenReturn(Arrays.asList(systemKnowledgeBase));

        mockMvc.perform(get("/api/knowledge-base/system"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].type").value("system"))
            .andExpect(jsonPath("$[0].name").value("System Knowledge Base"));
    }

    @Test
    void testGetSystemKnowledgeBases_Empty() throws Exception {
        when(knowledgeBaseService.getSystemKnowledgeBases())
            .thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/knowledge-base/system"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void testGetKnowledgeBaseById_Success() throws Exception {
        when(knowledgeBaseService.getKnowledgeBaseById(knowledgeBaseId, userId))
            .thenReturn(Optional.of(testKnowledgeBase));

        mockMvc.perform(get("/api/knowledge-base/{id}", knowledgeBaseId)
                .header("Authorization", "Bearer valid-token")
                .requestAttr("userId", userId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(knowledgeBaseId))
            .andExpect(jsonPath("$.name").value("Test Knowledge Base"));
    }

    @Test
    void testGetKnowledgeBaseById_NotFound() throws Exception {
        when(knowledgeBaseService.getKnowledgeBaseById(knowledgeBaseId, userId))
            .thenReturn(Optional.empty());

        mockMvc.perform(get("/api/knowledge-base/{id}", knowledgeBaseId)
                .header("Authorization", "Bearer valid-token")
                .requestAttr("userId", userId))
            .andExpect(status().isNotFound());
    }

    @Test
    void testGetKnowledgeBaseById_Unauthorized() throws Exception {
        mockMvc.perform(get("/api/knowledge-base/{id}", knowledgeBaseId))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void testCreateKnowledgeBase_Success() throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("name", "New Knowledge Base");
        request.put("description", "New description");
        request.put("content", "{\"skills\": [\"React\"]}");

        KnowledgeBase newKb = new KnowledgeBase();
        newKb.setId(3L);
        newKb.setUserId(userId);
        newKb.setName("New Knowledge Base");
        newKb.setDescription("New description");
        newKb.setContent("{\"skills\": [\"React\"]}");

        when(knowledgeBaseService.createKnowledgeBase(eq(userId), eq("New Knowledge Base"), 
                eq("New description"), eq("{\"skills\": [\"React\"]}")))
            .thenReturn(newKb);

        mockMvc.perform(post("/api/knowledge-base")
                .header("Authorization", "Bearer valid-token")
                .requestAttr("userId", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.knowledgeBase.name").value("New Knowledge Base"));
    }

    @Test
    void testCreateKnowledgeBase_MissingName() throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("description", "New description");

        mockMvc.perform(post("/api/knowledge-base")
                .header("Authorization", "Bearer valid-token")
                .requestAttr("userId", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("Name is required"));
    }

    @Test
    void testCreateKnowledgeBase_Unauthorized() throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("name", "New Knowledge Base");

        mockMvc.perform(post("/api/knowledge-base")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void testUpdateKnowledgeBase_Success() throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("name", "Updated Knowledge Base");
        request.put("description", "Updated description");
        request.put("content", "{\"skills\": [\"Vue\"]}");

        KnowledgeBase updatedKb = new KnowledgeBase();
        updatedKb.setId(knowledgeBaseId);
        updatedKb.setUserId(userId);
        updatedKb.setName("Updated Knowledge Base");
        updatedKb.setDescription("Updated description");
        updatedKb.setContent("{\"skills\": [\"Vue\"]}");

        when(knowledgeBaseService.updateKnowledgeBase(eq(knowledgeBaseId), eq(userId), 
                eq("Updated Knowledge Base"), eq("Updated description"), eq("{\"skills\": [\"Vue\"]}")))
            .thenReturn(updatedKb);

        mockMvc.perform(put("/api/knowledge-base/{id}", knowledgeBaseId)
                .header("Authorization", "Bearer valid-token")
                .requestAttr("userId", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.knowledgeBase.name").value("Updated Knowledge Base"));
    }

    @Test
    void testUpdateKnowledgeBase_NotFound() throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("name", "Updated Knowledge Base");

        when(knowledgeBaseService.updateKnowledgeBase(eq(knowledgeBaseId), eq(userId), 
                anyString(), any(), any()))
            .thenThrow(new RuntimeException("Knowledge base not found"));

        mockMvc.perform(put("/api/knowledge-base/{id}", knowledgeBaseId)
                .header("Authorization", "Bearer valid-token")
                .requestAttr("userId", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("Knowledge base not found"));
    }

    @Test
    void testUpdateKnowledgeBase_Unauthorized() throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("name", "Updated Knowledge Base");

        mockMvc.perform(put("/api/knowledge-base/{id}", knowledgeBaseId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void testDeleteKnowledgeBase_Success() throws Exception {
        when(knowledgeBaseService.deleteKnowledgeBase(knowledgeBaseId, userId))
            .thenReturn(true);

        mockMvc.perform(delete("/api/knowledge-base/{id}", knowledgeBaseId)
                .header("Authorization", "Bearer valid-token")
                .requestAttr("userId", userId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void testDeleteKnowledgeBase_NotFound() throws Exception {
        when(knowledgeBaseService.deleteKnowledgeBase(knowledgeBaseId, userId))
            .thenReturn(false);

        mockMvc.perform(delete("/api/knowledge-base/{id}", knowledgeBaseId)
                .header("Authorization", "Bearer valid-token")
                .requestAttr("userId", userId))
            .andExpect(status().isNotFound());
    }

    @Test
    void testDeleteKnowledgeBase_SystemKb() throws Exception {
        when(knowledgeBaseService.deleteKnowledgeBase(knowledgeBaseId, userId))
            .thenThrow(new RuntimeException("System knowledge bases cannot be deleted"));

        mockMvc.perform(delete("/api/knowledge-base/{id}", knowledgeBaseId)
                .header("Authorization", "Bearer valid-token")
                .requestAttr("userId", userId))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("System knowledge bases cannot be deleted"));
    }

    @Test
    void testDeleteKnowledgeBase_Unauthorized() throws Exception {
        mockMvc.perform(delete("/api/knowledge-base/{id}", knowledgeBaseId))
            .andExpect(status().isUnauthorized());
    }
}

