# Development 28 — Backup Restore Safety & Payment Party Validation

## Changes
- Restore now validates SQLite integrity, Room database version 3, and all seven required BFP business tables before touching the live database.
- Restore stages a second copy beside the live database, keeps the previous database until Room successfully opens the restored file, and attempts to roll back if installation/opening fails.
- Temporary restore files are cleaned up after the operation.
- Payment In is restricted to CUSTOMER/BOTH parties; Payment Out is restricted to SUPPLIER/BOTH parties. This applies to invoice-linked payments and unallocated advances.

## Verification performed
- Reviewed Room schema version and migration chain (1→2→3) against the backup version check.
- Re-ran invoice-number SQL checks and GST calculator smoke checks from Development 27.
- Parsed AndroidManifest.xml and checked ZIP integrity.

## Limits
- This is source-level validation only. Android Gradle build, emulator/device restore test, and APK generation were not possible in this environment because the project has no Gradle wrapper and the environment has no Android SDK/system Gradle.
- Restore rollback is best-effort and must still be exercised on an Android device before production use.
