# Dev87 — Stock Period Report and Export

- Added date-range stock movement report with selectable start/end dates.
- Opening quantity is calculated from all movements before the selected start date; period purchase, sale and adjustment movements are filtered to the selected range; closing quantity is reconstructed from movements before the exclusive end date.
- Purchase and sale edits/reversals remain represented through their stock movement types.
- Added month-wise sales, purchase and expense amount summary using the existing monthly business trend query and the same selected date range.
- Added Excel-compatible UTF-8 CSV export and PDF export through Android's document picker.
- No Room schema change; database remains schema version 10.
- App version 5.32.0 / versionCode 532.
- Source-level verification added to `tools/verify_all.py`.
