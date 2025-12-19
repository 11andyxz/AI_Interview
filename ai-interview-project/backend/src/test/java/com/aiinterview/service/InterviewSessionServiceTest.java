package com.aiinterview.service;

import com.aiinterview.dto.ChatRequest;
import com.aiinterview.dto.QAHistory;
import com.aiinterview.model.Candidate;
import com.aiinterview.model.Interview;
import com.aiinterview.model.InterviewMessage;
import com.aiinterview.repository.CandidateRepository;
import com.aiinterview.repository.InterviewMessageRepository;
import com.aiinterview.repository.InterviewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InterviewSessionServiceTest {
    
    @Mock
    private InterviewRepository interviewRepository;
    
    @Mock
    private CandidateRepository candidateRepository;
    
    @Mock
    private InterviewMessageRepository interviewMessageRepository;
    
    @Mock
    private OpenAiService openAiService;
    
    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    
    @Mock
    private ValueOperations<String, Object> valueOperations;
    
    @InjectMocks
    private InterviewSessionService interviewSessionService;
    
    private Interview testInterview;
    private Candidate testCandidate;
    
    @BeforeEach
    void setUp() {
        testInterview = new Interview();
        testInterview.setId("test-interview-id");
        testInterview.setCandidateId(1);
        testInterview.setTitle("Backend Java Developer");
        testInterview.setLanguage("English");
        testInterview.setTechStack("Java, Spring Boot");
        testInterview.setDate(LocalDate.now());
        testInterview.setStatus("In Progress");
        
        testCandidate = new Candidate();
        testCandidate.setId(1);
        testCandidate.setName("Test Candidate");
        testCandidate.setExperienceYears(5);
    }
    
    @Test
    void testGetInterviewSession() {
        when(interviewRepository.findById("test-interview-id")).thenReturn(Optional.of(testInterview));
        when(candidateRepository.findById(1)).thenReturn(Optional.of(testCandidate));
        when(interviewMessageRepository.findByInterviewIdOrderByCreatedAtAsc("test-interview-id"))
            .thenReturn(Collections.emptyList());
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
        
        Optional<Map<String, Object>> session = interviewSessionService.getInterviewSession("test-interview-id");
        
        assertTrue(session.isPresent());
        assertNotNull(session.get().get("interview"));
        assertNotNull(session.get().get("candidate"));
    }
    
    @Test
    void testSaveChatMessage() {
        when(interviewMessageRepository.save(any(InterviewMessage.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        
        QAHistory qa = new QAHistory("Question?", "Answer.");
        interviewSessionService.saveChatMessage("test-interview-id", qa);
        
        verify(interviewMessageRepository).save(any(InterviewMessage.class));
    }
    
    @Test
    void testGeneratePersonalizedResponse() {
        when(interviewRepository.findById("test-interview-id")).thenReturn(Optional.of(testInterview));
        when(candidateRepository.findById(1)).thenReturn(Optional.of(testCandidate));
        when(openAiService.chat(anyList())).thenReturn(Mono.just("AI Response"));
        when(interviewMessageRepository.findByInterviewIdOrderByCreatedAtAsc("test-interview-id"))
            .thenReturn(Collections.emptyList());
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
        when(interviewMessageRepository.save(any(InterviewMessage.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        
        ChatRequest request = new ChatRequest();
        request.setUserMessage("Test question");
        request.setLanguage("English");
        
        Mono<String> response = interviewSessionService.generatePersonalizedResponse("test-interview-id", request);
        
        StepVerifier.create(response)
            .expectNext("AI Response")
            .verifyComplete();
        
        verify(openAiService).chat(anyList());
    }
    
    @Test
    void testGetChatHistory() {
        InterviewMessage msg1 = new InterviewMessage();
        msg1.setUserMessage("Question 1");
        msg1.setAiMessage("Answer 1");
        
        InterviewMessage msg2 = new InterviewMessage();
        msg2.setUserMessage("Question 2");
        msg2.setAiMessage("Answer 2");
        
        when(interviewMessageRepository.findByInterviewIdOrderByCreatedAtAsc("test-interview-id"))
            .thenReturn(Arrays.asList(msg1, msg2));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
        
        List<QAHistory> history = interviewSessionService.getChatHistory("test-interview-id");
        
        assertEquals(2, history.size());
        assertEquals("Question 1", history.get(0).getQuestionText());
        assertEquals("Answer 1", history.get(0).getAnswerText());
    }
}

