# Development 64 – AI Decision Assistant

- Added bilingual, read-only AI Decision Assistant entry points on the Business Dashboard and Inventory Intelligence menu.
- Compares recorded SALE revenue for the latest 30 days with the preceding 30-day period and provides a cautious trend-based review suggestion.
- Highlights designs whose current stock is below recent 30-day demand plus the Item Master minimum-stock threshold.
- Summarizes customer receivables and supplier payables from existing insight queries, with top outstanding parties for manual review.
- Highlights supplier-item purchase-rate increases using the existing latest-versus-previous purchase-rate comparison.
- All suggestions are advisory; no sale, purchase, stock, receipt, payment, cash, bank, expense, or ledger entry is created or modified.
- No new database tables or schema migration; Room remains schema version 9.
- App version advanced to 5.19.0 (versionCode 519).
- Added focused Dev64 verifier and included it in the complete offline verification suite.
- APK build and physical Android device testing are not performed in this environment; use the existing GitHub Actions workflow for APK build.
