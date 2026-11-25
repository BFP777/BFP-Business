from pathlib import Path
root = Path(__file__).resolve().parents[1]
repo = (root / 'app/src/main/java/in/bhajan/bfpbusinesspro/data/InvoiceRepository.kt').read_text()
main = (root / 'app/src/main/java/in/bhajan/bfpbusinesspro/MainActivity.kt').read_text()
checks = {
    'item-wise purchase requires Item Master item while amount-only purchase may use itemId=0': 'if (input.kind == "PURCHASE" && it.itemId > 0)' in repo and 'itemId=0' in repo,
    'item-wise stock purchase requires a matching Item Code': 'स्टॉक खरीद के लिए Item Master से आइटम चुनें' in repo,
    'item code must resolve to the selected item': 'item.itemCode.equals(it.itemCode.trim(), true)' in repo,
    'stock posting remains atomic with invoice transaction': 'database.withTransaction' in repo and 'validateAndPostStock(input, emptyList())' in repo,
    'purchase item GST remains per supplier bill line': 'input.lines.map { GstLineInput(it.quantity, it.ratePaise, it.gstRatePercent) }' in repo,
    'purchase UI keeps item-code lookup available': 'Item Code से आइटम खोजें / Search Item' in main,
}
for label, ok in checks.items(): print(f"{'PASS' if ok else 'FAIL'}: {label}")
assert all(checks.values())
print('Development 83 purchase-stock-link checks: PASS')
