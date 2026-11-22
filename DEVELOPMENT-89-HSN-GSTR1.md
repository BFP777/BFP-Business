# Development 89 — HSN GST Summary and GSTR-1 Working CSV

## Changes
- Added read-only HSN + GST-rate summary projection for both SALE and PURCHASE invoice lines.
- HSN summary groups independently by invoice kind, normalized HSN (blank values shown as `UNSPECIFIED`) and saved GST rate. It sums quantity, taxable value and CGST/SGST/IGST.
- Added a sales-only invoice-level projection for GSTR-1 preparation with invoice number/date, customer name/GSTIN, taxable value, tax components and invoice total.
- Added a date-filtered CSV export action. The CSV is a working data extract, not a certified GSTN upload template and not a return filing action. B2B/B2C is only a provisional indicator based on whether customer GSTIN is present; verify classification, place of supply, rate-wise details, amendments/credit notes and official schema before filing.
- No Room schema or accounting-posting changes. Existing Sales invoice-level GST and Purchase item-wise GST calculations remain unchanged.

## Version
- versionName: 5.34.0
- versionCode: 534

## Verification
- `python3 tools/verify_all.py` passes, including the new Dev89 regression checks.
- ZIP integrity verified after packaging.
- Android Gradle build/device test not run in this environment; no Gradle wrapper/installed Gradle available.
