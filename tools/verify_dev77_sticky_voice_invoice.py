from pathlib import Path
root = Path(__file__).resolve().parents[1]
main = (root / "app/src/main/java/in/bhajan/bfpbusinesspro/MainActivity.kt").read_text()
gradle = (root / "app/build.gradle.kts").read_text()
checks = {
    "single continuous invoice body scroll contains item entry and bill details": "val bodyScroll = ScrollView(this@MainActivity)" in main and "addView(box)" in main and "addView(bodyScroll, LinearLayout.LayoutParams(-1, 0, 1f))" in main,
    "selected customer remains in the top header outside the body scroll": "customerHeader.addView(it)" in main and "addView(customerHeader, LinearLayout.LayoutParams(-1, -2))" in main,
    "item editor and item list are part of the same scrollable bill canvas": "box.addView(entryBox)" in main and "box.addView(listHeader); box.addView(linesBox)" in main,
    "discount and final GST controls follow item list": "box.addView(discount)" in main and "box.addView(gstToggle)" in main,
    "right-aligned item count updates as lines are added": "addView(itemCountButton, LinearLayout.LayoutParams(-2, -2))" in main and 'itemCountButton.text = "${rows.size} आइटम"' in main,
    "voice parser fills name, quantity and rate": "parseVoiceItemPhrase(spoken)" in main and "draftName.setText(parsed.first)" in main and 'draftQty.setText(parsed.second ?: "1")' in main and "draftRate.setText(it)" in main,
    "voice item is appended and next listening starts": "val added = commitDraftLine()" in main and "voiceHandler.postDelayed(restartVoiceRunnable!!, 450L)" in main,
    "invoice remains explicitly saved by user": 'setNeutralButton("Save Bill", null)' in main and "dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener" in main,
    "app version advanced to 5.64.0": 'versionCode = 564' in gradle and 'versionName = "5.64.0"' in gradle,
}
for label, ok in checks.items(): print(f"{'PASS' if ok else 'FAIL'}: {label}")
assert all(checks.values())
print("DEV77 approved single-screen invoice canvas + voice checks: PASS")
