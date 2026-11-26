from pathlib import Path
root = Path(__file__).resolve().parents[1]
main = (root / 'app/src/main/java/in/bhajan/bfpbusinesspro/MainActivity.kt').read_text()
scheduler = (root / 'app/src/main/java/in/bhajan/bfpbusinesspro/backup/AutoBackupScheduler.kt').read_text()
worker = (root / 'app/src/main/java/in/bhajan/bfpbusinesspro/backup/AutoBackupWorker.kt').read_text()
gradle = (root / 'app/build.gradle.kts').read_text()
checks = {
    'version 5.43.0 / 543': 'versionCode = 557' in gradle and 'versionName = "5.57.0"' in gradle,
    'WorkManager dependency present': 'androidx.work:work-runtime-ktx:2.10.0' in gradle,
    'DocumentFile dependency present': 'androidx.documentfile:documentfile:1.0.1' in gradle,
    'user selects cloud folder via SAF': 'OpenDocumentTree()' in main and 'takePersistableUriPermission' in main,
    'backup menu exposes opt-in and opt-out': 'Automatic Backup चालू करें' in main and 'Automatic Backup बंद करें' in main,
    'daily periodic work scheduled': 'PeriodicWorkRequestBuilder<AutoBackupWorker>(24, TimeUnit.HOURS)' in scheduler,
    'network required': 'NetworkType.CONNECTED' in scheduler,
    'unique periodic work avoids duplicate schedules': 'enqueueUniquePeriodicWork' in scheduler,
    'worker writes new ZIP in selected provider': 'root.createFile("application/zip"' in worker,
    'worker reuses integrity-checked backup exporter': 'DatabaseBackup.export(applicationContext, target.uri)' in worker,
    'worker records last successful backup': 'last_success_epoch' in worker,
    'documentation states scheduling limitations': 'best-effort' in (root / 'DEVELOPMENT-98-AUTOMATIC-CLOUD-BACKUP.md').read_text(),
}
for label, ok in checks.items(): print(('PASS' if ok else 'FAIL') + ': ' + label)
if not all(checks.values()): raise SystemExit(1)
