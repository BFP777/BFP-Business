from pathlib import Path
root = Path(__file__).resolve().parents[1]
main = (root / 'app/src/main/java/in/bhajan/bfpbusinesspro/MainActivity.kt').read_text()
repo = (root / 'app/src/main/java/in/bhajan/bfpbusinesspro/data/InvoiceRepository.kt').read_text()
gradle = (root / 'app/build.gradle.kts').read_text()
checks = {
    'stock summary displays total design count': 'कुल डिजाइन: ${items.size}' in main,
    'stock summary totals quantities': 'items.sumOf { it.quantity }' in main,
    'stock summary counts low-stock designs': 'items.count { it.quantity <= it.minimumStockQuantity }' in main,
    'stock valuation uses purchase rates and is labelled estimate': 'it.quantity * it.purchaseRatePaise.toDouble()' in main and 'मूल्य अनुमानित है' in main,
    'invoice edit reverses and reposts stock atomically': 'validateAndPostStock(replacement, oldLinesForStock)' in repo and 'database.withTransaction' in repo,
    'invoice delete reverses stock atomically': 'reverseStock(invoice.kind, lines, invoice.invoiceNumber, invoice.dateEpochMillis)' in repo and 'database.withTransaction' in repo,
    'version is 5.31.0 / 529': 'versionCode = 561' in gradle and 'versionName = "5.61.0"' in gradle,
}
for label, ok in checks.items(): print(f"{'PASS' if ok else 'FAIL'}: {label}")
assert all(checks.values())
print('Development 84 stock-summary checks: PASS')
