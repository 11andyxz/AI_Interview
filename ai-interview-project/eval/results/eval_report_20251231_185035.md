# AI Interview Evaluation Report

**Generated**: 2025-12-31 18:50:35

**Backend**: http://localhost:8080
**Model (backend config)**: gpt-3.5-turbo

## Overall Summary

- **Total Tests**: 40
- **Successful**: 40 (100.0%)
- **Failed**: 0
- **Average Latency**: 1981.92ms
- **Median Latency (p50)**: 1389.41ms
- **95th Percentile (p95)**: 3838.79ms
- **Total Tokens**: 5551
- **Avg Tokens per Test**: 138.8

## Quality Metrics (Rubric-Based)

- **Overall Quality**: 91.1/100
- **Completeness**: 88.8/100
- **Format Compliance**: 82.8/100
- **Factuality** (no uncertainty): 100.0/100
- **Coherence**: 91.5/100

## Results by Prompt Type

### Resume Analysis

- Tests: 15
- Success Rate: 100.0%
- Avg Latency: 3294.39ms
- p95 Latency: 4991.28ms
- Avg Tokens: 128.2

### Interview Qa

- Tests: 20
- Success Rate: 100.0%
- Avg Latency: 1161.11ms
- p95 Latency: 1666.43ms
- Avg Tokens: 136.6

### Scoring

- Tests: 2
- Success Rate: 100.0%
- Avg Latency: 1639.48ms
- p95 Latency: 2092.93ms
- Avg Tokens: 215.0

### Multi Turn

- Tests: 3
- Success Rate: 100.0%
- Avg Latency: 1119.88ms
- p95 Latency: 1330.93ms
- Avg Tokens: 155.7

## Slowest Tests (Top 5)

| ID | Task Type | Latency (ms) |
|-----|-----------|-------------|
| RS-01 | resume_summary | 4991.28 |
| RS-08 | readiness_assessment | 3838.79 |
| RS-07 | resume_summary | 3836.02 |
| RS-03 | resume_strengths | 3620.38 |
| RS-04 | resume_weaknesses | 3549.04 |

## Sample Outputs

Representative examples from each test category:

### Interview Qa

**Test**: IQ-01 (behavioral)

**Response**: {'question': '当然，让我们开始面试吧！首先，请您简要介绍一下您的工作经验和在Java后端开发方面的专长。\n\n接下来，我想问一个关于Spring Boot框架的问题：在您的项目经验中，您是如何处理Spring Boot应用程序中的异常情况的呢？您可以谈谈您的做法以及遇到的挑战吗？', 'questionNumber': 1, 'sessionId': 'eval_IQ-01'}

**Quality**: 89/100 (Completeness: 85, Format: 70, Factuality: 100, Coherence: 100)

**Latency**: 1035.12ms | **Tokens**: 217

---

**Test**: IQ-02 (behavioral)

**Response**: {'question': '当然，让我们开始吧！首先，很高兴能与您进行技术面试。在接下来的面试中，我会针对您的工作经验和项目背景提出一些问题，让您有机会展示您的技术能力。让我们开始第一个问题吧。\n\n在您的简历中提到了您有微服务架构的开发经验，请问在实际项目中，您是如何实现微服务之间的通信和数据同步的呢？请结合具体的技术方案和实现细节进行描述。', 'questionNumber': 1, 'sessionId': 'eval_IQ-02'}

**Quality**: 89/100 (Completeness: 85, Format: 70, Factuality: 100, Coherence: 100)

**Latency**: 1064.53ms | **Tokens**: 214

---

### Multi Turn

**Test**: MS-01 (multi_turn_technical)

**Response**: {'question': '欢迎参加面试，很高兴能与您交流技术。在接下来的面试中，我将提出一些与Java后端开发相关的问题，希望能更好地了解您的技术能力和经验。\n\n让我们开始第一个问题吧：\n\n在您的最近一个项目中，您是如何使用Spring Boot框架来构建微服务的？您可以分享一下您的设计思路和实施过程吗？', 'questionNumber': 1, 'sessionId': 'eval_MS-01'}

**Quality**: 89/100 (Completeness: 85, Format: 70, Factuality: 100, Coherence: 100)

**Latency**: 1094.66ms | **Tokens**: 205

---

**Test**: MS-02 (multi_turn_behavioral)

**Response**: {'question': '非常感谢您参加我们的面试。在接下来的面试中，我会针对您的工作经验和项目背景提出一些与Java后端开发相关的技术问题。让我们开始第一个问题吧。\n\n请简要介绍一下您在Java后端开发方面的工作经验，重点描述您在项目中负责的具体任务和涉及的技术栈。', 'questionNumber': 1, 'sessionId': 'eval_MS-02'}

**Quality**: 89/100 (Completeness: 85, Format: 70, Factuality: 100, Coherence: 100)

**Latency**: 934.06ms | **Tokens**: 188

---

### Resume Analysis

**Test**: RS-01 (resume_summary)

**Response**: {'improvements': ['Expand on experience with system design', 'Provide more details on project involvement and outcomes'], 'strengths': ['Solid understanding of Java and Spring Boot', 'Clear and concise communication'], 'rubricLevel': 'good', 'followUpQuestions': ['Can you discuss a specific project where you implemented REST APIs?', 'How have you handled scalability challenges in your previous ...

**Quality**: 96/100 (Completeness: 100, Format: 100, Factuality: 100, Coherence: 80)

**Latency**: 4991.28ms | **Tokens**: 215

---

**Test**: RS-02 (resume_summary)

**Response**: {'improvements': ['需要更清晰明了地提供一个符合`resume_summary` schema的JSON对象', '可以更具体地描述自己的技能和经验'], 'strengths': ['具有混合前端/后端经验', '对角色焦点不清晰有意识'], 'rubricLevel': 'average', 'followUpQuestions': ['您如何处理前后端技术的结合？', '您如何确认项目中的角色职责和焦点？'], 'detailedScores': {'technicalAccuracy': 6, 'communication': 7, 'experience': 7, 'depth': 5}, 'score': 65.0}

**Quality**: 96/100 (Completeness: 100, Format: 100, Factuality: 100, Coherence: 80)

**Latency**: 3459.57ms | **Tokens**: 167

---

### Scoring

**Test**: SC-01 (scoring)

**Response**: {'improvements': ['候选人未给出有效的回答，缺乏技术准确性和深度。', '建议候选人回顾LRU缓存的概念以及在高流量API场景下的最佳实践。'], 'strengths': [], 'rubricLevel': 'poor', 'followUpQuestions': ['您能详细解释LRU缓存的工作原理吗？', '在设计高流量API缓存时，您会考虑哪些其他因素？'], 'detailedScores': {'technicalAccuracy': 0, 'communication': 3, 'experience': 1, 'depth': 2}, 'score': 40.0}

**Quality**: 96/100 (Completeness: 100, Format: 100, Factuality: 100, Coherence: 80)

**Latency**: 2092.93ms | **Tokens**: 230

---

**Test**: SC-02 (scoring)

**Response**: {'improvements': ['缺乏回答', '需要展示对零停机时间架构迁移的了解'], 'strengths': [], 'rubricLevel': 'poor', 'followUpQuestions': ['您能详细解释零停机时间架构迁移的概念吗？', '在实际项目中，您遇到过哪些零停机时间架构迁移的挑战？'], 'detailedScores': {'technicalAccuracy': 0, 'communication': 10, 'experience': 0, 'depth': 0}, 'score': 20.0}

**Quality**: 91/100 (Completeness: 85, Format: 100, Factuality: 100, Coherence: 80)

**Latency**: 1186.02ms | **Tokens**: 200

---

