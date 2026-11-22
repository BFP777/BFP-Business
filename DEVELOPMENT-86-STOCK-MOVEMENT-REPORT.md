# Development 86 — Item-wise Stock Movement Report

- Added a live item-wise report reachable from Item Master / Stock.
- Shows opening quantity, net purchase movement, net sale movement, manual adjustment, and current closing quantity.
- Purchase/Sale edit and reversal movement rows are included through movement-type aggregation.
- Shows aggregate totals and clarifies that values are movement-ledger totals.
- No database schema change; report is derived from `stock_movements` and current `items.quantity`.
- Version: 5.31.0 (531).
- Offline source verification only; Android Gradle build/device test not performed here.
