from pathlib import Path
root = Path(__file__).resolve().parents[1]
repo = (root / 'app/src/main/java/in/bhajan/bfpbusinesspro/data/InvoiceRepository.kt').read_text()
dao = (root / 'app/src/main/java/in/bhajan/bfpbusinesspro/data/AppDao.kt').read_text()
ui = (root / 'app/src/main/java/in/bhajan/bfpbusinesspro/MainActivity.kt').read_text()
gradle = (root / 'app/build.gradle.kts').read_text()
checks = {
    'whole-bill delete removes linked payments in same transaction': 'dao.deleteInvoicePayments(invoiceId)' in repo and 'suspend fun delete(invoiceId: Long) = database.withTransaction' in repo,
    'linked-payment deletion is scoped to the selected invoice': 'DELETE FROM payments WHERE invoiceId = :invoiceId' in dao,
    'delete reverses stock before removing invoice lines': repo.index('reverseStock(invoice.kind, lines, invoice.invoiceNumber, invoice.dateEpochMillis)') < repo.index('dao.deleteInvoiceLines(invoiceId)', repo.index('suspend fun delete(invoiceId: Long)')),
    'confirmation explains ledger and stock effects': 'संबंधित कैश/बैंक और पार्टी लेजर एंट्री भी हटेगी' in ui,
    'unlinked payments are explicitly preserved': 'Unlinked party receipts/payments are deliberately untouched.' in repo,
    'app version is DEV112 5.57.0 / 557': 'versionCode = 557' in gradle and 'versionName = "5.57.0"' in gradle,
}
for label, passed in checks.items():
    print(('PASS' if passed else 'FAIL') + ': ' + label)
if not all(checks.values()):
    raise SystemExit(1)
