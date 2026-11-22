"""Execute SQLite EXPLAIN for every literal Room @Query in AppDao.kt."""
from pathlib import Path
import re
import sqlite3

ROOT = Path(__file__).resolve().parents[1]
DAO = ROOT / "app/src/main/java/in/bhajan/bfpbusinesspro/data/AppDao.kt"
source = DAO.read_text(encoding="utf-8")
queries = re.findall(r'@Query\("(.*?)"\)', source)
if not queries:
    raise SystemExit("No Room @Query statements found")

connection = sqlite3.connect(":memory:")
connection.executescript("""
CREATE TABLE parties (id INTEGER PRIMARY KEY, name TEXT NOT NULL, mobile TEXT NOT NULL, address TEXT NOT NULL, gstNumber TEXT NOT NULL, partyType TEXT NOT NULL, birthday TEXT NOT NULL DEFAULT '');
CREATE TABLE items (id INTEGER PRIMARY KEY, name TEXT NOT NULL, itemCode TEXT NOT NULL, category TEXT NOT NULL, purchaseRatePaise INTEGER NOT NULL, saleRatePaise INTEGER NOT NULL, quantity REAL NOT NULL, hsnCode TEXT NOT NULL, gstRatePercent REAL NOT NULL, minimumStockQuantity REAL NOT NULL DEFAULT 2.0);
CREATE TABLE invoices (id INTEGER PRIMARY KEY, invoiceNumber TEXT NOT NULL, partyId INTEGER NOT NULL, kind TEXT NOT NULL, dateEpochMillis INTEGER NOT NULL, subtotalPaise INTEGER NOT NULL, discountPaise INTEGER NOT NULL, taxPaise INTEGER NOT NULL, cgstPaise INTEGER NOT NULL, sgstPaise INTEGER NOT NULL, igstPaise INTEGER NOT NULL, totalPaise INTEGER NOT NULL, paidPaise INTEGER NOT NULL, note TEXT NOT NULL, sellerStateCode TEXT NOT NULL, buyerStateCode TEXT NOT NULL, attachmentPath TEXT NOT NULL);
CREATE TABLE invoice_lines (id INTEGER PRIMARY KEY, invoiceId INTEGER NOT NULL, itemId INTEGER NOT NULL, itemNameSnapshot TEXT NOT NULL, quantity REAL NOT NULL, itemCodeSnapshot TEXT NOT NULL, ratePaise INTEGER NOT NULL, lineTotalPaise INTEGER NOT NULL, gstRatePercent REAL NOT NULL, taxablePaise INTEGER NOT NULL, cgstPaise INTEGER NOT NULL, sgstPaise INTEGER NOT NULL, igstPaise INTEGER NOT NULL, hsnCode TEXT NOT NULL);
CREATE TABLE payments (id INTEGER PRIMARY KEY, partyId INTEGER NOT NULL, invoiceId INTEGER, direction TEXT NOT NULL, mode TEXT NOT NULL, amountPaise INTEGER NOT NULL, dateEpochMillis INTEGER NOT NULL, note TEXT NOT NULL, bankAccountId INTEGER);
CREATE TABLE cash_entries (id INTEGER PRIMARY KEY, entryType TEXT NOT NULL, mode TEXT NOT NULL, amountPaise INTEGER NOT NULL, dateEpochMillis INTEGER NOT NULL, note TEXT NOT NULL, bankAccountId INTEGER);
CREATE TABLE business_profile (id INTEGER PRIMARY KEY, businessName TEXT NOT NULL, address TEXT NOT NULL, mobile TEXT NOT NULL, gstin TEXT NOT NULL, stateCode TEXT NOT NULL, invoicePrefix TEXT NOT NULL);
CREATE TABLE bank_accounts (id INTEGER PRIMARY KEY, accountName TEXT NOT NULL);
CREATE TABLE employees (id INTEGER PRIMARY KEY, name TEXT NOT NULL, mobile TEXT NOT NULL, monthlySalaryPaise INTEGER NOT NULL, joiningDateEpochMillis INTEGER NOT NULL, active INTEGER NOT NULL, note TEXT NOT NULL);
CREATE TABLE salary_months (id INTEGER PRIMARY KEY, employeeId INTEGER NOT NULL, monthKey TEXT NOT NULL, payablePaise INTEGER NOT NULL, note TEXT NOT NULL);
CREATE TABLE salary_payments (id INTEGER PRIMARY KEY, salaryMonthId INTEGER NOT NULL, employeeId INTEGER NOT NULL, amountPaise INTEGER NOT NULL, dateEpochMillis INTEGER NOT NULL, mode TEXT NOT NULL, bankAccountId INTEGER, note TEXT NOT NULL);
CREATE TABLE stock_movements (id INTEGER PRIMARY KEY, itemId INTEGER NOT NULL, movementType TEXT NOT NULL, quantityDelta REAL NOT NULL, previousQuantity REAL NOT NULL, newQuantity REAL NOT NULL, reason TEXT NOT NULL, dateEpochMillis INTEGER NOT NULL, reference TEXT NOT NULL);
""")
params = {"partyId": 1, "itemId": 1, "id": 1, "kind": "SALE", "normalizedNumber": "S-1", "supplierGstin": "02ABCDE1234F1Z5", "excludeId": 0, "invoiceId": 1, "name": "SBI", "code": "BFP-001", "employeeId": 1, "monthKey": "2026-11", "salaryMonthId": 1, "excludeId": 0, "sinceEpochMillis": 0, "previousStartEpochMillis": 0, "recentStartEpochMillis": 0, "periodEndEpochMillis": 0, "startEpochMillis": 0, "endEpochMillis": 1}
for index, query in enumerate(queries, start=1):
    try:
        connection.execute("EXPLAIN " + query, params).fetchall()
    except sqlite3.Error as exc:
        raise SystemExit(f"Room DAO query {index} failed: {query}\n{exc}")
print(f"Room DAO SQLite query checks: PASS ({len(queries)} queries)")
