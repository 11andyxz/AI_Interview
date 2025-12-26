# AI Interview Output Schemas

**Purpose**: Strict JSON schema definitions for validating OpenAI API responses.

**Date**: December 23, 2025  
**Status**: Task 2 Implementation

---

## 1. Resume Summary Schema

**API Endpoint**: `/api/resume/analyze`

### Fields

**name**
- Type: `string`
- Required: Yes
- Constraints: 2-50 characters
- Example: `"张伟"`

**yearsExperience**
- Type: `number`
- Required: Yes
- Constraints: 0-50, integer
- Example: `5`

**coreSkills**
- Type: `array<string>`
- Required: Yes
- Constraints: 1-10 items, each 2-30 characters
- Example: `["Java后端开发", "Spring Boot", "MySQL数据库优化"]`

**positionFit**
- Type: `number`
- Required: Yes
- Constraints: 0-100, integer
- Example: `85`

**strengths**
- Type: `array<string>`
- Required: Yes
- Constraints: 2-5 items, each 10-100 characters
- Example: `["5年Java后端经验，技术栈匹配", "有大规模分布式系统经验"]`

**weaknesses**
- Type: `array<string>`
- Required: Yes
- Constraints: 1-3 items, each 10-100 characters
- Example: `["前端技能较弱", "缺乏微服务架构经验"]`

**suggestedQuestions**
- Type: `array<string>`
- Required: Yes
- Constraints: 3-5 items, each 10-80 characters
- Example: `["请介绍您在分布式系统中处理高并发的经验"]`

---

## 2. Interview Question Schema

**API Endpoint**: `/api/interview/next-question`

### Fields

**action**
- Type: `string`
- Required: Yes
- Constraints: Enum: `"next-question"` | `"follow-up"` | `"summary"`
- Example: `"next-question"`

**question**
- Type: `string` or `null`
- Required: Yes (null only if action="summary")
- Constraints: 10-200 characters
- Example: `"请介绍一下您在项目中如何设计RESTful API？"`

**reasoning**
- Type: `string`
- Required: Yes
- Constraints: 10-50 characters
- Example: `"基于简历中的API设计经验，评估架构能力"`

**expectedDepth**
- Type: `string`
- Required: Yes
- Constraints: Enum: `"brief"` | `"detailed"` | `"in-depth"`
- Example: `"detailed"`

---

## 3. Multi-turn Conversation Schema

**API Endpoint**: `/api/interview/next-question` (with conversation history)

### Fields

Same as Interview Question Schema above, with additional context handling:

**Context Compression** (not in output, but affects generation):
- Recent 3 turns: Full detail preserved
- Turns 4-6: Compressed (question + brief answer summary)
Same fields as Interview Question Schema above. Context compression applied automatically (recent 3 turns full, turns 4-6 compressed, turns 7+ summarized).
### Fields

**score**
- Type: `number`
- Required: Yes
- Constraints: 0-100, integer
- Example: `85`

**rubricLevel**
- Type: `string`
- Required: Yes
- Constraints: Enum: `"excellent"` | `"good"` | `"average"` | `"poor"`
- Example: `"good"`

**technicalAccuracy**
- Type: `number`
- Required: Yes
- Constraints: 0-10, integer
- Example: `8`

**depth**
- Type: `number`
- Required: Yes
- Constraints: 0-10, integer
- Example: `7`

**experience**
- Type: `number`
- Required: Yes
- Constraints: 0-10, integer
- Example: `9`

**communication**
- Type: `number`
- Required: Yes
- Constraints: 0-10, integer
- Example: `8`

**strengths**
- Type: `array<string>`
- Required: Yes
- Constraints: 1-3 items, each 10-50 characters
- Example: `["技术理解准确，概念清晰", "结合了实际项目经验"]`

**improvements**
- Type: `array<string>`
- Required: Yes
- Constraints: 1-3 items, each 10-50 characters
- Example: `["可以更深入讨论性能优化细节"]`

**followUpQuestions**
- Type: `array<string>`
- Required: Yes
- Constraints: 0-2 items, each 10-80 characters
- Example: `["您在优化时具体用了哪些工具？"]`

### Validation Rules

1. `score` must equal `(technicalAccuracy + depth + experience + communication) × 2.5`
2. `rubricLevel` must match score range:
   - excellent: 90-100
   - good: 75-89
   - average: 60-74
   - poor: <60
3. All dimension scores (0-10) must be integers
4. `strengths` and `improvements` must be specific (not generic)
5. If answer is poor quality, `technicalAccuracy` ≤ 5

---

## Implementation Notes
Key Rules

- `score` = `(technicalAccuracy + depth + experience + communication) × 2.5`
- `rubricLevel`: excellent (90-100), good (75-89), average (60-74), poor (<60)