#!/bin/bash

# AI Interview Experiment Runner
# 
# This script orchestrates end-to-end experiment runs, comparing baseline vs candidate models.
# It handles:
# - Running evaluation tests against backend API
# - Computing standardized metrics (RMSE, MAE, Brier score)
# - Comparing results and logging to experiment registry
#
# Usage:
#   ./run_experiments.sh baseline v1.0 '{"pass_threshold":0.95}'
#   ./run_experiments.sh candidate v1.1 '{"pass_threshold":0.92}' --compare-to baseline_20260327_001

set -e  # Exit on error

# Configuration
BACKEND_URL=${BACKEND_URL:-"http://localhost:8080"}
EVAL_DIR="$(dirname "$0")"
RESULTS_DIR="${EVAL_DIR}/results"
PROMPTS_FILE="${EVAL_DIR}/prompts/eval_prompts.jsonl"
REGISTRY_FILE="${EVAL_DIR}/experiment_registry.csv"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Helper functions
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[OK]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Parse arguments
EXPERIMENT_TYPE=${1:-"baseline"}  # baseline or candidate
MODEL_VERSION=${2:-"unknown"}
CONFIG_PARAMS=${3:-'{}'}
COMPARE_TO=""
SLICE=""

shift 3 || true
while [[ $# -gt 0 ]]; do
    case $1 in
        --compare-to)
            COMPARE_TO="$2"
            shift 2
            ;;
        --slice)
            SLICE="$2"
            shift 2
            ;;
        --backend)
            BACKEND_URL="$2"
            shift 2
            ;;
        *)
            log_error "Unknown option: $1"
            exit 1
            ;;
    esac
done

# Generate experiment ID
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
EXPERIMENT_ID="${EXPERIMENT_TYPE}_${TIMESTAMP}"

log_info "Starting experiment: ${EXPERIMENT_ID}"
log_info "  Model version: ${MODEL_VERSION}"
log_info "  Config: ${CONFIG_PARAMS}"
log_info "  Backend: ${BACKEND_URL}"

# Create results directory
mkdir -p "${RESULTS_DIR}"

# Step 1: Run evaluation tests
log_info "Step 1/4: Running evaluation against backend API..."

EVAL_OUTPUT="${RESULTS_DIR}/eval_results_${TIMESTAMP}.csv"

python3 "${EVAL_DIR}/run_eval.py" \
    --backend "${BACKEND_URL}" \
    --output "${RESULTS_DIR}" \
    2>&1 | tee "${RESULTS_DIR}/eval_log_${TIMESTAMP}.txt"

if [ ! -f "${EVAL_OUTPUT}" ]; then
    # Try alternative filename pattern
    EVAL_OUTPUT="${RESULTS_DIR}/eval_results_latest.csv"
fi

if [ ! -f "${EVAL_OUTPUT}" ]; then
    log_error "Evaluation results not found. Expected: ${EVAL_OUTPUT}"
    exit 1
fi

log_success "Evaluation complete. Results: ${EVAL_OUTPUT}"

# Step 2: Compute metrics
log_info "Step 2/4: Computing standardized metrics..."

METRICS_OUTPUT="${RESULTS_DIR}/metrics_${EXPERIMENT_ID}.json"

COMPUTE_CMD="python3 ${EVAL_DIR}/compute_metrics.py \
    --input ${EVAL_OUTPUT} \
    --output ${METRICS_OUTPUT} \
    --model-version ${MODEL_VERSION} \
    --config '${CONFIG_PARAMS}'"

# Add baseline comparison if provided
if [ -n "${COMPARE_TO}" ]; then
    BASELINE_METRICS="${RESULTS_DIR}/metrics_${COMPARE_TO}.json"
    
    if [ ! -f "${BASELINE_METRICS}" ]; then
        log_warn "Baseline metrics not found: ${BASELINE_METRICS}"
        log_warn "Will compute metrics without comparison"
    else
        COMPUTE_CMD="${COMPUTE_CMD} --baseline ${BASELINE_METRICS}"
        log_info "Comparing to baseline: ${COMPARE_TO}"
    fi
fi

eval $COMPUTE_CMD

if [ ! -f "${METRICS_OUTPUT}" ]; then
    log_error "Metrics computation failed. Expected: ${METRICS_OUTPUT}"
    exit 1
fi

log_success "Metrics computed. Report: ${METRICS_OUTPUT}"

# Step 3: Extract key metrics and display
log_info "Step 3/4: Extracting key metrics..."

