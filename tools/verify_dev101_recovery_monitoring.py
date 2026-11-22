from pathlib import Path
root = Path(__file__).resolve().parents[1]
gradle = (root / "app/build.gradle.kts").read_text()
worker = (root / "app/src/main/java/in/bhajan/bfpbusinesspro/backup/AutoBackupWorker.kt").read_text()
scheduler = (root / "app/src/main/java/in/bhajan/bfpbusinesspro/backup/AutoBackupScheduler.kt").read_text()
main = (root / "app/src/main/java/in/bhajan/bfpbusinesspro/MainActivity.kt").read_text()
db = (root / "app/src/main/java/in/bhajan/bfpbusinesspro/data/BfpDatabase.kt").read_text()
checks = {
    "version 5.46.0 / 546": "versionCode = 552" in gradle and 'versionName = "5.52.0"' in gradle,
    "Room schema remains 10": "version = 10" in db,
    "manual retry enqueues one-time backup work": "fun runNow(context: Context)" in scheduler and "OneTimeWorkRequestBuilder<AutoBackupWorker>" in scheduler,
    "backup failure without folder is recorded": "FOLDER_NOT_SELECTED" in worker and "Backup Folder नहीं चुना गया" in worker,
    "permission failures persist failure timestamp": "last_failure_epoch" in worker and "PERMISSION_ERROR" in worker,
    "successful backup clears failure timestamp": 'remove("last_failure_epoch")' in worker,
    "backup target creation failure is recorded through exception path": "Backup ZIP फाइल नहीं बन सकी" in worker,
    "UI exposes manual retry": "अभी Automatic Backup चलाएँ / Retry" in main and "AutoBackupScheduler.runNow(this)" in main,
    "UI warns when backup is stale or failed": "48 घंटे से अधिक पुराना बैकअप" in main and "आखिरी प्रयास में समस्या" in main,
}
for label, ok in checks.items(): print(("PASS" if ok else "FAIL") + ": " + label)
if not all(checks.values()): raise SystemExit(1)
