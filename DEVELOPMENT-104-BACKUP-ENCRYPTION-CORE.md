# Development 104 — Backup Encryption & Privacy Protection Core

Version: 5.49.0 (versionCode 549)

## Implemented in this source revision
- Added `BackupEncryption`, a versioned `BFPE` encrypted-container format.
- Uses AES-256-GCM with a 128-bit authentication tag.
- Derives the key from a user-supplied `CharArray` using PBKDF2-HMAC-SHA256, 310,000 iterations, a random 16-byte salt and random 12-byte nonce.
- Validates the envelope version and bounds the accepted KDF iteration count before key derivation.
- Decrypts to a private `.partial` cache file and only renames it after authenticated decryption completes. The caller must still validate the resulting BFP ZIP manifest, checksums and SQLite integrity before restore.
- Does not persist passwords, keys or password hints. Callers must clear their password `CharArray` after use.

## Important integration status
This revision adds and documents the cryptographic engine only. Existing manual backup/restore screens and `DatabaseBackup` are not yet wired to this envelope, so normal manual backups remain standard BFP ZIP files. Existing automatic backups also remain unencrypted: WorkManager cannot safely request an interactive password while running unattended. Do not describe this build as providing end-to-end encrypted backup yet.

Next integration work must: create a temporary ZIP, encrypt it to the user-selected destination, detect `BFPE` before ZIP/SQLite parsing, request the password for preview and restore, decrypt to a private temporary ZIP, run all existing manifest/checksum/SQLite checks, and clear password arrays and temporary plaintext files in `finally` blocks. Legacy ZIP and SQLite restore must remain supported.

## Validation limits
No Android Gradle build or device test is claimed. This environment has no Gradle wrapper or installed Gradle. The Dev104 source verifier checks the intended crypto primitives and guardrails; it is not a substitute for Android compilation or cryptographic integration tests.
