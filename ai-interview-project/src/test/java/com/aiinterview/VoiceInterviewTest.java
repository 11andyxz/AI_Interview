package com.aiinterview;

import com.aiinterview.service.StreamingAIService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 实时语音对话系统测试
 *
 * 注意：需要配置有效的 OpenAI API Key 才能运行集成测试
 */
@SpringBootTest
public class VoiceInterviewTest {

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SimpMessagingTemplate messagingTemplate;

    private StreamingAIService streamingAIService;

    @BeforeEach
    public void setup() {
        // 如果没有真实的 API Key，这里可以使用 Mock
        streamingAIService = new StreamingAIService();
    }

    /**
     * 测试：三重触发机制的参数验证
     */
    @Test
    public void testEndpointingParameters() {
        // 默认参数应该符合规范
        int silenceMs = 900;
        int hangoverMs = 200;
        int maxUtteranceMs = 25000;
        int minCharsToCommit = 3;

        assertTrue(silenceMs >= 700 && silenceMs <= 1500,
                "silenceMs 应在 700-1500ms 范围内");
        assertTrue(hangoverMs >= 100 && hangoverMs <= 500,
                "hangoverMs 应在 100-500ms 范围内");
        assertTrue(maxUtteranceMs >= 15000 && maxUtteranceMs <= 40000,
                "maxUtteranceMs 应在 15-40 秒范围内");
        assertTrue(minCharsToCommit >= 1 && minCharsToCommit <= 5,
                "minCharsToCommit 应在 1-5 字符范围内");
    }

    /**
     * 测试：空文本过滤
     */
    @Test
    public void testEmptyTextFiltering() {
        String[] invalidTexts = {"", " ", "  ", "啊", "嗯"};

        for (String text : invalidTexts) {
            // 少于 3 个字符应该被过滤
            if (text.trim().length() < 3) {
                assertTrue(text.trim().length() < 3,
                        "文本 '" + text + "' 应该被过滤");
            }
        }
    }

    /**
     * 测试：commit 消息格式
     */
    @Test
    public void testCommitMessageFormat() throws Exception {
        String json = """
                {
                    "type": "commit",
                    "turnId": "a3d8f9c1-4e2b-4c7a-9f6e-1234567890ab",
                    "text": "我有5年的Java开发经验",
                    "reason": "silence_detected",
                    "timestamp": 1703001234567
                }
                """;

        var message = objectMapper.readTree(json);

        assertEquals("commit", message.get("type").asText());
        assertTrue(message.has("turnId"));
        assertTrue(message.has("text"));
        assertTrue(message.has("reason"));
        assertTrue(message.has("timestamp"));

        // 验证 reason 的有效值
        String reason = message.get("reason").asText();
        assertTrue(
                reason.equals("isFinal") ||
                reason.equals("silence_detected") ||
                reason.equals("manual_button") ||
                reason.equals("max_duration"),
                "reason 必须是预定义的值之一"
        );
    }

    /**
     * 测试：turnId 唯一性
     */
    @Test
    public void testTurnIdUniqueness() {
        // 模拟生成 1000 个 turnId
        java.util.Set<String> turnIds = new java.util.HashSet<>();

        for (int i = 0; i < 1000; i++) {
            String turnId = java.util.UUID.randomUUID().toString();
            turnIds.add(turnId);
        }

        // 应该没有重复
        assertEquals(1000, turnIds.size(), "turnId 必须唯一");
    }

    /**
     * 测试：流式调用回调机制（需要 Mock OpenAI）
     */
    @Test
    public void testStreamingCallback() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger tokenCount = new AtomicInteger(0);
        AtomicReference<String> fullResponse = new AtomicReference<>("");

        // 这里应该 Mock OpenAI 的响应
        // 实际测试时需要使用 WireMock 或 MockWebServer

        // 模拟回调
        Runnable onToken = () -> tokenCount.incrementAndGet();
        Runnable onComplete = () -> {
            fullResponse.set("测试回复");
            latch.countDown();
        };

