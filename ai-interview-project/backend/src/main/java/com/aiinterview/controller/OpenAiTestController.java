package com.aiinterview.controller;

import com.aiinterview.model.openai.OpenAiMessage;
import com.aiinterview.service.OpenAiService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * OpenAI API 测试控制器
 * 用于验证OpenAI API连接是否正常
 */
@RestController
@RequestMapping("/api/test/openai")
@CrossOrigin(origins = "http://localhost:3000")
public class OpenAiTestController {

    @Autowired
    private OpenAiService openAiService;

    /**
     * 简单测试：验证OpenAI API是否能正常返回响应
     * GET http://localhost:8080/api/test/openai/simple
     */
    @GetMapping("/simple")
    public Mono<ResponseEntity<Map<String, Object>>> simpleTest() {
        System.out.println("=== OpenAI Simple Test Started ===");
        
        List<OpenAiMessage> messages = List.of(
            new OpenAiMessage("system", "你是一个友好的助手"),
            new OpenAiMessage("user", "请说'你好，测试成功！'")
        );

        return openAiService.chat(messages)
            .map(response -> {
                System.out.println("=== OpenAI Response: " + response + " ===");
                Map<String, Object> result = Map.of(
                    "status", "success",
                    "message", "OpenAI API 连接正常",
                    "response", response
                );
                return ResponseEntity.ok(result);
            })
            .onErrorResume(error -> {
                System.err.println("=== OpenAI Error: " + error.getMessage() + " ===");
                error.printStackTrace();
                Map<String, Object> errorResult = Map.of(
                    "status", "error",
                    "message", "OpenAI API 连接失败",
                    "error", error.getMessage(),
                    "errorType", error.getClass().getSimpleName()
                );
                return Mono.just(ResponseEntity.status(500).body(errorResult));
            });
    }

    /**
     * 中文测试：测试中文对话
     * GET http://localhost:8080/api/test/openai/chinese
     */
    @GetMapping("/chinese")
    public Mono<ResponseEntity<Map<String, Object>>> chineseTest() {
        System.out.println("=== OpenAI Chinese Test Started ===");
        
        List<OpenAiMessage> messages = List.of(
            new OpenAiMessage("system", "你是一个专业的Java面试官"),
            new OpenAiMessage("user", "请简单介绍一下HashMap的原理，不超过50字")
        );

        return openAiService.chat(messages)
            .map(response -> {
                System.out.println("=== OpenAI Chinese Response: " + response + " ===");
                Map<String, Object> result = Map.of(
                    "status", "success",
                    "message", "中文对话测试成功",
                    "question", "请简单介绍一下HashMap的原理",
                    "response", response
                );
                return ResponseEntity.ok(result);
            })
            .onErrorResume(error -> {
                System.err.println("=== OpenAI Chinese Test Error: " + error.getMessage() + " ===");
                Map<String, Object> errorResult = Map.of(
                    "status", "error",
                    "message", "中文对话测试失败",
                    "error", error.getMessage()
                );
                return Mono.just(ResponseEntity.status(500).body(errorResult));
            });
    }

    /**
     * 面试问题生成测试
     * GET http://localhost:8080/api/test/openai/interview-question
     */
    @GetMapping("/interview-question")
    public Mono<ResponseEntity<Map<String, Object>>> interviewQuestionTest() {
        System.out.println("=== OpenAI Interview Question Test Started ===");
        
        List<OpenAiMessage> messages = List.of(
            new OpenAiMessage("system", """
                你是一位专业的Java后端面试官。
                请根据候选人的背景提出一个中级Java开发的技术问题。
                """),
            new OpenAiMessage("user", """
                候选人背景：
                - 3年Java开发经验
                - 熟悉Spring Boot和微服务
                - 有电商系统开发经验
                
                请提出一个关于Spring Boot的面试问题。
                """)
        );

        return openAiService.chat(messages)
            .map(response -> {
                System.out.println("=== Generated Interview Question: " + response + " ===");
                Map<String, Object> result = Map.of(
                    "status", "success",
                    "message", "面试问题生成成功",
                    "question", response,
                    "context", "Java后端中级面试"
                );
                return ResponseEntity.ok(result);
            })
            .onErrorResume(error -> {
                System.err.println("=== Interview Question Generation Error: " + error.getMessage() + " ===");
                Map<String, Object> errorResult = Map.of(
                    "status", "error",
                    "message", "面试问题生成失败",
                    "error", error.getMessage()
                );
                return Mono.just(ResponseEntity.status(500).body(errorResult));
            });
    }

