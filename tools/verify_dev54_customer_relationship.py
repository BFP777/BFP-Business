from pathlib import Path
root = Path(__file__).resolve().parents[1]
main = (root / 'app/src/main/java/in/bhajan/bfpbusinesspro/MainActivity.kt').read_text()
gradle = (root / 'app/build.gradle.kts').read_text()
notes = (root / 'DEVELOPMENT-54-NOTES.md').read_text()
checks = {
    'explicit WhatsApp draft button': 'WhatsApp संदेश तैयार करें' in main,
    'WhatsApp click-to-chat URL': 'https://wa.me/$digits?text=${Uri.encode(message)}' in main,
    'no automatic send API': 'Intent.ACTION_SEND' not in main and 'sendTextMessage(' not in main,
    'Indian 10-digit normalization': 'if (digits.length == 10) digits = "91$digits"' in main,
    'invalid phone number guard': 'digits.length !in 11..15' in main,
    'customer-specific design personalization': 'preferred?.let' in main and 'it.itemName' in main,
    'version advanced': 'versionCode = 551' in gradle and 'versionName = "5.51.0"' in gradle,
    'database schema unchanged': 'Room schema: 9 (no database migration required)' in notes,
    'manual send clearly documented': 'user reviews and manually sends it' in notes,
}
for label, ok in checks.items():
    print(f'{label}: {"PASS" if ok else "FAIL"}')
if not all(checks.values()):
    raise SystemExit(1)
print('Development 54 customer relationship checks: PASS')
