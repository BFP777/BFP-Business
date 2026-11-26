from pathlib import Path
root = Path(__file__).resolve().parent
main = (root / "app/src/main/java/in/bhajan/bfpbusinesspro/MainActivity.kt").read_text()
gradle = (root / "app/build.gradle.kts").read_text()
checks = {
    "item row quantity and rate use separate lines": 'मात्रा (Qty): $q     रेट (Rate):' in main,
    "edited row uses separate quantity and rate lines": 'मात्रा (Qty): ${q}     रेट (Rate):' in main,
    "voice confirmation reports parsed quantity and rate": 'मात्रा ${parsed.second ?: "1"} | रेट ₹${parsed.third}' in main,
    "version code is 555": "versionCode = 557" in gradle,
    "version name is 5.55.0": 'versionName = "5.57.0"' in gradle,
}
for label, passed in checks.items(): print(("PASS" if passed else "FAIL") + ": " + label)
if not all(checks.values()): raise SystemExit(1)
