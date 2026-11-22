# Development 47 — Smart Stock Intelligence

- Added a configurable minimum-stock alert threshold to each Item Master record; default is 2 pieces and can be edited per design.
- Added a Smart Stock screen accessible from the menu. It separates low-stock designs from healthy inventory.
- Added a database-backed 30-day sales quantity aggregation per item. The screen identifies the five fastest-selling designs in the last 30 days.
- Reorder suggestions use the greater of the configured minimum stock threshold and the last-30-day average daily sales projected over 30 days, less current stock. Suggestions are informational only; no purchase order or accounting entry is created automatically.
- Added non-destructive Room migration 7→8 for `minimumStockQuantity`, preserving existing item records with a default threshold of 2.0.
- Backup validation now accepts database schema versions 3 through 8; previous ZIP and legacy SQLite restore formats remain supported.
- Version advanced to 503 / 5.3.0.
- Added SQLite-backed migration regression test and source-level checks to the offline verification suite.
- This release does not yet include OCR/AI supplier-bill extraction; that remains a separate next development task.
