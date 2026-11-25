from pathlib import Path
root = Path(__file__).resolve().parents[1]
entities = (root / 'app/src/main/java/in/bhajan/bfpbusinesspro/data/Entities.kt').read_text()
dao = (root / 'app/src/main/java/in/bhajan/bfpbusinesspro/data/AppDao.kt').read_text()
activity = (root / 'app/src/main/java/in/bhajan/bfpbusinesspro/MainActivity.kt').read_text()
gradle = (root / 'app/build.gradle.kts').read_text()
checks = {
    'HSN and GST rate report projection': 'data class HsnGstReportRow' in entities and 'hsnCode: String' in entities,
    'HSN summary groups kind, HSN and rate': 'getHsnGstReport(startEpochMillis: Long, endEpochMillis: Long)' in dao and 'GROUP BY i.kind' in dao and 'il.hsnCode' in dao,
    'GSTR-1 sales invoice export projection': 'data class Gstr1InvoiceRow' in entities and 'getGstr1InvoiceRows(startEpochMillis: Long, endEpochMillis: Long)' in dao,
    'GSTR-1 export includes customer GSTIN and invoice tax totals': all(x in dao for x in ['p.gstNumber AS customerGstin', 'SUM(il.taxablePaise)', 'SUM(il.igstPaise)', 'SUM(il.cgstPaise)', 'SUM(il.sgstPaise)']),
    'CSV export uses selected date period and labels B2B/B2C provisional': 'GSTR-1 Sales CSV Export' in activity and 'BFP-GSTR1-Sales-' in activity and 'customerGstin' in activity and 'B2C' in activity,
    'Sales and Purchase HSN summaries stay separated': 'r.kind' in activity and 'HSN-WISE SUMMARY' in activity,
    'version 5.43.0 / 534': 'versionCode = 552' in gradle and 'versionName = "5.52.0"' in gradle,
}
for name, ok in checks.items(): print(('PASS' if ok else 'FAIL') + ': ' + name)
assert all(checks.values())
