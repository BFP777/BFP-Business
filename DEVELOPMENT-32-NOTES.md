# Development 32 — GSTIN Validation and Invoice Print Audit

## Changes
- Party add/edit now trims and uppercases GSTIN and validates its 15-character format when provided. Blank GSTIN remains allowed for unregistered parties.
- GST settings help text now accurately describes the existing invoice integration: item GST rate and selected place/supplier state drive tax calculation; print output includes HSN and GST-rate-wise CGST/SGST/IGST summary.
- Added GSTIN format tests for a valid Himachal GSTIN, blank optional values, malformed length/state prefix, and lowercase normalization.

## Source audit findings
- Invoice creation and edit both use the shared GST calculator and persist line-level taxable value, HSN, GST rate, CGST, SGST, and IGST.
- Invoice print output reads the saved invoice lines and totals, escapes party/item text for HTML, and opens Android's system print flow for PDF/print output.
- The selected state is used as Place of Supply for SALE and Supplier State for PURCHASE.

## Important limitation
GSTIN validation checks the format only; it does not verify GST portal registration status or checksum validity. The app's print layout and tax treatment still require CA/GST-practitioner review before being treated as a compliant tax invoice. No Android Gradle build, print-preview device test, or APK build was possible because this environment has no Gradle wrapper/runtime or Android SDK.

## Verification
- GST calculator JVM smoke checks: pending execution in this step.
- Source and ZIP integrity checks: run before packaging.
