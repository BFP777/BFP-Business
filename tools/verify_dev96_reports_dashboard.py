from pathlib import Path
root = Path(__file__).resolve().parents[1]
main = (root / "app/src/main/java/in/bhajan/bfpbusinesspro/MainActivity.kt").read_text()
gradle = (root / "app/build.gradle.kts").read_text()
checks = {
    "reports hub added": 'BUSINESS REPORTS / कारोबार की रिपोर्टें' in main,
    "GST report shortcut": 'showGstReport()' in main,
    "stock report shortcut": 'showStockReport()' in main,
    "party ledger shortcut": 'showLedgerPicker()' in main,
    "cash bank reconciliation shortcut": 'showMoneyReconciliation()' in main,
    "financial audit shortcut": 'showBusinessFinancialAudit()' in main,
    "business trends shortcut": 'showBusinessTrends()' in main,
    "day book shortcut": 'showDayBook()' in main,
    "version 5.43.0 / 541": 'versionCode = 562' in gradle and 'versionName = "5.62.0"' in gradle,
    "Room schema remains 10": 'version = 10' in (root / "app/src/main/java/in/bhajan/bfpbusinesspro/data/BfpDatabase.kt").read_text(),
}
for name, ok in checks.items(): print(('PASS' if ok else 'FAIL') + ': ' + name)
if not all(checks.values()): raise SystemExit(1)
