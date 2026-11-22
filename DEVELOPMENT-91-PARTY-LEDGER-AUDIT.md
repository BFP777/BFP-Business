# Dev91 — Customer & Supplier Ledger Audit

- Added a read-only per-party ledger audit sourced from invoices and payment rows.
- Compares each invoice's stored `paidPaise` with the sum of its linked payments.
- Flags linked payment rows whose invoice, party, or direction does not match the SALE/IN or PURCHASE/OUT rule.
- Separately reports unallocated Payment In and Payment Out advances.
- Party ledger screen now shows invoice count/value, linked payments, advances, mismatch counts, and actionable audit warnings.
- Customer/supplier invoice totals and payment records remain separate; payments do not become sales or purchases.
- No opening-balance field exists in the current party schema; this release does not invent one or silently assume an opening balance of zero.
- No Room schema change. Version 5.36.0 / 536.
- Offline/source regression checks only; Android Gradle build and device test have not been run.
