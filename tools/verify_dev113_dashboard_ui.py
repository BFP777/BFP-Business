from pathlib import Path
root = Path(__file__).resolve().parents[1]
main = (root / "app/src/main/java/in/bhajan/bfpbusinesspro/MainActivity.kt").read_text()
gradle = (root / "app/build.gradle.kts").read_text()
checks = {
    "dashboard cards": all(x in main for x in ["TOTAL SALES", "TOTAL PURCHASE", "CASH BALANCE", "OUTSTANDING"]),
    "receivable/payable color cards": "DEBTORS  •  RECEIVABLE" in main and "CREDITORS  •  PAYABLE" in main,
    "quick action destinations": all(x in main for x in ["NEW SALE", "PAYMENT IN", "PAYMENT OUT", "EXPENSE", "BANK"]),
    "quick shortcuts": all(x in main for x in ["PARTIES", "STOCK / ITEMS", "DAY BOOK", "REPORTS"]),
    "navy BFP brand header": 'setBackgroundColor(0xFF071F55.toInt())' in main and 'BFP Business Pro' in main,
    "version 5.66.0 / 566": 'versionCode = 566' in gradle and 'versionName = "5.66.0"' in gradle,
}
for label, ok in checks.items(): print(f"{'PASS' if ok else 'FAIL'}: {label}")
assert all(checks.values())
