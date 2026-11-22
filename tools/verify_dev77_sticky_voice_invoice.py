from pathlib import Path
root = Path(__file__).resolve().parents[1]
main = (root / "app/src/main/java/in/bhajan/bfpbusinesspro/MainActivity.kt").read_text()
gradle = (root / "app/build.gradle.kts").read_text()
checks = {
    "fixed invoice editor is separated from added-item list": "val fixedHeader = LinearLayout" in main and "val itemListScroll = ScrollView" in main,
    "only added-item list receives its own bounded scroll area": "addView(itemListScroll, LinearLayout.LayoutParams(-1, dp(132)))" in main and "addView(linesBox)" in main,
    "item list is detached before moving into its own scroll view": main.index("box.removeView(linesBox)") < main.index("val itemListScroll") < main.index("addView(linesBox)", main.index("val itemListScroll")),
    "bill header and item entry stay outside item-list scroller": "fixedHeader.addView(child)" in main and "box.getChildAt(0) !== linesBox" in main,
    "remaining bill details stay in separate lower scroll area": "addView(ScrollView(this@MainActivity).apply { addView(bottomDetails) }" in main,
    "right-aligned item count updates as lines are added": "addView(itemCountButton, LinearLayout.LayoutParams(-2, -2))" in main and 'itemCountButton.text = "${rows.size} आइटम"' in main,
    "voice parser fills name, quantity and rate": "parseVoiceItemPhrase(spoken)" in main and "draftName.setText(parsed.first)" in main and "draftQty.setText(it)" in main and "draftRate.setText(it)" in main,
    "voice item is appended and next listening starts": "if (added && continuousVoiceEntry)" in main and "startNextVoiceEntry?.invoke()" in main,
    "invoice remains explicitly saved by user": 'setNeutralButton("सेव", null)' in main and "dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener" in main,
    "dialog is sized to keep fixed editor usable": "dialog.window?.setLayout(-1, (resources.displayMetrics.heightPixels * 0.92f).toInt())" in main,
    "app version advanced to 5.23.0": 'versionCode = 550' in gradle and 'versionName = "5.50.0"' in gradle,
}
for label, ok in checks.items(): print(f"{'PASS' if ok else 'FAIL'}: {label}")
assert all(checks.values())
print("Development 77 sticky invoice + voice checks: PASS")
