package com.aiinterview.controller;

import com.aiinterview.model.CustomQuestionSet;
import com.aiinterview.service.CustomQuestionSetService;
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
@WebMvcTest(CustomQuestionSetController.class)
class CustomQuestionSetControllerTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @MockBean
    private CustomQuestionSetService questionSetService;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    private CustomQuestionSet testQuestionSet;
    
    @MockBean
    private com.aiinterview.config.WebMvcConfig webMvcConfig;

    @MockBean
    private com.aiinterview.interceptor.AuthInterceptor authInterceptor;

    @MockBean
    private com.aiinterview.service.JwtService jwtService;

    @BeforeEach
    void setUp() {
        testQuestionSet = new CustomQuestionSet();
        testQuestionSet.setId(1L);
        testQuestionSet.setUserId(100L);
        testQuestionSet.setName("Java Questions");
        testQuestionSet.setDescription("Java interview questions");
        testQuestionSet.setQuestions(Arrays.asList("What is Java?", "Explain OOP"));
    }
    
    @Test
    void testGetQuestionSets() throws Exception {
        List<CustomQuestionSet> questionSets = Arrays.asList(testQuestionSet);
        when(questionSetService.getUserQuestionSets(100L)).thenReturn(questionSets);
        
        mockMvc.perform(get("/api/question-sets")
                .requestAttr("userId", 100L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1L))
                .andExpect(jsonPath("$[0].name").value("Java Questions"));
        
        verify(questionSetService).getUserQuestionSets(100L);
    }
    
    @Test
    void testGetUserQuestionSets() throws Exception {
        List<CustomQuestionSet> questionSets = Arrays.asList(testQuestionSet);
        when(questionSetService.getUserOwnedQuestionSets(100L)).thenReturn(questionSets);
        
        mockMvc.perform(get("/api/question-sets/my")
                .requestAttr("userId", 100L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1L));
        
        verify(questionSetService).getUserOwnedQuestionSets(100L);
    }
    
    @Test
    void testGetQuestionSet_Success() throws Exception {
        when(questionSetService.getQuestionSetById(1L)).thenReturn(Optional.of(testQuestionSet));
        
        mockMvc.perform(get("/api/question-sets/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.name").value("Java Questions"));
        
        verify(questionSetService).getQuestionSetById(1L);
    }
    
    @Test
    void testGetQuestionSet_NotFound() throws Exception {
        when(questionSetService.getQuestionSetById(999L)).thenReturn(Optional.empty());
        
        mockMvc.perform(get("/api/question-sets/999"))
                .andExpect(status().isNotFound());
        
        verify(questionSetService).getQuestionSetById(999L);
    }
    
    @Test
    void testCreateQuestionSet() throws Exception {
        when(questionSetService.createQuestionSet(any(CustomQuestionSet.class))).thenReturn(testQuestionSet);
        
        mockMvc.perform(post("/api/question-sets")
                .requestAttr("userId", 100L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(testQuestionSet)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questionSet.id").value(1L))
                .andExpect(jsonPath("$.message").value("Question set created successfully"));
        
        verify(questionSetService).createQuestionSet(any(CustomQuestionSet.class));
    }
    
    @Test
    void testUpdateQuestionSet() throws Exception {
        Map<String, Object> updates = new HashMap<>();
        updates.put("name", "Updated Name");
        
        when(questionSetService.updateQuestionSet(eq(1L), any(Map.class))).thenReturn(testQuestionSet);
        
        mockMvc.perform(put("/api/question-sets/1")
                .requestAttr("userId", 100L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updates)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Question set updated successfully"));
        
        verify(questionSetService).updateQuestionSet(eq(1L), any(Map.class));
    }
    
    @Test
    void testDeleteQuestionSet() throws Exception {
        doNothing().when(questionSetService).deleteQuestionSet(1L, 100L);
        
        mockMvc.perform(delete("/api/question-sets/1")
                .requestAttr("userId", 100L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Question set deleted successfully"));
        
        verify(questionSetService).deleteQuestionSet(1L, 100L);
    }
    
    @Test
    void testUseQuestionSet() throws Exception {
        doNothing().when(questionSetService).incrementUsageCount(1L);
        
        mockMvc.perform(post("/api/question-sets/1/use"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Question set usage recorded"));
        
        verify(questionSetService).incrementUsageCount(1L);
    }
    
    @Test
    void testAddQuestionsToSet() throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("questions", Arrays.asList("New Question 1", "New Question 2"));
        
        when(questionSetService.addQuestionsToSet(eq(1L), any(List.class))).thenReturn(testQuestionSet);
        
        mockMvc.perform(post("/api/question-sets/1/questions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Questions added successfully"));
        
        verify(questionSetService).addQuestionsToSet(eq(1L), any(List.class));
    }
    
    @Test
    void testGetPopularQuestionSets() throws Exception {
        List<CustomQuestionSet> questionSets = Arrays.asList(testQuestionSet);
        when(questionSetService.getPopularQuestionSets(10)).thenReturn(questionSets);
        
        mockMvc.perform(get("/api/question-sets/popular?limit=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1L));
        
        verify(questionSetService).getPopularQuestionSets(10);
    }
    
    @Test
    void testSearchQuestionSets() throws Exception {
        List<CustomQuestionSet> questionSets = Arrays.asList(testQuestionSet);
        when(questionSetService.searchQuestionSets("Java", "mid")).thenReturn(questionSets);
        
        mockMvc.perform(get("/api/question-sets/search?techStack=Java&level=mid"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1L));
        
        verify(questionSetService).searchQuestionSets("Java", "mid");
    }
}

