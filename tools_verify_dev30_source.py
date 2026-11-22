from pathlib import Path
root = Path(__file__).parent
ledger = (root / 'app/src/main/java/in/bhajan/bfpbusinesspro/data/LedgerRepository.kt').read_text()
main = (root / 'app/src/main/java/in/bhajan/bfpbusinesspro/MainActivity.kt').read_text()
checks = {
    'deposit label': 'Cash से Bank में जमा' in ledger and 'CASH → BANK' in ledger,
    'withdrawal label': 'Bank से Cash निकासी' in ledger and 'BANK → CASH' in ledger,
    'expense mode label': 'व्यापार खर्च (${e.mode})' in ledger,
    'amount overflow guard': 'paiseValue > Long.MAX_VALUE.toDouble()' in main,
    'minimum paisa guard': 'paise <= 0L' in main,
    'entry type allowlist': 'entryType !in setOf("OPENING", "EXPENSE", "CASH_DEPOSIT", "CASH_WITHDRAWAL")' in main,
    'mode allowlist': 'storedMode !in setOf("CASH", "UPI", "BANK")' in main,
}
for name, ok in checks.items():
    print(f'{name}: {"PASS" if ok else "FAIL"}')
assert all(checks.values())
