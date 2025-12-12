#!/bin/bash

# Quick OpenAI API Test Script
# 快速测试OpenAI API是否正常工作

BASE_URL="http://localhost:8080/api/test/openai"

# Color codes
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo ""
echo "=================================="
echo "  OpenAI API Quick Test"
echo "=================================="
echo ""

# Test 1: Config Check
echo -e "${BLUE}[1/5] 配置检查...${NC}"
response=$(curl -s "$BASE_URL/config")
echo "$response" | jq '.'

configured=$(echo "$response" | jq -r '.configured')
if [ "$configured" = "true" ]; then
    echo -e "${GREEN}✓ API Key 已配置${NC}"
else
    echo -e "${RED}✗ API Key 未配置！${NC}"
    echo -e "${YELLOW}请在 application.properties 中设置 openai.api.key${NC}"
    exit 1
fi
echo ""

# Test 2: Simple Test
echo -e "${BLUE}[2/5] 简单测试（基础连接）...${NC}"
response=$(curl -s "$BASE_URL/simple")
echo "$response" | jq '.'

status=$(echo "$response" | jq -r '.status')
if [ "$status" = "success" ]; then
    echo -e "${GREEN}✓ 简单测试通过${NC}"
    ai_response=$(echo "$response" | jq -r '.response')
    echo -e "  AI回复: ${GREEN}$ai_response${NC}"
else
    echo -e "${RED}✗ 简单测试失败${NC}"
    error=$(echo "$response" | jq -r '.error')
    echo -e "  错误: ${RED}$error${NC}"
fi
echo ""

# Test 3: Chinese Test
echo -e "${BLUE}[3/5] 中文测试（中文对话能力）...${NC}"
response=$(curl -s "$BASE_URL/chinese")
echo "$response" | jq '.'

status=$(echo "$response" | jq -r '.status')
if [ "$status" = "success" ]; then
    echo -e "${GREEN}✓ 中文测试通过${NC}"
    ai_response=$(echo "$response" | jq -r '.response')
    echo -e "  AI回复: ${GREEN}$ai_response${NC}"
else
    echo -e "${RED}✗ 中文测试失败${NC}"
fi
echo ""

# Test 4: Interview Question Test
echo -e "${BLUE}[4/5] 面试问题生成测试...${NC}"
response=$(curl -s "$BASE_URL/interview-question")
echo "$response" | jq '.'

status=$(echo "$response" | jq -r '.status')
if [ "$status" = "success" ]; then
    echo -e "${GREEN}✓ 面试问题生成成功${NC}"
    question=$(echo "$response" | jq -r '.question')
    echo -e "  生成的问题: ${GREEN}$question${NC}"
else
    echo -e "${RED}✗ 面试问题生成失败${NC}"
fi
echo ""

# Test 5: Custom Test
echo -e "${BLUE}[5/5] 自定义测试（POST请求）...${NC}"
response=$(curl -s -X POST "$BASE_URL/custom" \
  -H "Content-Type: application/json" \
  -d '{
    "system": "你是一个Java技术专家",
    "message": "用一句话解释什么是Spring Boot"
  }')
echo "$response" | jq '.'

status=$(echo "$response" | jq -r '.status')
if [ "$status" = "success" ]; then
    echo -e "${GREEN}✓ 自定义测试通过${NC}"
    ai_response=$(echo "$response" | jq -r '.response')
    echo -e "  AI回复: ${GREEN}$ai_response${NC}"
else
    echo -e "${RED}✗ 自定义测试失败${NC}"
fi
echo ""

# Summary
echo "=================================="
echo -e "${GREEN}  测试完成！${NC}"
echo "=================================="
echo ""
echo "测试端点列表："
echo "  1. 配置检查: $BASE_URL/config"
echo "  2. 简单测试: $BASE_URL/simple"
echo "  3. 中文测试: $BASE_URL/chinese"
echo "  4. 面试问题: $BASE_URL/interview-question"
echo "  5. 自定义测试: $BASE_URL/custom (POST)"
echo "  6. 所有测试: $BASE_URL/all"
echo ""
echo "在浏览器中访问："
echo "  http://localhost:8080/api/test/openai/simple"
echo ""

