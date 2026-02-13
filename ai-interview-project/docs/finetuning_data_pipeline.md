# Fine-Tuning Data Collection Pipeline

## Overview

The fine-tuning data pipeline automatically collects high-quality training data from production interviews, enabling continuous model improvement through supervised fine-tuning (SFT) and reinforcement learning from human feedback (RLHF/DPO).

**Key Features:**
- ✅ Automatic quality filtering (≥75 score threshold)
- ✅ Versioned JSONL exports with train/val/test splits
- ✅ Preference pair collection for RLHF/DPO
- ✅ Human feedback API for correction and flagging
- ✅ Comprehensive validation (balance, diversity, contamination)
- ✅ OpenAI fine-tuning format compatibility

## Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                     Production Interviews                            │
│  (InterviewInteraction entities with quality scores & validation)   │
└────────────────────────────┬────────────────────────────────────────┘
                             │
                             ▼
           ┌─────────────────────────────────┐
           │  TrainingDataCollector          │
           │  - Quality filtering            │
           │  - Preference pair matching     │
           │  - Metadata enrichment          │
           └─────────────┬───────────────────┘
                         │
         ┌───────────────┴───────────────┐
         │                               │
         ▼                               ▼
┌────────────────┐            ┌──────────────────┐
│ Supervised SFT │            │ Preference Pairs │
│ QA Examples    │            │ (RLHF/DPO)       │
└────────┬───────┘            └────────┬─────────┘
         │                             │
         ▼                             ▼
┌────────────────────────────────────────────────┐
│        TrainingDataExporter                    │
│  - JSONL formatting                            │
│  - Train/val/test splitting (80/10/10)        │
│  - Metadata generation                         │
│  - Quality reporting                           │
└────────────────┬───────────────────────────────┘
                 │
                 ▼
      ┌──────────────────────┐
      │  Validation Pipeline │
      │  - Quality checks    │
      │  - Balance analysis  │
      │  - Diversity metrics │
      │  - Contamination     │
      └──────────┬───────────┘
                 │
                 ▼
      ┌──────────────────────┐
      │ Versioned Datasets   │
      │ v1.0_YYYY-MM-DD/     │
      │  ├── train.jsonl     │
      │  ├── validation.jsonl│
      │  ├── test.jsonl      │
      │  ├── metadata.json   │
      │  └── quality_report.md│
      └──────────────────────┘
                 │
                 ▼
      ┌──────────────────────┐
      │  OpenAI Fine-Tuning  │
      │  gpt-3.5-turbo       │
      └──────────────────────┘
```

## Components

### 1. TrainingDataCollector

**Package:** `com.aiinterview.ml.training`

Collects high-quality examples from production interviews with automatic filtering.

#### Key Methods

```java
@Service
public class TrainingDataCollector {
    
    // Collect supervised fine-tuning examples
    List<TrainingExample> collectQualityPairs(LocalDate from, LocalDate to);
    
    // Collect preference pairs for RLHF/DPO
    List<PreferencePair> collectPreferencePairs();
    
    // Collect with custom filters
    List<TrainingExample> collectWithFilters(Map<String, Object> filters);
}
```

#### Quality Filters

| Filter | Threshold | Purpose |
|--------|-----------|---------|
| Quality Score | ≥ 75.0 | Ensure high-quality responses |
| Validation Status | Pass | No schema violations |
| Answer Length | 50-2000 chars | Filter too short/long responses |
| Flagged Status | false | Exclude reported issues |
| Duplicates | Removed | Ensure diversity |

#### Example Usage

```java
// Collect last 30 days
List<TrainingExample> examples = trainingDataCollector
    .collectQualityPairs(
        LocalDate.now().minusDays(30), 
        LocalDate.now()
    );

// Filter by role
Map<String, Object> filters = Map.of("role", "backend_java");
List<TrainingExample> javaExamples = trainingDataCollector
    .collectWithFilters(filters);
```

### 2. TrainingExample Model

Represents a single training example in OpenAI chat format.

```java
@Data
@Builder
public class TrainingExample {
    private Long interactionId;
    private String conversationId;
    private String role;              // backend_java, frontend_react, etc.
    private String difficulty;        // junior, mid, senior
    private List<Message> messages;   // Chat format
    private String completion;        // Expected answer
    private Double qualityScore;      // 0-100
    private Boolean validationPass;
    private String category;          // coding, system_design, etc.
    private String skill;             // java, react, databases, etc.
    private LocalDateTime createdAt;
    
