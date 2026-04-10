# AI Interview Experiment Runner (PowerShell)
# 
# This script orchestrates end-to-end experiment runs, comparing baseline vs candidate models.
#
# Usage:
#   .\run_experiments.ps1 -ExperimentType baseline -ModelVersion v1.0 -ConfigParams '{"pass_threshold":0.95}'
#   .\run_experiments.ps1 -ExperimentType candidate -ModelVersion v1.1 -ConfigParams '{"pass_threshold":0.92}' -CompareTo baseline_20260327_001

param(
    [Parameter(Mandatory=$false)]
    [string]$ExperimentType = "baseline",
    
    [Parameter(Mandatory=$false)]
    [string]$ModelVersion = "unknown",
    
    [Parameter(Mandatory=$false)]
    [string]$ConfigParams = "{}",
    
    [Parameter(Mandatory=$false)]
    [string]$CompareTo = "",
    
    [Parameter(Mandatory=$false)]
    [string]$Slice = "",
    
    [Parameter(Mandatory=$false)]
    [string]$BackendUrl = "http://localhost:8080"
)

$ErrorActionPreference = "Stop"

# Configuration
$EvalDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ResultsDir = Join-Path $EvalDir "results"
$PromptsFile = Join-Path $EvalDir "prompts\eval_prompts.jsonl"
$RegistryFile = Join-Path $EvalDir "experiment_registry.csv"

# Helper functions
function Write-Info {
    param([string]$Message)
    Write-Host "[INFO] $Message" -ForegroundColor Blue
}

function Write-Success {
    param([string]$Message)
    Write-Host "[OK] $Message" -ForegroundColor Green
}

function Write-Warning {
    param([string]$Message)
    Write-Host "[WARN] $Message" -ForegroundColor Yellow
}

function Write-ErrorMsg {
    param([string]$Message)
    Write-Host "[ERROR] $Message" -ForegroundColor Red
}

# Generate experiment ID
$Timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$ExperimentId = "${ExperimentType}_${Timestamp}"

Write-Info "Starting experiment: $ExperimentId"
Write-Info "  Model version: $ModelVersion"
Write-Info "  Config: $ConfigParams"
Write-Info "  Backend: $BackendUrl"

# Create results directory
if (-not (Test-Path $ResultsDir)) {
    New-Item -ItemType Directory -Path $ResultsDir | Out-Null
}

# Step 1: Run evaluation tests
Write-Info "Step 1/4: Running evaluation against backend API..."

$EvalOutput = Join-Path $ResultsDir "eval_results_${Timestamp}.csv"
$EvalLog = Join-Path $ResultsDir "eval_log_${Timestamp}.txt"

python "$EvalDir\run_eval.py" --backend $BackendUrl --output $ResultsDir 2>&1 | Tee-Object -FilePath $EvalLog

if (-not (Test-Path $EvalOutput)) {
    # Try alternative filename
    $EvalOutput = Join-Path $ResultsDir "eval_results_latest.csv"
}

if (-not (Test-Path $EvalOutput)) {
    Write-ErrorMsg "Evaluation results not found. Expected: $EvalOutput"
    exit 1
}

Write-Success "Evaluation complete. Results: $EvalOutput"

# Step 2: Compute metrics
Write-Info "Step 2/4: Computing standardized metrics..."

$MetricsOutput = Join-Path $ResultsDir "metrics_${ExperimentId}.json"

$ComputeArgs = @(
    "$EvalDir\compute_metrics.py",
    "--input", $EvalOutput,
    "--output", $MetricsOutput,
    "--model-version", $ModelVersion,
    "--config", $ConfigParams
)

# Add baseline comparison if provided
if ($CompareTo) {
    $BaselineMetrics = Join-Path $ResultsDir "metrics_${CompareTo}.json"
    
    if (-not (Test-Path $BaselineMetrics)) {
        Write-Warning "Baseline metrics not found: $BaselineMetrics"
        Write-Warning "Will compute metrics without comparison"
    } else {
        $ComputeArgs += "--baseline", $BaselineMetrics
        Write-Info "Comparing to baseline: $CompareTo"
    }
}

python @ComputeArgs

if (-not (Test-Path $MetricsOutput)) {
    Write-ErrorMsg "Metrics computation failed. Expected: $MetricsOutput"
    exit 1
}

Write-Success "Metrics computed. Report: $MetricsOutput"

# Step 3: Extract and display key metrics
Write-Info "Step 3/4: Extracting key metrics..."

$Metrics = Get-Content $MetricsOutput | ConvertFrom-Json

