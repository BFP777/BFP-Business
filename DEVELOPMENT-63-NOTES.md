# Development 63 – AI Profit & Cashflow Analysis

- Added bilingual, read-only six-month monthly profitability analysis from Dashboard and Inventory Intelligence.
- Displays recorded SALE invoice revenue, estimated gross profit, recorded EXPENSE cash entries, recorded salary payments, estimated gross margin, and estimated operating surplus.
- Estimated gross profit uses current Item Master sale/purchase rates, not historical FIFO/batch cost; explicitly disclosed in UI.
- Estimated operating surplus = estimated gross profit - recorded expenses - recorded salary payments.
- Receipts are not treated as sales; purchase bills are not directly treated as operating expenses; separate cashbook and bank screens remain the source for actual cash/bank balances.
- UI explicitly lists limitations (tax adjustments, stock valuation, owner drawings, debt/interest, depreciation and unrecorded expenses) and does not post or modify balances.
- Added Room projection/query only; schema remains version 9, no migration.
- App version advanced to 5.18.0 (versionCode 518).
- Added focused verifier and registered it in the full offline regression suite.
- APK build and physical Android testing remain pending.
