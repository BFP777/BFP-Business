# DEVELOPMENT 111 — Voice Quantity/Rate Display Fix

Base: DEV110 Single-Screen Billing source.

## Changes
- Kept DEV110's approved single continuous billing screen and pinned customer header.
- Invoice item rows now display item name, quantity, rate, and line amount on separate readable lines rather than compressing quantity and rate into one long line.
- Applied the same display format when an existing draft line is edited.
- Voice-add confirmation now reports the recognized item name, quantity, and rate for immediate checking.
- Preserved the existing quantity-first voice parser and invoice persistence path; no Room schema/database change.
- Bumped app version to 5.55.0 (versionCode 555).

## Expected voice examples
- “पाँच सूट पाँच सौ रुपये” → item सूट, quantity 5, rate ₹500.
- “सूट पाँच सौ रुपये” → item सूट, quantity 1, rate ₹500.
- “five suits five hundred rupees” → item suits, quantity 5, rate ₹500 (subject to Android speech recognition transcript).

## Validation limits
- Source-level checks are provided in `tools_verify_dev111_billing.py`.
- Android Gradle compilation and real-device microphone tests still require GitHub Actions / an Android device. This archive does not include `gradlew`; do not treat the ZIP as a tested APK.
