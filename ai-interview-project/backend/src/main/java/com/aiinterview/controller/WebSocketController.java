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
        logger.info("Received transcript: interviewId={}, text='{}', isFinal={}, language={}",
            message.getInterviewId(),
            message.getText() != null ? message.getText().substring(0, Math.min(50, message.getText().length())) : "null",
            message.isFinal(),
            message.getLanguage());

        // Validate message
        if (message.getInterviewId() == null || message.getInterviewId().isEmpty()) {
            logger.error("Received transcript with null or empty interviewId");
            sendErrorResponse(message.getInterviewId(), "Interview ID is required");
            return;
        }

        // Only process final transcript (after pause detection)
        if (message.isFinal() && message.getText() != null && !message.getText().trim().isEmpty()) {
            String userMessage = message.getText().trim();
            String interviewId = message.getInterviewId();

            logger.info("Processing final transcript for interview {}: '{}'",
                interviewId, userMessage.substring(0, Math.min(100, userMessage.length())));

            try {
                // Create ChatRequest
                ChatRequest chatRequest = new ChatRequest();
                chatRequest.setUserMessage(userMessage);
                chatRequest.setLanguage(message.getLanguage() != null ? message.getLanguage() : "English");

                // Get conversation history
                var history = interviewSessionService.getChatHistory(interviewId);
                chatRequest.setRecentHistory(history);
                logger.debug("Loaded {} previous Q&A pairs for context", history.size());

                // Call OpenAI with streaming
                var messages = interviewSessionService.buildMessagesForOpenAI(interviewId, chatRequest);
                logger.debug("Built {} messages for OpenAI", messages.size());

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
                        logger.error("Error streaming AI response for interview {}: {}",
                            interviewId, error.getMessage(), error);
                        StreamResponse errorResponse = new StreamResponse(
                            interviewId,
                            "I apologize, but I encountered an error processing your response. Please try again.",
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
                            logger.info("Stream complete for interview {}. AI response length: {} chars",
                                interviewId, aiResponse.length());
                            QAHistory qa = new QAHistory(userMessage, aiResponse);
                            interviewSessionService.saveChatMessage(interviewId, qa);
                            logger.debug("Saved Q&A to database for interview {}", interviewId);
                        } else {
                            logger.warn("Empty AI response for interview {}", interviewId);
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
            } catch (Exception e) {
                logger.error("Error processing transcript for interview {}: {}",
                    interviewId, e.getMessage(), e);
                sendErrorResponse(interviewId, "Failed to process your message. Please try again.");
            }
        } else {
            logger.debug("Skipping non-final or empty transcript for interview {}", message.getInterviewId());
        }
    }

    private void sendErrorResponse(String interviewId, String errorMessage) {
        StreamResponse errorResponse = new StreamResponse(
            interviewId,
            errorMessage,
            true,
            "error"
        );
        messagingTemplate.convertAndSend(
            "/topic/interview/" + interviewId + "/response",
            errorResponse
        );
    }
}

