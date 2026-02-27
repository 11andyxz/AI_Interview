# Golden Dataset

This directory contains golden dataset examples for ML quality validation and regression testing.

## Dataset Statistics

- **Resume Analysis**: 10 examples
- **Interview Questions**: 91 examples
- **Scoring**: 30 examples
- **Multi-turn Conversations**: 20 examples
- **Total**: 151 examples

## Directory Structure

```
golden_dataset/
├── resume_analysis/          # Resume analysis golden examples (10 files)
│   ├── senior_backend_java.json
│   ├── mid_frontend_react.json
│   ├── entry_fullstack.json
│   └── ... (7 more)
├── interview_questions/      # Interview question golden examples (91 files)
│   ├── question_001.json through question_091.json
│   ├── backend_l3_spring_boot.json
│   ├── frontend_l4_react_performance.json
│   └── ... (88 more)
├── scoring/                  # Scoring evaluation golden examples (30 files)
│   ├── scoring_001.json through scoring_031.json
│   ├── good_answer_spring_boot.json
│   └── ... (27 more)
└── multi_turn/              # Multi-turn conversation golden examples (20 files)
    ├── conversation_001.json through conversation_020.json
    ├── backend_microservices_deep_dive.json
    └── ... (18 more)
```

## Purpose

Golden datasets serve multiple purposes:

1. **Regression Testing**: Ensure ML model outputs don't degrade over time
2. **Quality Benchmarking**: Establish baseline quality metrics for different scenarios
3. **Validator Testing**: Validate that output validators work correctly
4. **Model Comparison**: Compare different ML models using standardized test cases

## Dataset Categories

### 1. Resume Analysis (resume_analysis/)

Examples of resume parsing and candidate assessment across different:
- Experience levels (Entry/Mid/Senior/Staff)
- Tech stacks (Backend/Frontend/Data/DevOps)
- Languages (Chinese/English)

**Current Examples**: 5
- Senior Backend Engineer (Java/Spring Boot)
- Mid-level Frontend Developer (React)
- Entry-level Full Stack Developer
- Senior Data Engineer (Spark/Flink)
- Senior DevOps Engineer (Kubernetes)

**Target**: 10 examples

### 2. Interview Questions (interview_questions/)

Golden examples of AI-generated interview questions with expected quality standards.

**Difficulty Levels**:
- L3: Mid-level (3-5 years experience)
- L4: Senior (5-8 years experience)
- L5: Staff/Principal (8+ years experience)

**Current Examples**: 3
- Backend L3: Spring Boot auto-configuration
- Frontend L4: React performance optimization
- System Design L5: Instant messaging system

**Target**: 50 examples (covering various domains and difficulty levels)

### 3. Scoring (scoring/)

Examples of candidate answer evaluations with detailed feedback and scoring criteria.

**Quality Levels**:
- Excellent (9.0-10.0)
- Good (7.0-8.9)
- Average (5.0-6.9)
- Poor (0-4.9)

**Current Examples**: 3
- Good answer: Spring Boot explanation
- Average answer: Spring Boot (lacks depth)
- Excellent answer: React performance optimization

**Target**: 30 examples (10 per quality level for Good/Average/Excellent)

### 4. Multi-turn Conversations (multi_turn/)

Golden examples of complete interview conversations with progressive depth.

**Current Examples**: 1
- Backend microservices deep dive (3 turns)

**Target**: 20 examples

## Data Format

### Resume Analysis Format
```json
{
  "candidateId": "golden_001",
  "name": "候选人姓名",
  "position": "职位",
  "yearsOfExperience": 5,
  "resume": "完整简历内容...",
  "techStack": ["技术栈"],
  "expectedOutput": {
    "candidateInfo": {...},
    "skills": {...},
    "experience": {...},
    "assessment": {...},
    "interviewQuestions": [...]
  },
  "qualityMetrics": {
    "structureValidity": 1.0,
    "contentQuality": 0.95,
    "relevance": 0.98,
    "completeness": 1.0
  }
}
```

### Interview Questions Format
```json
{
  "questionId": "backend_l3_001",
  "difficulty": "L3 - Mid-level",
  "domain": "Backend Development",
  "techStack": ["Java", "Spring Boot"],
  "question": "面试问题...",
  "expectedOutput": {
    "answerStructure": {...},
    "keyPoints": [...],
    "qualityIndicators": {...},
    "sampleGoodAnswer": "...",
    "evaluationCriteria": {...}
  },
  "qualityMetrics": {
    "relevance": 0.95,
    "difficulty": 0.70,
    "discriminability": 0.85
  }
}
```

### Scoring Format
```json
{
  "scoringId": "scoring_001",
  "questionId": "backend_l3_001",
  "question": "面试问题...",
  "candidateAnswer": "候选人回答...",
  "evaluation": {
    "score": 8.5,
    "level": "Good",
    "strengths": [...],
    "improvements": [...],
    "technicalAccuracy": 0.90,
    "completeness": 0.85,
    "clarity": 0.90,
    "depth": 0.80,
    "feedback": "详细反馈..."
  },
  "qualityMetrics": {...}
}
```

### Multi-turn Conversation Format
```json
{
  "conversationId": "multi_turn_001",
  "scenario": "场景描述",
  "difficulty": "L4 - Senior",
  "domain": "Backend Development",
  "turns": [
    {
      "turnNumber": 1,
      "interviewer": "面试官问题",
      "candidate": "候选人回答",
      "evaluation": {...}
    }
  ],
  "overallAssessment": {...},
  "qualityMetrics": {...}
}
```

## Quality Metrics

Each golden example includes quality metrics:

- **structureValidity** (0-1): JSON structure correctness
- **contentQuality** (0-1): Content richness and accuracy
- **relevance** (0-1): Relevance to position/tech stack
- **completeness** (0-1): Completeness of information
- **difficulty** (0-1): Question difficulty appropriateness
- **discriminability** (0-1): Ability to differentiate candidate levels

## Usage

### In Testing
```java
@Test
void testResumeAnalysisAgainstGoldenDataset() {
    GoldenDataset golden = loadGolden("resume_analysis/senior_backend_java.json");
    AIOutput actual = aiService.analyzeResume(golden.getResume());
    
    assertThat(actual).meetsQualityThreshold(golden.getQualityMetrics());
}
```

### In Quality Monitoring
```java
@Scheduled(cron = "0 0 * * * *")
void validateAgainstGoldenDataset() {
    for (GoldenExample example : goldenDataset) {
        ValidationResult result = validationPipeline.validateAll(
            aiService.process(example.getInput()),
            example.getContext()
        );
        
        if (result.getOverallScore() < example.getQualityThreshold()) {
            alertService.send("Quality degradation detected");
        }
    }
}
```

## Maintenance

- **Add new examples**: When encountering edge cases or new scenarios
- **Update metrics**: When quality standards change
- **Version control**: Track changes to golden dataset in Git
- **Review cycle**: Quarterly review to ensure relevance

## Statistics

| Category | Current | Target | Progress |
|----------|---------|--------|----------|
| Resume Analysis | 5 | 10 | 50% |
| Interview Questions | 3 | 50 | 6% |
| Scoring | 3 | 30 | 10% |
| Multi-turn | 1 | 20 | 5% |
| **Total** | **12** | **110** | **11%** |

Last Updated: 2024-02-05
