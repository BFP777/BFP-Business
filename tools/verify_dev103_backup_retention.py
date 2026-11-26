from pathlib import Path
root = Path(__file__).resolve().parents[1]
gradle = (root / "app/build.gradle.kts").read_text()
manager = (root / "app/src/main/java/in/bhajan/bfpbusinesspro/backup/CloudBackupManager.kt").read_text()
main = (root / "app/src/main/java/in/bhajan/bfpbusinesspro/MainActivity.kt").read_text()
db = (root / "app/src/main/java/in/bhajan/bfpbusinesspro/data/BfpDatabase.kt").read_text()
checks = {
    "version 5.49.0 / 549": 'versionCode = 557' in gradle and 'versionName = "5.57.0"' in gradle,
    "Room schema unchanged": "version = 10" in db,
    "storage summary counts recognized backups": "fun storageSummary" in manager and "backupCount = backups.size" in manager,
    "storage usage totals BFP backup sizes": "backups.sumOf { it.bytes.coerceAtLeast(0L) }" in manager,
    "retention keeps latest configurable count": "DEFAULT_KEEP_LATEST = 10" in manager and "backups.drop(keepLatest)" in manager,
    "at least one backup is required": "require(keepLatest >= 1)" in manager and "backups.size > 1" in manager,
    "whole-drive quota is not fabricated": "providers do not expose reliable whole-drive quota" in manager,
    "storage monitor exposed in backup menu": "Storage Monitor / पुराने Backup की समीक्षा" in main,
    "cleanup is explicitly user-confirmed": "पुराना Backup हटाएँ?" in main and "userConfirmed = true" in main,
    "cleanup is recorded in history": 'BackupHistory.record(this, "Retention Cleanup"' in main,
    "no automatic deletion policy": "कोई फाइल अपने-आप नहीं हटेगी" in main,
}
for label, ok in checks.items():
    print(("PASS" if ok else "FAIL") + ": " + label)
if not all(checks.values()):
    raise SystemExit(1)
