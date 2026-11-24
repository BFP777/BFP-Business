from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
source = (ROOT / "app/src/main/java/in/bhajan/bfpbusinesspro/MainActivity.kt").read_text()
gradle = (ROOT / "app/build.gradle.kts").read_text()
db = (ROOT / "app/src/main/java/in/bhajan/bfpbusinesspro/data/BfpDatabase.kt").read_text()
checks = {
    "selected customer header shows name and saved address only": 'selectedInfo.text = if (addressLine.isBlank()) customer.name else "${customer.name}\\n$addressLine"' in source and "चुना गया:" not in source,
    "customer search collapses after selection": "customerSearchPanel.visibility = View.GONE" in source and "customerSearchPanel.visibility = View.VISIBLE" in source,
    "customer can be changed after selection": 'text = "बदलें"' in source and "changeCustomer.setOnClickListener" in source,
    "mobile and outstanding are not rendered in selected header": 'मोबाइल:' not in source[source.find('fun chooseCustomer(customer: PartyEntity)'):source.find('fun addCustomerInline()')],
    "item list scrolls to newest row after add": "itemListScroll?.post { itemListScroll?.fullScroll(View.FOCUS_DOWN) }" in source,
    "item counter remains available": 'itemCountButton.text = "${rows.size} आइटम"' in source,
    "Room schema remains unchanged": "version = 10" in db,
    "app version is 5.52.0 / 552": 'versionCode = 561' in gradle and 'versionName = "5.61.0"' in gradle,
}
for label, ok in checks.items():
    print(("PASS" if ok else "FAIL") + ": " + label)
if not all(checks.values()):
    raise SystemExit(1)
print(f"DEV107: {len(checks)}/{len(checks)} checks passed")
