# Dev93 — Complete Business Financial Audit

- App version: 5.38.0 (versionCode 538)
- Room schema: 10 (unchanged; no migration)
- Adds a read-only cross-module audit in Reports.
- Summarizes source totals for sales, purchases, expenses, salary payments, payment-in and payment-out.
- Checks invoice paid amounts against linked payments, party and invoice references, payment direction, invalid bank account links, employee/salary-month references, non-positive payment/expense amounts, and stock movement net quantity against current item quantity.
- Sales and purchase totals remain separate. Payments are not reclassified as sales or purchases.
- No accounting records are modified by the audit. It does not validate external bank statements, GST portal/ITC eligibility, or statutory filing correctness.
- Added `tools/verify_dev93_business_financial_audit.py` to `tools/verify_all.py`.
- Android Gradle build/device test must be run in CI or Android build environment; offline source checks do not establish APK build success.
