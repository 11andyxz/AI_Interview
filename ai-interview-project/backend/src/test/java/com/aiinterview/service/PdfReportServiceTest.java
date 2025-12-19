package com.aiinterview.service;

import com.aiinterview.dto.QAHistory;
import com.aiinterview.model.Interview;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PdfReportServiceTest {

    @Mock
    private ReportService reportService;

    @InjectMocks
    private PdfReportService pdfReportService;

    private Map<String, Object> mockReportData;
    private Interview mockInterview;

    @BeforeEach
    void setUp() {
        // Setup mock interview
        mockInterview = new Interview();
        mockInterview.setTitle("Test Interview");
        mockInterview.setLanguage("English");
        mockInterview.setTechStack("Java, Spring");
        mockInterview.setStatus("Completed");

        // Setup mock report data
        mockReportData = new HashMap<>();
        mockReportData.put("interviewId", "test-interview-123");
        mockReportData.put("title", "Test Interview");
        mockReportData.put("status", "Completed");
        mockReportData.put("language", "English");
        mockReportData.put("techStack", "Java, Spring");
        mockReportData.put("date", LocalDate.now());

        // Mock conversation history
        QAHistory qa1 = new QAHistory("What is Java?", "Java is a programming language.");
        QAHistory qa2 = new QAHistory("Explain Spring?", "Spring is a framework for Java.");
        List<QAHistory> history = Arrays.asList(qa1, qa2);
        mockReportData.put("conversationHistory", history);

        // Mock statistics
        Map<String, Object> statistics = new HashMap<>();
        statistics.put("totalExchanges", 2);
        statistics.put("averageAnswerLength", 25.0);
        mockReportData.put("statistics", statistics);

        mockReportData.put("feedback", "Good performance overall.");
    }

    @Test
    void testGeneratePdfReport_Success() throws IOException {
        // Arrange
        when(reportService.generateReport(anyString())).thenReturn(mockReportData);

        // Act
        byte[] pdfBytes = pdfReportService.generatePdfReport("test-interview-123");

        // Assert
        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 0);

        // Verify the PDF content can be loaded
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            assertTrue(document.getNumberOfPages() > 0);

            // Extract text and verify content
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);

            assertTrue(text.contains("AI Interview Report"));
            assertTrue(text.contains("Test Interview"));
            assertTrue(text.contains("Java"));
            assertTrue(text.contains("Good performance overall"));
        }

        verify(reportService, times(1)).generateReport("test-interview-123");
    }

    @Test
    void testGeneratePdfReport_WithEmptyHistory() throws IOException {
        // Arrange
        mockReportData.put("conversationHistory", Arrays.asList());
        when(reportService.generateReport(anyString())).thenReturn(mockReportData);

        // Act
        byte[] pdfBytes = pdfReportService.generatePdfReport("test-interview-123");

        // Assert
        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 0);

        // Verify the PDF contains expected text for empty history
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);

            assertTrue(text.contains("Conversation History"));
        }
    }

    @Test
    void testGeneratePdfReport_WithNullStatistics() throws IOException {
        // Arrange
        mockReportData.put("statistics", null);
        when(reportService.generateReport(anyString())).thenReturn(mockReportData);

        // Act
        byte[] pdfBytes = pdfReportService.generatePdfReport("test-interview-123");

        // Assert
        assertNotNull(pdfBytes);

        // Verify PDF can be generated even with null statistics
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            assertTrue(document.getNumberOfPages() > 0);
        }
    }

    @Test
    void testGeneratePdfReport_WithLongContent() throws IOException {
        // Arrange
        String longAnswer = "This is a very long answer that should test the text wrapping functionality. ".repeat(20);
        QAHistory longQA = new QAHistory("Long question?", longAnswer);
        mockReportData.put("conversationHistory", Arrays.asList(longQA));

        String longFeedback = "This is a comprehensive feedback that provides detailed analysis of the candidate's performance. ".repeat(10);
        mockReportData.put("feedback", longFeedback);

        when(reportService.generateReport(anyString())).thenReturn(mockReportData);

        // Act
        byte[] pdfBytes = pdfReportService.generatePdfReport("test-interview-123");

        // Assert
        assertNotNull(pdfBytes);

        // Verify PDF can handle long content
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            assertTrue(document.getNumberOfPages() >= 1);
        }
    }

    @Test
    void testGeneratePdfReport_ReportServiceThrowsException() {
        // Arrange
        when(reportService.generateReport(anyString()))
            .thenThrow(new RuntimeException("Report generation failed"));

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
            pdfReportService.generatePdfReport("test-interview-123"));

        assertTrue(exception.getMessage().contains("Report generation failed"));
    }

    @Test
    void testGeneratePdfReport_InvalidInterviewId() {
        // Arrange
        when(reportService.generateReport("invalid-id"))
            .thenThrow(new RuntimeException("Interview not found"));

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
            pdfReportService.generatePdfReport("invalid-id"));

        assertTrue(exception.getMessage().contains("Interview not found"));
    }
}
