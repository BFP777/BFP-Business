from pathlib import Path
root = Path(__file__).resolve().parents[1]
gradle = (root / "app/build.gradle.kts").read_text()
manager = (root / "app/src/main/java/in/bhajan/bfpbusinesspro/backup/CloudBackupManager.kt").read_text()
main = (root / "app/src/main/java/in/bhajan/bfpbusinesspro/MainActivity.kt").read_text()
db = (root / "app/src/main/java/in/bhajan/bfpbusinesspro/data/BfpDatabase.kt").read_text()
checks = {
    "version 5.47.0 / 547": 'versionCode = 561' in gradle and 'versionName = "5.61.0"' in gradle,
    "Room schema remains 10": "version = 10" in db,
    "cloud folder backups are listed by SAF tree": "DocumentFile.fromTreeUri" in manager and "root.listFiles()" in manager,
    "only recognized BFP ZIP backup names are managed": "BFP-Auto-Backup-" in manager and "BFP-Backup-" in manager and 'endsWith(".zip"' in manager,
    "backup listing includes size and modified date": "it.length()" in manager and "it.lastModified()" in manager,
    "listing is newest first": "sortedWith(compareByDescending" in manager,
    "delete requires explicit confirmation": "require(userConfirmed)" in manager,
    "deletion keeps at least one backup": "backups.size > 1" in manager,
    "delete only targets a listed recognized backup": "backups.firstOrNull { it.uri == uri }" in manager and "require(isBfpBackup(file))" in manager,
    "UI exposes cloud backup management": "Cloud Backup सूची / Manage Backups" in main and "showCloudBackupManager()" in main,
    "UI asks before deleting": "Backup हटाने की पुष्टि" in main and 'setPositiveButton("हटाएँ")' in main,
    "cleanup result is recorded": 'BackupHistory.record(this, "Cloud Cleanup"' in main,
}
for label, ok in checks.items():
    print(("PASS" if ok else "FAIL") + ": " + label)
if not all(checks.values()):
    raise SystemExit(1)
