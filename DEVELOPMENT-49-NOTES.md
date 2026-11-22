# Development 49 — Supplier Bill Line Draft Extraction

- Added a conservative parser for common OCR invoice rows in the form Description, Quantity, Rate, Amount.
- Parser rejects ambiguous rows using numeric bounds and a quantity × rate versus amount consistency check; repeated OCR rows are deduplicated.
- OCR review dialog now previews candidate item lines and lets the user explicitly add them to the current purchase form as editable draft rows.
- Draft rows attempt to match an existing Item Master name. Matched rows inherit Item Master code, GST rate and HSN; OCR rate and quantity are retained. Unmatched rows remain free-text and do not affect stock until the user links the Item Code manually.
- The feature does not save the purchase, post stock, or create payments. The user must review item, quantity, rate, GST, supplier and total and press the existing Save button.
- This is a best-effort parser for common Latin-script row layouts, not a universal invoice understanding model. Different column orders, handwritten text, multi-line descriptions and OCR errors may be missed or misread.
- Added parser unit tests for normal rows, ambiguous row rejection and duplicate removal.
- Version advanced to 505 / 5.5.0. No database schema change.

## Verification scope

- Added source-level checks to the offline verification suite.
- Android Gradle compile, JUnit execution and on-device OCR validation remain required; they are not claimed as completed here.
