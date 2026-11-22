# Development 20 — GST Data Foundation

## Included
- Item master now carries HSN code and GST rate percentage (defaults preserve existing constructor call sites).
- Invoice header and invoice line models carry CGST, SGST and IGST amount fields.
- Business profile table stores seller name, address, mobile, GSTIN, state code and invoice prefix.
- DAO can save/read the seller business profile.
- `GstCalculator` validates GSTIN format and calculates intra-state split GST (CGST+SGST) or inter-state IGST in paise.
- Currency calculations use integer paise for tax amounts.

## Important limitations
- This is the GST data/calculation foundation, not yet a complete GST invoice workflow.
- Existing item and invoice entry screens have not yet been updated to capture HSN/GST rates or seller GST details.
- InvoiceRepository has not yet been wired to persist calculated GST splits; current bill tax input remains manual.
- No tax classification, exemption, reverse-charge, place-of-supply, e-invoice, e-way bill or statutory compliance certification is claimed.
- Database is version 1 for this fresh-start project. Do not install over an earlier development build with valuable data; this project intentionally has no migration path for previous development ZIPs.
- Android build/runtime verification remains outstanding because this environment has no Android SDK/Gradle wrapper installation.
