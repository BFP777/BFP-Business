# Dev94 — Profit & Loss and Financial Position

- App version: 5.39.0 (versionCode 539)
- Room schema: 10 (unchanged; no migration)
- Adds a read-only, current-month management P&L estimate and current financial-position snapshot under Reports.
- Revenue uses sales invoice subtotal less invoice discount (GST excluded).
- Estimated COGS uses each sold item's current Item Master purchase rate multiplied by sold quantity. Lines without a usable purchase rate are counted and disclosed; this is not historical batch costing.
- Shows gross profit estimate, recorded operating expenses, salary payments and estimated operating surplus.
- Snapshot shows current cash, UPI, bank, stock value at current purchase rates, receivables and payables.
- Explicitly states this is not a certified statutory P&L or legal Balance Sheet. The current schema does not fully capture capital, fixed assets, loans, depreciation, all tax liabilities or opening balances.
- No database schema change and no automatic accounting postings.
- Added `tools/verify_dev94_profit_loss_position.py` to `tools/verify_all.py`.
- Targeted Dev91–Dev94 source verifiers passed. The full `verify_all.py` run exceeded the execution time limit during repeated historical checks; it did not complete.
- Android Gradle build/device test not run in this environment.
