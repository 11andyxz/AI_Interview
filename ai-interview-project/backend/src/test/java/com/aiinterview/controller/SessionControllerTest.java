package com.aiinterview.controller;

import com.aiinterview.knowledge.model.QuestionItem;
import com.aiinterview.session.SessionService;
import com.aiinterview.session.model.InterviewSession;
import com.aiinterview.session.model.QAHistory;
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

import java.time.LocalDateTime;
import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Import(TestWebMvcConfig.class)
@WebMvcTest(SessionController.class)
class SessionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SessionService sessionService;

    @Autowired
    private ObjectMapper objectMapper;

    private String sessionId = "test-session-123";

    @MockBean
    private com.aiinterview.config.WebMvcConfig webMvcConfig;

    @MockBean
    private com.aiinterview.interceptor.AuthInterceptor authInterceptor;

    @MockBean
    private com.aiinterview.service.JwtService jwtService;

    private InterviewSession testSession;
    private QuestionItem testQuestion;
    private QAHistory testQAHistory;

    @BeforeEach
    void setUp() {
        testSession = new InterviewSession();
        testSession.setId(sessionId);
        testSession.setRoleId("backend_java");
        testSession.setLevel("mid");
        testSession.setSkills(Arrays.asList("Java", "Spring"));
        testSession.setStatus("ACTIVE");
        testSession.setCreatedAt(LocalDateTime.now());

        testQuestion = new QuestionItem();
        testQuestion.setId("q1");
        testQuestion.setText("What is Spring Boot?");
        testQuestion.setType("technical");
        testQuestion.setDifficulty("mid");
        testQuestion.setSkills(Arrays.asList("Java", "Spring"));

        testQAHistory = new QAHistory();
        testQAHistory.setQuestionId("q1");
        testQAHistory.setQuestionText("What is Spring Boot?");
        testQAHistory.setAnswerText("Spring Boot is a framework for building Java applications.");
        testQAHistory.setRubricLevel("excellent");
        testQAHistory.setEvalComment("Good depth and completeness.");
    }

    @Test
    void testCreateSession_Success() throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("roleId", "backend_java");
        request.put("level", "mid");
        request.put("skills", Arrays.asList("Java", "Spring"));

        when(sessionService.createSession(eq("backend_java"), eq("mid"), anyList()))
            .thenReturn(testSession);

        mockMvc.perform(post("/api/sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(sessionId))
            .andExpect(jsonPath("$.roleId").value("backend_java"))
            .andExpect(jsonPath("$.level").value("mid"))
            .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void testCreateSession_WithDefaults() throws Exception {
        Map<String, Object> request = new HashMap<>();
        // Empty request should use defaults

        when(sessionService.createSession(eq("backend_java"), eq("mid"), anyList()))
            .thenReturn(testSession);

        mockMvc.perform(post("/api/sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").exists());
    }

    @Test
    void testGetSession_Success() throws Exception {
        when(sessionService.getSession(sessionId))
            .thenReturn(Optional.of(testSession));

        mockMvc.perform(get("/api/sessions/{id}", sessionId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(sessionId))
            .andExpect(jsonPath("$.roleId").value("backend_java"));
    }

    @Test
    void testGetSession_NotFound() throws Exception {
        when(sessionService.getSession("non-existent"))
            .thenReturn(Optional.empty());

        mockMvc.perform(get("/api/sessions/{id}", "non-existent"))
            .andExpect(status().isNotFound());
    }

    @Test
    void testNextQuestion_Success() throws Exception {
        when(sessionService.pickNextQuestion(sessionId))
            .thenReturn(Optional.of(testQuestion));

        mockMvc.perform(post("/api/sessions/{id}/next-question", sessionId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("q1"))
            .andExpect(jsonPath("$.text").value("What is Spring Boot?"));
    }

    @Test
    void testNextQuestion_NoMoreQuestions() throws Exception {
        when(sessionService.pickNextQuestion(sessionId))
            .thenReturn(Optional.empty());

        mockMvc.perform(post("/api/sessions/{id}/next-question", sessionId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("No more questions"));
    }

    @Test
    void testAnswer_Success() throws Exception {
        Map<String, String> request = new HashMap<>();
        request.put("questionId", "q1");
        request.put("questionText", "What is Spring Boot?");
        request.put("answerText", "Spring Boot is a framework for building Java applications.");

        when(sessionService.recordAnswer(eq(sessionId), any(QuestionItem.class), eq("Spring Boot is a framework for building Java applications.")))
            .thenReturn(testQAHistory);

        mockMvc.perform(post("/api/sessions/{id}/answer", sessionId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.questionId").value("q1"))
            .andExpect(jsonPath("$.answerText").value("Spring Boot is a framework for building Java applications."))
            .andExpect(jsonPath("$.rubricLevel").value("excellent"));
    }

    @Test
    void testAnswer_SessionNotFound() throws Exception {
        Map<String, String> request = new HashMap<>();
        request.put("questionId", "q1");
        request.put("questionText", "What is Spring Boot?");
        request.put("answerText", "Answer text");

        when(sessionService.recordAnswer(eq("non-existent"), any(QuestionItem.class), anyString()))
            .thenThrow(new IllegalArgumentException("Session not found"));

        mockMvc.perform(post("/api/sessions/{id}/answer", "non-existent")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void testFeedback_Success() throws Exception {
        String feedback = "Overall feedback:\n- Q: What is Spring Boot? | Eval: excellent\n";

        when(sessionService.buildFeedback(sessionId))
            .thenReturn(feedback);

        mockMvc.perform(post("/api/sessions/{id}/feedback", sessionId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.feedback").value(feedback));
    }

    @Test
    void testFeedback_SessionNotFound() throws Exception {
        when(sessionService.buildFeedback("non-existent"))
            .thenThrow(new IllegalArgumentException("Session not found"));

        mockMvc.perform(post("/api/sessions/{id}/feedback", "non-existent"))
            .andExpect(status().isBadRequest());
    }
}

