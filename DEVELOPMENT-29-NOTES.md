# Development 29 — Invoice Edit Payment Integrity

## Audit findings
- Invoice stock reversal/re-application and invoice deletion are already wrapped in Room transactions.
- Deleting an invoice with linked payments is blocked, avoiding orphaned payment records.
- Editing an invoice already checks that the replacement total can cover its paid amount.

## Change made
- Invoice edit now derives `paidPaise` from the actual linked `payments` rows (`SUM(amountPaise)`) instead of trusting the cached `invoices.paidPaise` value.
- The edit is rejected if actual linked payments exceed the replacement invoice total.
- This prevents stale cached paid totals from being carried into an edited bill and keeps the invoice's displayed paid amount aligned with its payment records.

## Verification
- Source assertions confirm invoice edit reads the linked-payment sum, validates it against the replacement total, and writes that exact amount back to `paidPaise`.
- A standalone invariant script checks stock reversal/re-application scenarios and payment-total boundaries.
- ZIP integrity checked after packaging.

## Limits
- This is source-level and standalone logic verification only. No Android Gradle build, Room integration test, emulator/device test, or APK build was performed. The current environment still has no Gradle wrapper/system Gradle or Android SDK.
