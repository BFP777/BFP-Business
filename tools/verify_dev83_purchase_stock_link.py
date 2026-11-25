from pathlib import Path
root = Path(__file__).resolve().parents[1]
repo = (root / 'app/src/main/java/in/bhajan/bfpbusinesspro/data/InvoiceRepository.kt').read_text()
main = (root / 'app/src/main/java/in/bhajan/bfpbusinesspro/MainActivity.kt').read_text()
checks = {
    'purchase lines must select a real Item Master item': 'if (input.kind == "PURCHASE")' in repo and 'require(it.itemId > 0 && it.itemCode.isNotBlank())' in repo,
    'purchase validation explains how to link stock item': 'खरीद में स्टॉक जोड़ने के लिए Item Master से Item Code द्वारा आइटम चुनें' in repo,
    'item code must resolve to the selected item': 'item.itemCode.equals(it.itemCode.trim(), true)' in repo,
    'stock posting remains atomic with invoice transaction': 'database.withTransaction' in repo and 'validateAndPostStock(input, emptyList())' in repo,
    'purchase item GST remains per supplier bill line': 'input.lines.map { GstLineInput(it.quantity, it.ratePaise, it.gstRatePercent) }' in repo,
    'purchase UI keeps item-code lookup available': 'Item Code से आइटम खोजें / Search Item' in main,
}
for label, ok in checks.items(): print(f"{'PASS' if ok else 'FAIL'}: {label}")
assert all(checks.values())
print('Development 83 purchase-stock-link checks: PASS')
