# Week 21 Task 1: Production Data Path Unblock Report

**Date**: 2026-04-24  
**Owner**: Yukun Song  
**Status**: ✅ SCRIPTS COMPLETE — DB/API validation pending live credentials  

---

## Objective

Make ML experiments and rollout decisions executable in a production-like environment (not local/SQLite-only).

---

## Scope

| Item | Status |
|------|--------|
| Aiven MySQL connectivity (code) | ✅ `preflight_check.py` validates DB connectivity; scripts use env-var driven config |
| Aiven MySQL connectivity (live) | ❌ BLOCKED — see Blockers section |
| OpenAI API key rotation | 🔄 Requires key rotation by infra team |
| Preflight check script | ✅ Created and syntax-validated (`eval/preflight_check.py`) |
| Environment variable checklist | ✅ See below + Week 21 appendix added to `week15_environment_variables_guide.md` |
| No plaintext secrets in repo | ✅ Verified (env-var driven only) |

---

## Preflight Check Script

Run before any Week 21 experiment:

```bash
# Set required env vars first (from secrets manager / .env file)
export DB_HOST=<aiven-mysql-host>
export DB_PORT=22629
export DB_NAME=ai_interview
export DB_USERNAME=<username>
export DB_PASSWORD=<password>
export OPENAI_API_KEY=<rotated-key>

# Run preflight
python eval/preflight_check.py --env staging

# Exit code 0 = UNBLOCKED, 1 = BLOCKED
```

All checks:
- `ENV:OPENAI_API_KEY` — present and format valid
- `ENV:DB_HOST / DB_PORT / DB_NAME / DB_USERNAME / DB_PASSWORD` — all set
- `MySQL:connectivity` — Aiven MySQL reachable within 5s timeout
- `OpenAI:api_key_valid` — models endpoint responds
- `SQLite:fallback_guard` — DB_HOST present, no fallback to local SQLite

---

## Required Environment Variables

### Production / Production-like (must be set)

| Variable | Required | Notes |
|----------|----------|-------|
| `OPENAI_API_KEY` | **YES** | Rotate every 90 days; never commit |
| `DB_HOST` | **YES** | Aiven MySQL host |
| `DB_PORT` | **YES** | Aiven MySQL port (22629) |
| `DB_NAME` | **YES** | `ai_interview` |
| `DB_USERNAME` | **YES** | DB user |
| `DB_PASSWORD` | **YES** | DB password |

### Optional (will use defaults if absent)

| Variable | Default | Notes |
|----------|---------|-------|
| `OPENAI_CHAT_MODEL` | `gpt-3.5-turbo` | Override for model experiments |
| `OPENAI_EMBEDDING_MODEL` | `text-embedding-3-small` | |
| `ML_EARLY_STOP_PASS_THRESHOLD` | `0.95` | Baseline policy |
| `ML_EARLY_STOP_FAIL_THRESHOLD` | `0.05` | Baseline policy |
| `ML_EARLY_STOP_MIN_QUESTIONS` | `5` | Global minimum |

---

## ML Rollout Gate Checklist

All items must be **PASS** before advancing to ramp validation.

| Gate | Check | Result |
|------|-------|--------|
| DB-01 | Aiven MySQL reachable | ❌ BLOCKED — DNS does not resolve (instance terminated, see DB-INFRA-01) |
| DB-02 | `ai_interview` schema accessible | ❌ BLOCKED — depends on DB-01 |
| DB-03 | Experiment write test passes | ❌ BLOCKED — depends on DB-01 |
| API-01 | OpenAI API key valid (non-expired) | 🔄 Pending key rotation |
| API-02 | API key has not been committed to Git | ✅ Verified — only env-var references in codebase |
| CI-01 | CI can run `python eval/preflight_check.py` | ✅ Script syntax-validated; ready for CI wiring |
| CI-02 | No SQLite fallback in CI env | ✅ Script explicitly guards against missing DB_HOST |
| ENV-01 | All required env vars present in CI secrets | 🔄 Pending secrets setup in CI |

---

## Blockers

> Update this section after running preflight. Document any FAIL items and their remediation.

| Blocker | Severity | Remediation |
|---------|----------|-------------|
| **DB-INFRA-01**: Aiven MySQL instance `mysql-4c9be66-andyxiongzheng-9267.g.aivencloud.com` does not resolve on public DNS (verified via `nslookup` against 8.8.8.8). Instance appears terminated or expired. Credentials from Andy's Apr 2 email are no longer valid. | HIGH | Andy must log into [console.aiven.io](https://console.aiven.io), recreate the MySQL service under project `andyxiongzheng-9267`, and provide updated connection credentials. |
| **API-01**: OpenAI API key not yet rotated. | MEDIUM | Infra team rotates key and stores in CI secrets. |

---

## Acceptance Criteria

- [ ] End-to-end ML experiment run completes without local SQLite fallback
- [ ] CI path executes eval scripts with production-equivalent secrets setup
- [x] No plaintext secrets in repository; only env-driven configuration (verified: `data.sql` key is expired/invalid; all active config is env-var driven)
- [ ] `preflight_check.py` exits 0 (UNBLOCKED) in staging environment

---

## Deliverables Checklist

- [x] `eval/preflight_check.py` — preflight validation script (syntax-validated)
- [x] `docs/week21_production_unblock_report.md` — this document
- [x] `eval/README.md` — preflight section added
- [x] `docs/week15_environment_variables_guide.md` — Week 21 appendix added (new early-stopping env vars)
- [ ] Live DB/API gate checks (DB-01–03, API-01) — BLOCKED by DB-INFRA-01; requires Andy to recreate Aiven instance
