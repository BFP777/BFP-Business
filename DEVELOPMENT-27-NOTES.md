# BFP Business Pro 5.0 — Development 27

## Invoice data-integrity hardening

This pass applies repository-level validation, not just UI filtering:

1. A SALE invoice must reference a CUSTOMER or BOTH party.
2. A PURCHASE invoice must reference a SUPPLIER or BOTH party.
3. The party ID must resolve to an existing party row.
4. Invoice dates must be positive epoch-millisecond values.
5. Invoice numbers must be unique within the same invoice kind, compared case-insensitively after trimming. Editing excludes the current invoice ID so a bill can be saved without changing its own number.

The DAO now exposes the party lookup and invoice-number conflict count used by the repository validation. Checks execute inside the existing Room transaction for invoice create/update.

## Verification and limitations

- Reviewed Development 25 source and applied the changes to the actual Kotlin files.
- Executed a SQLite query-behavior check for case-insensitive invoice-number collision detection (see `tools/verify_invoice_number_sql.py`).
- Ran the standalone GST calculator test harness using the installed Kotlin compiler.
- No Gradle Wrapper JAR/script or Android SDK is present in this environment. Therefore no Gradle Android build, Room KSP compile, APK generation, or device test is claimed.
