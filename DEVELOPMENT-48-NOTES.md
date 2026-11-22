# Development 48 — Supplier Bill OCR Draft Reader

- Added on-device ML Kit text recognition for supplier bill attachments (image files and the first page of PDFs).
- Added a purchase-form action to read the attached supplier bill and show a review-only OCR draft preview.
- The preview highlights candidate invoice number, GSTIN and total amount, and displays editable extracted text.
- The user may explicitly append reviewed OCR text to the purchase note. The OCR feature does not create invoice lines, alter amounts, post stock, or save a purchase by itself.
- OCR is best-effort and Latin-script oriented; unclear photos, handwritten bills, multi-page PDFs and varied invoice layouts may need manual checking. PDF OCR currently reads only page 1.
- No database schema change; existing database and attachment backup format remain unchanged.
- Version advanced to 504 / 5.4.0.
- Added source-level regression checks to the offline verification suite.
- Android Gradle build/device testing is still required; the current environment has no Gradle executable.

## Verification scope

- `python tools/verify_all.py` passed all bundled offline/source-level checks, including 44 Room DAO SQL checks and the new Dev 48 OCR wiring checks.
- These checks do not replace an Android Gradle compile, ML Kit dependency resolution, or on-device OCR testing.
