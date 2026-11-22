# Dev99 — Backup History & Restore Preview

- Version: 5.44.0 / 544
- Room schema remains version 10; no database migration.
- Added a bounded local backup operation history (newest 20 records) for manual, automatic and restore operations.
- Backup screen now shows the latest automatic backup timestamp, last worker status and newest 10 history entries.
- Automatic worker records success, permission failure and retry outcomes.
- Restore selection now runs a read-only preview first: ZIP manifest/version, SHA-256 checks, SQLite integrity/schema, database size, attachment count and attachment bytes. Legacy SQLite backup is also validated and identified.
- Restore only proceeds after explicit user confirmation; preview itself does not replace the installed database or attachments.
- Source verifier: `tools/verify_dev99_backup_history_preview.py`.
- Android Gradle build and real Google Drive/provider restore test are not claimed as completed.
