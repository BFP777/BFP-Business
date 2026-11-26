# Development 113 — Dashboard UI Redesign (5.58.0)

- Reworked the home dashboard into a compact, card-based layout matching the approved BFP reference: navy brand header, sales/purchase/cash/outstanding cards, green debtors and red creditors, and quick-action tiles for sale, purchase, payment in/out, expense and bank.
- Added shortcuts for parties, stock/items, day book and reports, plus backup access.
- Dashboard values are read from the existing invoice, party-balance and reconciled money repositories; this change does not create or duplicate accounting entries.
- Updated the app version to 5.58.0 / 558. No Room schema change.
- Existing billing workflow and accounting repositories were preserved. Billing layout still requires device-level visual verification against the approved reference.

## Verification

Run `python3 tools/verify_all.py`. Android compilation and signed APK generation require the configured Android SDK and GitHub Actions signing secrets.
