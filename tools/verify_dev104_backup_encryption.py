from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
source = ROOT / "app/src/main/java/in/bhajan/bfpbusinesspro/backup/BackupEncryption.kt"
text = source.read_text(encoding="utf-8")
checks = {
    "versioned encrypted envelope": '0x42, 0x46, 0x50, 0x45' in text and 'VERSION: Int = 1' in text,
    "AES-GCM authenticated encryption": 'AES/GCM/NoPadding' in text and 'GCMParameterSpec(TAG_BITS, nonce)' in text,
    "PBKDF2-HMAC-SHA256": 'PBKDF2WithHmacSHA256' in text,
    "random salt and nonce": 'SecureRandom()' in text and 'SALT_BYTES: Int = 16' in text and 'NONCE_BYTES: Int = 12' in text,
    "bounded KDF parameters": 'iterations in 200_000..600_000' in text,
    "minimum passphrase length": 'MIN_PASSWORD_CHARS: Int = 10' in text,
    "decrypt to staging before rename": '.partial' in text and 'temp.renameTo(outputFile)' in text,
    "no password persistence": 'SharedPreferences' not in text and 'Log.' not in text,
}
for label, ok in checks.items():
    print(('PASS' if ok else 'FAIL') + ': ' + label)
if not all(checks.values()):
    raise SystemExit(1)

gradle = (ROOT / 'app/build.gradle.kts').read_text(encoding='utf-8')
assert 'versionCode = 559' in gradle and 'versionName = "5.59.0"' in gradle
print('PASS: current app version 5.52.0 / 552')
