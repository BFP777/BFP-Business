# BFP Business Pro 5.0 — Development 25

## Integrated review and corrections

This pass reviewed the current source package (Development 24) across invoice posting, payments, party ledger, day book, reports, cash/bank/UPI balances, GST calculation, and backup/restore.

### Corrections made

1. **Invoice party integrity:** an invoice with linked payments can no longer be reassigned to a different customer/supplier. This prevents the invoice balance from moving to a new party while its payment rows remain attached to the original party. The user must remove/reconcile linked payments first.
2. **UPI opening balance:** Opening Balance now offers CASH, UPI, and BANK. UPI balance calculation includes UPI opening balance and subtracts UPI expenses, in addition to UPI receipts/payments.

### Audit observations still requiring follow-up

- The project still contains no Gradle Wrapper (`gradlew` / `gradle/wrapper`) and no Android SDK configuration in this environment. A full Android build and device test have therefore **not** been run.
- The GST calculator has unit tests in `app/src/test/.../GstCalculatorTest.kt`, but these tests have not been executed through Gradle here.
- Backup/restore has not been exercised on a real Android device.
- Invoice PDF printing and all UI flows still need end-to-end device verification.
- The app still has some UI actions that deliberately show an “not fully integrated” message; these must be resolved before calling the product final.

### Verification performed in this pass

- Reviewed the actual source files from Development 24.
- Confirmed the ZIP contents and project configuration.
- Confirmed JDK 21 and Kotlin compiler are present; system Gradle and Android SDK are absent.
- Source-level checks only; this is not an Android APK build or runtime certification.
