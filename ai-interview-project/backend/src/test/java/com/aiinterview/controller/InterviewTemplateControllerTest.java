package com.aiinterview.controller;

import com.aiinterview.model.InterviewTemplate;
import com.aiinterview.service.InterviewTemplateService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import com.aiinterview.config.TestWebMvcConfig;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Import(TestWebMvcConfig.class)
@WebMvcTest(InterviewTemplateController.class)
class InterviewTemplateControllerTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @MockBean
    private InterviewTemplateService templateService;
    
    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private com.aiinterview.config.WebMvcConfig webMvcConfig;

    @MockBean
    private com.aiinterview.interceptor.AuthInterceptor authInterceptor;

    @MockBean
    private com.aiinterview.service.JwtService jwtService;

    private InterviewTemplate testTemplate;

    @BeforeEach
    void setUp() {
        testTemplate = new InterviewTemplate();
        testTemplate.setId(1L);
        testTemplate.setUserId(100L);
        testTemplate.setName("Java Backend Template");
        testTemplate.setDescription("Template for Java backend interviews");
    }
    
    @Test
    void testGetTemplates() throws Exception {
        List<InterviewTemplate> templates = Arrays.asList(testTemplate);
        when(templateService.getUserTemplates(100L)).thenReturn(templates);
        
        mockMvc.perform(get("/api/templates")
                .requestAttr("userId", 100L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1L))
                .andExpect(jsonPath("$[0].name").value("Java Backend Template"));
        
        verify(templateService).getUserTemplates(100L);
    }
    
    @Test
    void testGetUserTemplates() throws Exception {
        List<InterviewTemplate> templates = Arrays.asList(testTemplate);
        when(templateService.getUserOwnedTemplates(100L)).thenReturn(templates);
        
        mockMvc.perform(get("/api/templates/my")
                .requestAttr("userId", 100L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1L));
        
        verify(templateService).getUserOwnedTemplates(100L);
    }
    
    @Test
    void testGetTemplate_Success() throws Exception {
        when(templateService.getTemplateById(1L)).thenReturn(Optional.of(testTemplate));
        
        mockMvc.perform(get("/api/templates/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.name").value("Java Backend Template"));
        
        verify(templateService).getTemplateById(1L);
    }
    
    @Test
    void testGetTemplate_NotFound() throws Exception {
        when(templateService.getTemplateById(999L)).thenReturn(Optional.empty());
        
        mockMvc.perform(get("/api/templates/999"))
                .andExpect(status().isNotFound());
        
        verify(templateService).getTemplateById(999L);
    }
    
    @Test
    void testCreateTemplate() throws Exception {
        when(templateService.createTemplate(any(InterviewTemplate.class))).thenReturn(testTemplate);
        
        mockMvc.perform(post("/api/templates")
                .requestAttr("userId", 100L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(testTemplate)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.template.id").value(1L))
                .andExpect(jsonPath("$.message").value("Template created successfully"));
        
        verify(templateService).createTemplate(any(InterviewTemplate.class));
    }
    
    @Test
    void testUpdateTemplate() throws Exception {
        Map<String, Object> updates = new HashMap<>();
        updates.put("name", "Updated Template");
        
        when(templateService.updateTemplate(eq(1L), any(Map.class))).thenReturn(testTemplate);
        
        mockMvc.perform(put("/api/templates/1")
                .requestAttr("userId", 100L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updates)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Template updated successfully"));
        
        verify(templateService).updateTemplate(eq(1L), any(Map.class));
    }
    
    @Test
    void testDeleteTemplate() throws Exception {
        doNothing().when(templateService).deleteTemplate(1L, 100L);
        
        mockMvc.perform(delete("/api/templates/1")
                .requestAttr("userId", 100L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Template deleted successfully"));
        
        verify(templateService).deleteTemplate(1L, 100L);
    }
    
    @Test
    void testUseTemplate() throws Exception {
        doNothing().when(templateService).incrementUsageCount(1L);
        
        mockMvc.perform(post("/api/templates/1/use"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Template usage recorded"));
        
        verify(templateService).incrementUsageCount(1L);
    }
    
    @Test
    void testGetPopularTemplates() throws Exception {
        List<InterviewTemplate> templates = Arrays.asList(testTemplate);
        when(templateService.getPopularTemplates(10)).thenReturn(templates);
        
        mockMvc.perform(get("/api/templates/popular?limit=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1L));
        
        verify(templateService).getPopularTemplates(10);
    }
    
    @Test
    void testSearchTemplates() throws Exception {
        List<InterviewTemplate> templates = Arrays.asList(testTemplate);
        when(templateService.searchTemplates("Java", "mid")).thenReturn(templates);
        
        mockMvc.perform(get("/api/templates/search?techStack=Java&level=mid"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1L));
        
        verify(templateService).searchTemplates("Java", "mid");
    }
}

