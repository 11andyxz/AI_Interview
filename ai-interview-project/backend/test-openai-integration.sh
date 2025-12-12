#!/bin/bash

# OpenAI Integration Test Script
# 测试OpenAI集成功能

BASE_URL="http://localhost:8080"
SESSION_ID=""

echo "================================"
echo "OpenAI Integration Test Script"
echo "================================"
echo ""

# Color codes
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Test 1: Health Check
echo -e "${YELLOW}[Test 1] Health Check${NC}"
response=$(curl -s "${BASE_URL}/api/llm/health")
echo "Response: $response"
echo ""

# Test 2: Create Session
echo -e "${YELLOW}[Test 2] Create Session${NC}"
response=$(curl -s -X POST "${BASE_URL}/api/sessions" \
  -H "Content-Type: application/json" \
  -d '{
    "roleId": "backend_java",
    "level": "mid",
    "skills": ["java_core", "spring_boot"]
  }')
echo "Response: $response"
SESSION_ID=$(echo $response | grep -o '"id":"[^"]*' | sed 's/"id":"//')
echo "Session ID: $SESSION_ID"
echo ""

if [ -z "$SESSION_ID" ]; then
  echo -e "${RED}Failed to create session!${NC}"
  exit 1
fi

# Test 3: Generate Question (Non-streaming)
echo -e "${YELLOW}[Test 3] Generate Question (Non-streaming)${NC}"
curl -s -X POST "${BASE_URL}/api/llm/question-generate" \
  -H "Content-Type: application/json" \
  -d "{
    \"sessionId\": \"$SESSION_ID\",
    \"roleId\": \"backend_java\",
    \"level\": \"mid\"
  }" | jq '.'
echo ""

# Test 4: Evaluate Answer
echo -e "${YELLOW}[Test 4] Evaluate Answer${NC}"
curl -s -X POST "${BASE_URL}/api/llm/eval" \
  -H "Content-Type: application/json" \
  -d '{
    "question": "请解释HashMap的工作原理",
    "answer": "HashMap是基于哈希表实现的Map接口。它使用数组加链表/红黑树的结构。通过key的hashCode计算数组索引，处理哈希冲突时使用链表或红黑树。",
    "roleId": "backend_java",
    "level": "mid"
  }' | jq '.'
echo ""

# Test 5: Streaming Question (if supported)
echo -e "${YELLOW}[Test 5] Generate Question (Streaming)${NC}"
echo "Connecting to SSE endpoint..."
curl -N "${BASE_URL}/api/llm/question-generate/stream?sessionId=${SESSION_ID}&roleId=backend_java&level=mid" 2>/dev/null &
CURL_PID=$!
sleep 5
kill $CURL_PID 2>/dev/null
echo ""
echo ""

# Test 6: General Chat
echo -e "${YELLOW}[Test 6] General Chat${NC}"
curl -s -X POST "${BASE_URL}/api/llm/chat" \
  -H "Content-Type: application/json" \
  -d '{
    "messages": [
      {"role": "system", "content": "你是一个技术面试官"},
      {"role": "user", "content": "请用一句话介绍Spring Boot"}
    ]
  }' | jq '.'
echo ""

echo -e "${GREEN}================================${NC}"
echo -e "${GREEN}All tests completed!${NC}"
echo -e "${GREEN}================================${NC}"
echo ""
echo "Tips:"
echo "- Make sure OPENAI_API_KEY environment variable is set"
echo "- Application should be running on port 8080"
echo "- Use 'jq' for better JSON formatting (optional)"

