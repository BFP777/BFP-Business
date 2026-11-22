# Development 37 — Room DAO SQL Verification

## Changes
- Added `tools/verify_room_dao_sql.py`.
- The verifier extracts every literal Room `@Query` statement from `AppDao.kt` and runs SQLite `EXPLAIN` against an in-memory schema matching the Room entities.
- Added this check to `tools/verify_all.py`, so the CI source-verification stage executes it automatically.

## Verification
- All literal DAO query statements are syntax-checked by SQLite with representative named parameters.
- Full offline verification suite is run via `python3 tools/verify_all.py`.

## Limitations
SQLite `EXPLAIN` verifies SQL syntax and referenced table/column names, but it does not replace Room KSP annotation processing, Android compilation, instrumentation tests, or real-device accounting tests.
