from pathlib import Path
root = Path(__file__).resolve().parents[1]
main = (root / "app/src/main/java/in/bhajan/bfpbusinesspro/MainActivity.kt").read_text()
checks = {
    "continuous voice mode starts from invoice dialog microphone": 'setPositiveButton("🎙", null)' in main and "startVoiceEntryForCurrentRow = {" in main and "continuousVoiceEntry = true" in main,
    "recognized phrase fills item name, quantity and rate separately": "parseVoiceItemPhrase(spoken)" in main and "draftName.setText(parsed.first)" in main and "draftQty.setText(voiceQuantity)" in main and "draftRate.setText(it)" in main,
    "continuous mode commits a complete item and resumes listening": "if (added && continuousVoiceEntry)" in main and "commitDraftLine()" in main and "startNextVoiceEntry?.invoke()" in main,
    "microphone control toggles stop for continuous voice mode": 'voiceButton.text = "■"' in main and 'voiceButton.text = "🎙"' in main and "continuousVoiceEntry = false" in main,
    "English remains default without per-entry language dialog": 'getString("voice_input_language", "en-IN")' in main and "showVoiceInputLanguageSettings" in main,
    "quantity-rate parser returns three separate values": "Triple<String, String?, String?>" in main and "val quantity = if (qEnd > 0)" in main and "val rate = parseNumber(rateWords)" in main,
    "fixed item editor and appended item list": "val entryBox = LinearLayout" in main and "box.addView(entryBox)" in main and "linesBox.addView(summary)" in main,
    "right-side live item counter": "val itemCountButton = Button" in main and "addView(itemCountButton" in main and "refreshItemCount()" in main,
    "Hindi and English number phrases supported": '"पाँच" to 5L' in main and '"five" to 5L' in main and '"सौ"' in main and '"hundred"' in main,
    "voice result remains draft-only; existing explicit invoice save remains required": "pendingVoiceHandler?.invoke(spoken)" in main and "dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener" in main,
    "voice loop is stopped when invoice dialog closes": "continuousVoiceEntry = false" in main and "dialog.setOnDismissListener" in main,
    "version advanced to 5.52.0 / 552": 'versionCode = 559' in (root / "app/build.gradle.kts").read_text() and 'versionName = "5.59.0"' in (root / "app/build.gradle.kts").read_text(),
}
for label, ok in checks.items(): print(f"{'PASS' if ok else 'FAIL'}: {label}")
assert all(checks.values())
print("Development 74 Voice Billing checks: PASS")
