from pathlib import Path
import re, sqlite3
ROOT = Path(__file__).resolve().parents[1]
dao = (ROOT / "app/src/main/java/in/bhajan/bfpbusinesspro/data/AppDao.kt").read_text()
entities = (ROOT / "app/src/main/java/in/bhajan/bfpbusinesspro/data/Entities.kt").read_text()
db_source = (ROOT / "app/src/main/java/in/bhajan/bfpbusinesspro/data/BfpDatabase.kt").read_text()
main = (ROOT / "app/src/main/java/in/bhajan/bfpbusinesspro/MainActivity.kt").read_text()
gradle = (ROOT / "app/build.gradle.kts").read_text()
backup = (ROOT / "app/src/main/java/in/bhajan/bfpbusinesspro/backup/DatabaseBackup.kt").read_text()
checks = {
    "optional birthday field and non-destructive Room 8-to-9 migration": 'val birthday: String = ""' in entities and "Migration(8, 9)" in db_source and "ALTER TABLE parties ADD COLUMN birthday TEXT NOT NULL DEFAULT ''" in db_source,
    "customer insight query includes sale-only totals, bills, due and latest sale": "i.kind = 'SALE'" in dao and "totalSalesPaise" in dao and "outstandingPaise" in dao and "lastSaleEpochMillis" in dao,
    "design preference queries aggregate item quantities and amounts": "getCustomerDesignPreferences" in dao and "observeTopCustomerDesigns" in dao and "SUM(il.quantity)" in dao,
    "recent sale history query is sale-only and bounded to ten bills": "getCustomerPurchaseHistory" in dao and "kind = 'SALE'" in dao and "LIMIT 10" in dao,
    "birthday list includes customers even without a sale": "observeCustomerBirthdays" in dao and "trim(birthday) != ''" in dao,
    "customer intelligence is reachable and deliberately informational": "Customer Insights / ग्राहक विश्लेषण" in main and "कोई ऑटोमैटिक संदेश, भुगतान या रिकॉर्ड बदलाव नहीं होगा" in main,
    "VIP criteria are disclosed and optional birthday is validated": "5 या अधिक बिल अथवा ₹25,000" in main and "जन्मदिन (DD-MM-YYYY, वैकल्पिक)" in main and "DD-MM-YYYY सही भरें" in main,
    "backup validator accepts schema v9": "cursor.getInt(0) in 3..10" in backup,
    "app version advanced": "versionCode = 564" in gradle and 'versionName = "5.64.0"' in gradle,
}
# Extract exact Room queries for the four new customer methods.
methods = ["observeCustomerInsights", "observeCustomerBirthdays", "getCustomerPurchaseHistory", "getCustomerDesignPreferences", "observeTopCustomerDesigns"]
queries = {}
for method in methods:
    match = re.search(r'@Query\("(.*?)"\)\s*(?:suspend\s+)?fun\s+' + method, dao)
    assert match, f"Missing Room query for {method}"
    queries[method] = match.group(1)
# Execute the actual DAO statements against fixture tables and verify key results.
db = sqlite3.connect(":memory:")
db.executescript("""
CREATE TABLE parties(id INTEGER PRIMARY KEY, name TEXT, mobile TEXT, birthday TEXT, partyType TEXT);
CREATE TABLE invoices(id INTEGER PRIMARY KEY, partyId INTEGER, kind TEXT, invoiceNumber TEXT, totalPaise INTEGER, paidPaise INTEGER, dateEpochMillis INTEGER);
CREATE TABLE invoice_lines(id INTEGER PRIMARY KEY, invoiceId INTEGER, itemNameSnapshot TEXT, itemCodeSnapshot TEXT, quantity REAL, lineTotalPaise INTEGER);
INSERT INTO parties VALUES (1,'Customer A','9876543210','1990-12-01','CUSTOMER'),(2,'Supplier B','','','SUPPLIER'),(3,'Birthday Only','9123456780','1988-11-30','CUSTOMER');
INSERT INTO invoices VALUES (1,1,'SALE','S-001',500000,300000,1000),(2,1,'SALE','S-002',250000,250000,2000),(3,2,'SALE','S-003',900000,0,3000),(4,1,'PURCHASE','P-001',100000,0,4000);
INSERT INTO invoice_lines VALUES (1,1,'Suit Rose','S-01',2,200000),(2,2,'Suit Rose','S-01',3,300000),(3,2,'Suit Blue','S-02',1,100000),(4,3,'Suit Fake','F-01',99,999999);
""")
summary = db.execute(queries["observeCustomerInsights"]).fetchall()
assert len(summary) == 1 and summary[0][0] == 1 and summary[0][4] == 2 and summary[0][5] == 750000 and summary[0][6] == 200000
birthdays = db.execute(queries["observeCustomerBirthdays"]).fetchall()
assert len(birthdays) == 2 and any(row[1] == "Birthday Only" for row in birthdays)
history = db.execute(queries["getCustomerPurchaseHistory"], {"partyId": 1}).fetchall()
assert len(history) == 2 and history[0][0] == "S-002"
preferences = db.execute(queries["getCustomerDesignPreferences"], {"partyId": 1}).fetchall()
assert preferences and preferences[0][2] == "Suit Rose" and preferences[0][4] == 5.0
top = db.execute(queries["observeTopCustomerDesigns"]).fetchall()
assert top and top[0][1] == "Customer A" and top[0][2] == "Suit Rose" and top[0][4] == 5.0
# Execute the exact Room migration against a populated v8 party table.
migration_match = re.search(r'private val MIGRATION_8_9.*?database.execSQL\("(.*?)"\)', db_source, re.S)
assert migration_match, "Room 8-to-9 migration SQL not found"
old_db = sqlite3.connect(":memory:")
old_db.execute("CREATE TABLE parties(id INTEGER PRIMARY KEY, name TEXT NOT NULL, mobile TEXT NOT NULL, address TEXT NOT NULL, gstNumber TEXT NOT NULL, partyType TEXT NOT NULL)")
old_db.execute("INSERT INTO parties VALUES (1,'Existing Customer','','','','CUSTOMER')")
old_db.execute(migration_match.group(1))
assert old_db.execute("SELECT birthday FROM parties WHERE id=1").fetchone()[0] == ""
checks["SQLite execution: customer totals, birthday list, purchase history and design preferences correct"] = True
checks["SQLite migration: existing parties preserved and birthday defaults blank"] = True
for label, ok in checks.items(): print(("PASS" if ok else "FAIL") + ": " + label)
assert all(checks.values())
print("Development 53 Customer Intelligence checks: PASS")
