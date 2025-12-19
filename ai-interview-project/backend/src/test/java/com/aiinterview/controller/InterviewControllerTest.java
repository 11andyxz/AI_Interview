package com.aiinterview.controller;

import com.aiinterview.service.InterviewSessionService;
import com.aiinterview.service.PdfReportService;
import com.aiinterview.service.ReportService;
import com.aiinterview.model.Interview;
import com.aiinterview.repository.InterviewRepository;
import com.aiinterview.service.AiService;
import com.aiinterview.service.CandidateService;
import com.aiinterview.service.LlmEvaluationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import com.aiinterview.dto.ChatRequest;
import com.aiinterview.dto.CreateInterviewRequest;
import com.aiinterview.dto.QAHistory;
import com.aiinterview.model.Candidate;
import reactor.core.publisher.Mono;

import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(InterviewController.class)
class InterviewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AiService aiService;

    @MockBean
    private InterviewRepository interviewRepository;

    @MockBean
    private CandidateService candidateService;

    @MockBean
    private InterviewSessionService interviewSessionService;

    @MockBean
    private ReportService reportService;

    @MockBean
    private PdfReportService pdfReportService;

    @MockBean
    private LlmEvaluationService llmEvaluationService;

    @Autowired
    private ObjectMapper objectMapper;

    private String interviewId = "test-interview-123";

    @BeforeEach
    void setUp() {
        Interview mockInterview = new Interview();
        mockInterview.setId(interviewId);
        mockInterview.setTitle("Test Interview");
        mockInterview.setLanguage("English");
        mockInterview.setTechStack("Java, Spring");
        mockInterview.setDate(LocalDate.now());
        mockInterview.setStatus("Completed");

        when(interviewRepository.findById(interviewId))
            .thenReturn(java.util.Optional.of(mockInterview));
    }

    @Test
    void testDownloadInterviewReport_Success() throws Exception {
        // Mock PDF report generation
        byte[] mockPdfBytes = "Mock PDF Content".getBytes();
        when(pdfReportService.generatePdfReport(anyString())).thenReturn(mockPdfBytes);

        mockMvc.perform(get("/api/interviews/{id}/report/download", interviewId))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", "application/pdf"))
            .andExpect(header().string("Content-Disposition", "attachment; filename=\"interview-report-" + interviewId + ".pdf\""))
            .andExpect(content().bytes(mockPdfBytes));
    }

    @Test
    void testDownloadInterviewReport_InterviewNotFound() throws Exception {
        when(pdfReportService.generatePdfReport("non-existent-id"))
            .thenThrow(new RuntimeException("Interview not found"));

        mockMvc.perform(get("/api/interviews/{id}/report/download", "non-existent-id"))
            .andExpect(status().isNotFound());
    }

    @Test
    void testDownloadInterviewReport_PdfGenerationError() throws Exception {
        when(pdfReportService.generatePdfReport(anyString()))
            .thenThrow(new RuntimeException("PDF generation failed"));

        mockMvc.perform(get("/api/interviews/{id}/report/download", interviewId))
            .andExpect(status().isNotFound());
    }

    @Test
    void testGetInterviewReport_Success() throws Exception {
        Map<String, Object> mockReport = new HashMap<>();
        mockReport.put("interviewId", interviewId);
        mockReport.put("title", "Test Interview");
        mockReport.put("status", "Completed");
        when(reportService.generateReport(anyString())).thenReturn(mockReport);

        mockMvc.perform(get("/api/interviews/{id}/report", interviewId))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.interviewId").value(interviewId))
            .andExpect(jsonPath("$.title").value("Test Interview"));
    }

    @Test
    void testGetInterviewReportJson_Success() throws Exception {
        Map<String, Object> mockReport = new HashMap<>();
        mockReport.put("interviewId", interviewId);
        mockReport.put("totalQuestions", 5);
        mockReport.put("feedback", "Good performance");
        when(reportService.generateReport(anyString())).thenReturn(mockReport);

        mockMvc.perform(get("/api/interviews/{id}/report/json", interviewId))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.totalQuestions").value(5));
    }

    @Test
    void testGetAllInterviews_Success() throws Exception {
        Interview interview1 = new Interview();
        interview1.setId("interview-1");
        interview1.setTitle("Interview 1");
        Interview interview2 = new Interview();
        interview2.setId("interview-2");
        interview2.setTitle("Interview 2");

        when(interviewRepository.findAll())
            .thenReturn(Arrays.asList(interview1, interview2));

        mockMvc.perform(get("/api/interviews"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value("interview-1"))
            .andExpect(jsonPath("$[1].id").value("interview-2"));
    }

    @Test
    void testCreateInterview_Success() throws Exception {
        CreateInterviewRequest request = new CreateInterviewRequest();
        request.setCandidateId(1);
        request.setPositionType("Backend Java Developer");
        request.setLanguage("English");
        request.setProgrammingLanguages(Arrays.asList("Java", "Spring"));
        request.setUseCustomKnowledge(false);

        Candidate candidate = new Candidate();
        candidate.setId(1);
        candidate.setName("Test Candidate");

        Interview savedInterview = new Interview();
        savedInterview.setId("new-interview-123");
        savedInterview.setTitle("Backend Java Developer");
        savedInterview.setStatus("In Progress");

        Map<String, Object> knowledgeBase = new HashMap<>();
        knowledgeBase.put("skills", Arrays.asList("Java", "Spring"));

        when(candidateService.findById(1)).thenReturn(Optional.of(candidate));
        when(interviewRepository.save(any(Interview.class))).thenReturn(savedInterview);
        when(candidateService.buildKnowledgeBase(any(), anyString(), any(), anyString()))
            .thenReturn(knowledgeBase);

        mockMvc.perform(post("/api/interviews")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.interview.id").value("new-interview-123"))
            .andExpect(jsonPath("$.knowledgeBase").exists());
    }

    @Test
    void testCreateInterview_MissingCandidateId() throws Exception {
        CreateInterviewRequest request = new CreateInterviewRequest();
        request.setPositionType("Backend Java Developer");

        mockMvc.perform(post("/api/interviews")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("candidateId is required"));
    }

    @Test
    void testCreateInterview_CandidateNotFound() throws Exception {
        CreateInterviewRequest request = new CreateInterviewRequest();
        request.setCandidateId(999);

        when(candidateService.findById(999)).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/interviews")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("candidate not found"));
    }

    @Test
    void testStartAiInterview_Success() throws Exception {
        String jobRole = "Backend Developer";
        when(aiService.generateInterviewQuestions(jobRole))
            .thenReturn(Arrays.asList("Question 1", "Question 2"));

        mockMvc.perform(post("/api/interviews/start")
                .contentType(MediaType.APPLICATION_JSON)
                .content("\"" + jobRole + "\""))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value(jobRole))
            .andExpect(jsonPath("$.status").value("In Progress"));
    }

    @Test
    void testStartAiInterview_EmptyRole() throws Exception {
        when(aiService.generateInterviewQuestions(""))
            .thenReturn(Collections.emptyList());

        mockMvc.perform(post("/api/interviews/start")
                .contentType(MediaType.APPLICATION_JSON)
                .content("\"\""))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("New AI Interview"));
    }

    @Test
    void testChatWithAi_Success() throws Exception {
        ChatRequest chatRequest = new ChatRequest();
        chatRequest.setUserMessage("Hello");

        when(interviewSessionService.generatePersonalizedResponse(eq(interviewId), any(ChatRequest.class)))
            .thenReturn(Mono.just("AI Response"));

        mockMvc.perform(post("/api/interviews/{id}/chat", interviewId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(chatRequest)))
            .andExpect(status().isOk())
            .andExpect(content().string("AI Response"));
    }

    @Test
    void testGetInterviewSession_Success() throws Exception {
        Map<String, Object> session = new HashMap<>();
        session.put("interviewId", interviewId);
        session.put("status", "active");

        when(interviewSessionService.getInterviewSession(interviewId))
            .thenReturn(Optional.of(session));

        mockMvc.perform(get("/api/interviews/{id}/session", interviewId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.interviewId").value(interviewId))
            .andExpect(jsonPath("$.status").value("active"));
    }

    @Test
    void testGetInterviewSession_NotFound() throws Exception {
        when(interviewSessionService.getInterviewSession("non-existent"))
            .thenReturn(Optional.empty());

        mockMvc.perform(get("/api/interviews/{id}/session", "non-existent"))
            .andExpect(status().isNotFound());
    }

    @Test
    void testGetChatHistory_Success() throws Exception {
        QAHistory qa1 = new QAHistory();
        qa1.setQuestionText("Question 1");
        qa1.setAnswerText("Answer 1");
        QAHistory qa2 = new QAHistory();
        qa2.setQuestionText("Question 2");
        qa2.setAnswerText("Answer 2");

        when(interviewSessionService.getChatHistory(interviewId))
            .thenReturn(Arrays.asList(qa1, qa2));

        mockMvc.perform(get("/api/interviews/{id}/history", interviewId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].questionText").value("Question 1"))
            .andExpect(jsonPath("$[1].questionText").value("Question 2"));
    }

    @Test
    void testEndInterview_Success() throws Exception {
        Map<String, Object> report = new HashMap<>();
        report.put("interviewId", interviewId);
        report.put("status", "Completed");

        when(reportService.generateReport(interviewId)).thenReturn(report);

        mockMvc.perform(post("/api/interviews/{id}/end", interviewId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.report").exists());
    }

    @Test
    void testEndInterview_NotFound() throws Exception {
        when(interviewRepository.findById("non-existent"))
            .thenReturn(Optional.empty());

        mockMvc.perform(post("/api/interviews/{id}/end", "non-existent"))
            .andExpect(status().isNotFound());
    }

    @Test
    void testUpdateInterview_Success() throws Exception {
        Map<String, Object> updates = new HashMap<>();
        updates.put("title", "Updated Title");
        updates.put("status", "Completed");

        Interview updatedInterview = new Interview();
        updatedInterview.setId(interviewId);
        updatedInterview.setTitle("Updated Title");
        updatedInterview.setStatus("Completed");

        when(interviewRepository.save(any(Interview.class))).thenReturn(updatedInterview);

        mockMvc.perform(put("/api/interviews/{id}", interviewId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updates)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.interview.title").value("Updated Title"));
    }

    @Test
    void testUpdateInterview_NotFound() throws Exception {
        when(interviewRepository.findById("non-existent"))
            .thenReturn(Optional.empty());

        Map<String, Object> updates = new HashMap<>();
        updates.put("title", "Updated Title");

        mockMvc.perform(put("/api/interviews/{id}", "non-existent")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updates)))
            .andExpect(status().isNotFound());
    }

    @Test
    void testDeleteInterview_Success() throws Exception {
        mockMvc.perform(delete("/api/interviews/{id}", interviewId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void testDeleteInterview_NotFound() throws Exception {
        when(interviewRepository.findById("non-existent"))
            .thenReturn(Optional.empty());

        mockMvc.perform(delete("/api/interviews/{id}", "non-existent"))
            .andExpect(status().isNotFound());
    }
}