$RMSE = if ($Metrics.rmse) { $Metrics.rmse.ToString("F3") } else { "N/A" }
$MAE = if ($Metrics.mae) { $Metrics.mae.ToString("F3") } else { "N/A" }
$Brier = if ($Metrics.brier_score) { $Metrics.brier_score.ToString("F3") } else { "N/A" }
$SampleSize = if ($Metrics.sample_size) { $Metrics.sample_size } else { "N/A" }
$AvgLatency = if ($Metrics.avg_latency_ms) { $Metrics.avg_latency_ms.ToString("F1") } else { "N/A" }
$P50Latency = if ($Metrics.latency_percentiles.p50) { $Metrics.latency_percentiles.p50.ToString("F1") } else { "N/A" }
$EarlyStopRate = if ($Metrics.early_stop_metrics.early_stop_rate) { $Metrics.early_stop_metrics.early_stop_rate.ToString("F3") } else { "N/A" }
$AvgQuestions = if ($Metrics.early_stop_metrics.avg_questions_overall) { $Metrics.early_stop_metrics.avg_questions_overall.ToString("F1") } else { "N/A" }

Write-Host ""
Write-Host "========================================"
Write-Host "  Experiment Results: $ExperimentId"
Write-Host "========================================"
Write-Host "  Model Version:     $ModelVersion"
Write-Host "  Sample Size:       $SampleSize"
Write-Host ""
Write-Host "Prediction Quality:"
Write-Host "  RMSE:              $RMSE"
Write-Host "  MAE:               $MAE"
Write-Host "  Brier Score:       $Brier"
Write-Host ""
Write-Host "Performance:"
Write-Host "  Avg Latency:       $AvgLatency ms"
Write-Host "  P50 Latency:       $P50Latency ms"
Write-Host ""
Write-Host "Early-Stop Metrics:"
Write-Host "  Early-Stop Rate:   $EarlyStopRate"
Write-Host "  Avg Questions:     $AvgQuestions"
Write-Host "========================================"
Write-Host ""

# Display comparison if available
if ($CompareTo -and $Metrics.comparison) {
    Write-Info "Comparison to baseline $CompareTo`:"
    Write-Host ""
    Write-Host "  Metric Deltas:"
    
    foreach ($metric in $Metrics.comparison.deltas.PSObject.Properties) {
        $name = $metric.Name
        $delta = $metric.Value
        
        $baseline = $delta.baseline.ToString("F3")
        $candidate = $delta.candidate.ToString("F3")
        $deltaPct = $delta.relative_delta_pct.ToString("F1")
        
        $symbol = if ($delta.relative_delta_pct -gt 0) { "↑" } else { "↓" }
        
        # Green for improvements, red for regressions
        $color = if (($name -in @('rmse', 'mae', 'brier_score', 'avg_latency_ms') -and $delta.relative_delta_pct -lt 0) -or 
                     ($name -eq 'avg_questions' -and $delta.relative_delta_pct -lt 0)) {
            "Green"
        } else {
            "Red"
        }
        
        Write-Host ("  {0} {1,-20}: {2,8} → {3,8} ({4}{5,5}%)" -f 
            $(if ($color -eq "Green") { "✓" } else { "✗" }),
            $name,
            $baseline,
            $candidate,
            $symbol,
            [Math]::Abs($deltaPct)
        ) -ForegroundColor $color
    }
    Write-Host ""
}

# Step 4: Update experiment registry
Write-Info "Step 4/4: Updating experiment registry..."

$SliceValue = if ($Slice) { $Slice } else { "all" }
$ConfigEscaped = $ConfigParams.Replace('"', '""')
$Timestamp = Get-Date -Format "yyyy-MM-ddTHH:mm:ss"

$CsvRow = "$ExperimentId,$Timestamp,$ModelVersion,$SliceValue,`"$ConfigEscaped`",$RMSE,$MAE,$Brier,$SampleSize,$AvgLatency,$P50Latency,N/A,N/A,N/A,$EarlyStopRate,$AvgQuestions,$ExperimentType experiment"

# Create registry if doesn't exist
if (-not (Test-Path $RegistryFile)) {
    "experiment_id,timestamp,model_version,slice,config_params,rmse,mae,brier_score,sample_size,avg_latency_ms,p50_latency_ms,p90_latency_ms,p95_latency_ms,p99_latency_ms,early_stop_rate,avg_questions,notes" | Out-File -FilePath $RegistryFile -Encoding utf8
}

# Append row
Add-Content -Path $RegistryFile -Value $CsvRow -Encoding utf8

Write-Success "Registry updated: $RegistryFile"

# Final summary
Write-Host ""
Write-Success "Experiment complete!"
Write-Info "  Experiment ID:   $ExperimentId"
Write-Info "  Raw results:     $EvalOutput"
Write-Info "  Metrics report:  $MetricsOutput"
Write-Info "  Registry entry:  $RegistryFile"
Write-Host ""

# Return experiment ID
return $ExperimentId
