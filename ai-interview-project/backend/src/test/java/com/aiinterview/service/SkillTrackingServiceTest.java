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
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SkillTrackingServiceTest {

    @Mock
    private InterviewRepository interviewRepository;

    @Mock
    private InterviewSessionService interviewSessionService;

    @InjectMocks
    private SkillTrackingService skillTrackingService;

    private Interview mockInterview1;
    private Interview mockInterview2;
    private QAHistory mockQA1;
    private QAHistory mockQA2;

    @BeforeEach
    void setUp() {
        mockInterview1 = createMockInterview("interview1", LocalDate.now().minusDays(10));
        mockInterview2 = createMockInterview("interview2", LocalDate.now().minusDays(5));

        mockQA1 = new QAHistory("Question 1", "Answer 1");
        mockQA1.setScore(8.5);
        Map<String, Integer> scores1 = Map.of(
            "technicalAccuracy", 8,
            "depth", 7,
            "experience", 9,
            "communication", 8
        );
        mockQA1.setDetailedScores(scores1);

        mockQA2 = new QAHistory("Question 2", "Answer 2");
        mockQA2.setScore(7.0);
        Map<String, Integer> scores2 = Map.of(
            "technicalAccuracy", 7,
            "depth", 8,
            "experience", 6,
            "communication", 7
        );
        mockQA2.setDetailedScores(scores2);
    }

    @Test
    void getSkillProgress_NoInterviews() {
        // Given
        when(interviewRepository.findByCandidateId(1L)).thenReturn(List.of());

        // When
        Map<String, Object> result = skillTrackingService.getSkillProgress(1L);

        // Then
        assertNotNull(result);
        assertEquals(0, result.get("totalInterviews"));
        assertTrue(((Map) result.get("skillAverages")).isEmpty());
    }

    @Test
    void getSkillProgress_WithInterviews() {
        // Given
        List<Interview> interviews = List.of(mockInterview1, mockInterview2);
        when(interviewRepository.findByCandidateId(1L)).thenReturn(interviews);
        when(interviewSessionService.getChatHistory("interview1")).thenReturn(List.of(mockQA1));
        when(interviewSessionService.getChatHistory("interview2")).thenReturn(List.of(mockQA2));

        // When
        Map<String, Object> result = skillTrackingService.getSkillProgress(1L);

        // Then
        assertNotNull(result);
        assertEquals(2, result.get("totalInterviews"));

        @SuppressWarnings("unchecked")
        Map<String, Double> skillAverages = (Map<String, Double>) result.get("skillAverages");
        assertNotNull(skillAverages);
        assertNotNull(skillAverages);
        assertTrue(skillAverages.containsKey("technicalAccuracy"));
        assertTrue(skillAverages.containsKey("communication"));

        // Verify skill averages are calculated correctly
        assertEquals(7.5, skillAverages.get("technicalAccuracy"), 0.1);
    }

    @Test
    void getSkillRecommendations_HighPriority() {
        // Given
        List<Interview> interviews = List.of(mockInterview1);
        when(interviewRepository.findByCandidateId(1L)).thenReturn(interviews);
        when(interviewSessionService.getChatHistory("interview1")).thenReturn(List.of(mockQA1));

        // When
        Map<String, Object> result = skillTrackingService.getSkillRecommendations(1L);

        // Then
        assertNotNull(result);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> recommendations = (List<Map<String, Object>>) result.get("recommendations");
        assertNotNull(recommendations);
        // Should have recommendations based on scores below threshold
    }

    @Test
    void getSkillTrends_WithMultipleMonths() {
        // Given
        Interview oldInterview = createMockInterview("old", LocalDate.now().minusMonths(2));
        Interview newInterview = createMockInterview("new", LocalDate.now().minusMonths(1));

        List<Interview> interviews = List.of(oldInterview, newInterview);
        when(interviewRepository.findByCandidateId(1L)).thenReturn(interviews);

        // Mock QA history with different scores
        QAHistory oldQA = new QAHistory("Q", "A");
        oldQA.setScore(6.0);
        Map<String, Integer> oldScores = Map.of("technicalAccuracy", 6);
        oldQA.setDetailedScores(oldScores);

        QAHistory newQA = new QAHistory("Q", "A");
        newQA.setScore(8.0);
        Map<String, Integer> newScores = Map.of("technicalAccuracy", 8);
        newQA.setDetailedScores(newScores);

        when(interviewSessionService.getChatHistory("old")).thenReturn(List.of(oldQA));
        when(interviewSessionService.getChatHistory("new")).thenReturn(List.of(newQA));

        // When
        Map<String, Object> progress = skillTrackingService.getSkillProgress(1L);
        Map<String, Object> trends = skillTrackingService.getSkillTrends(1L);
        assertNotNull(progress);

        // Then
        assertNotNull(trends);
        @SuppressWarnings("unchecked")
        Map<String, Double> skillTrends = (Map<String, Double>) trends.get("trends");
        assertNotNull(skillTrends);
        assertTrue(skillTrends.containsKey("technicalAccuracy"));
    }

    @Test
    void getSkillProgress_WithMultipleInterviews() {
        // Given
        List<Interview> interviews = List.of(mockInterview1, mockInterview2);
        when(interviewRepository.findByCandidateId(1L)).thenReturn(interviews);
        when(interviewSessionService.getChatHistory("interview1")).thenReturn(List.of(mockQA1));
        when(interviewSessionService.getChatHistory("interview2")).thenReturn(List.of(mockQA2));

        // When
        Map<String, Object> result = skillTrackingService.getSkillProgress(1L);

        // Then
        assertNotNull(result);
        assertEquals(2, result.get("totalInterviews"));
    }

    private Interview createMockInterview(String id, LocalDate date) {
        Interview interview = new Interview();
        interview.setId(id);
        interview.setTitle("Test Interview " + id);
        interview.setStatus("Completed");
        interview.setDate(date);
        interview.setCandidateId(1);
        interview.setUpdatedAt(LocalDateTime.now());
        return interview;
    }
}
