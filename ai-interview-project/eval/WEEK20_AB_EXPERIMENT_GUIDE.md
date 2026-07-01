# Week 20 A/B Experiment Execution Guide

## Overview

This guide walks through running the Week 20 empirical A/B validation experiment with ≥60 sessions per arm.

## Prerequisites

1. **Backend service running**: `mvn spring-boot:run` in `backend/`
2. **Database accessible**: Aiven MySQL connection configured
3. **Environment variables set**:
   ```powershell
   $env:DB_PASSWORD="your_db_password"
   ```
4. **Python dependencies installed**:
   ```bash
   pip install mysql-connector-python scipy numpy pandas
   ```

## Step-by-Step Execution

### Phase 1: Run Control Group (Baseline Policy)

```bash
cd backend

# Run 60 control sessions with baseline policy (0.95/0.05, min 5 questions)
python run_ab_experiment.py --policy baseline --sessions 60 --experiment-id week20_control_20260417
```

**What happens**:
1. Script updates `application.properties` to disable new policy
2. Script prompts you to **restart backend** with updated config
3. After restart, press Enter to continue
4. Script runs 60 E2E sessions via `run_e2e_sessions.py`
5. Metadata saved to `eval/results/week20_control_20260417_metadata.json`

**Expected duration**: ~10-15 minutes (depending on API latency)

---

### Phase 2: Run Treatment Group (New Policy)

```bash
# Run 60 treatment sessions with new policy (0.90/0.10, min 6 questions)
python run_ab_experiment.py --policy new --sessions 60 --experiment-id week20_treatment_20260417
```

**What happens**:
1. Script updates `application.properties` to enable new policy
2. Script prompts you to **restart backend** with updated config
3. After restart, press Enter to continue
4. Script runs 60 E2E sessions
5. Metadata saved to `eval/results/week20_treatment_20260417_metadata.json`

**Expected duration**: ~10-15 minutes

---

### Phase 3: Analyze Results

```bash
# Run statistical analysis
python run_ab_experiment.py --analyze \
    --control week20_control_20260417 \
    --treatment week20_treatment_20260417
```

**What happens**:
1. Script loads metadata from both experiments
2. Queries database for session data (interview and interview_message tables)
3. Computes metrics:
   - Avg questions per session (overall + by slice)
   - Latency (p50, p95)
   - Early-stop rate
   - Statistical tests (t-test, Cohen's d, confidence intervals)
4. Saves analysis to `eval/results/ab_analysis_week20_control_20260417_vs_week20_treatment_20260417.json`
5. Prints summary to console

**Expected output**:
```
📊 Data Summary:
   Control sessions:   60
   Treatment sessions: 60

📈 Control Metrics:
   Total sessions: 60
   Avg questions:  9.5 ± 2.3
   ...

🔬 Statistical Tests:
   t-statistic: -2.456
   p-value:     0.0156
   Significant: YES ✓ (α=0.05)
   Effect size: 0.42 (Cohen's d)
   ...

💡 Recommendation:
   ✓ RAMP - Significant efficiency improvement detected
```

---

### Phase 4: Generate Readout Document

```bash
# Generate markdown readout
python generate_ab_readout.py \
    --analysis eval/results/ab_analysis_week20_control_20260417_vs_week20_treatment_20260417.json \
    --output docs/week20_ab_readout_empirical.md
```

**What happens**:
1. Reads analysis JSON
2. Generates comprehensive markdown report with:
   - Executive summary
   - Experiment design
   - Results tables
   - Statistical tests
   - Slice-level breakdown
   - Decision recommendation
   - Reproducibility commands
3. Saves to `docs/week20_ab_readout_empirical.md`

---

### Phase 5: Update Experiment Registry

```bash
cd ../eval

# Manually add entries to experiment_registry.csv
# Use analysis JSON data to populate fields
```

**Example entries**:
```csv
experiment_id,timestamp,model_version,slice,config_params,rmse,mae,brier_score,sample_size,avg_latency_ms,p50_latency_ms,p90_latency_ms,p95_latency_ms,p99_latency_ms,early_stop_rate,avg_questions,notes
week20_control_20260417,2026-04-17T10:00:00,prediction-v1.0,all,"{""pass_threshold"":0.95,""fail_threshold"":0.05,""min_questions"":5,""policy"":""baseline""}",10.5,7.8,0.083,60,5800,5200,7100,7500,8200,0.00,9.5,"Week 20 control - 60 real E2E sessions"
week20_treatment_20260417,2026-04-17T10:20:00,prediction-v1.0,all,"{""pass_threshold"":0.90,""fail_threshold"":0.10,""min_questions"":6,""policy"":""new_policy""}",10.8,8.0,0.085,60,5600,5000,6900,7300,8000,0.18,8.3,"Week 20 treatment - 60 real E2E sessions, 12.6% question reduction"
```

---

## Validation Checklist

After completing all phases, verify:

- [ ] Control sessions: ≥ 60 total
- [ ] Treatment sessions: ≥ 60 total
- [ ] Junior slice: ≥ 20 per arm
- [ ] Analysis JSON generated
- [ ] Readout markdown generated
- [ ] experiment_registry.csv updated
- [ ] Statistical significance determined
- [ ] Decision recommendation documented

---

## Troubleshooting

### Issue: "Error: DB_PASSWORD environment variable not set"

**Solution**:
```powershell
$env:DB_PASSWORD="your_aiven_password"
```

### Issue: "Error: Failed to create user" or "Connection refused"

**Solution**:
- Ensure backend is running: `mvn spring-boot:run`
- Check port 8080 is accessible
- Verify application started without errors

### Issue: "No data found for time window"

**Solution**:
- Check metadata JSON files have correct timestamps
- Verify database has interview records in that time window
- Query database manually to confirm data exists

### Issue: Sample size below 60

**Solution**:
- Check E2E session output for failed sessions
- Re-run with more sessions: `--sessions 70` to account for failures
- Investigate backend logs for errors

---

## Cost Estimate

**OpenAI API costs** (gpt-3.5-turbo):
- 60 control sessions × ~10 questions × 2 API calls (question + response) = ~1200 calls
- 60 treatment sessions × ~8 questions × 2 API calls = ~960 calls
- Total: ~2160 API calls
- Estimated cost: ~$0.50 - $1.00 (depending on prompt/response lengths)

**Time estimate**:
- Control group: 12-15 minutes
- Treatment group: 10-12 minutes
- Analysis: < 1 minute
- Total: ~25-30 minutes

---

## Next Steps After Completion

1. Review `docs/week20_ab_readout_empirical.md`
2. If recommendation is **RAMP**:
   - Proceed to Task 2 (calibration tuning)
   - Prepare gradual rollout plan
3. If recommendation is **HOLD** or **ROLLBACK**:
   - Conduct root cause analysis
   - Adjust policy parameters
   - Plan re-test

---

**Last updated**: 2026-04-17  
**Owner**: Yukun
