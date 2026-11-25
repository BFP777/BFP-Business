# DEV121 — Separate Debtors/Creditors and Simple Purchase

- Dashboard Debtors card opens a customer-only directory; Creditors card opens a supplier-only directory.
- Each directory provides Add Party, Ledger, Edit/Delete, and the correct new bill action.
- The old combined Parties destination is removed from the bottom navigation and Dashboard shortcuts.
- Purchase creation now asks for one of two modes: Simple Purchase (amount-only, no inventory) or Item-wise Purchase (existing item-master linked flow, stock increases).
- Simple Purchase records a supplier purchase invoice with one non-stock line (`itemId=0`), so supplier ledger and purchase totals are updated while stock remains unchanged.
- Existing item-wise purchase flow, supplier bill attachment, OCR, GST and ledger posting remain available.
- Version: 5.64.0 (versionCode 564).
