from pathlib import Path
import re
import sqlite3
root = Path(__file__).resolve().parents[1]
entities = (root / 'app/src/main/java/in/bhajan/bfpbusinesspro/data/Entities.kt').read_text()
db = (root / 'app/src/main/java/in/bhajan/bfpbusinesspro/data/BfpDatabase.kt').read_text()
dao = (root / 'app/src/main/java/in/bhajan/bfpbusinesspro/data/AppDao.kt').read_text()
main = (root / 'app/src/main/java/in/bhajan/bfpbusinesspro/MainActivity.kt').read_text()
backup = (root / 'app/src/main/java/in/bhajan/bfpbusinesspro/backup/DatabaseBackup.kt').read_text()
gradle = (root / 'app/build.gradle.kts').read_text()
checks = {
    'item master stores minimum stock threshold': 'val minimumStockQuantity: Double = 2.0' in entities,
    'stock insight model embeds item and recent sales': '@Embedded val item: ItemEntity' in entities and 'val soldLast30Days: Double' in entities,
    'DAO aggregates actual sale quantities for selected period': "inv.kind = 'SALE'" in dao and 'inv.dateEpochMillis >= :sinceEpochMillis' in dao and 'AS soldLast30Days' in dao,
    'smart stock screen is accessible from menu': 'Smart Stock / Reorder Suggestions' in main and 'private fun showSmartStock()' in main,
    'low stock uses user-defined threshold': 'it.item.quantity <= it.item.minimumStockQuantity' in main,
    'suggestions are explicitly non-automatic': 'ऐप अपने-आप खरीद ऑर्डर नहीं बनाएगा' in main,
    'reorder suggestion considers 30-day demand and threshold': 'averageDaily * 30.0' in main and 'maxOf(demandTarget, thresholdTarget)' in main,
    'item form validates minimum stock quantity': 'minQ < 0' in main and 'minimumStockQuantity = minQ' in main,
    'non-destructive 7 to 8 migration registered': 'Migration(7, 8)' in db and 'ALTER TABLE items ADD COLUMN minimumStockQuantity REAL NOT NULL DEFAULT 2.0' in db and 'MIGRATION_7_8' in db,
    'backup accepts schema v9': 'cursor.getInt(0) in 3..10' in backup,
    'app version advanced': 'versionCode = 564' in gradle and 'versionName = "5.64.0"' in gradle,
}
# Execute the migration against a realistic prior-version items table.
migration = db.split('private val MIGRATION_7_8', 1)[1].split('private val MIGRATION_8_9', 1)[0]
statements = re.findall(r'database.execSQL\("(.*?)"\)', migration)
con = sqlite3.connect(':memory:')
con.execute('CREATE TABLE items (id INTEGER PRIMARY KEY, name TEXT NOT NULL, itemCode TEXT NOT NULL DEFAULT \'\', category TEXT NOT NULL DEFAULT \'\', purchaseRatePaise INTEGER NOT NULL DEFAULT 0, saleRatePaise INTEGER NOT NULL DEFAULT 0, quantity REAL NOT NULL DEFAULT 0, hsnCode TEXT NOT NULL DEFAULT \'\', gstRatePercent REAL NOT NULL DEFAULT 0)')
con.execute("INSERT INTO items (id,name,itemCode) VALUES (1,'Test suit','S001')")
for sql in statements: con.execute(sql)
columns = {row[1] for row in con.execute('PRAGMA table_info(items)')}
value = con.execute('SELECT minimumStockQuantity FROM items WHERE id=1').fetchone()[0]
checks['SQLite migration preserves item and sets default alert threshold'] = 'minimumStockQuantity' in columns and value == 2.0 and con.execute('SELECT name FROM items WHERE id=1').fetchone()[0] == 'Test suit'
con.close()
for label, ok in checks.items(): print(f"{'PASS' if ok else 'FAIL'}: {label}")
assert all(checks.values())
print('Development 47 Smart Stock checks: PASS')
