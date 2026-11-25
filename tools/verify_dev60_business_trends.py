from pathlib import Path
import re, sqlite3
ROOT = Path(__file__).resolve().parents[1]
entities = (ROOT/'app/src/main/java/in/bhajan/bfpbusinesspro/data/Entities.kt').read_text()
dao = (ROOT/'app/src/main/java/in/bhajan/bfpbusinesspro/data/AppDao.kt').read_text()
main = (ROOT/'app/src/main/java/in/bhajan/bfpbusinesspro/MainActivity.kt').read_text()
gradle = (ROOT/'app/build.gradle.kts').read_text()
db = (ROOT/'app/src/main/java/in/bhajan/bfpbusinesspro/data/BfpDatabase.kt').read_text()
checks = {
 'monthly trend projection has separate sales purchase expense and estimate fields': all(x in entities for x in ['data class MonthlyBusinessTrendRow','val monthKey: String','val salesPaise: Long','val purchasesPaise: Long','val expensesPaise: Long','val estimatedGrossProfitPaise: Long']),
 'DAO aggregates six-month month-key trend by local calendar month': 'observeMonthlyBusinessTrends(startEpochMillis: Long, endEpochMillis: Long)' in dao and "strftime('%Y-%m'" in dao and 'GROUP BY monthKey ORDER BY monthKey' in dao,
 'sales and purchases are separated by invoice kind': "kind = 'SALE'" in dao and "kind = 'PURCHASE'" in dao,
 'expenses are sourced only from EXPENSE entries': "entryType = 'EXPENSE'" in dao,
 'estimated gross profit is sale-line quantity times current item margin': 'il.quantity * (it.saleRatePaise - it.purchaseRatePaise)' in dao,
 'trend screen has bilingual labels and six-month graph': 'BUSINESS TRENDS / कारोबार के रुझान' in main and 'पिछले 6 महीनों' in main and 'canvas.drawRoundRect' in main,
 'trend screen includes monthly breakdown and estimate caveat': 'Est. Gross Profit' in main and 'ऐतिहासिक लागत या नेट प्रॉफिट नहीं है' in main,
 'dashboard and menu both link to trends': main.count('showBusinessTrends()') >= 3,
 'screen is read-only and uses DAO Flow': 'observeMonthlyBusinessTrends(startCal.timeInMillis, endCal.timeInMillis).first()' in main,
 'app version 5.15.0 without Room schema migration': 'versionCode = 564' in gradle and 'versionName = "5.64.0"' in gradle and 'version = 10' in db,
}
fn = dao.index('fun observeMonthlyBusinessTrends')
query_start = dao.rfind('@Query(', 0, fn)
assert query_start >= 0, 'Monthly trends query missing'
query_section = dao[query_start:fn]
parts = re.findall(r'"((?:\\.|[^"\\])*)"', query_section)
sql = ''.join(bytes(x, 'utf-8').decode('unicode_escape') for x in parts)
con = sqlite3.connect(':memory:')
con.executescript('''
CREATE TABLE invoices(id INTEGER, kind TEXT, dateEpochMillis INTEGER, totalPaise INTEGER);
CREATE TABLE cash_entries(id INTEGER, entryType TEXT, dateEpochMillis INTEGER, amountPaise INTEGER);
CREATE TABLE items(id INTEGER, saleRatePaise INTEGER, purchaseRatePaise INTEGER);
CREATE TABLE invoice_lines(id INTEGER, invoiceId INTEGER, itemId INTEGER, quantity REAL);
''')
# UTC epoch fixture: Jan and Feb 2026. All records within requested period.
import calendar, datetime
jan = int(datetime.datetime(2026,1,15,tzinfo=datetime.timezone.utc).timestamp()*1000)
feb = int(datetime.datetime(2026,2,15,tzinfo=datetime.timezone.utc).timestamp()*1000)
con.executemany('INSERT INTO invoices VALUES (?,?,?,?)', [(1,'SALE',jan,100000),(2,'PURCHASE',jan,40000),(3,'SALE',feb,50000)])
con.executemany('INSERT INTO cash_entries VALUES (?,?,?,?)', [(1,'EXPENSE',feb,7000),(2,'CASH_RECEIPT',feb,20000)])
con.execute('INSERT INTO items VALUES (1,80000,50000)')
con.executemany('INSERT INTO invoice_lines VALUES (?,?,?,?)', [(1,1,1,2.0),(2,3,1,1.0)])
rows = con.execute(sql, {'startEpochMillis': int(datetime.datetime(2026,1,1,tzinfo=datetime.timezone.utc).timestamp()*1000), 'endEpochMillis': int(datetime.datetime(2026,3,1,tzinfo=datetime.timezone.utc).timestamp()*1000)}).fetchall()
expected = [('2026-01',100000,40000,0,60000),('2026-02',50000,0,7000,30000)]
checks['SQLite executes exact DAO query with monthly grouping and separate expense/profit totals'] = rows == expected
con.close()
for k,v in checks.items(): print(f"{'PASS' if v else 'FAIL'}: {k}")
assert all(checks.values()), 'Dev60 business trends checks failed'
print(f'Development 60 Business Trends checks: PASS ({len(checks)}/{len(checks)})')
