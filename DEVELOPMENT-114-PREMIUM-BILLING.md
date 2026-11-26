# DEV114 — Premium Billing Visual Refinement

## Source
Based on the user-provided `BFP-Business-main (5).zip`.

## Applied
- Refined the invoice dialog canvas to a soft blue-grey premium background.
- Updated the Credit/Cash selector styling and blue accent.
- Refined the pinned customer header surface.
- Updated the billing brand bar to the approved navy palette and made its context label distinguish Sale vs Purchase.

## Preserved
- Existing invoice creation, update, preview and save flow.
- Credit remains the default settlement mode for new sales.
- Existing Cash sale behavior.
- Existing item entry, voice entry, optional invoice-level GST, stock and ledger repository calls.
- No Room schema/database migration or persisted data changes.

## Not claimed
- Android Gradle compilation and real-device testing have not been run here.
- This visual refinement does not yet add new firm/godown/payment-terms data fields or barcode hardware integration beyond existing project behavior.
