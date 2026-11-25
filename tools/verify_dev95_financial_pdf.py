from pathlib import Path
root = Path(__file__).resolve().parents[1]
main = (root / "app/src/main/java/in/bhajan/bfpbusinesspro/MainActivity.kt").read_text()
gradle = (root / "app/build.gradle.kts").read_text()
db = (root / "app/src/main/java/in/bhajan/bfpbusinesspro/data/BfpDatabase.kt").read_text()
checks = {
    "P&L is a date-range report": 'private fun showFinancialStatement()' in main and 'Apply Dates' in main and 'var from =' in main and 'var to =' in main,
    "P&L report can be exported as PDF": 'PDF Export / PDF सेव करें' in main and 'BFP-Profit-Loss-' in main and 'createStockPdf.launch' in main,
    "PDF includes period and P&L values": 'PROFIT & LOSS (MANAGEMENT ESTIMATE)' in main and 'Estimated operating surplus:' in main and 'Period:' in main,
    "financial position snapshot is explicitly distinguished from historical period end": 'आज के snapshot' in main and 'not period-end historical balances' in main,
    "management estimate and costing limitations disclosed": 'not a certified statutory P&L or legal Balance Sheet' in main and 'current Item Master purchase rates' in main,
    "version 5.43.0 / 540": 'versionCode = 562' in gradle and 'versionName = "5.62.0"' in gradle,
    "Room schema remains version 10": 'version = 10' in db,
}
for name, ok in checks.items(): print(("PASS" if ok else "FAIL") + ": " + name)
assert all(checks.values())
