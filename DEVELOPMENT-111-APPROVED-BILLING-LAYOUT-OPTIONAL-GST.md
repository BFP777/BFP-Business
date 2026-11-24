# DEV111 — Approved Single-Screen Billing Layout + Optional GST

Base: DEV110 single-screen billing source, including the DEV111 quantity/rate display patch.

## Changes
- Added a compact manual-entry row with separate Qty, Item Name, and Rate fields.
- Added visible Save Item and Save & New actions; both add the current manual item through the existing invoice-line workflow.
- Added an optional sales GST checkbox. New sales bills default to GST enabled at 5%; unticking it sends 0% GST to the existing invoice repository. Existing bills infer the toggle from their saved invoice lines.
- Added BFP Business Pro dark-navy brand bar to the billing screen.
- Preserved customer selection, continuous voice entry, saved-item suggestions, barcode/item-code lookup, edit/delete rows, invoice preview, stock, ledger, and purchase attachment/OCR paths.
- Version advanced to 5.56.0 (556).

## Validation
- ZIP integrity and source-level assertions are checked by the packaging step.
- Android Gradle compilation and real-device UI/voice/payment testing have NOT been run in this environment. Run the repository Android build workflow before installing.