    @Data
    @Builder
    public static class Message {
        private String role;       // system | user | assistant
        private String content;
    }
}
```

### 3. PreferencePair Model

Represents a pair of responses for RLHF/DPO training.

```java
@Data
@Builder
public class PreferencePair {
    private Long pairId;
    private String conversationId;
    private String prompt;          // The question
    private String context;         // Resume, previous Q&A
    private Response chosen;        // Higher quality response
    private Response rejected;      // Lower quality response
    private Double margin;          // Quality difference
    private String role;
    private String difficulty;
    private LocalDateTime createdAt;
    
    @Data
    @Builder
    public static class Response {
        private String content;
        private Double qualityScore;
        private String modelVersion;
        private Boolean validationPass;
    }
}
```

**Preference Pair Selection:**
1. Group interactions by question
2. Sort by quality score
3. Pair top quartile (chosen) with bottom quartile (rejected)
4. Require minimum margin of 20 points

### 4. TrainingDataExporter

**Package:** `com.aiinterview.ml.training`

Exports data to versioned JSONL format with automatic splitting.

```java
@Service
public class TrainingDataExporter {
    
    // Export supervised fine-tuning data
    Path exportTrainingData(LocalDate from, LocalDate to) throws IOException;
    
    // Export preference pairs
    Path exportPreferencePairs() throws IOException;
}
```

#### Data Splits

- **Train:** 80% (primary training data)
- **Validation:** 10% (hyperparameter tuning)
- **Test:** 10% (final evaluation)

Splitting is random with seed for reproducibility.

#### Export Format

**Supervised Fine-Tuning (train.jsonl):**

```jsonl
{
  "messages": [
    {
      "role": "system",
      "content": "You are an expert technical interviewer. Generate insightful questions and provide constructive feedback."
    },
    {
      "role": "user",
      "content": "Explain the difference between ArrayList and LinkedList in Java"
    },
    {
      "role": "assistant",
      "content": "ArrayList and LinkedList are both List implementations but with different performance characteristics:\n\n1. ArrayList:\n- Backed by dynamic array\n- O(1) random access\n- O(n) insertions/deletions in middle\n- Better cache locality\n\n2. LinkedList:\n- Backed by doubly-linked list\n- O(n) random access\n- O(1) insertions/deletions at ends\n- Higher memory overhead\n\nUse ArrayList by default unless you need frequent insertions/deletions."
    }
  ],
  "metadata": {
    "interaction_id": 12345,
    "role": "backend_java",
    "difficulty": "mid",
    "quality_score": 87.5,
    "category": "coding",
    "skill": "java"
  }
}
```

**Preference Pairs (train_preferences.jsonl):**

```jsonl
{
  "prompt": "What is dependency injection?",
  "context": "Senior backend Java developer interview, discussing Spring Framework",
  "chosen": "Dependency injection (DI) is a design pattern where dependencies are provided to a class rather than created internally. In Spring, this is achieved through:\n\n1. Constructor injection (recommended)\n2. Setter injection\n3. Field injection\n\nBenefits:\n- Loose coupling\n- Easier testing (mock dependencies)\n- Better maintainability\n- Framework manages lifecycle\n\nSpring's IoC container handles object creation and wiring automatically.",
  "rejected": "It's when you use @Autowired annotation in Spring to inject beans.",
  "margin": 28.5,
  "metadata": {
    "pair_id": 123450001,
    "role": "backend_java",
    "difficulty": "senior",
    "chosen_score": 94.5,
    "rejected_score": 66.0
  }
}
```

#### Metadata File (metadata.json)

```json
{
  "version": "v1.0_2026-02-13",
  "created_at": "2026-02-13",
  "date_range": {
    "from": "2026-01-14",
    "to": "2026-02-13"
  },
  "splits": {
    "train": 240,
    "validation": 30,
    "test": 30,
    "total": 300
  },
  "role_distribution": {
    "backend_java": 120,
    "frontend_react": 90,
    "fullstack": 60,
    "system_design": 30
  },
  "difficulty_distribution": {
    "mid": 150,
    "senior": 90,
    "junior": 60
  },
  "category_distribution": {
    "coding": 180,
    "system_design": 60,
    "behavioral": 60
  },
  "avg_quality_score": 82.3
}
```

### 5. Human Feedback API

**Package:** `com.aiinterview.ml.training`

Collect human feedback for training data quality control.

#### FeedbackRecord Entity

```java
@Entity
@Table(name = "ai_feedback")
public class FeedbackRecord {
    private Long id;
    private Long interactionId;
    private String conversationId;
    private String feedbackType;    // thumbs_up | thumbs_down | flag | correction
    private Integer rating;         // 1-5
    private String issues;          // hallucination | irrelevant | incomplete | incorrect
    private String comment;         // Free text
    private String correction;      // Suggested correction
    private String source;          // interviewer | candidate | admin | automated
    private LocalDateTime createdAt;
}
```

#### API Endpoints

**Submit Feedback:**

```http
POST /api/ml/training/feedback
Content-Type: application/json

