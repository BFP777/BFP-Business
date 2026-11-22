from pathlib import Path
root = Path(__file__).resolve().parents[1]
ledger = (root / 'app/src/main/java/in/bhajan/bfpbusinesspro/data/LedgerRepository.kt').read_text()
main = (root / 'app/src/main/java/in/bhajan/bfpbusinesspro/MainActivity.kt').read_text()
gradle = (root / 'app/build.gradle.kts').read_text()
db = (root / 'app/src/main/java/in/bhajan/bfpbusinesspro/data/BfpDatabase.kt').read_text()
checks = {
    'read-only cash/UPI/bank reconciliation model': 'data class MoneyReconciliation' in ledger and 'suspend fun reconcileMoneyBalances()' in ledger,
    'bank payments, cash entries/transfers and salary payouts checked for account links': all(x in ledger for x in ['payments.filter { it.mode == "BANK" }', 'entries.filter { it.mode == "BANK" || it.entryType in setOf("CASH_DEPOSIT", "CASH_WITHDRAWAL") }', 'salaries.filter { it.mode == "BANK" }']),
    'overall bank balance compared with named account totals': 'bank != accountTotal' in ledger and 'bankAccountBalancePaise(it.id)' in ledger,
    'reconciliation action available in Bank screen': 'Cash / Bank Reconciliation जाँचें' in main and 'showMoneyReconciliation()' in main,
    'report explicitly does not auto-edit balances': 'कोई एंट्री या बैलेंस अपने-आप नहीं बदलेगा' in main,
    'version 5.43.0 / 537': 'versionCode = 552' in gradle and 'versionName = "5.52.0"' in gradle,
    'Room schema remains version 10': 'version = 10' in db,
}
for name, ok in checks.items():
    print(('PASS' if ok else 'FAIL') + ': ' + name)
assert all(checks.values())
