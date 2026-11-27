from pathlib import Path
root = Path(__file__).resolve().parents[1]
main = (root / "app/src/main/java/in/bhajan/bfpbusinesspro/MainActivity.kt").read_text()
repo = (root / "app/src/main/java/in/bhajan/bfpbusinesspro/data/LedgerRepository.kt").read_text()
gradle = (root / "app/build.gradle.kts").read_text()
checks = {
    "bank-to-bank transfer action": '"Bank to Bank Transfer"' in main and 'bankToBankTransferDialog()' in main,
    "bank-to-cash action retained": '"Bank to Cash"' in main and 'cashEntryDialog("CASH_WITHDRAWAL")' in main,
    "cash-to-bank action retained": '"Cash to Bank"' in main and 'cashEntryDialog("CASH_DEPOSIT")' in main,
    "source balance validation": 'Source bank में पर्याप्त balance नहीं है' in main,
    "single-record bank transfer tracks both accounts": 'BANK_TRANSFER|${source.id}|${destination.id}' in main and 'entry.entryType == "BANK_TRANSFER"' in repo,
    "bank balance adjustment is non-destructive": 'BANK_ADJUSTMENT_IN' in main and 'BANK_ADJUSTMENT_OUT' in repo and 'Direct balance edit adjustment' in main,
    "cash balance unaffected by bank-to-bank transfer": 'observeCashEntries().first().filter { it.mode == "CASH" }' in repo,
    "reconciliation validates transfer destination": 'TRANSFER-TO-${it.id}' in repo,
    "release version 5.65.0 / 565": 'versionCode = 565' in gradle and 'versionName = "5.65.0"' in gradle,
}
for label, ok in checks.items(): print(f'{"PASS" if ok else "FAIL"}: {label}')
assert all(checks.values())
print("DEV125 bank transfer and balance checks: PASS")
