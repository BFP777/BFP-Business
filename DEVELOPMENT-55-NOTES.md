# Development 55 — Customer Sales & Receipt Tabs

Version: 5.10.0 (versionCode 510)
Room schema: 9 (no database migration required)

## Features
- Customer detail opens as a full screen instead of a compact purchase-history dialog.
- Customer profile shows name, mobile, address, GSTIN and optional birthday.
- Two clear tabs appear directly below the customer profile: SALE / बिक्री and RECEIPT / प्राप्ति.
- SALE tab lists only that customer's sale invoices, with date, total, paid amount and outstanding due, plus a total sales summary.
- RECEIPT tab lists only incoming payments for that customer, with amount, date, payment mode, optional linked bill and note, plus a total received summary.
- New Sale and New Receipt actions open the existing entry forms with the current customer preselected. The user can still review the form before saving.
- Existing WhatsApp draft and preferred-design actions remain available; no message is sent automatically.
- Uses existing invoices and payments tables; no database migration or duplicate accounting rows are introduced.
- Tab labels and key customer-facing text are bilingual (Hindi + English).

## Verification
- Targeted source checks confirm the two tabs, party-scoped sale and receipt lists, separate totals, and preselected customer in both entry forms.
- SQLite fixture checks confirm that only SALE invoices and IN receipts for the selected customer are included.
- Full offline regression suite is run before packaging.
- Android Gradle/KSP compilation and on-device UI testing still require GitHub Actions / an Android device.
