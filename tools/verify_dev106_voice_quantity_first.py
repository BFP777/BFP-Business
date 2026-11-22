from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
source = (ROOT / "app/src/main/java/in/bhajan/bfpbusinesspro/MainActivity.kt").read_text()
gradle = (ROOT / "app/build.gradle.kts").read_text()

checks = {
    "leading spoken quantity is retained by parser": "val quantity = if (qEnd > 0) parseNumber(tokens.subList(0, qEnd)) else null" in source,
    "spoken quantity is applied, defaulting to one": 'val voiceQuantity = parsed.second ?: "1"' in source and 'draftQty.setText(voiceQuantity)' in source,
    "rate is sufficient for voice auto-add": "if (parsed.third != null)" in source,
    "continuous voice resumes after successful add": "if (added && continuousVoiceEntry)" in source and "startNextVoiceEntry?.invoke()" in source,
    "release version is 5.52.0 / 552": 'versionCode = 552' in gradle and 'versionName = "5.52.0"' in gradle,
}
for label, ok in checks.items():
    print(("PASS" if ok else "FAIL") + ": " + label)
if not all(checks.values()):
    raise SystemExit(1)
print(f"DEV106: {len(checks)}/{len(checks)} checks passed")
