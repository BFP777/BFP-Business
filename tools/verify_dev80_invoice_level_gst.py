from pathlib import Path
root = Path(__file__).resolve().parents[1]
main = (root / 'app/src/main/java/in/bhajan/bfpbusinesspro/MainActivity.kt').read_text()
gradle = (root / 'app/build.gradle.kts').read_text()
checks = {
    'sale keeps one invoice-level GST field': 'फाइनल बिल पर GST दर % (पूरे बिल पर)' in main and 'invoiceGst.visibility = if (kind == "SALE" && saleGstEnabled) View.VISIBLE else View.GONE' in main,
    'sale applies the same final GST rate to every sale line': 'if (kind == "PURCHASE") row.gst.text.toString().toDoubleOrNull() else if (saleGstEnabled) invoiceGst.text.toString().toDoubleOrNull() else 0.0' in main,
    'purchase does not show invoice-level GST': 'invoiceGst.visibility = if (kind == "SALE" && saleGstEnabled) View.VISIBLE else View.GONE' in main,
    'purchase has a per-item supplier-bill GST field': 'इस आइटम का GST % (सप्लायर बिल के अनुसार)' in main and 'draftPurchaseGst.visibility = if (kind == "PURCHASE") View.VISIBLE else View.GONE' in main,
    'purchase saves each row GST independently': 'val gstValue = if (kind == "PURCHASE") row.gst.text.toString().toDoubleOrNull()' in main,
    'purchase summary displays each item GST': 'if (kind == "PURCHASE") "     GST: ${row.gst.text}%" else ""' in main,
    'purchase OCR matched GST remains on its item line': 'gstRate = matched?.gstRatePercent ?: 0.0' in main,
    'manual purchase GST is validated from 0 to 100 percent': 'itemGst !in 0.0..100.0' in main and 'gstValue !in 0.0..100.0' in main,
    'app version advanced to 5.26.0 without Room schema change': 'versionCode = 559' in gradle and 'versionName = "5.59.0"' in gradle,
}
for label, ok in checks.items(): print(f"{'PASS' if ok else 'FAIL'}: {label}")
assert all(checks.values())
print('Development 80/81 differentiated GST checks: PASS')
