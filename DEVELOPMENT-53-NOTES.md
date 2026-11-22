# Development 53 — Customer Intelligence

Version: 5.8.0 (versionCode 508)
Room schema: 9 (non-destructive migration from v8)

## Features
- Customer purchase summary: sale bill count, total sales, outstanding balance and last sale timestamp.
- Transparent VIP label: five or more sale bills OR recorded sale total of at least ₹25,000. This is a visual classification only.
- Optional customer birthday stored as ISO `YYYY-MM-DD`; input is validated as `DD-MM-YYYY`.
- Upcoming birthday list for the next 30 days, including customers without sales.
- Recent ten sale bills and item/design preference summary per customer.
- Global most-purchased customer/design combinations.
- Read-only intelligence screens: no automatic WhatsApp, accounting, payment, inventory or customer-record changes.

## Data safety
- Room migration 8 -> 9 adds `parties.birthday` with an empty-string default; existing party records are preserved.
- Backup validator accepts schema versions 3 through 9.
- Customer summaries include SALE invoices only; purchase invoices are excluded.
- Currency remains stored in paise.

## Verification
- `python3 tools/verify_all.py`
- Targeted SQLite execution covers customer totals/outstanding, birthday selection, recent sale history, design preferences and migration preservation.
- Android Gradle/KSP compilation and device testing still require the configured GitHub Actions build and an Android device.
