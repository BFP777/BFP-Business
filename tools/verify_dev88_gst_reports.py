from pathlib import Path
root = Path(__file__).resolve().parents[1]
dao = (root / 'app/src/main/java/in/bhajan/bfpbusinesspro/data/AppDao.kt').read_text()
entities = (root / 'app/src/main/java/in/bhajan/bfpbusinesspro/data/Entities.kt').read_text()
activity = (root / 'app/src/main/java/in/bhajan/bfpbusinesspro/MainActivity.kt').read_text()
gradle = (root / 'app/build.gradle.kts').read_text()
checks = {
    'rate-wise GST report projection': 'data class GstRateReportRow' in entities,
    'period-filtered invoice line tax query': 'getGstRateReport(startEpochMillis: Long, endEpochMillis: Long)' in dao and 'il.gstRatePercent' in dao,
    'GST tax components aggregated independently': all(x in dao for x in ['SUM(il.taxablePaise)', 'SUM(il.cgstPaise)', 'SUM(il.sgstPaise)', 'SUM(il.igstPaise)']),
    'sales and purchase GST remain separate': "i.kind IN ('SALE','PURCHASE')" in dao and 'SALES GST' in activity and 'PURCHASE GST' in activity,
    'date-range GST report UI': 'private fun showGstReport()' in activity and 'GST / Tax Report' in activity,
    'version 5.43.0 / 533': 'versionCode = 557' in gradle and 'versionName = "5.57.0"' in gradle,
}
for name, ok in checks.items(): print(('PASS' if ok else 'FAIL') + ': ' + name)
assert all(checks.values())
