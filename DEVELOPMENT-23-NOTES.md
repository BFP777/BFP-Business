# Development 23 — Manual State / Place-of-Supply Selection

## Implemented
- Added a state selector to the invoice form for SALE and PURCHASE.
- SALE uses the business profile state as seller state and the selected customer/place-of-supply state as buyer state.
- PURCHASE uses the selected supplier state as seller state and the business profile state as buyer state.
- GSTIN state code is used as the initial suggestion when available; the user can explicitly choose another state for unregistered or special cases.
- Persisted seller and buyer state codes on each invoice so later edits and reprints retain the tax classification.
- Added Room migration 2 -> 3 for the two invoice state-code columns; migration 1 -> 2 remains in the chain.
- Invoice print output now displays the saved Place of Supply / Supplier State name.

## Validation performed
- `GstCalculator.kt` standalone Kotlin compilation succeeded.
- Source inspection confirms invoice create and update persist both state codes.
- Source inspection confirms invoice form sends the selected state into the GST calculator input.
- Migration SQL adds non-null state-code columns with a safe default for existing rows.
- ZIP integrity and Android Manifest XML validation completed.
- Kotlin compiler scan found no parser diagnostics (`expecting`, `unexpected tokens`, or syntax errors) in the source set.

## Not verified
- A full Gradle/Android build and device test were not run: this environment has Java/Kotlin compiler but no Gradle executable/wrapper and no Android SDK configured.
- State/place-of-supply selection is a manual operational input. The business/accountant must confirm the legally applicable place of supply for each transaction before issuing a tax invoice.
