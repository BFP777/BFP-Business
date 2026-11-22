# Dev101 — Data Recovery & Backup Monitoring

- Version: 5.46.0 (546)
- Room schema: 10; no database migration.
- Added an explicit one-time backup retry action using WorkManager and the selected SAF folder.
- Automatic backup now records missing-folder, revoked/unwritable-folder, ZIP creation, and export failures in local backup history and persists the last failure timestamp/status.
- Successful backup clears the failure timestamp and updates last-success time.
- Backup screen reports when no successful backup exists, the last success is older than 48 hours, or a newer failure occurred after the last success.
- Backup remains opt-in; no automatic restore or destructive recovery action was added.
- Verification: targeted source checks and ZIP integrity. Android Gradle build and real cloud/device round-trip testing are not claimed.
