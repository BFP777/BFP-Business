# Development 92 — Cash / UPI / Bank Reconciliation

- App version: 5.37.0 (versionCode 537)
- Room schema: 10; no database migration required.
- Added a read-only `LedgerRepository.reconcileMoneyBalances()` audit.
- Report summarizes current Cash, UPI and overall Bank balances using existing source movements.
- Checks BANK-mode payment rows, BANK opening/expense rows, cash-to-bank transfer rows and BANK salary payouts for a valid named bank-account link.
- Compares the overall bank balance with the sum of named bank-account balances when all bank movements are assigned.
- Added a Cash / Bank Reconciliation action on the existing Bank screen.
- The report never posts, edits or deletes accounting entries. It flags missing account links and mismatches for manual review.
- Cash, UPI, Bank, expenses, party payments and salary remain separate source categories; transfers are not treated as sales or purchases.
- Offline/source verification: `python3 tools/verify_all.py`.
- Android Gradle build and device test were not run in this environment; no Gradle wrapper/installed Gradle command is available.
