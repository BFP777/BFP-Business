# DEV133 – Hindi Voice Recognition Reliability Fix

- Billing speech recognition explicitly requests Hindi (hi-IN), independent of an old English dictation preference.
- Partial recognition text is surfaced in the voice status line for troubleshooting.
- Speech input silence windows and retry handling are tuned for continuous Hindi commands.
- Unknown catalogue matches are not auto-inserted as guessed item names; this avoids corrupt bill lines.
- Queue restart handling drains pending final transcripts without leaving them stranded.
- Version: 5.68.5 (versionCode 574).

Build and test on the target Android device before treating voice recognition as verified.
