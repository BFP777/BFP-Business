# DEVELOPMENT 109 — Manual Item Save & New

Base: DEV108 Continuous Voice + Manual Billing + Preview + Saved Sales source.

## Change
- Added a **Save & New / सेव और नया** action to the new-item form.
- Saving a new item through this action inserts the item and its opening stock using the existing `StockRepository.insertItemWithOpeningStock` path.
- After a successful save, the form remains open and clears the fields for the next manual item.
- The regular **Save / सेव** action continues to save and close the form.
- Existing-item editing and its stock-adjustment/history action remain unchanged.
- No database schema or version change was made.

## Validation status
- Source archive integrity and Kotlin source patch reviewed.
- Gradle compilation could not be run in this environment because the archive does not contain `gradlew` and system Gradle is unavailable. Run the existing GitHub Actions Android build before installing the APK.
