# Development 16 — Reports and Stock Report

## Implemented in this step
- Replaced the Dashboard Reports placeholder with a report screen.
- Added date range selection using Android date pickers.
- Added period summaries for sales, purchases, Payment In, Payment Out, and expenses, including Cash/UPI/Bank breakdowns.
- Added Sundry Debtors and Sundry Creditors totals.
- Added a Stock Report screen with item count, aggregate quantity, current purchase-rate stock valuation, item-level quantities/rates, and a low-stock marker.

## Explicit limitations
- Low-stock threshold is fixed at 5 units.
- Stock valuation uses each item's current purchase rate, not a historical cost method.
- Cash/Bank values are transaction movement summaries, not reconciled account balances; opening balances and transfer pairing are not implemented.
- Expense-entry and deposit/withdrawal UI is not complete.
- Android build and device runtime testing remain outstanding because this workspace has no Android SDK and no Gradle executable or wrapper.
