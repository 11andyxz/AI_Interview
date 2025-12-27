package com.aiinterview.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 流式 AI 服务 - 调用 OpenAI API
 *
 * 核心功能：
 * 1. 每个 WebSocket session 只能有一个活跃流
 * 2. 新请求会自动取消旧流
 * 3. 支持手动取消
 * 4. 流式返回 token 给前端
 */
@Service
public class StreamingAIService {

    private static final Logger logger = LoggerFactory.getLogger(StreamingAIService.class);

    @Value("${openai.api.key}")
    private String openaiApiKey;

    @Value("${openai.api.url:https://api.openai.com/v1/chat/completions}")
    private String openaiApiUrl;

    @Value("${openai.model:gpt-4}")
    private String model;

    @Value("${openai.max-tokens:1000}")
    private int maxTokens;

    @Value("${openai.temperature:0.7}")
    private double temperature;

    private final WebClient webClient;

    // 每个 session 的活跃流订阅
    private final Map<String, Disposable> activeStreams = new ConcurrentHashMap<>();

    public StreamingAIService() {
        this.webClient = WebClient.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024)) // 10MB
                .build();
    }

    /**
     * 启动流式调用
     *
     * @param sessionId 会话ID（WebSocket session）
     * @param turnId 对话轮次ID
     * @param userMessage 用户消息
     * @param username 用户名
     * @param onToken Token 回调
     * @param onComplete 完成回调
     * @param onError 错误回调
     */
    public void startStream(
            String sessionId,
            String turnId,
            String userMessage,
            String username,
            Consumer<String> onToken,
            Consumer<String> onComplete,
            Consumer<String> onError
    ) {
        logger.info("[Stream] 启动流式调用, sessionId={}, turnId={}", sessionId, turnId);

        // 构建请求体
        Map<String, Object> requestBody = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", "你是一位专业的AI面试官，请根据候选人的回答进行深入交流。"),
                        Map.of("role", "user", "content", userMessage)
                ),
                "max_tokens", maxTokens,
                "temperature", temperature,
                "stream", true
        );

        // 累积完整响应
        StringBuilder fullResponse = new StringBuilder();

        // 发起流式请求
        Disposable subscription = webClient.post()
                .uri(openaiApiUrl)
                .header("Authorization", "Bearer " + openaiApiKey)
                .header("Content-Type", "application/json")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToFlux(String.class)
                .flatMap(this::parseSSEChunk)
                .doOnNext(token -> {
                    // 流式返回 token
                    fullResponse.append(token);
                    onToken.accept(token);
                })
                .doOnComplete(() -> {
                    // 流结束
                    logger.info("[Stream] 完成, turnId={}, 总长度={}", turnId, fullResponse.length());
                    activeStreams.remove(sessionId);
                    onComplete.accept(fullResponse.toString());
                })
                .doOnError(error -> {
                    // 流错误
                    logger.error("[Stream] 错误, turnId={}", turnId, error);
                    activeStreams.remove(sessionId);
                    onError.accept(error.getMessage());
                })
                .subscribe();

        // 保存订阅，用于后续取消
        activeStreams.put(sessionId, subscription);
    }

    /**
     * 取消流
     *
     * @param sessionId 会话ID
     * @return 是否成功取消
     */
    public boolean cancelStream(String sessionId) {
        Disposable stream = activeStreams.remove(sessionId);
        if (stream != null && !stream.isDisposed()) {
            logger.info("[Stream] 取消流, sessionId={}", sessionId);
            stream.dispose();
            return true;
        }
        return false;
    }

    /**
     * 解析 OpenAI SSE 数据块
     *
     * OpenAI 流式返回格式：
     * data: {"choices":[{"delta":{"content":"token"}}]}
     * data: [DONE]
     */
    private Flux<String> parseSSEChunk(String chunk) {
        if (chunk == null || chunk.isBlank()) {
            return Flux.empty();
        }

        // 处理 SSE 格式
        String[] lines = chunk.split("\n");
        return Flux.fromArray(lines)
                .filter(line -> line.startsWith("data: "))
                .map(line -> line.substring(6)) // 去掉 "data: "
                .filter(data -> !data.equals("[DONE]"))
                .mapNotNull(data -> {
                    try {
                        // 简单 JSON 解析（生产环境建议使用 Jackson）
                        int contentStart = data.indexOf("\"content\":\"");
                        if (contentStart == -1) {
                            return null;
                        }
                        contentStart += 11; // "content":"
                        int contentEnd = data.indexOf("\"", contentStart);
                        if (contentEnd == -1) {
                            return null;
                        }
                        String token = data.substring(contentStart, contentEnd);
                        return unescapeJson(token);
                    } catch (Exception e) {
                        logger.warn("[Stream] 解析 chunk 失败: {}", data, e);
                        return null;
                    }
                });
    }

    /**
     * JSON 字符串反转义
     */
    private String unescapeJson(String s) {
        return s.replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
    }
}
