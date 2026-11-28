from pathlib import Path
root = Path(__file__).resolve().parents[1]
main = (root / 'app/src/main/java/in/bhajan/bfpbusinesspro/MainActivity.kt').read_text()
gradle = (root / 'app/build.gradle.kts').read_text()
checks = {
    'multi-item parser anchors on saved item names': 'parseVoiceItemPhrases(transcript, knownNames)' in main and 'matchNameAt' in main,
    'quantity and rate are applied as separate fields': 'draftQty.setText(parsed.second)' in main and 'draftRate.setText(parsed.third)' in main,
    'saved Item Master and saved sale suggestions are both searched': 'masterItems.map { it.name } + readSavedSaleItems().map { it.first }' in main,
    'duplicate final transcript protection exists': 'now - lastVoiceTranscriptAt < 2500L' in main,
    'speech results are queued for sequential processing': 'pendingVoiceTranscripts.addLast(spoken)' in main and 'while (pendingVoiceTranscripts.isNotEmpty())' in main,
    'unknown multi-item transcript is not collapsed to one line': 'if (parsedItems.isEmpty() && knownNames.isEmpty())' in main,
    'version is 5.68.4 / 573': 'versionCode = 573' in gradle and 'versionName = "5.68.4"' in gradle,
}
for label, ok in checks.items():
    print(('PASS' if ok else 'FAIL') + ': ' + label)
if not all(checks.values()):
    raise SystemExit(1)
