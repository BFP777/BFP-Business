# DEVELOPMENT 110 — Single-Screen Billing Redesign

Base: DEV109 Manual Item Save & New source (DEV108 billing/voice features and DEV107 customer/item-list behavior retained).

## Approved sales billing layout
- Replaced the split fixed-header / independently scrolling item-list / separate lower-details layout with one continuous, full-screen billing canvas.
- The selected customer header remains pinned at the top; it displays the customer name only, with the existing Change action retained.
- Item entry, saved-item suggestions, voice controls, barcode/item-code lookup, added invoice rows, discount, invoice-level sales GST, note and other bill details are in one vertically scrollable body.
- The complete invoice body scrolls together; adding an item scrolls to the newest item area using the existing auto-scroll callback.
- Dialog action labels retain Cancel, Voice and Save Bill; Save Bill continues through the existing preview and invoice repository save path.
- Purchase billing continues to use the party selector and supplier-bill attachment/OCR workflow; the pinned customer-name header is sales-only.

## Preserved constraints and behavior
- Sales GST remains invoice-level, applied at final bill stage; new sales bills default to 5%, while editing an existing bill retains its saved invoice GST rate.
- New sales settlement remains CREDIT by default; CASH behavior remains unchanged.
- DEV109 manual Save & New item workflow, saved sale item suggestions, continuous voice entry, item editing, invoice preview, invoice save/update, stock and ledger repository behavior are unchanged.
- No Room schema or database version change.
- App version advanced from 5.53.0 (553) to 5.54.0 (554).

## Validation
- Source-level structure checks performed for pinned customer-only header and single continuous scroll body.
- Existing historical verification scripts contain hard-coded version expectations for earlier development builds and are not valid as-is for DEV110; failures from those stale assertions are not treated as regressions.
- Gradle compilation and real-device visual/voice tests remain pending; this environment does not provide a verified Android build result.
