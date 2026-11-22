# Development 77 — Voice quantity, item and rate parsing

- Continuous voice billing now parses a spoken leading quantity, item name, and trailing rate into three separate invoice draft fields.
- Examples: “पाँच सूट पाँच सौ रुपये” → quantity 5, item “सूट”, rate ₹500; “छह सूट आठ सौ रुपये” → 6 / सूट / ₹800; “seven suits nine hundred rupees” → 7 / suits / ₹900.
- Hindi and English quantity words (1–20) and numeric quantities are supported. If no leading quantity is recognized, the existing default quantity remains unchanged.
- Voice recognition remains draft-only; the user must review and explicitly save the invoice.
- Version: 5.23.0 (versionCode 523).
- Verification: offline source checks only; Android Gradle build/device test not run in this environment.
