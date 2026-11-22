# Development 85 — Opening Stock & Adjustment Audit

- Added `stock_movements` audit table with movement type, delta, previous/new quantity, reason, timestamp and reference.
- Added Room migration 9→10 and index by item/date. Existing non-zero item quantities are preserved as baseline Opening Stock history during migration.
- New Item Master items and their opening quantity are saved atomically as an `OPENING` movement.
- Existing item quantity is read-only in the Item Master editor; changes must use Stock Adjustment with a mandatory reason.
- Adjustment writes the new Item Master quantity and audit row in one Room transaction. Purchase/sale posting, invoice edits and invoice reversals also create corresponding stock movement audit rows in the same invoice transaction.
- Item editor offers Stock Movement History.
- Existing invoice purchase/sale posting remains the source of invoice-driven stock changes; this screen's manual audit log currently records opening and manual adjustment events.
- Offline verification includes schema, repository, UI and quantity reconciliation checks.
- Version 5.30.0 / 530.
