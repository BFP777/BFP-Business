from pathlib import Path
import re, sqlite3
ROOT = Path(__file__).resolve().parents[1]
dao = (ROOT/'app/src/main/java/in/bhajan/bfpbusinesspro/data/AppDao.kt').read_text()
repo = (ROOT/'app/src/main/java/in/bhajan/bfpbusinesspro/data/InvoiceRepository.kt').read_text()
checks = {
 'duplicate purchase query is scoped to PURCHASE': "i.kind = 'PURCHASE'" in dao,
 'duplicate match uses normalized invoice number': 'trim(i.invoiceNumber) = trim(:normalizedNumber) COLLATE NOCASE' in dao,
 'duplicate match checks same supplier or same GSTIN': 'i.partyId = :partyId' in dao and 'upper(replace(trim(p.gstNumber)' in dao,
 'editing current bill excludes its own id': 'i.id != :excludeId' in dao,
 'purchase validation blocks duplicates with clear Hindi warning': 'डुप्लीकेट सप्लायर बिल' in repo,
 'sales retain existing invoice number conflict validation': 'countInvoiceNumberConflicts(input.kind, normalizedNumber, excludeInvoiceId)' in repo,
 'create validates before stock posting': repo.index('validate(input)') < repo.index('validateAndPostStock(input, emptyList())'),
}
# Execute the exact DAO SQL against a tiny SQLite fixture to prove matching behavior.
dao_line = next(line for line in dao.splitlines() if "countDuplicateSupplierBills" in line)
sql = dao_line.split('@Query("', 1)[1].split('") suspend fun', 1)[0]
db = sqlite3.connect(":memory:")
db.executescript("CREATE TABLE parties(id INTEGER PRIMARY KEY, gstNumber TEXT); CREATE TABLE invoices(id INTEGER PRIMARY KEY, partyId INTEGER, kind TEXT, invoiceNumber TEXT);")
db.executemany("INSERT INTO parties VALUES (?,?)", [(1,"02ABCDE1234F1Z5"),(2,"02ABCDE1234F1Z5"),(3,"07ZZZZZ9999Z1Z9"),(4,"09YYYYY8888Y1Y8")])
db.executemany("INSERT INTO invoices VALUES (?,?,?,?)", [(10,1,"PURCHASE","AB-123"),(11,3,"PURCHASE","AB-123"),(12,1,"SALE","AB-123")])
params = {"partyId":2,"supplierGstin":"02ABCDE1234F1Z5","normalizedNumber":"ab-123","excludeId":0}
assert db.execute(sql, params).fetchone()[0] == 1  # same GSTIN, other party
params.update(partyId=4, supplierGstin="09YYYYY8888Y1Y8")
assert db.execute(sql, params).fetchone()[0] == 0  # unrelated supplier
params.update(partyId=1, supplierGstin="02ABCDE1234F1Z5", excludeId=10)
assert db.execute(sql, params).fetchone()[0] == 0  # editing itself
checks['SQLite behavior: same GSTIN duplicate is detected; unrelated supplier and self-edit are not'] = True
for name, ok in checks.items(): print(('PASS' if ok else 'FAIL') + ': ' + name)
assert all(checks.values())
print('Development 51 Duplicate Purchase Detection checks: PASS')
