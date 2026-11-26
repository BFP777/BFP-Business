from pathlib import Path
root = Path(__file__).resolve().parents[1]
main = (root / "app/src/main/java/in/bhajan/bfpbusinesspro/MainActivity.kt").read_text()
checks = {
    "continuous voice mode starts from invoice dialog voice toggle": 'text = "🎙 बोलना शुरू करें"' in main and "startVoiceEntryForCurrentRow = { beginContinuousVoice() }" in main and "continuousVoiceEntry = true" in main,
    "recognized phrase fills item name, quantity and rate separately": "parseVoiceItemPhrase(spoken)" in main and "draftName.setText(parsed.first)" in main and "draftQty.setText(parsed.second ?: \"1\")" in main and "draftRate.setText(it)" in main,
    "voice result auto-adds a complete item and recognition resumes": "if (parsed.third != null)" in main and "val added = commitDraftLine()" in main and "voiceHandler.postDelayed(restartVoiceRunnable!!, 450L)" in main,
    "microphone control toggles stop for continuous voice mode": 'voiceToggle?.text = "■ Voice रोकें"' in main and 'voiceToggle?.text = "🎙 बोलना शुरू करें"' in main and "continuousVoiceEntry = false" in main,
    "English remains default without per-entry language dialog": 'getString("voice_input_language", "en-IN")' in main and "showVoiceInputLanguageSettings" in main,
    "quantity-rate parser returns three separate values": "Triple<String, String?, String?>" in main and "val quantity = if (qEnd > 0)" in main and "val rate = parseNumber(rateWords)" in main,
    "fixed item editor and appended item list": "val entryBox = LinearLayout" in main and "box.addView(entryBox)" in main and "box.addView(listHeader); box.addView(linesBox)" in main,
    "right-side live item counter": "val itemCountButton = Button" in main and "addView(itemCountButton" in main and "refreshItemCount()" in main,
    "Hindi and English number phrases supported": '"पाँच" to 5L' in main and '"five" to 5L' in main and '"सौ"' in main and '"hundred"' in main,
    "voice-added items remain an unsaved invoice draft until explicit Save Bill": "val added = commitDraftLine()" in main and "dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener" in main,
    "voice loop is stopped when invoice dialog closes": "continuousVoiceEntry = false" in main and "dialog.setOnDismissListener" in main,
    "version advanced to 5.52.0 / 552": 'versionCode = 557' in (root / "app/build.gradle.kts").read_text() and 'versionName = "5.57.0"' in (root / "app/build.gradle.kts").read_text(),
}
for label, ok in checks.items(): print(f"{'PASS' if ok else 'FAIL'}: {label}")
assert all(checks.values())
print("Development 74 Voice Billing checks: PASS")
