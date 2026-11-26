from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
main = (ROOT / 'app/src/main/java/in/bhajan/bfpbusinesspro/MainActivity.kt').read_text()
dao = (ROOT / 'app/src/main/java/in/bhajan/bfpbusinesspro/data/AppDao.kt').read_text()
entities = (ROOT / 'app/src/main/java/in/bhajan/bfpbusinesspro/data/Entities.kt').read_text()
gradle = (ROOT / 'app/build.gradle.kts').read_text()
checks = {
    'period stock query': 'getStockPeriodReport(startEpochMillis: Long, endEpochMillis: Long)' in dao,
    'opening stock before selected start': "sm.dateEpochMillis < :startEpochMillis" in dao,
    'purchase movement date filter': "sm.dateEpochMillis >= :startEpochMillis AND sm.dateEpochMillis < :endEpochMillis AND sm.movementType LIKE 'PURCHASE%'" in dao,
    'sale movement date filter': "sm.dateEpochMillis >= :startEpochMillis AND sm.dateEpochMillis < :endEpochMillis AND sm.movementType LIKE 'SALE%'" in dao,
    'closing stock at selected end': 'AS closingQuantity' in dao,
    'period report projection': 'data class StockPeriodReportRow' in entities,
    'date pickers and validation': 'DatePickerDialog' in main and 'endMillis <= startMillis' in main,
    'monthly business trend reuse': 'observeMonthlyBusinessTrends(startMillis, endMillis)' in main,
    'CSV export': 'text/csv' in main and 'Excel CSV Export' in main,
    'PDF export': 'application/pdf' in main and 'PdfDocument' in main,
    'version 5.43.0 / 533': 'versionCode = 557' in gradle and 'versionName = "5.57.0"' in gradle,
}
for name, ok in checks.items():
    print(f'{name}: {"PASS" if ok else "FAIL"}')
assert all(checks.values())
