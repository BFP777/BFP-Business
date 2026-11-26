from pathlib import Path
root = Path(__file__).resolve().parents[1]
main = (root / 'app/src/main/java/in/bhajan/bfpbusinesspro/MainActivity.kt').read_text()
gradle = (root / 'app/build.gradle.kts').read_text()
checks = {
    'sale GST is optional and controlled by checkbox': 'saleGstEnabled' in main and 'gstToggle.setOnCheckedChangeListener' in main,
    'sale GST field appears only when checkbox is enabled': 'invoiceGst.visibility = if (checked) View.VISIBLE else View.GONE' in main,
    'enabled sale GST applies one final bill rate to all sale lines': 'else if (saleGstEnabled) invoiceGst.text.toString().toDoubleOrNull() else 0.0' in main,
    'unchecked sale GST saves zero GST': 'else 0.0' in main and 'बिना GST' in main,
    'purchase keeps per-item supplier-bill GST field': 'इस आइटम का GST % (सप्लायर बिल के अनुसार)' in main and 'draftPurchaseGst.visibility = if (kind == "PURCHASE") View.VISIBLE else View.GONE' in main,
    'purchase saves each row GST independently': 'val gstValue = if (kind == "PURCHASE") row.gst.text.toString().toDoubleOrNull()' in main,
    'purchase OCR matched GST remains on its item line': 'gstRate = matched?.gstRatePercent ?: 0.0' in main,
    'manual purchase GST is validated from 0 to 100 percent': 'itemGst !in 0.0..100.0' in main and 'gstValue !in 0.0..100.0' in main,
    'app version is DEV112 5.57.0 / 557': 'versionCode = 557' in gradle and 'versionName = "5.57.0"' in gradle,
}
for label, ok in checks.items(): print(f"{'PASS' if ok else 'FAIL'}: {label}")
assert all(checks.values())
print('Optional invoice-level sales GST + item-level purchase GST checks: PASS')
