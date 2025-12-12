# OpenAI API 测试指南

## 🎯 快速测试

已创建专门的测试端点来验证OpenAI API是否正常工作。

## 📋 测试端点列表

### 1. 配置检查（首先运行这个）
```bash
curl http://localhost:8080/api/test/openai/config
```

**作用**：检查API key是否正确配置

**预期输出**：
```json
{
  "configured": true,
  "apiKeyPreview": "sk-proj...bmsA",
  "model": "gpt-3.5-turbo",
  "apiUrl": "https://api.openai.com/v1/chat/completions",
  "status": "ready",
  "message": "OpenAI配置正常"
}
```

### 2. 简单测试
```bash
curl http://localhost:8080/api/test/openai/simple
```

**作用**：验证最基本的OpenAI API调用

**预期输出**：
```json
{
  "status": "success",
  "message": "OpenAI API 连接正常",
  "response": "你好，测试成功！"
}
```

### 3. 中文测试
```bash
curl http://localhost:8080/api/test/openai/chinese
```

**作用**：测试中文对话能力

**预期输出**：
```json
{
  "status": "success",
  "message": "中文对话测试成功",
  "question": "请简单介绍一下HashMap的原理",
  "response": "HashMap基于哈希表实现，通过键的hashCode计算索引位置..."
}
```

### 4. 面试问题生成测试
```bash
curl http://localhost:8080/api/test/openai/interview-question
```

**作用**：测试面试场景的问题生成

**预期输出**：
```json
{
  "status": "success",
  "message": "面试问题生成成功",
  "question": "请描述一下在Spring Boot中如何实现分布式事务？",
  "context": "Java后端中级面试"
}
```

### 5. 自定义测试
```bash
curl -X POST http://localhost:8080/api/test/openai/custom \
  -H "Content-Type: application/json" \
  -d '{
    "system": "你是一个Java专家",
    "message": "什么是Spring IoC？"
  }'
```

**作用**：测试自定义消息

**预期输出**：
```json
{
  "status": "success",
  "message": "自定义测试成功",
  "request": {
    "system": "你是一个Java专家",
    "user": "什么是Spring IoC？"
  },
  "response": "Spring IoC（控制反转）是Spring框架的核心概念..."
}
```

### 6. 运行所有测试
```bash
curl http://localhost:8080/api/test/openai/all
```

**作用**：一次性运行所有测试

## 🚀 使用步骤

### 步骤1：启动应用
```bash
cd backend
mvn clean install
mvn spring-boot:run
```

### 步骤2：检查配置
```bash
curl http://localhost:8080/api/test/openai/config
```

确认输出中 `"configured": true`

### 步骤3：运行简单测试
```bash
curl http://localhost:8080/api/test/openai/simple
```

如果看到 `"status": "success"` 和 AI 的回复，说明配置正确！

### 步骤4：运行完整测试
```bash
curl http://localhost:8080/api/test/openai/all
```

## 🌐 在浏览器中测试

你也可以直接在浏览器中访问：

1. **配置检查**：http://localhost:8080/api/test/openai/config
2. **简单测试**：http://localhost:8080/api/test/openai/simple
3. **中文测试**：http://localhost:8080/api/test/openai/chinese
4. **面试问题**：http://localhost:8080/api/test/openai/interview-question
5. **所有测试**：http://localhost:8080/api/test/openai/all

## 📊 查看日志

测试运行时会在控制台输出详细日志：

```
=== OpenAI Simple Test Started ===
=== OpenAI Response: 你好，测试成功！ ===
```

如果失败，会看到错误信息：
```
=== OpenAI Error: 401 Unauthorized ===
```

## ⚠️ 常见错误

### 错误1：401 Unauthorized
**原因**：API Key 无效或未配置
**解决**：检查 application.properties 中的 openai.api.key

### 错误2：连接超时
**原因**：无法访问 api.openai.com
**解决**：检查网络连接，可能需要代理

### 错误3：429 Too Many Requests
**原因**：请求频率过高
**解决**：等待一段时间后重试

### 错误4：500 Internal Server Error
**原因**：可能是请求格式错误
**解决**：查看控制台日志获取详细错误信息

## ✅ 成功标志

看到以下输出说明一切正常：

```json
{
  "status": "success",
  "message": "OpenAI API 连接正常",
  "response": "你好，测试成功！"
}
```

## 🔧 调试技巧

1. **查看完整日志**：在 application.properties 中添加
   ```properties
   logging.level.com.aiinterview=DEBUG
   logging.level.org.springframework.web.reactive=DEBUG
   ```

2. **测试API Key**：可以直接用 curl 测试
   ```bash
   curl https://api.openai.com/v1/chat/completions \
     -H "Content-Type: application/json" \
     -H "Authorization: Bearer sk-your-api-key" \
     -d '{
       "model": "gpt-3.5-turbo",
       "messages": [{"role": "user", "content": "Hello"}]
     }'
   ```

3. **使用 Postman**：导入以下请求进行测试

## 📝 测试脚本

也可以使用这个快速测试脚本：

```bash
#!/bin/bash

BASE_URL="http://localhost:8080/api/test/openai"

echo "=== 1. 配置检查 ==="
curl -s "$BASE_URL/config" | jq '.'
echo ""

echo "=== 2. 简单测试 ==="
curl -s "$BASE_URL/simple" | jq '.'
echo ""

echo "=== 3. 中文测试 ==="
curl -s "$BASE_URL/chinese" | jq '.'
echo ""

echo "=== 4. 面试问题测试 ==="
curl -s "$BASE_URL/interview-question" | jq '.'
echo ""

echo "测试完成！"
```

保存为 `quick-test.sh`，然后运行：
```bash
chmod +x quick-test.sh
./quick-test.sh
```

## 🎉 下一步

测试通过后，你可以：
1. 测试完整的面试流程 API
2. 集成到前端应用
3. 根据实际效果优化 prompt

