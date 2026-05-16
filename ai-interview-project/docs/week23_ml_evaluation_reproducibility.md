# Week 23 ML Evaluation Pipeline Hardening and Reproducibility

**Owner**: Yukun Song  
**Week**: 23 — Task 2 (P0)  
**Artifact**: `eval/results/week23_reproducibility_manifest.json`

---

## Goal

Make the Week 23 ML evaluation workflow reliable, repeatable, and suitable for weekly
decision-making without manual reconstruction.

---

## Audit: Live Evaluation Script Input/Output Consistency

Audited scripts: `auto_summary_generator.py`, `run_ramp_validation.py`,
`preflight_check.py`, `live_calibration_analysis.py`, `calibration_stability.py`

| Script | Input | Output | Issues Found | Status |
|--------|-------|--------|--------------|--------|
| `preflight_check.py` | env vars, MySQL | JSON report | None | ✅ |
| `run_ramp_validation.py` | MySQL live, env | stage result JSON | Did not validate required fields before writing | Fixed |
| `auto_summary_generator.py` | registry CSV, results/ | Markdown readout | No artifact existence check before generating readout | Fixed |
| `live_calibration_analysis.py` | MySQL live | calibration JSON | Missing `calibration_version` field in output | Fixed |
| `calibration_stability.py` | CSV training data | stability JSON | `data_source` field absent from output | Fixed |

**Fixes applied**: See `eval/auto_summary_generator.py` for artifact validation gate added
to `_generate_week_readout`. Scripts now fail clearly when required artifacts are missing
or stale rather than generating an incomplete readout silently.

---

## Standardized Artifact Naming Convention

| Artifact Type | Pattern | Example (Week 23) |
|--------------|---------|-------------------|
| Preflight results | `week{N}_preflight_{env}.json` | `week23_preflight_staging.json` |
| Live ramp — Stage A | `week{N}_stagea_live_result.json` | `week23_stagea_live_result.json` |
| Live ramp — Stage B | `week{N}_stageb_live_result.json` | `week23_stageb_live_result.json` |
| Live ramp — Stage C | `week{N}_stagec_live_result.json` | `week23_stagec_live_result.json` |
| Calibration results | `week{N}_live_calibration.json` | `week23_live_calibration.json` |
| Slice-level report | `week{N}_slice_report.json` | `week23_slice_report.json` |
| Reproducibility manifest | `week{N}_reproducibility_manifest.json` | `week23_reproducibility_manifest.json` |
| ML decision readout | `docs/week{N}_ml_decision_readout.md` | `docs/week23_ml_decision_readout.md` |

All artifacts must be placed in `eval/results/` unless noted otherwise.

---

## Required Fields for Every Experiment Result

Every experiment result JSON must include:

| Field | Description | Example |
|-------|-------------|---------|
| `experiment_id` | Registry-matching ID | `week23_live_stagea_20260515` |
| `data_source` | Where data came from | `mysql_live` |
| `timestamp` | ISO 8601 UTC | `2026-05-15T00:37:53Z` |
| `model_version` | Model + calibration | `prediction-v1.0+platt-v2.1` |
| `calibration_version` | Calibration artifact | `platt-v2.1` |
| `sample_size` | `{n_control, n_treatment}` | `{"n_control": 3, "n_treatment": 0}` |
| `guardrail_results` | Per-guardrail pass/fail | see stage result JSON |
| `decision` | GO / HOLD / ROLLBACK | `HOLD` |

Any artifact missing these fields causes `auto_summary_generator.py` to fail before
generating the readout (validation gate added in Week 23).

---

## Artifact Validation Gate (Added to `auto_summary_generator.py`)

The function `_validate_week_artifacts` is called at the start of `_generate_week_readout`.
It fails with a non-zero exit and a clear error message if:

1. A required artifact file does not exist in `eval/results/`
2. An artifact is stale (modification time > 14 days before generation date)
3. A required field is absent from a JSON artifact

This prevents partial or outdated readouts from being published to `docs/`.

---

## Week 23 Evaluation Sequence

Run in order. Each step must succeed (exit 0) before proceeding.

```bash
# Step 1 — preflight (May 11, AM)
python eval/preflight_check.py --env staging \
  --output eval/results/week23_preflight_staging.json

# Step 2 — Stage A live ramp (May 12)
python eval/run_ramp_validation.py --stage A --live \
  --output eval/results/week23_stagea_live_result.json

# Step 3 — Stage B live ramp (May 13, only if Stage A GO)
python eval/run_ramp_validation.py --stage B --live \
  --output eval/results/week23_stageb_live_result.json

# Step 4 — calibration analysis (May 14)
python eval/live_calibration_analysis.py --week 23 \
  --output eval/results/week23_live_calibration.json

# Step 5 — generate Week 23 readout (May 15)
python eval/auto_summary_generator.py --week 23 --include-live \
  --validate-artifacts \
  --output docs/week23_ml_decision_readout.md
```

---

## Acceptance Check

| Criterion | Status |
|-----------|--------|
| Week 23 evaluation reproducible from scripts and checked artifacts | ✅ |
| Missing or incomplete artifacts fail clearly before decision reporting | ✅ (validation gate added) |
| Experiment registry and generated readout match the same validation window | ✅ |
| All result JSONs include required fields (data_source, timestamp, model version, sample size, guardrail decision) | ✅ |

---

## Experiment Registry — Week 23 Entries

| Experiment ID | Stage | Decision |
|---------------|-------|----------|
| `week23_live_stagea_20260508` | A | HOLD |
| `week23_live_stagea_20260515` | A | HOLD |
| `week23_live_stageb_pending` | B | Blocked |
| `week23_live_stagec_pending` | C | Blocked |

See `eval/experiment_registry.csv` for full entries.
