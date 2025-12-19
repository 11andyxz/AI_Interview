package com.aiinterview.service;

import com.aiinterview.model.EvaluationResult;
import com.aiinterview.model.openai.OpenAiMessage;
import com.aiinterview.model.openai.OpenAiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LlmEvaluationServiceTest {
    
    @Mock
    private OpenAiService openAiService;
    
    @Mock
    private PromptService promptService;
    
    @InjectMocks
    private LlmEvaluationService llmEvaluationService;
    
    @BeforeEach
    void setUp() {
        when(promptService.buildEvaluationSystemPrompt()).thenReturn("System prompt");
        when(promptService.buildEvaluationPrompt(anyString(), anyString(), anyString(), anyString()))
            .thenReturn("Evaluation prompt");
    }
    
    @Test
    void testEvaluateAnswer_Success() {
        String validJsonResponse = """
            {
                "score": 85.0,
                "rubricLevel": "good",
                "technicalAccuracy": 8,
                "depth": 8,
                "experience": 9,
                "communication": 8,
                "strengths": ["Good technical knowledge", "Clear explanation"],
                "improvements": ["Could provide more examples"],
                "followUpQuestions": ["Can you elaborate on that?"]
            }
            """;
        
        when(openAiService.chat(any(List.class))).thenReturn(Mono.just(validJsonResponse));
        
        StepVerifier.create(llmEvaluationService.evaluateAnswer(
            "What is Java?", 
            "Java is a programming language", 
            "backend_java", 
            "mid"
        ))
        .assertNext(result -> {
            assertNotNull(result);
            assertEquals(85.0, result.getScore());
            assertEquals("good", result.getRubricLevel());
            assertEquals(8, result.getTechnicalAccuracy());
            assertEquals(8, result.getDepth());
            assertEquals(9, result.getExperience());
            assertEquals(8, result.getCommunication());
            assertNotNull(result.getStrengths());
            assertNotNull(result.getImprovements());
        })
        .verifyComplete();
        
        verify(openAiService).chat(any(List.class));
        verify(promptService).buildEvaluationSystemPrompt();
        verify(promptService).buildEvaluationPrompt(anyString(), anyString(), anyString(), anyString());
    }
    
    @Test
    void testEvaluateAnswer_WithExtraText() {
        String responseWithExtraText = "Here is the evaluation: {\"score\": 75.0, \"rubricLevel\": \"good\"}";
        
        when(openAiService.chat(any(List.class))).thenReturn(Mono.just(responseWithExtraText));
        
        StepVerifier.create(llmEvaluationService.evaluateAnswer(
            "What is Spring?", 
            "Spring is a framework", 
            "backend_java", 
            "mid"
        ))
        .assertNext(result -> {
            assertNotNull(result);
            // Should extract JSON from response
        })
        .verifyComplete();
    }
    
    @Test
    void testEvaluateAnswer_ParseError() {
        String invalidJson = "This is not valid JSON";
        
        when(openAiService.chat(any(List.class))).thenReturn(Mono.just(invalidJson));
        
        StepVerifier.create(llmEvaluationService.evaluateAnswer(
            "Question", 
            "Answer", 
            "backend_java", 
            "mid"
        ))
        .assertNext(result -> {
            assertNotNull(result);
            // Should return fallback evaluation
            assertNotNull(result.getScore());
            assertNotNull(result.getRubricLevel());
        })
        .verifyComplete();
    }
    
    @Test
    void testEvaluateAnswer_ServiceError() {
        when(openAiService.chat(any(List.class)))
            .thenReturn(Mono.error(new RuntimeException("Service unavailable")));
        
        StepVerifier.create(llmEvaluationService.evaluateAnswer(
            "Question", 
            "Answer", 
            "backend_java", 
            "mid"
        ))
        .assertNext(result -> {
            assertNotNull(result);
            // Should return fallback evaluation
            assertNotNull(result.getScore());
            assertNotNull(result.getRubricLevel());
        })
        .verifyComplete();
    }
    
    @Test
    void testEvaluateAnswer_FallbackForShortAnswer() {
        when(openAiService.chat(any(List.class)))
            .thenReturn(Mono.error(new RuntimeException("Error")));
        
        StepVerifier.create(llmEvaluationService.evaluateAnswer(
            "Question", 
            "Short", 
            "backend_java", 
            "mid"
        ))
        .assertNext(result -> {
            assertNotNull(result);
            // Short answer should get lower score in fallback
            assertTrue(result.getScore() < 70);
        })
        .verifyComplete();
    }
    
    @Test
    void testEvaluateAnswer_FallbackForLongAnswer() {
        String longAnswer = "A".repeat(300);
        when(openAiService.chat(any(List.class)))
            .thenReturn(Mono.error(new RuntimeException("Error")));
        
        StepVerifier.create(llmEvaluationService.evaluateAnswer(
            "Question", 
            longAnswer, 
            "backend_java", 
            "mid"
        ))
        .assertNext(result -> {
            assertNotNull(result);
            // Long answer should get higher score in fallback
            assertTrue(result.getScore() >= 70);
        })
        .verifyComplete();
    }
}

