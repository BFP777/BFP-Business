# Development 58 — Per-Suit Profit Estimate + Dead Stock

Version: 5.13.0 (versionCode 513)
Room schema: 9 (no database migration required)

## Features
- New Inventory Intelligence screen: Per-Suit Profit & Dead Stock.
- Per-suit estimated gross profit is current Item Master sale rate minus current Item Master purchase rate.
- Estimated gross margin percentage is shown relative to the current sale rate.
- Items without both rates are excluded from profit calculations and clearly called out.
- Dead stock is defined transparently as positive on-hand quantity with no recorded sale in the last 90 days (including never sold).
- Last sale date and current stock quantity are shown.
- No sale, purchase, stock, cash, bank, or ledger entries are created or changed.
- No Room schema change or migration.

## Accounting caveat
This is an estimate based on current master rates, not historical batch costing or net profit. It excludes discounts, tax, overhead, and other expenses.

## Safety
All calculations are read-only. The screen only displays information and opens the existing item editor when selected.
