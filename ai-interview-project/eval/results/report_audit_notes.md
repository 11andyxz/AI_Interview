# Evaluation Report Audit Notes

Purpose: record reports affected by prior validator/runner bugs and point to the verified canonical report.

Known faulty reports
- `eval_report_20251229_195204.md` — affected by an import/validator bug that produced false-negative scoring failures. Do not use this report for acceptance decisions.
- `eval_report_20251229_201824.md` — this run occurred during fixes and may contain transient failures; treat with caution.

Canonical verified report
- Use `eval_report_20251229_203734.md` as the canonical offline verification run for Week 2 rollout. CSV: `eval_results_latest.csv` (located in the same folder) contains the detailed per-prompt rows and trace fields.

Audit guidance
- Preserve original faulty report files (do not delete).
- When publishing acceptance evidence, link to the canonical report above and note that earlier runs are annotated here.
- If you want, I can (a) prefix older faulty report filenames with `IGNORED_` and add a small JSON file mapping originals→reason, or (b) leave files as-is and add a `reports_index.csv` that enumerates status for each file.
