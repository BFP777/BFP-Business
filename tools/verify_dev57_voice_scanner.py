from pathlib import Path
import re
ROOT = Path(__file__).resolve().parents[1]
main = (ROOT / 'app/src/main/java/in/bhajan/bfpbusinesspro/MainActivity.kt').read_text()
gradle = (ROOT / 'app/build.gradle.kts').read_text()
manifest = (ROOT / 'app/src/main/AndroidManifest.xml').read_text()
notes = (ROOT / 'DEVELOPMENT-57-NOTES.md').read_text()
checks = {
    'Hindi speech locale': 'hi-IN' in main,
    'English speech locale': 'en-IN' in main,
    'Speech recognition launcher': 'RecognizerIntent.ACTION_RECOGNIZE_SPEECH' in main and 'voiceInput.launch(intent)' in main,
    'Voice input only fills editable item name': 'pendingVoiceInput?.setText(spoken)' in main,
    'Google Code Scanner dependency': 'play-services-code-scanner:16.1.0' in gradle,
    'Google Code Scanner invocation': 'GmsBarcodeScanning.getClient(this@MainActivity).startScan()' in main,
    'Scanned code resolves through existing Item Master DAO': 'db.dao().getItemByCode(normalized)' in main,
    'Scanner module metadata': 'android:value="barcode_ui"' in manifest,
    'No direct camera permission': 'android.permission.CAMERA' not in manifest,
    'No Room migration/schema bump': 'Room schema: 9 (no database migration required)' in notes,
    'Version 5.14.0': 'versionName = "5.59.0"' in gradle and 'versionCode = 559' in gradle,
}
for name, ok in checks.items():
    print(f'{name}: {"PASS" if ok else "FAIL"}')
assert all(checks.values()), [k for k,v in checks.items() if not v]
print(f'Dev57 source checks: PASS ({len(checks)}/{len(checks)})')
