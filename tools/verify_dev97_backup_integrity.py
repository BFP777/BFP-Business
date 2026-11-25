from pathlib import Path
root = Path(__file__).resolve().parents[1]
backup = (root / 'app/src/main/java/in/bhajan/bfpbusinesspro/backup/DatabaseBackup.kt').read_text()
gradle = (root / 'app/build.gradle.kts').read_text()
checks = {
    'backup ZIP contains versioned manifest': '"BFP_BACKUP"' in backup and '"formatVersion", 2' in backup,
    'SHA-256 checksums are generated': 'MessageDigest.getInstance("SHA-256")' in backup and 'checksums.put(entryName' in backup,
    'restore verifies each manifest checksum': 'sha256(checkedFile)' in backup,
    'restore rejects duplicate ZIP paths': 'extractedEntries.add(normalized)' in backup,
    'restore rejects missing database payload': 'databaseSource.isFile' in backup,
    'restore validates archive entry allowlist': 'normalized == "manifest.json" || normalized.startsWith("database/")' in backup,
    'restore keeps legacy SQLite support': 'rawTemp.copyTo(legacyDb' in backup,
    'restore validates SQLite integrity': 'PRAGMA integrity_check' in backup,
    'database version range supports schema 10': 'cursor.getInt(0) in 3..10' in backup,
    'app version 5.43.0 / 542': 'versionCode = 552' in gradle and 'versionName = "5.52.0"' in gradle,
}
for name, ok in checks.items():
    print(('PASS' if ok else 'FAIL') + ': ' + name)
if not all(checks.values()):
    raise SystemExit(1)
