# OpenAI集成使用指南

## 📋 概述

本系统已集成OpenAI API，实现了智能面试问题生成和候选人回答评估功能。

## 🔑 配置

### 1. 设置OpenAI API Key

在运行应用之前，需要设置环境变量：

```bash
export OPENAI_API_KEY="your-api-key-here"
```

或者在IDEA中配置环境变量：
- Run -> Edit Configurations
- Environment variables: `OPENAI_API_KEY=your-api-key-here`

### 2. 配置参数

在 `application.properties` 中可以调整以下参数：

```properties
# OpenAI模型配置
openai.model=gpt-3.5-turbo          # 使用的模型
openai.temperature=0.7               # 生成温度 (0-1)
openai.max-tokens=1000              # 最大token数
openai.max-history-messages=10      # 保留的历史对话轮数
```

## 🎯 核心功能

### 1. 智能问题生成

**Endpoint:** `POST /api/llm/question-generate`

根据岗位、候选人背景和对话历史，智能生成下一个面试问题。

**请求示例：**
```json
{
  "sessionId": "session-uuid",
  "roleId": "backend_java",
  "level": "mid",
  "candidateInfo": {
    "workExperience": [...],
    "projects": [...]
  }
}
```

**响应示例：**
```json
{
  "question": "我看到你在简历中提到了微服务架构的经验...",
  "sessionId": "session-uuid",
  "questionNumber": 3
}
```

### 2. 流式问题生成（SSE）

**Endpoint:** `GET /api/llm/question-generate/stream`

实时流式输出问题，提供更好的用户体验。

**请求示例：**
```bash
curl -N "http://localhost:8080/api/llm/question-generate/stream?sessionId=xxx&roleId=backend_java&level=mid"
```

**响应格式：**
```
data: 我
data: 看到
data: 你在
data: 简历中
...
event: end
data: [DONE]
```

### 3. 回答评估

**Endpoint:** `POST /api/llm/eval`

评估候选人的回答质量，提供详细的评分和改进建议。

**请求示例：**
```json
{
  "question": "请解释HashMap的工作原理",
  "answer": "HashMap基于哈希表实现...",
  "roleId": "backend_java",
  "level": "mid"
}
```

**响应示例：**
```json
{
  "score": 85.0,
  "rubricLevel": "good",
  "detailedScores": {
    "technicalAccuracy": 9,
    "depth": 8,
    "experience": 8,
    "communication": 9
  },
  "strengths": [
    "准确理解了HashMap的底层实现",
    "清晰解释了哈希冲突的处理方式"
  ],
  "improvements": [
    "可以深入讨论扩容机制的细节",
    "建议补充并发场景下的问题"
  ],
  "followUpQuestions": [
    "HashMap在高并发情况下会有什么问题？",
    "ConcurrentHashMap是如何解决这些问题的？"
  ]
}
```

### 4. 通用对话

**Endpoint:** `POST /api/llm/chat`

用于自定义对话场景。

**请求示例：**
```json
{
  "messages": [
    {"role": "system", "content": "你是一个技术面试官"},
    {"role": "user", "content": "请介绍一下你自己"}
  ]
}
```

### 5. 健康检查

**Endpoint:** `GET /api/llm/health`

检查OpenAI服务配置状态。

**响应示例：**
```json
{
  "configured": true,
  "status": "ready",
  "message": "OpenAI service is ready"
}
```

## 📝 Prompt管理

系统使用JSON文件管理prompt模板，便于维护和迭代。

### Prompt文件位置

- `src/main/resources/prompts/system-prompts.json` - 系统级prompt
- `src/main/resources/prompts/role-prompts.json` - 岗位特定prompt

### 自定义Prompt

#### 1. 修改系统基础Prompt

编辑 `system-prompts.json`：

```json
{
  "base": "你是一位专业的技术面试官...",
  "evaluation": "你是一位专业的技术面试评估专家..."
}
```

#### 2. 添加新的岗位类型

编辑 `role-prompts.json`：

