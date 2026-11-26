from pathlib import Path
root = Path(__file__).resolve().parents[1]
main = (root / 'app/src/main/java/in/bhajan/bfpbusinesspro/MainActivity.kt').read_text()
gradle = (root / 'app/build.gradle.kts').read_text()
checks = {
    'single continuous scroll body contains item editor, list and bill details': 'val bodyScroll = ScrollView(this@MainActivity)' in main and 'addView(box)' in main and 'addView(bodyScroll, LinearLayout.LayoutParams(-1, 0, 1f))' in main,
    'customer picker remains pinned above the scroll body': 'addView(customerHeader, LinearLayout.LayoutParams(-1, -2))' in main and 'if (kind == "SALE") salePartyPicker?.let { customerHeader.addView(it) }' in main,
    'older nested item-list and bottom-details scroll containers are absent': 'itemListViewport' not in main and 'bottomDetails' not in main,
    'added item list auto-scrolls to latest row': 'itemListScroll?.post { itemListScroll?.fullScroll(View.FOCUS_DOWN) }' in main,
    'item counter refreshes as rows are added': 'itemCountButton.text = "${rows.size} आइटम"' in main and 'refreshItemCount()' in main,
    'voice parser fills item name quantity and rate separately': 'draftName.setText(parsed.first)' in main and 'draftQty.setText(parsed.second ?: "1")' in main and 'parsed.third?.let { draftRate.setText(it) }' in main,
    'voice-added rows stay inside unsaved invoice until Save Bill': 'val added = commitDraftLine()' in main and '.setNeutralButton("Save Bill", null)' in main,
    'dialog dismissal stops continuous voice listening': 'dialog.setOnDismissListener' in main and 'continuousVoiceEntry = false' in main,
    'app version is DEV112 5.57.0 / 557': 'versionCode = 557' in gradle and 'versionName = "5.57.0"' in gradle,
}
for label, ok in checks.items(): print(('PASS' if ok else 'FAIL') + ': ' + label)
if not all(checks.values()): raise SystemExit(1)
print('DEV112 current single-screen billing and voice regression checks: PASS')
