from pathlib import Path
root = Path(__file__).resolve().parents[1]
ledger = (root / 'app/src/main/java/in/bhajan/bfpbusinesspro/data/LedgerRepository.kt').read_text()
main = (root / 'app/src/main/java/in/bhajan/bfpbusinesspro/MainActivity.kt').read_text()
gradle = (root / 'app/build.gradle.kts').read_text()
db = (root / 'app/src/main/java/in/bhajan/bfpbusinesspro/data/BfpDatabase.kt').read_text()
checks = {
    'read-only cross-module financial audit': 'suspend fun auditBusinessFinancials()' in ledger and 'data class BusinessFinancialAudit' in ledger,
    'sale and purchase remain separate': 'observeInvoices("SALE")' in ledger and 'observeInvoices("PURCHASE")' in ledger,
    'invoice payment linkage and paid amount reconciliation': 'linkedTotal != invoice.paidPaise' in ledger and 'payment.direction != expectedDirection' in ledger,
    'party, bank, salary and employee references audited': all(x in ledger for x in ['पार्टी रिकॉर्ड नहीं मिला', 'मान्य बैंक खाता लिंक नहीं', 'वेतन माह/कर्मचारी लिंक गलत है']),
    'stock movement total reconciled against current stock': 'movementNet - item.quantity' in ledger,
    'financial audit accessible from reports': 'Complete Business Financial Audit / समग्र वित्तीय ऑडिट' in main and 'showBusinessFinancialAudit()' in main,
    'report explicitly read-only and states scope limits': 'कोई स्वतः बदलाव नहीं करता' in main and 'वास्तविक बैंक स्टेटमेंट सत्यापन' in main,
    'version 5.43.0 / 538': 'versionCode = 559' in gradle and 'versionName = "5.59.0"' in gradle,
    'Room schema unchanged at 10': 'version = 10' in db,
}
for name, ok in checks.items(): print(('PASS' if ok else 'FAIL') + ': ' + name)
assert all(checks.values())
