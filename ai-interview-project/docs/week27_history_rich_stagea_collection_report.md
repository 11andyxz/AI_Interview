# Week 27 History-Rich Stage A Collection Report

Generated: 2026-06-13T13:55:33.309163+00:00

## Result

- Decision: HOLD for Stage B.
- Real-prefix sample: treatment=52, baseline=24.
- OpenAI chat path: fixed to `/chat/completions`; model set to `gpt-4o-mini`.
- Excluded evidence: pre-fix `w27h-*` rows are not used for this readout because backend chat fell back after 404s.

## Quality

- Treatment avg=84.231, std=2.665.
- Baseline avg=84.583, std=1.998.
- Treatment minus baseline delta=-0.353.

## Notes

The n_treatment>=50 gate is met on repaired real-prefix rows. Stage B remains held because session-level early-stop data is partial and baseline real-prefix sample size is 24.
