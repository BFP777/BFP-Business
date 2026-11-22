# Development 74 – Continuous Voice Billing Entry

- Added a continuous voice-entry mode on invoice item rows.
- Each recognized phrase is split into an item name and an optional trailing rate; both fields remain editable.
- Supports common Hindi and English number words, including “सूट पाँच सौ रुपये” and “Suit five hundred rupees”, plus spoken numeric rates.
- After each recognition, a fresh blank invoice line is added and listening starts again while continuous mode remains active.
- A Stop Voice Entry control is provided; cancelling speech recognition also ends continuous mode.
- English (`en-IN`) remains the default voice locale; Hindi remains available through the existing persistent Utilities setting. No per-entry language prompt is introduced.
- Voice recognition only populates the editable draft. It does not save invoices, create Item Master records, or post stock/cash/ledger entries.
- Existing CREDIT-default sales flow, CASH settlement posting, purchase flow, and invoice save validation are unchanged.

## Verification scope

- Source-level voice-billing checks added and included in the offline verifier.
- Android Gradle/KSP compilation, APK build, and physical-device continuous speech testing must still be run through GitHub Actions / an Android device.
