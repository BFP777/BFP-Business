# Development 59 – Business Dashboard AI

- Added a bilingual daily business dashboard with separate sales, purchase, payment receipts, and expense totals.
- Added estimated gross profit using sale invoice line quantities and the current Item Master sale/purchase rates. This is explicitly an estimate, not historical batch costing or net profit.
- Added customer receivables and supplier payables using the existing party-balance repository; green/red visual distinction is preserved.
- Dashboard is read-only. It does not create, edit, delete, or post accounting entries.
- Receipts are sourced only from `payments.direction = 'IN'`; they are never added to sales.
- Expense totals are sourced only from `cash_entries.entryType = 'EXPENSE'`.
- Room schema remains version 9; no database migration is introduced.
- App version: 5.14.0 (versionCode 514).