```json
{
  "roles": {
    "your_new_role": {
      "name": "岗位名称",
      "description": "岗位描述",
      "focus_areas": ["技能1", "技能2"],
      "levels": {
        "junior": {...},
        "mid": {...},
        "senior": {...}
      }
    }
  }
}
```

## 🔄 工作流程

### 完整面试流程

1. **创建会话**
```bash
POST /api/sessions
{
  "roleId": "backend_java",
  "level": "mid",
  "skills": ["java_core", "spring_boot"]
}
```

2. **生成第一个问题**
```bash
POST /api/llm/question-generate
{
  "sessionId": "...",
  "roleId": "backend_java",
  "level": "mid"
}
```

3. **候选人回答**
```bash
POST /api/sessions/{sessionId}/answer
{
  "questionId": "...",
  "questionText": "...",
  "answerText": "候选人的回答"
}
```

4. **评估回答**
```bash
POST /api/llm/eval
{
  "question": "...",
  "answer": "...",
  "roleId": "backend_java",
  "level": "mid"
}
```

5. **生成下一个问题**（重复步骤2-4）

6. **生成最终反馈**
```bash
POST /api/sessions/{sessionId}/feedback
```

## 🎨 前端集成示例

### 使用流式输出

```javascript
const eventSource = new EventSource(
  `http://localhost:8080/api/llm/question-generate/stream?sessionId=${sessionId}&roleId=${roleId}&level=${level}`
);

let question = "";

eventSource.onmessage = (event) => {
  if (event.data === "[DONE]") {
    eventSource.close();
    console.log("完整问题:", question);
  } else {
    question += event.data;
    updateUI(question); // 实时更新UI
  }
};

eventSource.onerror = (error) => {
  console.error("Stream error:", error);
  eventSource.close();
};
```

### 使用非流式输出

```javascript
const response = await fetch("http://localhost:8080/api/llm/question-generate", {
  method: "POST",
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify({
    sessionId: sessionId,
    roleId: "backend_java",
    level: "mid"
  })
});

const data = await response.json();
console.log("问题:", data.question);
```

## ⚡ 性能优化

### Token管理

系统自动管理对话历史，只保留最近N轮对话（默认10轮），以控制token使用。

### 缓存策略

对于相同的问题生成请求，建议在应用层实现缓存：

```java
// 可以基于 roleId + level + history 生成缓存key
String cacheKey = String.format("%s:%s:%d", roleId, level, historySize);
```

### 错误处理

所有API调用都包含fallback机制：
- OpenAI服务不可用时，返回友好的错误消息
- 评估失败时，使用基于规则的简单评分

## 🐛 调试

### 查看详细日志

在 `application.properties` 中启用debug日志：

```properties
logging.level.com.aiinterview.service.OpenAiService=DEBUG
logging.level.com.aiinterview.service.PromptService=DEBUG
```

### 常见问题

1. **API Key未配置**
   - 错误：`OpenAI API key not configured`
   - 解决：设置 `OPENAI_API_KEY` 环境变量

2. **请求超时**
   - 错误：`TimeoutException`
   - 解决：检查网络连接，考虑增加超时时间

3. **Token限制**
   - 错误：`context_length_exceeded`
   - 解决：减少 `openai.max-history-messages` 或 `openai.max-tokens`

## 💰 成本估算

使用 GPT-3.5-turbo 的大致成本：

- 问题生成：~500 tokens/次 ≈ $0.001
- 回答评估：~800 tokens/次 ≈ $0.0016
- 完整面试（10轮）：~$0.026

使用 GPT-4 会贵约15倍，但质量更高。

## 🔐 安全建议

1. **不要在代码中硬编码API Key**
2. **使用环境变量或密钥管理服务**
3. **实现请求频率限制**
4. **监控API使用量和成本**
5. **在生产环境中使用HTTPS**

## 📚 更多资源

- [OpenAI API文档](https://platform.openai.com/docs)
- [Spring WebFlux文档](https://docs.spring.io/spring-framework/docs/current/reference/html/web-reactive.html)
- [Reactor Project](https://projectreactor.io/)

