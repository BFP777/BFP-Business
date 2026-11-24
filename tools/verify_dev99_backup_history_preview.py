from pathlib import Path
root = Path(__file__).resolve().parents[1]
gradle = (root / 'app/build.gradle.kts').read_text()
main = (root / 'app/src/main/java/in/bhajan/bfpbusinesspro/MainActivity.kt').read_text()
backup = (root / 'app/src/main/java/in/bhajan/bfpbusinesspro/backup/DatabaseBackup.kt').read_text()
history = (root / 'app/src/main/java/in/bhajan/bfpbusinesspro/backup/BackupHistory.kt').read_text()
worker = (root / 'app/src/main/java/in/bhajan/bfpbusinesspro/backup/AutoBackupWorker.kt').read_text()
checks = {
    'version 5.44.0 / 544': 'versionCode = 561' in gradle and 'versionName = "5.61.0"' in gradle,
    'backup history bounded to 20 records': 'minOf(old.length(), 19)' in history,
    'backup history shows newest records': 'Backup History (नवीनतम 10)' in history,
    'manual backup success/failure recorded': 'BackupHistory.record(this, "Manual"' in main,
    'automatic backup success/failure recorded': 'BackupHistory.record(applicationContext, "Automatic"' in worker,
    'last automatic backup and status visible': 'Last Automatic Backup:' in main and 'last_status' in main,
    'restore preview validates archive manifest': 'fun preview(context: Context, sourceUri: Uri)' in backup and 'SHA-256 जाँच असफल' in backup,
    'restore preview validates sqlite database': 'validateBackup(dbFile)' in backup and 'PRAGMA integrity_check' in backup,
    'preview reports attachment count and bytes': 'attachmentCount: Int' in backup and 'files.sumOf { it.length() }' in backup,
    'restore requires explicit preview confirmation': 'Restore Preview – पुष्टि करें' in main and 'DatabaseBackup.preview(this, uri)' in main,
    'preview does not mutate live database': 'fun preview(context: Context, sourceUri: Uri)' in backup and 'BfpDatabase.closeForMaintenance()' not in backup[backup.index('fun preview(context: Context, sourceUri: Uri)'):backup.index('fun restore(context: Context, sourceUri: Uri)')],
    'room schema remains 10': 'version = 10' in (root / 'app/src/main/java/in/bhajan/bfpbusinesspro/data/BfpDatabase.kt').read_text(),
}
for label, ok in checks.items(): print(('PASS' if ok else 'FAIL') + ': ' + label)
if not all(checks.values()): raise SystemExit(1)
