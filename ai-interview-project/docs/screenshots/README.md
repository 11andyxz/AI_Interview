# Grafana Dashboard Screenshots

## 📊 Purpose
This directory contains screenshots of the Grafana ML monitoring dashboard for staging validation evidence (required by Andy's Week 18 deliverables).

## 🚀 Grafana Deployment Status

**Status**: ✅ Deployed and Running  
**URL**: http://localhost:3000  
**Deployed**: April 3, 2026

## 📸 Required Screenshots

### 1. Dashboard Overview (`grafana-dashboard-overview.png`)
- Full dashboard with all 4 ML monitoring panels
- Shows dashboard title: "AI Interview ML Monitoring"
- Time range: Last 1 hour
- **Required panels**:
  - Early-Stop Rate by Policy
  - Average Questions per Session
  - RMSE Trend
  - Premature Stop Detection

### 2. Early-Stop Rate Panel (`grafana-early-stop-rate.png`)
- Close-up of early-stop rate comparison (baseline vs new policy)
- Shows metric: `ai_interview_early_stop_triggered_total`
- Legend showing both policies

### 3. RMSE Trend Panel (`grafana-rmse-trend.png`)
- RMSE over time for different slices (junior, mid, senior)
- Target line at 11.5 (junior target)

### 4. System Health Panel (`grafana-system-health.png`)
- Latency metrics (P50, P95, P99)
- Request rate
- Error rate

## 🛠️ How to Capture Screenshots

### Step-by-Step Guide

1. **Access Grafana**:
   ```
   Open browser: http://localhost:3000
   Login: admin / admin (skip password change if prompted)
   ```

2. **Import Dashboard**:
   - Click **"+"** (Plus icon) → **Import**
   - Click **"Upload JSON file"**
   - Select: `D:\dev\AI_Interview\ai-interview-project\docs\grafana-ml-dashboard-v0.json`
   - Select Data Source: **TestData DB** (for demo) or configure Prometheus
   - Click **"Import"**

3. **Configure Time Range** (optional):
   - Click time picker (top right)
   - Select: "Last 1 hour" or "Last 6 hours"

4. **Take Screenshots**:
   - **Windows**: Press `Win + Shift + S` (Snipping Tool)
   - **Full Dashboard**: Capture entire viewport
   - **Individual Panels**: Click panel title → View → Fullscreen, then screenshot
   - Save to this directory with recommended filenames above

5. **Alternative: Use TestData**:
   - If Prometheus is not running, Grafana's TestData source can show panel layouts
   - This demonstrates dashboard configuration is ready

## 📋 Validation Checklist

- [ ] Grafana deployed and accessible
- [ ] Dashboard JSON imported successfully
- [ ] All 4 panels visible
- [ ] Screenshot 1: Dashboard overview
- [ ] Screenshot 2: Early-stop rate panel
- [ ] Screenshot 3: RMSE trend panel
- [ ] Screenshot 4: System health metrics
- [ ] Screenshots saved to this directory
- [ ] staging-validation-report.md updated with screenshot references

## 🔗 Related Files

- **Dashboard Configuration**: `../grafana-ml-dashboard-v0.json` (512 lines)
- **Validation Report**: `../staging-validation-report.md`
- **Rollout Plan**: `../early-stop-rollout-plan.md`

## ⚠️ Notes

- **Data Source**: If Spring Boot is not running, TestData DB can be used to show panel configurations
- **Prometheus Endpoint**: http://localhost:8080/actuator/prometheus (when backend is running)
- **Screenshot Format**: PNG preferred, max 1920x1080 resolution
- **File Size**: Keep under 500KB each for repository size

## ✅ Completion Status

- [x] Grafana downloaded and extracted
- [x] Grafana server started on port 3000
- [x] Browser opened to http://localhost:3000
- [ ] Screenshots captured (MANUAL STEP REQUIRED)
- [ ] staging-validation-report.md updated

**Next Action**: Complete manual screenshot capture and update staging-validation-report.md
