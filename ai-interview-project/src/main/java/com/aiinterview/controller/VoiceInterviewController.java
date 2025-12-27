package com.aiinterview.controller;

import com.aiinterview.service.StreamingAIService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;

/**
 * 实时语音对话 STOMP 控制器
 *
 * 处理客户端的 commit 和 cancel 请求，调用 OpenAI 流式接口
 */
@Controller
public class VoiceInterviewController {

    private static final Logger logger = LoggerFactory.getLogger(VoiceInterviewController.class);

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private StreamingAIService streamingAIService;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 处理用户提交的发言
     *
     * @MessageMapping("/interview/commit") 对应客户端发送到 /app/interview/commit
     */
    @MessageMapping("/interview/commit")
    public void handleCommit(@Payload String payload, Principal principal, SimpMessageHeaderAccessor headerAccessor) {
        try {
            // 解析客户端消息
            JsonNode message = objectMapper.readTree(payload);
            String turnId = message.get("turnId").asText();
            String text = message.get("text").asText();
            String reason = message.get("reason").asText();
            long timestamp = message.get("timestamp").asLong();

            String sessionId = headerAccessor.getSessionId();
            String username = principal != null ? principal.getName() : "anonymous";

            logger.info("[Commit] 用户={}, 会话={}, turnId={}, 原因={}, 文本长度={}",
                    username, sessionId, turnId, reason, text.length());
            logger.debug("[Commit] 文本内容: {}", text);

            // 取消之前的流（如果有）
            streamingAIService.cancelStream(sessionId);

            // 启动新的流式调用
            streamingAIService.startStream(
                    sessionId,
                    turnId,
                    text,
                    username,
                    // Token 回调
                    (token) -> sendToUser(username, createMessage("ai_token", turnId, Map.of("token", token))),
                    // 完成回调
                    (fullResponse) -> {
                        logger.info("[Done] turnId={}, 总字符数={}", turnId, fullResponse.length());
                        sendToUser(username, createMessage("ai_done", turnId, Map.of(
                                "fullText", fullResponse,
                                "tokenCount", fullResponse.length()
                        )));
                    },
                    // 错误回调
                    (error) -> {
                        logger.error("[Error] turnId={}, 错误: {}", turnId, error);
                        sendToUser(username, createMessage("ai_error", turnId, Map.of("error", error)));
                    }
            );

        } catch (Exception e) {
            logger.error("[Commit] 处理失败", e);
            sendErrorToUser(principal, "处理请求失败: " + e.getMessage());
        }
    }

    /**
     * 处理用户取消请求
     *
     * @MessageMapping("/interview/cancel") 对应客户端发送到 /app/interview/cancel
     */
    @MessageMapping("/interview/cancel")
    public void handleCancel(@Payload String payload, Principal principal, SimpMessageHeaderAccessor headerAccessor) {
        try {
            JsonNode message = objectMapper.readTree(payload);
            String turnId = message.get("turnId").asText();
            String sessionId = headerAccessor.getSessionId();
            String username = principal != null ? principal.getName() : "anonymous";

            logger.info("[Cancel] 用户={}, 会话={}, turnId={}", username, sessionId, turnId);

            // 取消流
            boolean cancelled = streamingAIService.cancelStream(sessionId);

            if (cancelled) {
                sendToUser(username, createMessage("ai_cancelled", turnId, Map.of(
                        "message", "对话已取消"
                )));
            } else {
                logger.warn("[Cancel] 未找到活跃的流，会话={}", sessionId);
            }

        } catch (Exception e) {
            logger.error("[Cancel] 处理失败", e);
        }
    }

    /**
     * 向特定用户发送消息
     */
    private void sendToUser(String username, Map<String, Object> message) {
        messagingTemplate.convertAndSendToUser(username, "/queue/interview", message);
    }

    /**
     * 发送错误消息到用户
     */
    private void sendErrorToUser(Principal principal, String error) {
        String username = principal != null ? principal.getName() : "anonymous";
        sendToUser(username, createMessage("ai_error", "unknown", Map.of("error", error)));
    }

    /**
     * 创建标准消息格式
     */
    private Map<String, Object> createMessage(String type, String turnId, Map<String, Object> data) {
        Map<String, Object> message = new HashMap<>();
        message.put("type", type);
        message.put("turnId", turnId);
        message.put("timestamp", System.currentTimeMillis());
        message.putAll(data);
        return message;
    }
}
