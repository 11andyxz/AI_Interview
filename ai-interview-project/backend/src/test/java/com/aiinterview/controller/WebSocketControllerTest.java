package com.aiinterview.controller;

import com.aiinterview.dto.ChatRequest;
import com.aiinterview.dto.QAHistory;
import com.aiinterview.dto.TranscriptMessage;
import com.aiinterview.model.openai.OpenAiMessage;
import com.aiinterview.service.InterviewSessionService;
import com.aiinterview.service.OpenAiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebSocketControllerTest {
    
    @Mock
    private InterviewSessionService interviewSessionService;
    
    @Mock
    private OpenAiService openAiService;
    
    @Mock
    private SimpMessagingTemplate messagingTemplate;
    
    @InjectMocks
    private WebSocketController webSocketController;
    
    private TranscriptMessage testMessage;
    
    @BeforeEach
    void setUp() {
        testMessage = new TranscriptMessage();
        testMessage.setInterviewId("interview-123");
        testMessage.setText("Hello, this is a test message");
        testMessage.setFinal(true);
        testMessage.setLanguage("English");
    }
    
    @Test
    void testHandleTranscript_FinalMessage() {
        List<QAHistory> history = new ArrayList<>();
        List<OpenAiMessage> messages = new ArrayList<>();
        messages.add(new OpenAiMessage("system", "You are a helpful assistant"));
        messages.add(new OpenAiMessage("user", "Hello"));
        
        when(interviewSessionService.getChatHistory("interview-123")).thenReturn(history);
        when(interviewSessionService.buildMessagesForOpenAI(eq("interview-123"), any(ChatRequest.class)))
            .thenReturn(messages);
        when(openAiService.chatStream(messages)).thenReturn(Flux.just("Hi", " there", "!"));
        doNothing().when(interviewSessionService).saveChatMessage(anyString(), any(QAHistory.class));
        
        // Execute
        webSocketController.handleTranscript(testMessage);
        
        // Verify interactions
        verify(interviewSessionService).getChatHistory("interview-123");
        verify(interviewSessionService).buildMessagesForOpenAI(eq("interview-123"), any(ChatRequest.class));
        verify(openAiService).chatStream(messages);
        // Note: saveChatMessage is called asynchronously, so we verify it with a delay
        // In a real test, we might need to wait or use a different approach
    }
    
    @Test
    void testHandleTranscript_NonFinalMessage() {
        testMessage.setFinal(false);
        
        webSocketController.handleTranscript(testMessage);
        
        // Should not process non-final messages
        verify(interviewSessionService, never()).getChatHistory(anyString());
        verify(openAiService, never()).chatStream(anyList());
    }
    
    @Test
    void testHandleTranscript_EmptyText() {
        testMessage.setText("");
        
        webSocketController.handleTranscript(testMessage);
        
        // Should not process empty messages
        verify(interviewSessionService, never()).getChatHistory(anyString());
        verify(openAiService, never()).chatStream(anyList());
    }
    
    @Test
    void testHandleTranscript_NullText() {
        testMessage.setText(null);
        
        webSocketController.handleTranscript(testMessage);
        
        // Should not process null messages
        verify(interviewSessionService, never()).getChatHistory(anyString());
        verify(openAiService, never()).chatStream(anyList());
    }
    
    @Test
    void testHandleTranscript_WhitespaceText() {
        testMessage.setText("   ");
        
        webSocketController.handleTranscript(testMessage);
        
        // Should not process whitespace-only messages
        verify(interviewSessionService, never()).getChatHistory(anyString());
        verify(openAiService, never()).chatStream(anyList());
    }
}