{
  "interaction_id": 12345,
  "feedback_type": "thumbs_down",
  "rating": 2,
  "issues": "hallucination,incomplete",
  "comment": "Response contained outdated information about React hooks",
  "correction": "React hooks were introduced in 16.8, not 16.0. Use useState for state in function components.",
  "source": "interviewer"
}

Response: 200 OK
{
  "id": 567,
  "interaction_id": 12345,
  "feedback_type": "thumbs_down",
  "rating": 2,
  "created_at": "2026-02-13T10:30:00"
}
```

**Get Feedback for Interaction:**

```http
GET /api/ml/training/feedback/12345

Response: 200 OK
[
  {
    "id": 567,
    "interaction_id": 12345,
    "feedback_type": "thumbs_down",
    "rating": 2,
    "comment": "Response contained outdated information",
    "created_at": "2026-02-13T10:30:00"
  }
]
```

**Get Feedback Statistics:**

```http
GET /api/ml/training/feedback/stats?days=7

Response: 200 OK
{
  "period_days": 7,
  "thumbs_up": 145,
  "thumbs_down": 23,
  "flags": 5,
  "corrections": 12,
  "total": 185
}
```

### 6. Validation Pipeline

**Script:** `eval/training_data/scripts/validate_dataset.py`

Comprehensive dataset validation before fine-tuning.

#### Validation Checks

**1. Quality Validation**
- Verify all examples meet minimum quality threshold
- Report average and minimum quality scores
- Flag low-quality examples

```bash
[1/5] Quality Validation
  ✓ Average quality: 82.30 (threshold: 75.0)
```

**2. Balance Validation**
- Check role, difficulty, and category distributions
- Flag if any class exceeds 80% (severe imbalance)
- Report distribution statistics

```bash
[2/5] Balance Validation
  ✓ Balanced distributions
    - Roles: {'backend_java': 120, 'frontend_react': 90, 'fullstack': 60}
    - Difficulties: {'mid': 150, 'senior': 90, 'junior': 60}
```

**3. Diversity Validation**
- Calculate unique/total prompt ratio
- Measure vocabulary size
- Detect duplicates

```bash
[3/5] Diversity Validation
  ✓ Diverse content
    - 285/300 unique prompts
    - 3,450 unique words
```

**4. Format Validation**
- Verify OpenAI chat format compliance
- Check required fields (messages, role, content)
- Validate message structure

```bash
[4/5] Format Validation
  ✓ Valid OpenAI chat format
```

**5. Length Validation**
- Check token count distribution
- Flag examples that are too short (<20 tokens) or too long (>4096 tokens)
- Report average and max lengths

```bash
[5/5] Length Validation
  ✓ Appropriate lengths
    - Average: 250 tokens
    - Max: 1,200 tokens
```

**6. Contamination Detection**
- Compare train and test set prompts
- Flag if overlap exceeds 1%
- Ensure proper data splitting

```bash
CONTAMINATION CHECK
✓ No contamination detected
  - 2/30 overlapping prompts (0.67%)
```

#### Usage

```bash
# Validate dataset
python eval/training_data/scripts/validate_dataset.py \
  exports/v1.0_2026-02-13/

# Save validation report
python eval/training_data/scripts/validate_dataset.py \
  exports/v1.0_2026-02-13/ \
  --output validation_report.json
```

## Workflows

### 1. Monthly Data Export

**Objective:** Export quality training data from last 30 days

```bash
# Via API
curl -X POST "http://localhost:8080/api/ml/training/export?from=2026-01-14&to=2026-02-13"

