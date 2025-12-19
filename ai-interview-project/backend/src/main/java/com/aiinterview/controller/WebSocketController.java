package com.aiinterview.controller;

import com.aiinterview.dto.ChatRequest;
import com.aiinterview.dto.QAHistory;
import com.aiinterview.dto.StreamResponse;
import com.aiinterview.dto.TranscriptMessage;
import com.aiinterview.service.InterviewSessionService;
import com.aiinterview.service.OpenAiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

@Controller
public class WebSocketController {
    
    private static final Logger logger = LoggerFactory.getLogger(WebSocketController.class);
    
    @Autowired
    private InterviewSessionService interviewSessionService;
    
    @Autowired
    private OpenAiService openAiService;
    
    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    /**
     * Handle transcript messages from client
     */
    @MessageMapping("/transcript")
    public void handleTranscript(TranscriptMessage message) {
        logger.debug("Received transcript: interviewId={}, text={}, isFinal={}", 
            message.getInterviewId(), message.getText(), message.isFinal());
        
        // Only process final transcript (after pause detection)
        if (message.isFinal() && message.getText() != null && !message.getText().trim().isEmpty()) {
            String userMessage = message.getText().trim();
            String interviewId = message.getInterviewId();
            
            // Create ChatRequest
            ChatRequest chatRequest = new ChatRequest();
            chatRequest.setUserMessage(userMessage);
            chatRequest.setLanguage(message.getLanguage() != null ? message.getLanguage() : "English");
            
            // Get conversation history
            var history = interviewSessionService.getChatHistory(interviewId);
            chatRequest.setRecentHistory(history);
            
            // Call OpenAI with streaming
            var messages = interviewSessionService.buildMessagesForOpenAI(interviewId, chatRequest);
            
            // Stream response through WebSocket
            List<String> fullResponse = new ArrayList<>();
            Flux<String> stream = openAiService.chatStream(messages);
            
            stream.subscribe(
                chunk -> {
                    if (chunk != null && !chunk.isEmpty()) {
                        fullResponse.add(chunk);
                        // Send each chunk to client
                        StreamResponse response = new StreamResponse(
                            interviewId,
                            chunk,
                            false,
                            "chunk"
                        );
                        messagingTemplate.convertAndSend(
                            "/topic/interview/" + interviewId + "/response",
                            response
                        );
                    }
                },
                error -> {
                    logger.error("Error streaming AI response", error);
                    StreamResponse errorResponse = new StreamResponse(
                        interviewId,
                        "Error: " + error.getMessage(),
                        true,
                        "error"
                    );
                    messagingTemplate.convertAndSend(
                        "/topic/interview/" + interviewId + "/response",
                        errorResponse
                    );
                },
                () -> {
                    // Stream complete - save to database
                    String aiResponse = String.join("", fullResponse);
                    if (!aiResponse.isEmpty()) {
                        QAHistory qa = new QAHistory(userMessage, aiResponse);
                        interviewSessionService.saveChatMessage(interviewId, qa);
                    }
                    
                    // Send completion message
                    StreamResponse completeResponse = new StreamResponse(
                        interviewId,
                        "",
                        true,
                        "complete"
                    );
                    messagingTemplate.convertAndSend(
                        "/topic/interview/" + interviewId + "/response",
                        completeResponse
                    );
                }
            );
        }
    }
}

