# Development 21 — GST Settings and Item GST Fields

## Implemented in this step
- Added a Dashboard entry for GST / business settings.
- Added a settings screen backed by the existing `business_profile` Room table.
- Business profile fields can be saved: business name, address, mobile, GSTIN, state code, and invoice prefix.
- GSTIN format is validated using `GstCalculator.validateGstin`; when GSTIN is supplied, its first two digits must match the configured state code.
- Item add/edit screen now captures optional HSN code and GST rate (0–100%). Item list displays these values.
- Added JVM unit test cases for intra-state CGST/SGST split, inter-state IGST, and GSTIN format.

## Important scope boundary
- Invoice entry still uses the existing manually entered GST/Tax amount. This step does not yet automatically calculate tax per invoice line or print a complete GST tax invoice.
- Existing Room database schema is unchanged; the new UI uses fields and table already present in Development 20.

## Validation
- `GstCalculator.kt` compiled with the installed Kotlin compiler and a standalone calculation check was run.
- Source ZIP integrity was checked after packaging.
- Full Android/Gradle build and on-device test were not run: this environment has Java but no Gradle executable, Gradle wrapper, or Android SDK configured.
