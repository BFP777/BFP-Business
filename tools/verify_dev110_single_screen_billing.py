from pathlib import Path

root = Path(__file__).resolve().parents[1]
main = (root / 'app/src/main/java/in/bhajan/bfpbusinesspro/MainActivity.kt').read_text(encoding='utf-8')
gradle = (root / 'app/build.gradle.kts').read_text(encoding='utf-8')
checks = {
    'selected customer header shows name only': 'selectedInfo.text = customer.name' in main and 'selectedInfo.text = if (addressLine' not in main,
    'customer picker is pinned separately': 'if (kind == "SALE") salePartyPicker?.let { customerHeader.addView(it) }' in main,
    'one continuous scroll body contains invoice form': 'addView(bodyScroll, LinearLayout.LayoutParams(-1, 0, 1f))' in main and 'addView(box)' in main,
    'old independent item-list viewport removed': 'itemListViewport' not in main and 'bottomDetails' not in main,
    'Save Bill action label present': '.setNeutralButton("Save Bill", null)' in main,
    'app version advanced': 'versionCode = 557' in gradle and 'versionName = "5.57.0"' in gradle,
    'Room schema untouched': 'no room schema' in (root / 'DEVELOPMENT-110-SINGLE-SCREEN-BILLING-REDESIGN.md').read_text(encoding='utf-8').lower(),
}
for label, ok in checks.items():
    print(('PASS' if ok else 'FAIL') + ': ' + label)
if not all(checks.values()):
    raise SystemExit(1)
