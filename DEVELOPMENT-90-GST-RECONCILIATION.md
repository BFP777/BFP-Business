# Dev90 — GST Reconciliation

- Added a date-scoped book reconciliation summary: Sales output GST, Purchase input tax, and estimated net tax from recorded invoice lines.
- Added supplier-wise purchase tax summary with GSTIN, invoice count, taxable value, and CGST/SGST/IGST totals.
- Kept Sales and Purchase GST calculations separate; no database schema change.
- UI explicitly labels purchase tax as potential ITC and warns that no GSTR-2B/portal matching or ITC eligibility validation is performed.
- Version: 5.35.0 / 535.
- Offline regression verifier: `tools/verify_dev90_gst_reconciliation.py`.
