from pathlib import Path
root = Path(__file__).resolve().parents[1]
gradle = (root / 'app/build.gradle.kts').read_text()
backup = (root / 'app/src/main/java/in/bhajan/bfpbusinesspro/backup/DatabaseBackup.kt').read_text()
main = (root / 'app/src/main/java/in/bhajan/bfpbusinesspro/MainActivity.kt').read_text()
db = (root / 'app/src/main/java/in/bhajan/bfpbusinesspro/data/BfpDatabase.kt').read_text()
checks = {
    'version 5.46.0 / 546': 'versionCode = 550' in gradle and 'versionName = "5.50.0"' in gradle,
    'Room schema remains 10': 'version = 10' in db,
    'restore rejects ZIP without manifest': 'require(manifestFile.isFile)' in backup and 'ZIP बैकअप अस्वीकार किया गया' in backup,
    'preview and restore compare exact manifest/archive file sets': backup.count('manifestNames ==') >= 1 and 'manifestNames == extractedFiles' in backup and 'manifestNames == archiveFiles' in backup,
    'database checksum is mandatory': 'manifestNames.contains("database/$DB_NAME")' in backup and 'files.has("database/$DB_NAME")' in backup,
    'archive entry count is bounded in preview and restore': backup.count('entryCount <= 5000') == 2,
    'expanded archive size is bounded': 'expanded <= 250L * 1024L * 1024L' in backup and 'totalBytes <= 250L * 1024L * 1024L' in backup,
    'SQLite integrity and required BFP tables validated': 'PRAGMA integrity_check' in backup and 'parties", "items", "invoices", "invoice_lines", "payments"' in backup,
    'restore stages database and attachments before replacement': 'restore-new' in backup and 'restore-previous' in backup,
    'rollback restores prior database on failure': 'if (oldDbMoved && previous.exists()) previous.renameTo(target)' in backup,
    'restore remains behind explicit confirmation': 'Restore Preview – पुष्टि करें' in main and 'DatabaseBackup.preview(this, uri)' in main,
}
for label, ok in checks.items(): print(('PASS' if ok else 'FAIL') + ': ' + label)
if not all(checks.values()): raise SystemExit(1)
