# Dev98 – Automatic Cloud Folder Backup

- Version: 5.43.0 / 543; Room schema remains 10.
- Added explicit opt-in folder selection through Android Storage Access Framework. The user may select Google Drive or another installed document provider; the app does not request Google account passwords or direct Drive API credentials.
- Added daily WorkManager backup requiring network connectivity. Backup files use the existing versioned BFP ZIP manifest and SHA-256 checksums, and include the database plus purchase attachments.
- Added controls in Backup / Restore to choose/change folder and enable/disable daily automatic backup.
- Manual backup and restore remain available. Automatic backup never restores or overwrites app data.
- Limitations: WorkManager is best-effort and may run later than the 24-hour interval due to Android battery/network scheduling. The user must choose a writable cloud folder and keep its provider/account available. No automatic retention/deletion policy is applied.
- Android build/device and live Google Drive provider tests are not claimed unless run separately.
