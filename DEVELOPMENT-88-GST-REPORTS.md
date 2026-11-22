# Development 88 — GST & Tax Reports

- Added a date-range GST report reachable from the main Reports screen.
- Sales GST and Purchase GST are rendered in separate sections.
- Aggregation is by invoice kind and the GST rate saved on each invoice line.
- Shows distinct invoice count, taxable amount, CGST, SGST, IGST and total GST.
- Sales continue to use the invoice-level GST rate already saved across the invoice lines.
- Purchase continues to preserve supplier-bill item-wise GST rates; each rate is reported separately.
- Read-only report: no accounting entries or tax data are modified.
- No Room schema change; version 5.33.0 / 533.
- Offline source regression suite passed. Android Gradle/device build was not run in this environment.
