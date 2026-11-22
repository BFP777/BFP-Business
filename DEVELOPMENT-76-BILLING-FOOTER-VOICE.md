# Development 76 — Invoice footer voice control

- Moved the per-row continuous voice controls out of each item row.
- Invoice dialog actions now place Cancel on the left, Save in the middle, and the microphone action at the right (the previous Save position).
- The microphone starts continuous voice entry; while listening, it changes to a stop control. Pressing it again stops continuous mode.
- Recognized speech continues to populate editable draft item name/rate fields and automatically creates/listens for the next row while continuous mode remains active.
- Save remains an explicit user action; voice recognition does not save an invoice.
- Updated the voice-billing source verifier to assert the new dialog-footer control arrangement.

Verification:
- `python tools/verify_dev74_voice_billing.py` — PASS (9 checks)
- `python tools/verify_all.py` — PASS (full offline source suite)
- Android Gradle build not run: this source archive does not include `gradlew`, and Gradle is not installed in this environment.
