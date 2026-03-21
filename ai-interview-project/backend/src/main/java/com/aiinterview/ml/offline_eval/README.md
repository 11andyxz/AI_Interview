# Offline Evaluation Pipeline

This directory contains scripts for offline evaluation and experimentation on ML models.

## Structure

```
ml/offline_eval/
├── configs/           # Experiment configurations
├── data/             # Extracted data (gitignored)
├── results/          # Evaluation results
├── data_extraction.py         # Data ETL pipeline
├── evaluate_outcome_prediction.py  # Outcome model evaluation
├── evaluate_early_stopping.py     # Early-stop policy simulation
└── README.md         # This file
```

## Prerequisites

```bash
pip install pandas numpy scikit-learn pyyaml mysql-connector-python
```

## Usage

### 1. Extract Data

```bash
# Extract all historical sessions
python data_extraction.py --output data/sessions.csv

# Extract specific date range
python data_extraction.py \
  --start-date 2026-03-01 \
  --end-date 2026-03-15 \
  --output data/sessions_march.csv

# Extract with filters
python data_extraction.py \
  --role-level senior \
  --tech-stack java \
  --min-questions 5 \
  --output data/sessions_java_senior.csv
```

### 2. Evaluate Outcome Prediction

```bash
# Evaluate on extracted data
python evaluate_outcome_prediction.py \
  --data data/sessions.csv \
  --config configs/baseline.yaml \
  --output results/outcome_eval.json

# Cross-validation mode
python evaluate_outcome_prediction.py \
  --data data/sessions.csv \
  --cv 5 \
  --output results/outcome_cv.json
```

### 3. Evaluate Early-Stop Policy

```bash
# Simulate early-stop policy
python evaluate_early_stopping.py \
  --data data/sessions.csv \
  --policy-config configs/early_stop_policy.yaml \
  --output results/early_stop_simulation.json

# Compare multiple policies
python evaluate_early_stopping.py \
  --data data/sessions.csv \
  --policies configs/policy_*.yaml \
  --output results/policy_comparison.json
```

## Configuration

See `configs/` directory for example configurations:
- `baseline.yaml`: Baseline model configuration
- `early_stop_policy.yaml`: Early stopping policy parameters
- `experiment_template.yaml`: Template for new experiments

## Data Privacy

- All PII (names, emails) are anonymized during extraction
- Data files are in `.gitignore` and should not be committed
- Use synthetic data for public documentation

## Output Formats

All evaluation scripts output JSON with:
```json
{
  "metrics": {
    "rmse": 10.12,
    "mae": 8.45,
    "r2": 0.83
  },
  "config": { ... },
  "timestamp": "2026-03-16T10:30:00Z"
}
```