RMSE=$(python3 -c "import json; print(json.load(open('${METRICS_OUTPUT}'))['rmse'])" 2>/dev/null || echo "N/A")
MAE=$(python3 -c "import json; print(json.load(open('${METRICS_OUTPUT}'))['mae'])" 2>/dev/null || echo "N/A")
BRIER=$(python3 -c "import json; print(json.load(open('${METRICS_OUTPUT}'))['brier_score'])" 2>/dev/null || echo "N/A")
SAMPLE_SIZE=$(python3 -c "import json; print(json.load(open('${METRICS_OUTPUT}'))['sample_size'])" 2>/dev/null || echo "N/A")
AVG_LATENCY=$(python3 -c "import json; print(json.load(open('${METRICS_OUTPUT}'))['avg_latency_ms'])" 2>/dev/null || echo "N/A")
P50_LATENCY=$(python3 -c "import json; print(json.load(open('${METRICS_OUTPUT}'))['latency_percentiles']['p50'])" 2>/dev/null || echo "N/A")
EARLY_STOP_RATE=$(python3 -c "import json; print(json.load(open('${METRICS_OUTPUT}'))['early_stop_metrics']['early_stop_rate'])" 2>/dev/null || echo "N/A")
AVG_QUESTIONS=$(python3 -c "import json; print(json.load(open('${METRICS_OUTPUT}'))['early_stop_metrics']['avg_questions_overall'])" 2>/dev/null || echo "N/A")

echo ""
echo "========================================"
echo "  Experiment Results: ${EXPERIMENT_ID}"
echo "========================================"
echo "  Model Version:     ${MODEL_VERSION}"
echo "  Sample Size:       ${SAMPLE_SIZE}"
echo ""
echo "Prediction Quality:"
echo "  RMSE:              ${RMSE}"
echo "  MAE:               ${MAE}"
echo "  Brier Score:       ${BRIER}"
echo ""
echo "Performance:"
echo "  Avg Latency:       ${AVG_LATENCY} ms"
echo "  P50 Latency:       ${P50_LATENCY} ms"
echo ""
echo "Early-Stop Metrics:"
echo "  Early-Stop Rate:   ${EARLY_STOP_RATE}"
echo "  Avg Questions:     ${AVG_QUESTIONS}"
echo "========================================"
echo ""

# If comparison available, display deltas
if [ -n "${COMPARE_TO}" ] && [ -f "${RESULTS_DIR}/metrics_${COMPARE_TO}.json" ]; then
    log_info "Comparison to baseline ${COMPARE_TO}:"
    
    python3 -c "
import json
import sys

try:
    with open('${METRICS_OUTPUT}', 'r') as f:
        report = json.load(f)
    
    if 'comparison' in report:
        deltas = report['comparison']['deltas']
        
        print('\n  Metric Deltas:')
        for metric, delta_info in deltas.items():
            baseline = delta_info['baseline']
            candidate = delta_info['candidate']
            delta_pct = delta_info['relative_delta_pct']
            
            symbol = '↑' if delta_pct > 0 else '↓'
            color = '🟢' if (metric in ['rmse', 'mae', 'brier_score', 'avg_latency_ms'] and delta_pct < 0) or \
                            (metric == 'avg_questions' and delta_pct < 0) else '🔴'
            
            print(f'  {color} {metric:20s}: {baseline:8.3f} → {candidate:8.3f} ({symbol}{abs(delta_pct):5.1f}%)')
except Exception as e:
    print(f'  (Comparison data not available: {e})', file=sys.stderr)
"
    echo ""
fi

# Step 4: Update experiment registry
log_info "Step 4/4: Updating experiment registry..."

# Prepare CSV row
SLICE_VALUE=${SLICE:-"all"}
CONFIG_ESCAPED=$(echo "${CONFIG_PARAMS}" | sed 's/"/\\"/g')

CSV_ROW="${EXPERIMENT_ID},$(date -Iseconds),${MODEL_VERSION},${SLICE_VALUE},\"${CONFIG_ESCAPED}\",${RMSE},${MAE},${BRIER},${SAMPLE_SIZE},${AVG_LATENCY},${P50_LATENCY},N/A,N/A,N/A,${EARLY_STOP_RATE},${AVG_QUESTIONS},${EXPERIMENT_TYPE} experiment"

# Create registry if doesn't exist
if [ ! -f "${REGISTRY_FILE}" ]; then
    echo "experiment_id,timestamp,model_version,slice,config_params,rmse,mae,brier_score,sample_size,avg_latency_ms,p50_latency_ms,p90_latency_ms,p95_latency_ms,p99_latency_ms,early_stop_rate,avg_questions,notes" > "${REGISTRY_FILE}"
fi

# Append row
echo "${CSV_ROW}" >> "${REGISTRY_FILE}"

log_success "Registry updated: ${REGISTRY_FILE}"

# Final summary
echo ""
log_success "Experiment complete!"
log_info "  Experiment ID:   ${EXPERIMENT_ID}"
log_info "  Raw results:     ${EVAL_OUTPUT}"
log_info "  Metrics report:  ${METRICS_OUTPUT}"
log_info "  Registry entry:  ${REGISTRY_FILE}"
echo ""

# Return experiment ID for chaining
echo "${EXPERIMENT_ID}"
