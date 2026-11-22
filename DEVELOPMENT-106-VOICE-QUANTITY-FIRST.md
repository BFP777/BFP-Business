# Development 106 — Voice Billing Quantity-First Entry

- Voice item phrases now support quantity-first ordering, such as “पाँच सूट पाँच सौ रुपये”, “सात सूट आठ सौ रुपये”, and “छह सूट एक हजार रुपये”.
- The existing parser extracts the leading quantity, item name, and trailing rate.
- If quantity is omitted, voice entry defaults to quantity 1 and can still auto-add when a rate is recognized.
- Continuous voice entry resumes listening after a successful add.
- App version advanced to 5.51.0 (versionCode 551); Room schema unchanged.
- Source-level regression check: `tools/verify_dev106_voice_quantity_first.py`.
- Android compilation, APK signing verification, and device-level voice recognition tests remain to be run in Android CI/device environment.
