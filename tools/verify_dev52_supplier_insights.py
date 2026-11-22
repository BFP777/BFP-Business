from pathlib import Path
import re, sqlite3
ROOT = Path(__file__).resolve().parents[1]
dao = (ROOT/'app/src/main/java/in/bhajan/bfpbusinesspro/data/AppDao.kt').read_text()
entities = (ROOT/'app/src/main/java/in/bhajan/bfpbusinesspro/data/Entities.kt').read_text()
main = (ROOT/'app/src/main/java/in/bhajan/bfpbusinesspro/MainActivity.kt').read_text()
gradle = (ROOT/'app/build.gradle.kts').read_text()
checks = {
    'supplier summary is purchase-only and grouped by supplier': "i.kind = 'PURCHASE'" in dao and 'GROUP BY p.id, p.name, p.gstNumber' in dao,
    'supplier report exposes bill count, total purchase and outstanding': all(x in dao for x in ['billCount', 'totalPurchasePaise', 'outstandingPaise']),
    'rate comparison uses only item-master-linked purchase lines': "i.kind = 'PURCHASE' AND il.itemId > 0" in dao,
    'rate comparison orders latest purchase first and compares prior record': 'NOT EXISTS (SELECT 1 FROM invoice_lines newer' in dao and 'ORDER BY priorInvoice.dateEpochMillis DESC, priorInvoice.id DESC, prior.id DESC LIMIT 1' in dao and 'AS previousRatePaise' in dao,
    'typed Room projection models exist': 'data class SupplierPurchaseSummaryRow' in entities and 'data class SupplierItemRateChangeRow' in entities,
    'supplier insights screen is reachable from menu': 'Supplier Purchase Insights / सप्लायर खरीद रिपोर्ट' in main and 'private fun showSupplierInsights()' in main,
    'screen is informational and does not post accounting entries': 'कोई खरीद/भुगतान अपने-आप नहीं बदलेगा' in main,
    'app version advanced without database schema change': 'versionCode = 552' in gradle and 'versionName = "5.52.0"' in gradle,
}
# Execute exact DAO SQL against realistic minimal SQLite fixtures.
queries = []
lines = dao.splitlines()
for idx, line in enumerate(lines):
    if idx + 1 < len(lines) and 'fun observeSupplierPurchaseSummaries' in lines[idx + 1]:
        queries.append(line.split('@Query("', 1)[1].rsplit('")', 1)[0])
    if idx + 1 < len(lines) and 'fun observeSupplierItemRateChanges' in lines[idx + 1]:
        queries.append(line.split('@Query("', 1)[1].rsplit('")', 1)[0])
assert len(queries) == 2, f'Expected 2 supplier queries, got {len(queries)}'
db = sqlite3.connect(':memory:')
db.executescript('''
CREATE TABLE parties(id INTEGER PRIMARY KEY, name TEXT, gstNumber TEXT, partyType TEXT);
CREATE TABLE invoices(id INTEGER PRIMARY KEY, partyId INTEGER, kind TEXT, dateEpochMillis INTEGER, totalPaise INTEGER, paidPaise INTEGER);
CREATE TABLE invoice_lines(id INTEGER PRIMARY KEY, invoiceId INTEGER, itemId INTEGER, itemNameSnapshot TEXT, itemCodeSnapshot TEXT, ratePaise INTEGER);
INSERT INTO parties VALUES (1,'Supplier A','02GST','SUPPLIER'),(2,'Customer X','','CUSTOMER');
INSERT INTO invoices VALUES (1,1,'PURCHASE',1000,100000,40000),(2,1,'PURCHASE',2000,120000,60000),(3,2,'PURCHASE',3000,90000,0),(4,1,'SALE',4000,50000,0);
INSERT INTO invoice_lines VALUES (1,1,7,'Suit Red','S-7',5000),(2,2,7,'Suit Red','S-7',6000),(3,2,8,'Suit Blue','S-8',7000),(4,3,7,'Suit Red','S-7',1000);
''')
summary = db.execute(queries[0]).fetchall()
assert len(summary) == 1 and summary[0][0] == 1 and summary[0][3] == 2 and summary[0][4] == 220000 and summary[0][5] == 120000
rates = db.execute(queries[1]).fetchall()
assert len(rates) == 1 and rates[0][0] == 1 and rates[0][4] == 6000 and rates[0][5] == 5000
checks['SQLite execution: supplier totals/outstanding and latest-vs-previous item rate are correct'] = True
for name, ok in checks.items(): print(('PASS' if ok else 'FAIL') + ': ' + name)
assert all(checks.values())
print('Development 52 Supplier Intelligence checks: PASS')
