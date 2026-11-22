# Dev102 — Cloud Backup Management

- Version: 5.47.0 (547)
- Room database schema remains 10; no migration.
- Added a SAF cloud-folder inventory for recognized BFP ZIP backups.
- Inventory is sorted newest-first and shows filename, modified date, and file size.
- Added guarded deletion: explicit confirmation is required, only recognized BFP backup ZIPs from the selected folder can be deleted, and the final recognized backup cannot be deleted.
- Added a Backup / Restore menu entry for cloud backup management.
- Cloud cleanup outcome is recorded in Backup History.
- This does not automatically delete old backups or silently change retention; each deletion is user initiated and confirmed.
- Offline source checks cover the above guards. Android Gradle build and real Google Drive provider testing are not claimed here.
