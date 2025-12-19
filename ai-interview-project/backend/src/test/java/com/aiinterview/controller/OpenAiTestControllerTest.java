package com.aiinterview.controller;

import com.aiinterview.model.openai.OpenAiMessage;
import com.aiinterview.service.OpenAiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OpenAiTestController.class)
class OpenAiTestControllerTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @MockBean
    private OpenAiService openAiService;
    
    @BeforeEach
    void setUp() {
        // Mock successful responses
        when(openAiService.chat(anyList())).thenReturn(Mono.just("Test response"));
    }
    
    @Test
    void testSimpleTest() throws Exception {
        mockMvc.perform(get("/api/test/openai/simple"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").exists())
                .andExpect(jsonPath("$.message").exists());
        
        verify(openAiService).chat(anyList());
    }
    
    @Test
    void testChineseTest() throws Exception {
        mockMvc.perform(get("/api/test/openai/chinese"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").exists())
                .andExpect(jsonPath("$.question").exists());
        
        verify(openAiService).chat(anyList());
    }
    
    @Test
    void testInterviewQuestionTest() throws Exception {
        mockMvc.perform(get("/api/test/openai/interview-question"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").exists())
                .andExpect(jsonPath("$.question").exists());
        
        verify(openAiService).chat(anyList());
    }
    
    @Test
    void testCustomTest() throws Exception {
        String requestBody = "{\"message\":\"Hello\",\"system\":\"You are a helpful assistant\"}";
        
        mockMvc.perform(post("/api/test/openai/custom")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").exists())
                .andExpect(jsonPath("$.request").exists());
        
        verify(openAiService).chat(anyList());
    }
    
    @Test
    void testConfigCheck() throws Exception {
        mockMvc.perform(get("/api/test/openai/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").exists())
                .andExpect(jsonPath("$.model").exists())
                .andExpect(jsonPath("$.apiUrl").exists());
    }
    
    @Test
    void testSimpleTest_Error() throws Exception {
        when(openAiService.chat(anyList()))
            .thenReturn(Mono.error(new RuntimeException("API Error")));
        
        mockMvc.perform(get("/api/test/openai/simple"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.error").exists());
        
        verify(openAiService).chat(anyList());
    }
}

