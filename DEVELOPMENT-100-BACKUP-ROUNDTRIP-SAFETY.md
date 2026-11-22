# Dev100 – Backup & Restore Round-trip Safety

Version: 5.45.0 (545)  
Room schema: 10 (unchanged)

## Changes
- ZIP restore now rejects archives that do not contain a valid BFP backup manifest.
- Restore and restore-preview require the manifest file list to exactly match the archive's extracted files; the database checksum entry is mandatory.
- Both preview and restore cap archive entry count at 5,000, in addition to existing compressed-input and expanded-size limits.
- Existing SHA-256, SQLite integrity, required-table, staging, rollback, legacy raw SQLite restore, and explicit user confirmation behavior are retained.
- No database migration or automatic business-ledger mutation was introduced.

## Verification
- `tools/verify_dev100_backup_roundtrip_safety.py` checks the source safety invariants.
- Historical verifier scripts were updated to assert current app version 5.45.0 / 545.
- Android Gradle build and device/cloud-provider round-trip testing must still be performed in Android CI/device environment; source checks do not substitute for those tests.
