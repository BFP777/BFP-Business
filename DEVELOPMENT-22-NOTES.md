# Development 22 — GST-aware Invoice Calculation and Print Detail

## Implemented
- Invoice line inputs now carry the item's GST rate and HSN snapshot.
- GST is calculated per invoice line from the selected item's GST rate; the manually entered invoice-level tax field was removed from the invoice form.
- Discount is allocated proportionally across invoice lines in integer paise, using a largest-remainder allocation so no line receives a negative or over-allocated discount.
- Intra-state invoices split tax into CGST and SGST; inter-state invoices use IGST.
- Invoice header stores the resulting CGST, SGST, IGST, and combined tax totals.
- Invoice lines store taxable value, GST rate, HSN, and line-level CGST/SGST/IGST amounts.
- PDF/Print output now includes business profile details, HSN, taxable value, GST rate, line tax, and separate CGST/SGST/IGST totals.
- Added Room migration 1 -> 2 to add the HSN snapshot column to `invoice_lines`; existing data is preserved by the migration.

## State selection rule in this step
- For SALE, seller state is the business profile state and buyer state is taken from the first two digits of the party GSTIN; if the party is unregistered/no GSTIN, the business state is assumed.
- For PURCHASE, the party GSTIN state is treated as supplier state; if unavailable, the business state is assumed.
- This default supports local unregistered parties. A manual place-of-supply/state selector is still needed for unregistered out-of-state parties and special place-of-supply cases.

## Validation performed
- Standalone Kotlin compilation of `GstCalculator.kt` succeeded.
- Executed calculation checks passed for: intra-state multi-rate invoice with discount, inter-state IGST, and discount rounding across four one-paise lines.
- Kotlin parser check across changed Kotlin sources reported no syntax diagnostics (Android/Room dependencies are not available locally, so this is not a full compile).
- ZIP integrity and Android Manifest XML validation are performed at packaging time.

## Not verified
- Full Gradle/Android build and device testing were not run because this environment has no Gradle executable/wrapper or Android SDK configured.
- This is not a certification of GST legal compliance. HSN/rate and place-of-supply data must be verified by the business/accountant before issuing tax invoices.
