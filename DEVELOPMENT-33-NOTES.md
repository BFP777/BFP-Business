# Development 33 — Integrated Database, Ledger, Reports & Backup Audit

## Change made
- Invoice-number uniqueness now trims whitespace from both the stored invoice number and the new number before case-insensitive comparison. This closes a bypass where a legacy/stored value such as ` S-200 ` could coexist with `S-200`.
- Extended the SQLite regression test to cover stored and entered values with surrounding spaces.
- Added an integrated source-audit script covering backup integrity/version/table checks, explicit Room migrations, payment-derived invoice paid totals, cash-only Day Book balance arithmetic, and outstanding-balance handling for unallocated advances.

## Audit observations
- Room database is version 3 with explicit migrations 1→2→3; no destructive migration fallback is enabled.
- Backup restore validates SQLite integrity, database version 3, and required business tables before replacing the active file, then opens the restored database through Room. Rollback remains best-effort and needs device testing.
- Invoice-linked payments are the source of truth for invoice paid totals. Party outstanding reports use invoice totals less linked paid amounts and separately account for unallocated advances.
- Day Book cash opening/closing calculations use cash-mode payment movements and cash entries; UPI and bank payments do not inflate cash.

## Verification performed
- `python3 tools/verify_invoice_number_sql.py` — PASS, including whitespace and case-insensitive duplicates.
- `python3 tools/verify_posting_invariants.py` — PASS.
- `python3 tools_verify_daybook.py` — PASS.
- `python3 tools/verify_dev33_integrated_audit.py` — PASS.
- AndroidManifest XML parse — PASS.
- ZIP integrity — PASS after packaging.

## Limitations
- This is a source-level audit with standalone regression checks, not an Android instrumentation or Room integration test.
- No full Gradle build, emulator/device testing, restore exercise, or APK build was possible in this environment because the project has no Gradle wrapper and no Android SDK/system Gradle is installed.
