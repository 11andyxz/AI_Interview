package com.aiinterview.controller;

import com.aiinterview.model.EvaluationResult;
import com.aiinterview.model.openai.OpenAiMessage;
import com.aiinterview.service.LlmEvaluationService;
import com.aiinterview.service.OpenAiService;
import com.aiinterview.service.PromptService;
import com.aiinterview.session.SessionService;
import com.aiinterview.session.model.InterviewSession;
import com.aiinterview.session.model.QAHistory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import com.aiinterview.config.TestWebMvcConfig;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import reactor.core.publisher.Mono;

import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;

@Import(TestWebMvcConfig.class)
@WebMvcTest(LlmGatewayController.class)
class LlmGatewayControllerTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @MockBean
    private OpenAiService openAiService;
    
    @MockBean
    private PromptService promptService;
    
    @MockBean
    private LlmEvaluationService evaluationService;
    
    @MockBean
    private SessionService sessionService;
    
    @MockBean
    private com.aiinterview.config.WebMvcConfig webMvcConfig;

    @MockBean
    private com.aiinterview.interceptor.AuthInterceptor authInterceptor;

    @MockBean
    private com.aiinterview.service.JwtService jwtService;

    @BeforeEach
    void setUp() {
        when(promptService.buildSystemPrompt(anyString(), anyString(), any())).thenReturn("System prompt");
        when(promptService.buildConversationHistoryPrompt(anyList(), anyInt())).thenReturn("User prompt");
    }
    
    @Test
    void testQuestionGenerate_Success() throws Exception {
        String sessionId = "session-123";
        Map<String, Object> body = new HashMap<>();
        body.put("sessionId", sessionId);
        body.put("roleId", "backend_java");
        body.put("level", "mid");

        InterviewSession session = new InterviewSession();
        session.setHistory(new ArrayList<>());
        when(sessionService.getSession(sessionId)).thenReturn(Optional.of(session));
        when(openAiService.chat(anyList())).thenReturn(Mono.just("What is Java?"));

        mockMvc.perform(post("/api/llm/question-generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sessionId\":\"session-123\",\"roleId\":\"backend_java\",\"level\":\"mid\"}"))
                .andExpect(request().asyncStarted())
                .andDo(result -> mockMvc.perform(asyncDispatch(result)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.question").exists())
                .andExpect(jsonPath("$.sessionId").value(sessionId));

        verify(openAiService).chat(anyList());
    }
    
    @Test
    void testEval_Success() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("question", "What is Java?");
        body.put("answer", "Java is a programming language");
        body.put("roleId", "backend_java");
        body.put("level", "mid");

        EvaluationResult result = new EvaluationResult();
        result.setScore(85.0);
        result.setRubricLevel("good");
        result.setTechnicalAccuracy(8);
        result.setDepth(8);
        result.setExperience(9);
        result.setCommunication(8);
        result.setStrengths(Arrays.asList("Good knowledge"));
        result.setImprovements(Arrays.asList("Could be more detailed"));
        result.setFollowUpQuestions(Arrays.asList("Can you elaborate?"));

        when(evaluationService.evaluateAnswer(anyString(), anyString(), anyString(), anyString()))
            .thenReturn(Mono.just(result));

        mockMvc.perform(post("/api/llm/eval")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"What is Java?\",\"answer\":\"Java is a programming language\",\"roleId\":\"backend_java\",\"level\":\"mid\"}"))
                .andExpect(request().asyncStarted())
                .andDo(result1 -> mockMvc.perform(asyncDispatch(result1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(85.0))
                .andExpect(jsonPath("$.rubricLevel").value("good"));

        verify(evaluationService).evaluateAnswer(anyString(), anyString(), anyString(), anyString());
    }
    
    @Test
    void testChat_Success() throws Exception {
        Map<String, Object> body = new HashMap<>();
        List<Map<String, String>> messages = new ArrayList<>();
        Map<String, String> msg1 = new HashMap<>();
        msg1.put("role", "user");
        msg1.put("content", "Hello");
        messages.add(msg1);
        body.put("messages", messages);

        when(openAiService.chat(anyList())).thenReturn(Mono.just("Hi there!"));

        mockMvc.perform(post("/api/llm/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"messages\":[{\"role\":\"user\",\"content\":\"Hello\"}]}"))
                .andExpect(request().asyncStarted())
                .andDo(result -> mockMvc.perform(asyncDispatch(result)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("Hi there!"));

        verify(openAiService).chat(anyList());
    }

    @Test
    void testChat_EmptyMessages() throws Exception {
        mockMvc.perform(post("/api/llm/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"messages\":[]}"))
                .andExpect(request().asyncStarted())
                .andDo(result -> mockMvc.perform(asyncDispatch(result)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Messages are required"));

        verify(openAiService, never()).chat(anyList());
    }
    
    @Test
    void testHealth_Configured() throws Exception {
        when(openAiService.isConfigured()).thenReturn(true);
        
        mockMvc.perform(get("/api/llm/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(true))
                .andExpect(jsonPath("$.status").value("ready"));
        
        verify(openAiService).isConfigured();
    }
    
    @Test
    void testHealth_NotConfigured() throws Exception {
        when(openAiService.isConfigured()).thenReturn(false);
        
        mockMvc.perform(get("/api/llm/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(false))
                .andExpect(jsonPath("$.status").value("not_configured"));
        
        verify(openAiService).isConfigured();
    }
}