# Via Python script
python eval/training_data/scripts/export_training_data.py --days 30
```

**Output:**
```
exports/v1.0_2026-02-13/
├── train.jsonl          (240 examples)
├── validation.jsonl     (30 examples)
├── test.jsonl           (30 examples)
├── metadata.json
└── quality_report.md
```

### 2. Preference Pair Export (RLHF/DPO)

**Objective:** Export preference pairs for reinforcement learning

```bash
curl -X POST "http://localhost:8080/api/ml/training/export/preferences"
```

**Output:**
```
exports/v1.0_2026-02-13_preferences/
├── train_preferences.jsonl
├── validation_preferences.jsonl
└── test_preferences.jsonl
```

### 3. Validation & Fine-Tuning

```bash
# 1. Validate dataset
python eval/training_data/scripts/validate_dataset.py \
  exports/v1.0_2026-02-13/

# 2. Upload to OpenAI
openai api files create \
  -f exports/v1.0_2026-02-13/train.jsonl \
  -p fine-tune

openai api files create \
  -f exports/v1.0_2026-02-13/validation.jsonl \
  -p fine-tune

# 3. Create fine-tuning job
openai api fine_tuning.jobs.create \
  -t file-abc123 \
  -v file-def456 \
  -m gpt-3.5-turbo \
  --suffix "interview-v1"

# 4. Monitor training
openai api fine_tuning.jobs.get -i ftjob-xyz789

# 5. Test fine-tuned model
openai api chat.completions.create \
  -m ft:gpt-3.5-turbo:org:suffix:id \
  --messages '[{"role":"user","content":"Explain SOLID principles"}]'
```

### 4. Feedback Collection

**UI Integration:**

```javascript
// Submit feedback
fetch('/api/ml/training/feedback', {
  method: 'POST',
  headers: {'Content-Type': 'application/json'},
  body: JSON.stringify({
    interaction_id: interactionId,
    feedback_type: 'thumbs_down',
    rating: 2,
    issues: 'hallucination',
    comment: 'Contained incorrect information',
    source: 'interviewer'
  })
});
```

**Review Feedback:**

```bash
# Get recent feedback
curl "http://localhost:8080/api/ml/training/feedback/stats?days=7"

# Review flagged interactions
curl "http://localhost:8080/api/ml/training/feedback/{interactionId}"
```

## Best Practices

### Data Collection

1. **Quality Threshold:** Keep minimum at 75 to ensure high-quality training data
2. **Diversity:** Collect from multiple roles, difficulties, and categories
3. **Recency:** Prioritize recent data (last 30-60 days) to capture current patterns
4. **Volume:** Aim for 500+ examples for initial fine-tuning, 2000+ for production

### Fine-Tuning

1. **Start Small:** Begin with 100-200 examples to test
2. **Monitor Overfitting:** Watch validation loss closely
3. **Hyperparameters:**
   - Learning rate: 0.1-0.3 (OpenAI default)
   - Epochs: 3-5 (adjust based on validation loss)
   - Batch size: Default (OpenAI auto-selects)

4. **Evaluation:** Test on held-out test set before deployment

### Feedback Loop

1. **Enable UI Feedback:** Add thumbs up/down buttons
2. **Review Weekly:** Check feedback stats every week
3. **Address Flags:** Manually review flagged responses
4. **Iterate:** Retrain monthly with new data + feedback corrections

### Continuous Improvement

```
Production Interviews
     ↓
Quality Filtering
     ↓
Dataset Export
     ↓
Validation
     ↓
Fine-Tuning
     ↓
A/B Testing (Experiment Framework)
     ↓
Collect Feedback
     ↓
