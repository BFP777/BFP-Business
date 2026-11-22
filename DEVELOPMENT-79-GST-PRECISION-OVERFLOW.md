# Development 79 — GST Precision and Overflow Safety

## Changes
- Replaced floating-point line-amount multiplication with `BigDecimal` multiplication and `HALF_UP` paise rounding.
- Exact `Long` conversion rejects an individual line amount outside the supported range instead of silently saturating.
- Invoice subtotal, taxable amount, CGST, SGST, IGST, and final total now use checked addition; overflow returns a clear validation error and prevents an invalid bill total.
- Added JVM tests for fractional quantity rounding, single-line overflow, and multi-line subtotal overflow.
- Advanced app version to 5.24.0 / versionCode 524. Room schema remains unchanged.

## Verification
- `tools/verify_dev79_gst_overflow.py`: PASS.
- `tools/verify_all.py`: PASS.
- Android Gradle/JVM test execution was not performed in this environment; the new JUnit tests are included for CI execution.
