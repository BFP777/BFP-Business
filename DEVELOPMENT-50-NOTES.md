# Development 50 — Supplier Bill Verification

- OCR review now extracts supplier invoice number, a date candidate, GSTIN, invoice total, and visible CGST/SGST/IGST amounts.
- The review compares the scanned GSTIN with the selected supplier's GSTIN when the supplier master contains one. Missing or invalid/mismatched values are explicitly flagged.
- The total check compares recognized line amounts plus recognized GST components against the scanned invoice total, allowing the larger of ₹2 or 1% as a conservative OCR rounding tolerance. Missing inputs remain "not checked" rather than being treated as passed.
- PDF supplier bills are OCR-processed page by page (up to 20 pages); each rendered bitmap is released after recognition.
- All values remain review-only. OCR does not create an invoice, payment, stock movement, or accounting posting. The user must review the bill and use the existing purchase Save action.
- Added source-level verification and Kotlin unit tests for metadata, total reconciliation, and supplier GSTIN mismatch.

## Limitations

OCR can misread dates, invoice numbers, GSTINs and amounts. Date format is preserved as read and is not automatically posted into the purchase date. The GST/total checks are advisory and depend on OCR finding the relevant labels. A bill with tax-inclusive line amounts or unusual layouts may need manual review. Android Gradle/JUnit and real-device OCR tests must still run in CI/device before production release.
