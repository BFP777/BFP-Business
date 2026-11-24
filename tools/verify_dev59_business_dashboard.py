from pathlib import Path
import re, sqlite3
ROOT = Path(__file__).resolve().parents[1]
entities = (ROOT/'app/src/main/java/in/bhajan/bfpbusinesspro/data/Entities.kt').read_text()
dao = (ROOT/'app/src/main/java/in/bhajan/bfpbusinesspro/data/AppDao.kt').read_text()
main = (ROOT/'app/src/main/java/in/bhajan/bfpbusinesspro/MainActivity.kt').read_text()
gradle = (ROOT/'app/build.gradle.kts').read_text()
checks = {
 'dashboard projection exists': 'data class BusinessDashboardRow' in entities,
 'daily dashboard DAO flow exists': 'observeBusinessDashboard(startEpochMillis: Long, endEpochMillis: Long)' in dao,
 'sales and purchases use invoice kind and date window': "kind = \'SALE\' AND dateEpochMillis >= :startEpochMillis" in dao and "kind = \'PURCHASE\' AND dateEpochMillis >= :startEpochMillis" in dao,
 'receipts remain separate payment IN total': "direction = \'IN\' AND dateEpochMillis >= :startEpochMillis" in dao,
 'expenses come only from EXPENSE cash entries': "entryType = \'EXPENSE\' AND dateEpochMillis >= :startEpochMillis" in dao,
 'estimated gross profit uses sale lines and current item master rates': 'il.quantity * (it.saleRatePaise - it.purchaseRatePaise)' in dao and "inv.kind = \'SALE\'" in dao,
 'bilingual dashboard screen and estimate caveat visible': 'BUSINESS DASHBOARD / बिजनेस डैशबोर्ड' in main and 'प्राप्तियाँ बिक्री से अलग हैं' in main,
 'receivables and payables retain green and red colors': '0xFF168447' in main and '0xFFC93636' in main,
 'dashboard is read-only and navigates to existing modules': 'observeBusinessDashboard(startMillis, endMillis).first()' in main and 'showPayments("IN")' in main and 'showExpenses()' in main,
 'app version advanced without Room schema change': 'versionCode = 561' in gradle and 'versionName = "5.61.0"' in gradle and 'version = 10' in (ROOT/'app/src/main/java/in/bhajan/bfpbusinesspro/data/BfpDatabase.kt').read_text(),
}
# Extract Kotlin compile-time string fragments from the @Query annotation and execute its exact SQL.
m = re.search(r'@Query\((.*?)\)\s*fun observeBusinessDashboard', dao, re.S)
assert m, 'Dashboard query annotation missing'
parts = re.findall(r'"((?:\\.|[^"\\])*)"', m.group(1))
sql = ''.join(bytes(x, 'utf-8').decode('unicode_escape') for x in parts)
con = sqlite3.connect(':memory:')
con.executescript('''
CREATE TABLE invoices(id INTEGER, kind TEXT, dateEpochMillis INTEGER, totalPaise INTEGER);
CREATE TABLE payments(id INTEGER, direction TEXT, dateEpochMillis INTEGER, amountPaise INTEGER);
CREATE TABLE cash_entries(id INTEGER, entryType TEXT, dateEpochMillis INTEGER, amountPaise INTEGER);
CREATE TABLE items(id INTEGER, saleRatePaise INTEGER, purchaseRatePaise INTEGER);
CREATE TABLE invoice_lines(id INTEGER, invoiceId INTEGER, itemId INTEGER, quantity REAL);
INSERT INTO invoices VALUES (1,'SALE',100,100000),(2,'PURCHASE',110,40000),(3,'SALE',300,90000);
INSERT INTO payments VALUES (1,'IN',120,30000),(2,'OUT',130,5000);
INSERT INTO cash_entries VALUES (1,'EXPENSE',140,7000),(2,'CASH_DEPOSIT',150,20000);
INSERT INTO items VALUES (1,80000,50000);
INSERT INTO invoice_lines VALUES (1,1,1,2.0),(2,3,1,1.0);
''')
row = con.execute(sql, {'startEpochMillis': 0, 'endEpochMillis': 200}).fetchone()
checks['SQLite executes exact DAO query with receipts separate and day filtering'] = row == (100000,40000,30000,7000,60000)
con.close()
for k,v in checks.items(): print(f"{'PASS' if v else 'FAIL'}: {k}")
assert all(checks.values()), 'Dev59 dashboard checks failed'
print('Development 59 Business Dashboard checks: PASS')
