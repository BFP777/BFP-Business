from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
entities = (ROOT / "app/src/main/java/in/bhajan/bfpbusinesspro/data/Entities.kt").read_text()
dao = (ROOT / "app/src/main/java/in/bhajan/bfpbusinesspro/data/AppDao.kt").read_text()
ui = (ROOT / "app/src/main/java/in/bhajan/bfpbusinesspro/MainActivity.kt").read_text()
gradle = (ROOT / "app/build.gradle.kts").read_text()
checks = {
    "separate output and input GST totals": all(x in entities for x in ["salesCgstPaise", "salesSgstPaise", "salesIgstPaise", "purchaseCgstPaise", "purchaseSgstPaise", "purchaseIgstPaise"]),
    "net GST estimate derived from books": "outputTaxPaise - inputTaxPaise" in entities,
    "date-scoped reconciliation query": "getGstReconciliationSummary(startEpochMillis: Long, endEpochMillis: Long)" in dao,
    "supplier-wise potential ITC query": "getSupplierItcReport(startEpochMillis: Long, endEpochMillis: Long)" in dao and "GROUP BY p.id,p.name,p.gstNumber" in dao,
    "sales and purchase kinds remain separated": "i.kind='SALE'" in dao and "i.kind='PURCHASE'" in dao,
    "UI labels ITC as potential and warns no GSTR-2B matching": "संभावित ITC" in ui and "GSTR-2B/सरकारी पोर्टल से मिलान नहीं करता" in ui,
    "version 5.43.0 / 535": 'versionCode = 565' in gradle and 'versionName = "5.65.0"' in gradle,
}
for label, ok in checks.items():
    print(("PASS" if ok else "FAIL") + ": " + label)
assert all(checks.values())
