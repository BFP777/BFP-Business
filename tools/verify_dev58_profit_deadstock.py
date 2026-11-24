from pathlib import Path
import re
import sqlite3

ROOT = Path(__file__).resolve().parents[1]
entities = (ROOT / "app/src/main/java/in/bhajan/bfpbusinesspro/data/Entities.kt").read_text()
dao = (ROOT / "app/src/main/java/in/bhajan/bfpbusinesspro/data/AppDao.kt").read_text()
main = (ROOT / "app/src/main/java/in/bhajan/bfpbusinesspro/MainActivity.kt").read_text()
gradle = (ROOT / "app/build.gradle.kts").read_text()
db = (ROOT / "app/src/main/java/in/bhajan/bfpbusinesspro/data/BfpDatabase.kt").read_text()
checks = {
    "performance projection embeds existing item master": "data class ItemPerformanceRow" in entities and "@Embedded val item: ItemEntity" in entities,
    "DAO returns 90-day sold quantity": "AS soldLast90Days" in dao and "inv.dateEpochMillis >= :sinceEpochMillis" in dao,
    "DAO returns all-time last sale timestamp": "AS lastSaleEpochMillis" in dao and "MAX(inv.dateEpochMillis)" in dao,
    "screen accessible from bilingual inventory menu": "Per-Suit Profit & Dead Stock" in main and "private fun showProfitAndDeadStock()" in main,
    "profit uses current sale less purchase rate": "item.saleRatePaise - item.purchaseRatePaise" in main,
    "profit margin uses sale rate denominator": "profit.toDouble() * 100.0 / item.saleRatePaise.toDouble()" in main,
    "dead stock requires positive quantity and no sale in 90 days": "it.item.quantity > 0.0 && (it.lastSaleEpochMillis == 0L || it.lastSaleEpochMillis < cutoff)" in main,
    "estimate caveat is visible": "इसमें छूट, टैक्स, दुकान का खर्च और पुराने बैच की वास्तविक लागत शामिल नहीं है" in main,
    "screen is read-only and uses DAO Flow": "observeItemPerformance(cutoff).collectLatest" in main,
    "Room schema unchanged": "version = 10" in db and "versionCode = 561" in gradle and 'versionName = "5.61.0"' in gradle,
}

# Execute the exact SQL declared in the DAO against a small realistic fixture.
match = re.search(r'@Query\("(SELECT items\.\*,.*?AS lastSaleEpochMillis FROM items ORDER BY items\.name COLLATE NOCASE)"\)\s*fun observeItemPerformance', dao)
assert match, "Could not extract observeItemPerformance SQL"
sql = match.group(1)
con = sqlite3.connect(":memory:")
con.executescript("""
CREATE TABLE items (id INTEGER PRIMARY KEY, name TEXT NOT NULL, itemCode TEXT NOT NULL, category TEXT NOT NULL, purchaseRatePaise INTEGER NOT NULL, saleRatePaise INTEGER NOT NULL, quantity REAL NOT NULL, hsnCode TEXT NOT NULL, gstRatePercent REAL NOT NULL, minimumStockQuantity REAL NOT NULL);
CREATE TABLE invoices (id INTEGER PRIMARY KEY, kind TEXT NOT NULL, dateEpochMillis INTEGER NOT NULL);
CREATE TABLE invoice_lines (id INTEGER PRIMARY KEY, invoiceId INTEGER NOT NULL, itemId INTEGER NOT NULL, quantity REAL NOT NULL);
INSERT INTO items VALUES (1,'Active Suit','A01','Suit',50000,80000,3,'',5,2);
INSERT INTO items VALUES (2,'Dead Suit','D02','Suit',40000,65000,2,'',5,2);
INSERT INTO items VALUES (3,'Never Sold','N03','Suit',30000,50000,1,'',5,2);
INSERT INTO invoices VALUES (1,'SALE',950);
INSERT INTO invoices VALUES (2,'SALE',100);
INSERT INTO invoice_lines VALUES (1,1,1,2.0);
INSERT INTO invoice_lines VALUES (2,2,2,1.0);
""")
rows = con.execute(sql, {"sinceEpochMillis": 200}).fetchall()
by_id = {row[0]: row for row in rows}
# Selected projection: item columns (10), sold90Days at 10, lastSaleEpochMillis at 11.
checks["SQLite query returns 90-day sales and all-time last sale correctly"] = (
    len(rows) == 3 and by_id[1][10] == 2.0 and by_id[1][11] == 950 and
    by_id[2][10] == 0.0 and by_id[2][11] == 100 and
    by_id[3][10] == 0.0 and by_id[3][11] == 0
)
con.close()

for label, ok in checks.items():
    print(f"{'PASS' if ok else 'FAIL'}: {label}")
assert all(checks.values())
print("Development 58 Profit & Dead Stock checks: PASS")
