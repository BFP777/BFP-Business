# DEV130 — Hindi Voice Billing Parser

- Voice input defaults to Hindi (hi-IN); English remains selectable in settings.
- Voice parser normalizes Devanagari digits to ASCII digits and accepts Hindi number-word variants such as पाँच/पांच and छह/छः/छे.
- Expected spoken order: quantity, item name, trailing rate. Example: “दो, सूट, पंद्रह सौ” → quantity 2, item “सूट”, rate ₹1,500.
- Existing continuous-entry behavior is preserved: a completed item is committed and the next spoken phrase fills a fresh row.
- App version: 5.69.0 (versionCode 569).
