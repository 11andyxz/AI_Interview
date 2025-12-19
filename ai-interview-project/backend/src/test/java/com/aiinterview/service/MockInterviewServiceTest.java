package com.aiinterview.service;

import com.aiinterview.model.MockInterview;
import com.aiinterview.model.MockInterviewMessage;
import com.aiinterview.repository.MockInterviewRepository;
import com.aiinterview.repository.MockInterviewMessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MockInterviewServiceTest {

    @Mock
    private MockInterviewRepository mockInterviewRepository;

    @Mock
    private MockInterviewMessageRepository messageRepository;

    @InjectMocks
    private MockInterviewService mockInterviewService;

    private MockInterview mockInterview;
    private String interviewId = "mock-interview-123";
    private Long userId = 1L;

    @BeforeEach
    void setUp() {
        mockInterview = new MockInterview();
        mockInterview.setId(interviewId);
        mockInterview.setUserId(userId);
        mockInterview.setStatus("practice");
        mockInterview.setCurrentQuestionIndex(0);
        mockInterview.setPositionType("backend");
        mockInterview.setProgrammingLanguages("Java");
    }

    @Test
    void testGetUserMockInterviews_AllStatuses() {
        when(mockInterviewRepository.findByUserIdOrderByCreatedAtDesc(userId))
            .thenReturn(Arrays.asList(mockInterview));

        List<MockInterview> result = mockInterviewService.getUserMockInterviews(userId, null);

        assertEquals(1, result.size());
        assertEquals(mockInterview, result.get(0));
    }

    @Test
    void testGetUserMockInterviews_ByStatus() {
        when(mockInterviewRepository.findByUserIdAndStatusOrderByCreatedAtDesc(userId, "completed"))
            .thenReturn(Arrays.asList(mockInterview));

        List<MockInterview> result = mockInterviewService.getUserMockInterviews(userId, "completed");

        assertEquals(1, result.size());
        verify(mockInterviewRepository).findByUserIdAndStatusOrderByCreatedAtDesc(userId, "completed");
    }

    @Test
    void testGetMockInterviewById_Success() {
        when(mockInterviewRepository.findByIdAndUserId(interviewId, userId))
            .thenReturn(Optional.of(mockInterview));

        Optional<MockInterview> result = mockInterviewService.getMockInterviewById(interviewId, userId);

        assertTrue(result.isPresent());
        assertEquals(mockInterview, result.get());
    }

    @Test
    void testCreateMockInterview() {
        when(mockInterviewRepository.save(any(MockInterview.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        MockInterview created = mockInterviewService.createMockInterview(
            userId, "Test Interview", "Backend Developer", "[\"Java\"]", "English");

        assertNotNull(created);
        assertEquals(userId, created.getUserId());
        assertEquals("practice", created.getStatus());
        assertEquals(0, created.getCurrentQuestionIndex());
        verify(mockInterviewRepository).save(any(MockInterview.class));
    }

    @Test
    void testGetMockInterviewMessages() {
        MockInterviewMessage message = new MockInterviewMessage();
        message.setMockInterviewId(interviewId);
        message.setQuestionText("Question");
        message.setAnswerText("Answer");

        when(messageRepository.findByMockInterviewIdOrderByCreatedAtAsc(interviewId))
            .thenReturn(Arrays.asList(message));

        List<MockInterviewMessage> result = mockInterviewService.getMockInterviewMessages(interviewId);

        assertEquals(1, result.size());
        assertEquals(message, result.get(0));
    }

    @Test
    void testAddMessage() {
        when(messageRepository.save(any(MockInterviewMessage.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        MockInterviewMessage message = mockInterviewService.addMessage(
            interviewId, "Question", "Answer");

        assertNotNull(message);
        assertEquals(interviewId, message.getMockInterviewId());
        assertEquals("Question", message.getQuestionText());
        assertEquals("Answer", message.getAnswerText());
        verify(messageRepository).save(any(MockInterviewMessage.class));
    }

    @Test
    void testRetryMockInterview_Success() {
        when(mockInterviewRepository.findByIdAndUserId(interviewId, userId))
            .thenReturn(Optional.of(mockInterview));
        when(messageRepository.findByMockInterviewIdOrderByCreatedAtAsc(interviewId))
            .thenReturn(Arrays.asList(new MockInterviewMessage()));
        when(mockInterviewRepository.save(any(MockInterview.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        doNothing().when(messageRepository).deleteAll(any());

        MockInterview retried = mockInterviewService.retryMockInterview(interviewId, userId);

        assertEquals("practice", retried.getStatus());
        assertEquals(0, retried.getCurrentQuestionIndex());
        assertNull(retried.getScore());
        verify(messageRepository).deleteAll(any());
    }

    @Test
    void testRetryMockInterview_NotFound() {
        when(mockInterviewRepository.findByIdAndUserId(interviewId, userId))
            .thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> {
            mockInterviewService.retryMockInterview(interviewId, userId);
        });
    }

    @Test
    void testGetHint_BasicHint() {
        String hint = mockInterviewService.getHint(interviewId, 0L);

        assertNotNull(hint);
        assertFalse(hint.isEmpty());
    }

    @Test
    void testGetHint_AlgorithmQuestion() {
        // Setup mock interview with algorithm context
        MockInterviewMessage algorithmMessage = new MockInterviewMessage();
        algorithmMessage.setQuestionText("What is the time complexity of this algorithm?");

        when(mockInterviewRepository.findById(interviewId)).thenReturn(Optional.of(mockInterview));
        when(messageRepository.findByMockInterviewIdOrderByCreatedAtAsc(interviewId))
            .thenReturn(Arrays.asList(algorithmMessage));

        String hint = mockInterviewService.getHint(interviewId, 0L);

        assertNotNull(hint);
        assertTrue(hint.contains("time") || hint.contains("space") || hint.contains("complexity"));
    }

    @Test
    void testGetHint_DatabaseQuestion() {
        // Setup mock interview with database context
        MockInterviewMessage dbMessage = new MockInterviewMessage();
        dbMessage.setQuestionText("Explain database normalization");

        when(mockInterviewRepository.findById(interviewId)).thenReturn(Optional.of(mockInterview));
        when(messageRepository.findByMockInterviewIdOrderByCreatedAtAsc(interviewId))
            .thenReturn(Arrays.asList(dbMessage));

        String hint = mockInterviewService.getHint(interviewId, 0L);

        assertNotNull(hint);
        assertTrue(hint.contains("normalization") || hint.contains("ACID") || hint.contains("indexing"));
    }

    @Test
    void testGetHint_BackendPosition() {
        // Setup backend developer position
        mockInterview.setPositionType("Backend Java Developer");

        MockInterviewMessage backendMessage = new MockInterviewMessage();
        backendMessage.setQuestionText("What is dependency injection?");

        when(mockInterviewRepository.findById(interviewId)).thenReturn(Optional.of(mockInterview));
        when(messageRepository.findByMockInterviewIdOrderByCreatedAtAsc(interviewId))
            .thenReturn(Arrays.asList(backendMessage));

        String hint = mockInterviewService.getHint(interviewId, 0L);

        assertNotNull(hint);
        assertTrue(hint.contains("backend") || hint.contains("server") || hint.contains("API"));
    }

    @Test
    void testGetHint_FrontendPosition() {
        // Setup frontend developer position
        mockInterview.setPositionType("Frontend React Developer");

        MockInterviewMessage frontendMessage = new MockInterviewMessage();
        frontendMessage.setQuestionText("What is the virtual DOM?");

        when(mockInterviewRepository.findById(interviewId)).thenReturn(Optional.of(mockInterview));
        when(messageRepository.findByMockInterviewIdOrderByCreatedAtAsc(interviewId))
            .thenReturn(Arrays.asList(frontendMessage));

        String hint = mockInterviewService.getHint(interviewId, 0L);

        assertNotNull(hint);
        assertTrue(hint.contains("frontend") || hint.contains("user") || hint.contains("experience"));
    }

    @Test
    void testGetHint_InterviewNotFound() {
        when(mockInterviewRepository.findById("non-existent-id")).thenReturn(Optional.empty());

        String hint = mockInterviewService.getHint("non-existent-id", 0L);

        assertNotNull(hint);
        assertTrue(hint.contains("not found"));
    }

    @Test
    void testGetHint_NoMessages() {
        when(mockInterviewRepository.findById(interviewId)).thenReturn(Optional.of(mockInterview));
        when(messageRepository.findByMockInterviewIdOrderByCreatedAtAsc(interviewId))
            .thenReturn(Arrays.asList());

        String hint = mockInterviewService.getHint(interviewId, 0L);

        assertNotNull(hint);
        assertTrue(hint.contains("fundamental") || hint.contains("concepts"));
    }

    @Test
    void testGetHint_SecurityQuestion() {
        MockInterviewMessage securityMessage = new MockInterviewMessage();
        securityMessage.setQuestionText("How do you handle security and authentication?");

        when(mockInterviewRepository.findById(interviewId)).thenReturn(Optional.of(mockInterview));
        when(messageRepository.findByMockInterviewIdOrderByCreatedAtAsc(interviewId))
            .thenReturn(Arrays.asList(securityMessage));

        String hint = mockInterviewService.getHint(interviewId, 0L);

        assertNotNull(hint);
        // The hint should contain "vulnerability", "secure", or "standards" based on the actual implementation
        assertTrue(hint.contains("vulnerability") || hint.contains("secure") || hint.contains("standards") || 
                   hint.contains("security") || hint.contains("authentication"));
    }

    @Test
    void testGetHint_PerformanceQuestion() {
        MockInterviewMessage perfMessage = new MockInterviewMessage();
        perfMessage.setQuestionText("How do you optimize application performance?");

        when(mockInterviewRepository.findById(interviewId)).thenReturn(Optional.of(mockInterview));
        when(messageRepository.findByMockInterviewIdOrderByCreatedAtAsc(interviewId))
            .thenReturn(Arrays.asList(perfMessage));

        String hint = mockInterviewService.getHint(interviewId, 0L);

        assertNotNull(hint);
        assertTrue(hint.contains("performance") || hint.contains("optimize") || hint.contains("bottleneck") || hint.contains("caching"));
    }

    @Test
    void testCompleteMockInterview() {
        when(mockInterviewRepository.findByIdAndUserId(interviewId, userId))
            .thenReturn(Optional.of(mockInterview));
        when(mockInterviewRepository.save(any(MockInterview.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        MockInterview completed = mockInterviewService.completeMockInterview(
            interviewId, userId, new BigDecimal("85.5"), "Good performance");

        assertEquals("completed", completed.getStatus());
        assertEquals(new BigDecimal("85.5"), completed.getScore());
        assertEquals("Good performance", completed.getFeedback());
        assertNotNull(completed.getEndedAt());
    }
}