    /**
     * 自定义测试：允许用户输入自定义消息
     * POST http://localhost:8080/api/test/openai/custom
     */
    @PostMapping("/custom")
    public Mono<ResponseEntity<Map<String, Object>>> customTest(@RequestBody Map<String, String> request) {
        String userMessage = request.getOrDefault("message", "你好");
        String systemMessage = request.getOrDefault("system", "你是一个友好的助手");
        
        System.out.println("=== OpenAI Custom Test Started ===");
        System.out.println("System: " + systemMessage);
        System.out.println("User: " + userMessage);
        
        List<OpenAiMessage> messages = List.of(
            new OpenAiMessage("system", systemMessage),
            new OpenAiMessage("user", userMessage)
        );

        return openAiService.chat(messages)
            .map(response -> {
                System.out.println("=== OpenAI Custom Response: " + response + " ===");
                return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "自定义测试成功",
                    "request", Map.of(
                        "system", systemMessage,
                        "user", userMessage
                    ),
                    "response", response
                ));
            })
            .onErrorResume(error -> {
                System.err.println("=== Custom Test Error: " + error.getMessage() + " ===");
                error.printStackTrace();
                return Mono.just(ResponseEntity.status(500).body(Map.of(
                    "status", "error",
                    "message", "自定义测试失败",
                    "error", error.getMessage(),
                    "stackTrace", error.toString()
                )));
            });
    }

    /**
     * 配置检查：检查OpenAI配置是否正确
     * GET http://localhost:8080/api/test/openai/config
     */
    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> configCheck(
            @org.springframework.beans.factory.annotation.Value("${openai.api.key}") String apiKey,
            @org.springframework.beans.factory.annotation.Value("${openai.model}") String model,
            @org.springframework.beans.factory.annotation.Value("${openai.api.url}") String apiUrl) {
        
        System.out.println("=== OpenAI Configuration Check ===");
        
        boolean keyConfigured = apiKey != null && !apiKey.isEmpty() && !apiKey.equals("${OPENAI_API_KEY:}");
        String keyPreview = keyConfigured ? 
            (apiKey.substring(0, Math.min(7, apiKey.length())) + "..." + 
             apiKey.substring(Math.max(0, apiKey.length() - 4))) : 
            "未配置";
        
        System.out.println("API Key: " + (keyConfigured ? "已配置" : "未配置"));
        System.out.println("Model: " + model);
        System.out.println("API URL: " + apiUrl);
        
        return ResponseEntity.ok(Map.of(
            "configured", keyConfigured,
            "apiKeyPreview", keyPreview,
            "model", model,
            "apiUrl", apiUrl,
            "status", keyConfigured ? "ready" : "not_configured",
            "message", keyConfigured ? "OpenAI配置正常" : "请设置OPENAI_API_KEY环境变量或在application.properties中配置"
        ));
    }

    /**
     * 完整测试套件：运行所有测试
     * GET http://localhost:8080/api/test/openai/all
     */
    @GetMapping("/all")
    public Mono<ResponseEntity<Map<String, Object>>> runAllTests() {
        System.out.println("=== Running All OpenAI Tests ===");
        
        return simpleTest()
            .flatMap(simpleResult -> chineseTest()
                .flatMap(chineseResult -> interviewQuestionTest()
                    .map(interviewResult -> {
                        boolean allSuccess = 
                            simpleResult.getStatusCode().is2xxSuccessful() &&
                            chineseResult.getStatusCode().is2xxSuccessful() &&
                            interviewResult.getStatusCode().is2xxSuccessful();
                        
                        return ResponseEntity.ok(Map.of(
                            "status", allSuccess ? "success" : "partial_failure",
                            "message", allSuccess ? "所有测试通过" : "部分测试失败",
                            "tests", Map.of(
                                "simple", simpleResult.getBody(),
                                "chinese", chineseResult.getBody(),
                                "interview", interviewResult.getBody()
                            )
                        ));
                    })
                )
            )
            .onErrorResume(error -> {
                System.err.println("=== All Tests Error: " + error.getMessage() + " ===");
                return Mono.just(ResponseEntity.status(500).body(Map.of(
                    "status", "error",
                    "message", "测试套件执行失败",
                    "error", error.getMessage()
                )));
            });
    }
}