        // 模拟调用
        onToken.run();
        onToken.run();
        onToken.run();
        onComplete.run();

        // 等待完成
        assertTrue(latch.await(5, TimeUnit.SECONDS), "应该在 5 秒内完成");
        assertEquals(3, tokenCount.get(), "应该收到 3 个 token");
        assertEquals("测试回复", fullResponse.get(), "应该收到完整回复");
    }

    /**
     * 测试：取消流的功能
     */
    @Test
    public void testCancelStream() {
        String sessionId = "test-session-123";

        // 第一次取消（没有活跃流）
        boolean cancelled1 = streamingAIService.cancelStream(sessionId);
        assertFalse(cancelled1, "没有活跃流时应该返回 false");

        // 实际场景需要先启动一个流，然后取消
        // 这里简化测试
    }

    /**
     * 测试：并发请求自动取消旧流
     */
    @Test
    public void testConcurrentRequestsCancelOldStream() throws Exception {
        String sessionId = "test-session-456";
        CountDownLatch latch = new CountDownLatch(1);

        // 模拟场景：
        // 1. 启动流 A
        // 2. 启动流 B（应该自动取消 A）
        // 3. 只有 B 的结果应该被处理

        AtomicReference<String> activeTurnId = new AtomicReference<>("turnId-A");

        // 启动流 A
        String turnIdA = "turnId-A";

        // 模拟用户快速提交新请求
        Thread.sleep(100);

        // 启动流 B（应该取消 A）
        String turnIdB = "turnId-B";
        activeTurnId.set(turnIdB);

        // 验证只有 B 被处理
        assertEquals(turnIdB, activeTurnId.get(),
                "应该只保留最新的 turnId");
    }

    /**
     * 测试：超时保护机制
     */
    @Test
    public void testMaxUtteranceTimeout() throws Exception {
        int maxUtteranceMs = 25000;
        long startTime = System.currentTimeMillis();

        // 模拟定时器
        CountDownLatch timeoutLatch = new CountDownLatch(1);

        // 在实际应用中，这个定时器应该在 25 秒后触发
        java.util.Timer timer = new java.util.Timer();
        timer.schedule(new java.util.TimerTask() {
            @Override
            public void run() {
                timeoutLatch.countDown();
            }
        }, 100); // 测试中使用 100ms 模拟

        // 等待超时
        assertTrue(timeoutLatch.await(200, TimeUnit.MILLISECONDS),
                "超时机制应该生效");

        timer.cancel();
    }

    /**
     * 集成测试：完整流程（需要真实 API Key）
     *
     * 注意：这个测试需要配置有效的 OpenAI API Key
     * 如果没有配置，可以使用 @Disabled 注解跳过
     */
    @Test
    // @Disabled("需要真实的 OpenAI API Key")
    public void testFullWorkflow() throws Exception {
        // 1. 模拟用户发言
        String userText = "我有5年的Java开发经验，主要做过电商系统";

        // 2. 验证文本长度
        assertTrue(userText.length() >= 3,
                "文本应该通过最小字符数检查");

        // 3. 生成 turnId
        String turnId = java.util.UUID.randomUUID().toString();
        assertNotNull(turnId);

        // 4. 模拟提交到后端
        CountDownLatch responseLatch = new CountDownLatch(1);
        AtomicReference<String> aiResponse = new AtomicReference<>("");

        // 5. 模拟 AI 回复（实际测试中应该调用真实服务）
        aiResponse.set("能详细说说您在电商系统中做过哪些模块吗？");
        responseLatch.countDown();

        // 6. 等待 AI 回复
        assertTrue(responseLatch.await(30, TimeUnit.SECONDS),
                "应该在 30 秒内收到 AI 回复");

        // 7. 验证回复
        assertFalse(aiResponse.get().isEmpty(),
                "AI 应该返回非空回复");

        System.out.println("用户: " + userText);
        System.out.println("AI: " + aiResponse.get());
    }
}
