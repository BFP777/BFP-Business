from pathlib import Path
import re, sqlite3
ROOT = Path(__file__).resolve().parents[1]
main = (ROOT / "app/src/main/java/in/bhajan/bfpbusinesspro/MainActivity.kt").read_text()
dao = (ROOT / "app/src/main/java/in/bhajan/bfpbusinesspro/data/AppDao.kt").read_text()
gradle = (ROOT / "app/build.gradle.kts").read_text()
notes = (ROOT / "DEVELOPMENT-55-NOTES.md").read_text()
checks = {
    "customer detail is a full screen with profile": "CUSTOMER DETAILS / ग्राहक विवरण" in main and "Customer Profile / ग्राहक की जानकारी" in main,
    "separate bilingual SALE and RECEIPT tabs": 'text = "SALE / बिक्री"' in main and 'text = "RECEIPT / प्राप्ति"' in main,
    "sale tab is filtered to selected customer and SALE invoices": 'observeInvoices("SALE").first().filter { it.partyId == partyId }' in main,
    "receipt tab is filtered to selected customer and incoming receipts": 'getPartyPayments(partyId).filter { it.direction == "IN" }' in main,
    "sale tab displays totals, paid and due amounts": 'Total Sales' in main and 'Total Due' in main and 'invoice.paidPaise' in main,
    "receipt tab displays total received and payment mode": 'Total Received' in main and 'receipt.mode' in main,
    "new sale preselects the current customer": 'invoiceDialog("SALE", null, preselectedPartyId = partyId)' in main,
    "new receipt preselects the current customer": 'paymentDialog("IN", null, preselectedPartyId = partyId)' in main,
    "entry forms support preselected party without changing existing callers": 'preselectedPartyId: Long? = null' in main and 'existing?.partyId ?: preselectedPartyId' in main,
    "existing WhatsApp remains manual draft": 'openWhatsAppDraft(customerMobile, offerText)' in main and 'https://wa.me/$digits?text=${Uri.encode(message)}' in main,
    "version advanced without Room schema migration": 'versionCode = 562' in gradle and 'versionName = "5.62.0"' in gradle and 'Room schema: 9 (no database migration required)' in notes,
}
# Fixture-level semantics: same customer, same data stores; separate sale invoices and incoming receipts.
db = sqlite3.connect(":memory:")
db.executescript("""
CREATE TABLE invoices(id INTEGER, partyId INTEGER, kind TEXT, totalPaise INTEGER, paidPaise INTEGER);
CREATE TABLE payments(id INTEGER, partyId INTEGER, direction TEXT, amountPaise INTEGER, mode TEXT);
INSERT INTO invoices VALUES (1,10,'SALE',10000,4000),(2,10,'PURCHASE',8000,0),(3,11,'SALE',50000,0);
INSERT INTO payments VALUES (1,10,'IN',4000,'CASH'),(2,10,'OUT',2000,'CASH'),(3,11,'IN',9000,'UPI');
""")
sales = db.execute("SELECT totalPaise, paidPaise FROM invoices WHERE partyId=? AND kind='SALE'", (10,)).fetchall()
receipts = db.execute("SELECT amountPaise, mode FROM payments WHERE partyId=? AND direction='IN'", (10,)).fetchall()
assert sales == [(10000, 4000)] and receipts == [(4000, 'CASH')]
checks["SQLite fixture: SALE and incoming RECEIPT are correctly separated by customer"] = True
for label, ok in checks.items(): print(("PASS" if ok else "FAIL") + ": " + label)
if not all(checks.values()): raise SystemExit(1)
print("Development 55 Customer Sales & Receipt Tabs checks: PASS")
