# ML Quality Dashboard

Streamlit-based real-time visualization for AI output quality metrics and regression test results.

## Features

- **Real-time Quality Scores**: Live monitoring of 6 quality dimensions
- **Output Examples**: Browse golden dataset with quality annotations
- **Error Gallery**: Visual display of validation failures
- **Comparison View**: Current vs 7-day baseline comparison
- **Regression Test Results**: Latest test run summary and details

## Installation

```bash
cd ai-interview-project/eval
pip install -r requirements-dashboard.txt
```

## Configuration

### Database Connection

Set environment variables (or use defaults):

```bash
# Windows PowerShell
$env:DB_HOST="localhost"
$env:DB_PORT="3306"
$env:DB_NAME="ai_interview_db"
$env:DB_USER="root"
$env:DB_PASSWORD="root"

# Linux/Mac
export DB_HOST="localhost"
export DB_PORT="3306"
export DB_NAME="ai_interview_db"
export DB_USER="root"
export DB_PASSWORD="root"
```

Defaults:
- Host: localhost
- Port: 3306
- Database: ai_interview_db
- User: root
- Password: root

## Usage

### Start Dashboard

```bash
cd ai-interview-project/eval
streamlit run quality_dashboard.py
```

Dashboard will open at: http://localhost:8501

### Sidebar Controls

**Time Range**:
- Last 1 hour
- Last 6 hours
- Last 24 hours (default)
- Last 7 days

**Model Filter**:
- gpt-4o-mini (default)
- gpt-4
- gpt-3.5-turbo

**Endpoint Filter**:
- RESUME_ANALYSIS (default)
- GENERATE_QUESTIONS (default)
- SCORE_ANSWER (default)

**Refresh Button**: Clear cache and reload data

**Database Status**: Real-time connection indicator

## Dashboard Tabs

### 1. Quality Scores 📈

**Metrics Display**:
- Completeness
- Token Efficiency
- Vocabulary Diversity
- Repetition Score
- Technical Accuracy
- Clarity Score

**Features**:
- Current value with average comparison
- Time series chart with all metrics
- Hover for detailed values

### 2. Output Examples 📝

**Categories**:
- Resume Analysis
- Interview Questions
- Scoring
- Multi-turn Conversations

**Display**:
- Content preview with key fields
- Quality metrics sidebar
- File name reference

### 3. Error Gallery 🚨

**Displays**:
- Total validation failure count
- Failures by validator type (bar chart)
- Recent failures table (last 20)

**Threshold**: Score < 0.8 = failure

### 4. Comparison View 📊

**Comparison**:
- Current (last 24 hours)
- Baseline (7 days ago, 24-hour window)
- Percentage change

**Visualization**:
- Side-by-side bar chart
- Grouped by metric

### 5. Regression Tests 🧪

**Summary**:
- Total tests
- Passed count
- Failed count
- Pass rate percentage
- Last run timestamp

**Details**:
- Individual test results (expandable)
- Pass/fail status with icons
- All test metrics

## Data Sources

### Database Tables

**ai_metrics_log**: Quality metrics and validation results
- `ml.output.completeness`
- `ml.token.efficiency`
- `ml.vocabulary.diversity`
- `ml.repetition.score`
- `ml.technical.accuracy`
- `ml.clarity.score`
- `ml.validator.*` (validation results)

### File System

**test_results.json**: Latest regression test output from `run_regression_tests.py`

**golden_dataset/**: Example files by category
- resume_analysis/
- interview_questions/
- scoring/
- multi_turn/

## Troubleshooting

### Database Connection Failed

**Symptoms**: Red "❌ Disconnected" in sidebar

**Solutions**:
1. Verify MySQL is running: `systemctl status mysql` (Linux) or Task Manager (Windows)
2. Check credentials in environment variables
3. Verify database exists: `SHOW DATABASES LIKE 'ai_interview_db';`
4. Check firewall rules for port 3306

### No Data Available

**Symptoms**: "No quality metrics data available"

**Solutions**:
1. Verify backend is logging metrics (check `OpenAiService.chatWithValidation()`)
2. Run sample requests to populate data
3. Check time range (try "Last 7 days")
4. Verify table exists: `SHOW TABLES LIKE 'ai_metrics_log';`

### No Test Results

**Symptoms**: "No regression test results available"

**Solution**: Run regression tests:
```bash
cd ai-interview-project/eval
python run_regression_tests.py
```

### Dashboard Won't Start

**Symptoms**: `streamlit: command not found`

**Solution**: Install dependencies:
```bash
pip install -r requirements-dashboard.txt
```

### Slow Performance

**Solutions**:
1. Reduce time range (use "Last 1 hour")
2. Filter by specific model/endpoint
3. Click "🔄 Refresh Data" to clear cache
4. Check database query performance

## Development

### Adding New Metrics

1. **Backend**: Log metric in `QualityMonitor.java`:
```java
metricsService.recordMetric(
    "ml.custom.metric", 
    value, 
    endpoint, 
    modelVersion
);
```

2. **Dashboard**: Add to `metrics` list in `render_quality_scores()`:
```python
metrics = [
    # ...existing...
    'ml.custom.metric'
]

metric_labels = {
    # ...existing...
    'ml.custom.metric': 'Custom Metric'
}
```

### Adding New Tabs

1. Add tab in `main()`:
```python
tab1, tab2, ..., tabN = st.tabs([
    # ...existing...
    "🆕 New Tab"
])

with tabN:
    render_new_feature()
```

2. Implement render function:
```python
def render_new_feature():
    st.header("🆕 New Feature")
    # Implementation
```

## Architecture

### Data Flow

```
Backend (OpenAiService) 
    → MySQL (ai_metrics_log table)
        → Dashboard (quality_dashboard.py)
            → Streamlit UI

Regression Tests (run_regression_tests.py)
    → test_results.json
        → Dashboard
```

### Caching Strategy

- `@st.cache_resource`: Database connection (persistent)
- `@st.cache_data`: Query results (cleared on refresh)

### Update Frequency

- **Manual**: Click "🔄 Refresh Data" button
- **Auto**: Set in Streamlit config (not recommended for DB-heavy apps)

## Performance Tips

1. **Optimize queries**: Add indexes on `created_at` and `metric_name`
```sql
CREATE INDEX idx_metrics_created ON ai_metrics_log(created_at);
CREATE INDEX idx_metrics_name ON ai_metrics_log(metric_name);
```

2. **Limit data range**: Use 1-6 hour windows for real-time monitoring

3. **Filter early**: Apply model/endpoint filters in SQL, not Python

4. **Cache wisely**: Use st.cache_data for expensive operations

## Security Notes

1. **Database credentials**: Use environment variables, never hardcode
2. **Connection pooling**: Implemented with `@st.cache_resource`
3. **SQL injection**: Using parameterized queries throughout
4. **Access control**: Deploy behind authentication (Streamlit Cloud, nginx, etc.)

## Deployment

### Local Development
```bash
streamlit run quality_dashboard.py
```

### Production (Docker)
```dockerfile
FROM python:3.11-slim
WORKDIR /app
COPY requirements-dashboard.txt .
RUN pip install -r requirements-dashboard.txt
COPY quality_dashboard.py .
EXPOSE 8501
CMD ["streamlit", "run", "quality_dashboard.py", "--server.port=8501"]
```

### Streamlit Cloud

1. Push to GitHub
2. Connect repository at share.streamlit.io
3. Set environment secrets in dashboard settings
4. Deploy

## License

Internal use only - AI Interview Project
