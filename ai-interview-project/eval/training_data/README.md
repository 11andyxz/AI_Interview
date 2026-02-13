# Training Data Collection Pipeline

This directory contains tools for collecting, validating, and exporting high-quality training data from production interviews for model fine-tuning.

## Directory Structure

```
training_data/
├── exports/              # Versioned dataset exports
│   └── v1.0_YYYY-MM-DD/
│       ├── train.jsonl
│       ├── validation.jsonl
│       ├── test.jsonl
│       ├── metadata.json
│       └── quality_report.md
├── scripts/              # Validation and export tools
│   ├── validate_dataset.py
│   └── export_training_data.py
└── README.md
```

## Quick Start

### 1. Export Training Data

Export supervised fine-tuning data:

```bash
# Export last 30 days
python scripts/export_training_data.py --days 30

# Export specific date range
python scripts/export_training_data.py \
  --from-date 2026-01-01 \
  --to-date 2026-02-01

# Export preference pairs for RLHF/DPO
python scripts/export_training_data.py --preferences
```

### 2. Validate Dataset

Run validation checks on exported data:

```bash
python scripts/validate_dataset.py exports/v1.0_2026-02-13/
```

Validation includes:
- **Quality**: All examples meet minimum quality threshold
- **Balance**: No severe class imbalance
- **Diversity**: Sufficient vocabulary and unique prompts
- **Format**: Valid OpenAI chat format
- **Length**: Appropriate token counts
- **Contamination**: No train/test leakage

### 3. Use for Fine-Tuning

The exported JSONL files are ready for OpenAI fine-tuning:

```bash
openai api fine_tuning.jobs.create \
  -t exports/v1.0_2026-02-13/train.jsonl \
  -v exports/v1.0_2026-02-13/validation.jsonl \
  -m gpt-3.5-turbo
```

## API Endpoints

### Training Data Collection

```http
GET /api/ml/training/examples?from=2026-01-01&to=2026-02-01
```

Returns high-quality QA pairs filtered by:
- Quality score ≥ 75
- Validation passed
- Appropriate length (50-2000 chars)
- Not flagged

### Preference Pairs

```http
GET /api/ml/training/preferences
```

Returns pairs of responses (chosen vs rejected) for RLHF/DPO training.

### Export Data

```http
POST /api/ml/training/export?from=2026-01-01&to=2026-02-01
```

Exports versioned JSONL dataset with train/val/test splits.

### Human Feedback

```http
POST /api/ml/training/feedback
{
  "interaction_id": 12345,
  "feedback_type": "thumbs_down",
  "rating": 2,
  "issues": "hallucination,incomplete",
  "comment": "Answer contained incorrect information",
  "correction": "The correct answer should be..."
}
```

Supported feedback types:
- `thumbs_up`: Positive feedback
- `thumbs_down`: Negative feedback
- `flag`: Report serious issue
- `correction`: Provide corrected response

### Feedback Statistics

```http
GET /api/ml/training/feedback/stats?days=7
```

Returns aggregated feedback metrics.

## Data Quality Filters

Training examples must meet these criteria:

| Filter | Threshold |
|--------|-----------|
| Quality Score | ≥ 75.0 |
| Validation Status | Passed |
| Answer Length | 50-2000 chars |
| Flagged | false |
| Duplicates | Removed |

## Export Format

### Supervised Fine-Tuning (train.jsonl)

```jsonl
{
  "messages": [
    {"role": "system", "content": "You are an expert technical interviewer..."},
    {"role": "user", "content": "Explain the difference between == and === in JavaScript"},
    {"role": "assistant", "content": "In JavaScript, == performs type coercion..."}
  ],
  "metadata": {
    "interaction_id": 12345,
    "role": "frontend_react",
    "difficulty": "mid",
    "quality_score": 87.5,
    "category": "coding",
    "skill": "javascript"
  }
}
```

### Preference Pairs (train_preferences.jsonl)

```jsonl
{
  "prompt": "Explain dependency injection in Spring",
  "context": "Senior backend Java developer interview",
  "chosen": "Dependency injection is a design pattern where...",
  "rejected": "It's when you use @Autowired annotation...",
  "margin": 25.5,
  "metadata": {
    "pair_id": 123450001,
    "role": "backend_java",
    "difficulty": "senior",
    "chosen_score": 92.0,
    "rejected_score": 66.5
  }
}
```

## Validation Checks

### Quality Validation

- All examples meet minimum quality threshold
- Average quality score reported
- Low-quality examples flagged

### Balance Validation

- Check role distribution
- Check difficulty distribution
- Check category distribution
- Flag if any class > 80%

### Diversity Validation

- Unique/total prompt ratio
- Vocabulary size
- Duplicate detection

### Contamination Detection

- Compare train/test prompts
- Flag if overlap > 1%
- Ensure proper data splitting

## Best Practices

### Data Collection

1. **Quality First**: Only collect examples with quality_score ≥ 75
2. **Diverse Sources**: Include multiple roles, difficulties, and categories
3. **Human Validation**: Prioritize examples with positive feedback
4. **Regular Updates**: Export monthly to capture new patterns

### Fine-Tuning

1. **Start Small**: Begin with 100-500 examples
2. **Monitor Overfitting**: Use validation set to track performance
3. **Iterate**: Collect feedback on fine-tuned model and retrain
4. **Version Control**: Keep all dataset versions for reproducibility

### Feedback Loop

1. **Collect Feedback**: Enable thumbs up/down in UI
2. **Review Flags**: Manually review flagged responses
3. **Apply Corrections**: Use corrections as golden examples
4. **Retrain**: Incorporate feedback into next dataset version

## Troubleshooting

### Low Data Volume

If export returns < 100 examples:
- Expand date range
- Lower quality threshold (not recommended)
- Check database for interview activity

### Imbalanced Dataset

If validation reports imbalance:
- Use filters to oversample minority classes
- Collect more data for underrepresented categories
- Apply stratified sampling

### High Contamination

If train/test overlap detected:
- Re-run export with proper UUID-based splitting
- Check for duplicate questions in database
- Clean data source

## Integration Example

```java
// Collect quality data
List<TrainingExample> examples = trainingDataCollector
    .collectQualityPairs(LocalDate.now().minusDays(30), LocalDate.now());

// Export to JSONL
Path exportPath = trainingDataExporter.exportTrainingData(
    LocalDate.now().minusDays(30), 
    LocalDate.now()
);

// Validate
# python scripts/validate_dataset.py {exportPath}

// Check feedback
List<FeedbackRecord> feedback = feedbackRepository
    .findByDateRange(startDate, endDate);
```

## Monitoring

Track these metrics:

- **Export Volume**: Examples per day/week
- **Quality Distribution**: Average scores by category
- **Feedback Ratio**: thumbs_up / total_feedback
- **Contamination Rate**: Train/test overlap %
- **Balance Metrics**: Class distribution entropy

## Related Documentation

- [RAG Pipeline Architecture](../../docs/rag_pipeline_architecture.md)
- [Experiment Framework Guide](../../docs/experiment_framework_guide.md)
- [Fine-Tuning Data Pipeline](../../docs/finetuning_data_pipeline.md)
