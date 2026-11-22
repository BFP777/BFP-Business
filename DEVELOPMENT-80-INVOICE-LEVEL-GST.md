# Development 80 — Invoice-level GST

- Removed GST percentage from the Item Master form and item-list display.
- Removed GST percentage from the manual/voice item-entry editor; item rows no longer ask for a tax rate.
- Added one GST percentage field in the bill-finalization section. The selected rate is applied uniformly to every line in the invoice, so GST is calculated on the invoice taxable amount at finalization rather than separately selected for each suit.
- Applied the invoice-level rate to both sales and purchase invoice records; supplier GSTIN and supplier-bill OCR metadata remain intact.
- Existing invoices load the first saved line's GST rate as the invoice-level rate when edited; saving normalizes all lines to that selected rate.
- Item Master writes the legacy per-item GST field as 0.0; no Room schema change or migration was required.
- Version: 5.25.0 / 525.
- Verification: `python tools/verify_dev80_invoice_level_gst.py` and `python tools/verify_all.py` passed. Android Gradle build/device UI test not run in this environment.
