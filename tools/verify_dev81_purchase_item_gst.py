from pathlib import Path
root = Path(__file__).resolve().parents[1]
main = (root / 'app/src/main/java/in/bhajan/bfpbusinesspro/MainActivity.kt').read_text()
repo = (root / 'app/src/main/java/in/bhajan/bfpbusinesspro/data/InvoiceRepository.kt').read_text()
checks = {
    'purchase GST field is available while sale GST field is hidden per item': 'draftPurchaseGst.visibility = if (kind == "PURCHASE") View.VISIBLE else View.GONE' in main,
    'purchase line GST is restored when editing a row': 'draftPurchaseGst.setText(row.gst.text)'  in main,
    'purchase line GST appears in the item summary': 'GST: ${row.gst.text}%' in main,
    'purchase line GST goes into repository LineInput': 'InvoiceRepository.LineInput(itemName, quantity, kotlin.math.round(rateValue * 100).toLong(), gstValue,' in main,
    'repository calculates GST from each line GST rate': 'input.lines.map { GstLineInput(it.quantity, it.ratePaise, it.gstRatePercent) }' in repo,
    'purchase GST validation remains in repository': 'require(it.gstRatePercent in 0.0..100.0 && it.gstRatePercent.isFinite())' in repo,
}
for label, ok in checks.items(): print(f"{'PASS' if ok else 'FAIL'}: {label}")
assert all(checks.values())
print('Development 81 purchase item GST checks: PASS')
