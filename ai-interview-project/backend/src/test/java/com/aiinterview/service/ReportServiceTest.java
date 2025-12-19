package com.aiinterview.service;

import com.aiinterview.dto.QAHistory;
import com.aiinterview.model.Interview;
import com.aiinterview.repository.InterviewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {
    
    @Mock
    private InterviewRepository interviewRepository;
    
    @Mock
    private InterviewSessionService interviewSessionService;
    
    @InjectMocks
    private ReportService reportService;
    
    private Interview testInterview;
    private List<QAHistory> testHistory;
    
    @BeforeEach
    void setUp() {
        testInterview = new Interview();
        testInterview.setId("interview-123");
        testInterview.setTitle("Backend Java Developer Interview");
        testInterview.setStatus("Completed");
        testInterview.setDate(LocalDate.now());
        testInterview.setCreatedAt(LocalDateTime.now().minusHours(2));
        testInterview.setUpdatedAt(LocalDateTime.now());
        
        QAHistory qa1 = new QAHistory();
        qa1.setQuestionText("What is Java?");
        qa1.setAnswerText("Java is a programming language");
        
        QAHistory qa2 = new QAHistory();
        qa2.setQuestionText("Explain Spring Boot");
        qa2.setAnswerText("Spring Boot is a framework for building Java applications");
        
        testHistory = Arrays.asList(qa1, qa2);
    }
    
    @Test
    void testGenerateReport_Success() {
        when(interviewRepository.findById("interview-123")).thenReturn(Optional.of(testInterview));
        when(interviewSessionService.getChatHistory("interview-123")).thenReturn(testHistory);
        when(interviewSessionService.buildFeedback(eq("interview-123"), anyString()))
            .thenReturn("Good performance overall");
        
        Map<String, Object> report = reportService.generateReport("interview-123");
        
        assertNotNull(report);
        assertEquals("interview-123", report.get("interviewId"));
        assertEquals("Backend Java Developer Interview", report.get("title"));
        assertEquals("Completed", report.get("status"));
        assertEquals(2, report.get("totalQuestions"));
        assertNotNull(report.get("conversationHistory"));
        assertNotNull(report.get("feedback"));
        assertNotNull(report.get("statistics"));
        assertNotNull(report.get("generatedAt"));
        
        verify(interviewRepository).findById("interview-123");
        verify(interviewSessionService).getChatHistory("interview-123");
        verify(interviewSessionService).buildFeedback(eq("interview-123"), anyString());
    }
    
    @Test
    void testGenerateReport_InterviewNotFound() {
        when(interviewRepository.findById("non-existent")).thenReturn(Optional.empty());
        
        assertThrows(RuntimeException.class, () -> {
            reportService.generateReport("non-existent");
        });
        
        verify(interviewRepository).findById("non-existent");
        verify(interviewSessionService, never()).getChatHistory(anyString());
    }
    
    @Test
    void testGenerateReport_WithEmptyHistory() {
        when(interviewRepository.findById("interview-123")).thenReturn(Optional.of(testInterview));
        when(interviewSessionService.getChatHistory("interview-123")).thenReturn(Arrays.asList());
        when(interviewSessionService.buildFeedback(eq("interview-123"), anyString()))
            .thenReturn("No conversation yet");
        
        Map<String, Object> report = reportService.generateReport("interview-123");
        
        assertNotNull(report);
        assertEquals(0, report.get("totalQuestions"));
        assertNotNull(report.get("statistics"));
        
        @SuppressWarnings("unchecked")
        Map<String, Object> statistics = (Map<String, Object>) report.get("statistics");
        assertEquals(0.0, statistics.get("averageAnswerLength"));
    }
    
    @Test
    void testGenerateReport_CalculateAverageAnswerLength() {
        when(interviewRepository.findById("interview-123")).thenReturn(Optional.of(testInterview));
        when(interviewSessionService.getChatHistory("interview-123")).thenReturn(testHistory);
        when(interviewSessionService.buildFeedback(eq("interview-123"), anyString()))
            .thenReturn("Feedback");
        
        Map<String, Object> report = reportService.generateReport("interview-123");
        
        @SuppressWarnings("unchecked")
        Map<String, Object> statistics = (Map<String, Object>) report.get("statistics");
        assertNotNull(statistics);
        assertTrue((Double) statistics.get("averageAnswerLength") > 0);
    }
    
    @Test
    void testGenerateReport_WithNullTitle() {
        testInterview.setTitle(null);
        when(interviewRepository.findById("interview-123")).thenReturn(Optional.of(testInterview));
        when(interviewSessionService.getChatHistory("interview-123")).thenReturn(testHistory);
        when(interviewSessionService.buildFeedback(eq("interview-123"), eq("general")))
            .thenReturn("Feedback");
        
        Map<String, Object> report = reportService.generateReport("interview-123");
        
        assertNotNull(report);
        verify(interviewSessionService).buildFeedback("interview-123", "general");
    }
}