[Loop back to Quality Filtering]
```

## Monitoring

Track these metrics:

| Metric | Target | Action if Below |
|--------|--------|-----------------|
| Export Volume | 300+ examples/month | Expand date range or check interview activity |
| Avg Quality Score | ≥ 80 | Review quality scoring logic |
| Validation Pass Rate | ≥ 95% | Check validation rules |
| Feedback Ratio | thumbs_up / total ≥ 0.8 | Review model performance |
| Contamination Rate | < 1% | Fix splitting logic |

## Troubleshooting

### Issue: Low Export Volume

**Symptoms:** < 100 examples exported

**Solutions:**
1. Expand date range (30 → 60 days)
2. Check database for interview activity
3. Review quality filters (temporarily lower to 70)
4. Verify InteractionRepository queries

### Issue: Imbalanced Dataset

**Symptoms:** Validation reports > 80% in one class

**Solutions:**
1. Use `collectWithFilters()` to oversample minority classes
2. Collect more data for underrepresented categories
3. Apply stratified sampling during split
4. Consider synthetic data augmentation

### Issue: Contamination Detected

**Symptoms:** Train/test overlap > 1%

**Solutions:**
1. Check for duplicate questions in database
2. Verify random splitting with fixed seed
3. Use conversation_id for splitting instead of interaction_id
4. Clean source data of duplicates

### Issue: Low Fine-Tuning Performance

**Symptoms:** Fine-tuned model worse than baseline

**Solutions:**
1. Increase training data volume (500+ examples)
2. Review quality scores - may need higher threshold
3. Check for data quality issues (formatting errors)
4. Verify prompt consistency between training and inference
5. Adjust hyperparameters (learning rate, epochs)

## Performance

| Operation | Time | Notes |
|-----------|------|-------|
| Collect 1000 examples | ~2s | Database query + filtering |
| Export to JSONL | ~1s | File I/O |
| Validation | ~500ms | All checks on 300 examples |
| Fine-tuning (OpenAI) | 10-30 min | Depends on dataset size |

## Security

1. **Data Privacy:** Training data contains sensitive interview content
   - Store exports securely
   - Encrypt at rest
   - Limit access to ML team

2. **PII Filtering:** Remove personally identifiable information
   - Candidate names
   - Company names
   - Email addresses

3. **Feedback Moderation:** Review corrections before using in training
   - Check for malicious input
   - Verify correction quality
   - Audit source of feedback

## Related Documentation

- [RAG Pipeline Architecture](rag_pipeline_architecture.md)
- [Experiment Framework Guide](experiment_framework_guide.md)
- [Quality Metrics Dashboard](../eval/quality_dashboard.py)

## Appendix A: Database Schema

```sql
CREATE TABLE ai_feedback (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    interaction_id BIGINT NOT NULL,
    conversation_id VARCHAR(64),
    feedback_type VARCHAR(20),
    rating INT,
    issues TEXT,
    comment TEXT,
    correction TEXT,
    source VARCHAR(20),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_interaction (interaction_id),
    INDEX idx_conversation (conversation_id),
    INDEX idx_type_created (feedback_type, created_at)
);
```

## Appendix B: Example Quality Report

```markdown
# Training Data Quality Report

Generated: 2026-02-13

## Dataset Statistics

| Split | Size |
|-------|------|
| Train | 240 |
| Validation | 30 |
| Test | 30 |
| **Total** | **300** |

## Quality Metrics

- Average Quality Score: 82.30
- Validation Pass Rate: 97.3%

## Distribution Analysis

### By Role
- backend_java: 120
- frontend_react: 90
- fullstack: 60
- system_design: 30

### By Difficulty
- mid: 150
- senior: 90
- junior: 60

### By Category
- coding: 180
- system_design: 60
- behavioral: 60
```

## Appendix C: RLHF/DPO Training

**Using Preference Pairs:**

```python
from transformers import AutoModelForCausalLM, AutoTokenizer
from trl import DPOTrainer

model = AutoModelForCausalLM.from_pretrained("gpt2")
tokenizer = AutoTokenizer.from_pretrained("gpt2")

# Load preference pairs
train_dataset = load_dataset("json", 
    data_files="exports/v1.0_2026-02-13_preferences/train_preferences.jsonl")

# DPO training
trainer = DPOTrainer(
    model=model,
    ref_model=None,  # Use implicit reference
    train_dataset=train_dataset,
    tokenizer=tokenizer,
    beta=0.1,  # KL penalty coefficient
)

trainer.train()
```

## Summary

The fine-tuning data pipeline provides Production-ready data collection with quality filtering, versioned exports, and comprehensive validation. It supports both supervised fine-tuning (SFT) and reinforcement learning (RLHF/DPO), with built-in human feedback integration for continuous improvement.

**Next Steps:**
1. Export first dataset with 30-day date range
2. Validate using provided scripts
3. Fine-tune gpt-3.5-turbo on OpenAI platform
4. A/B test fine-tuned model vs baseline
5. Iterate based on feedback and experiment results
