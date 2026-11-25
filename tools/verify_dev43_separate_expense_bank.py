from pathlib import Path
p = Path(__file__).resolve().parents[1] / 'app/src/main/java/in/bhajan/bfpbusinesspro/MainActivity.kt'
s = p.read_text()
checks = {
    'Expense has its own screen': 'private fun showExpenses()' in s,
    'Bank has its own screen': 'private fun showBankManagement()' in s,
    'Menu routes Expenses separately': '"Expenses / खर्च" to { showExpenses() }' in s,
    'Menu routes Bank separately': '"Bank / बैंक" to { showBankManagement() }' in s,
    'Expense screen only lists EXPENSE rows': 'filter { it.entryType == "EXPENSE" }' in s,
    'Bank screen excludes EXPENSE rows': 'filter { it.entryType != "EXPENSE" }' in s,
    'Expense add button opens expense form': 'cashEntryDialog("EXPENSE")' in s,
    'Expense save returns to Expense screen': 'if (entryType == "EXPENSE") showExpenses() else showBankManagement()' in s,
    'Bank account management remains accessible': 'showBankAccounts()' in s,
}
for label, ok in checks.items():
    print(('PASS' if ok else 'FAIL') + ': ' + label)
assert all(checks.values())
