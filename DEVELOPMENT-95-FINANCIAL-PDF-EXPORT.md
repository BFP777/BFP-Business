# Dev95 — Date-Range Profit & Loss PDF Export

- App version: 5.40.0 (versionCode 540)
- Room schema: 10 (unchanged; no migration)
- Replaced the P&L / financial-position alert with a report screen.
- Added selectable start and end dates for the management P&L calculation.
- Added PDF export using Android PdfDocument and the existing CreateDocument PDF flow.
- PDF includes the selected period, net sales excluding GST, estimated COGS, gross profit, expenses, salary, estimated operating surplus, and current financial-position snapshot.
- Clearly labels cash/UPI/bank, stock and party balances as current snapshots, not historical period-end balances.
- Preserves existing limitations: current Item Master purchase rates are only an estimate of COGS; this is not a certified statutory P&L or legal Balance Sheet.
- No database schema change and no automatic accounting postings.
- Added `tools/verify_dev95_financial_pdf.py` to `tools/verify_all.py`.
- Android Gradle build/device test not run in this environment.
