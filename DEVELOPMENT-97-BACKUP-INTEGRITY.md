# Dev97 — Backup & Restore Integrity Audit

Version: 5.42.0 (542)  
Room schema: 10 (unchanged)

## Changes
- New ZIP backups include a versioned `manifest.json` describing the BFP backup format, database filename, creation timestamp, and SHA-256 checksums for the database and every purchase attachment.
- Restore validates the manifest format, database identity, required database checksum, every listed file checksum, and that the manifest file count matches extracted payload files.
- Restore rejects duplicate ZIP entry paths, unexpected archive paths, missing database payloads, path traversal, oversized archives, and corrupt/unsupported SQLite databases.
- Legacy raw SQLite backup restore remains supported; manifest validation applies to new version-2 ZIP packages.
- Existing staging/rollback behavior is retained. No Room schema migration or database table change.

## Verification
- `tools/verify_dev97_backup_integrity.py` checks the source-level backup/restore safeguards.
- `tools/verify_all.py` invokes the Dev97 verifier.
- Android build/device restore test is not claimed by this source-only change.
