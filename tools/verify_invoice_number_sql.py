"""Small SQLite check for the invoice-number uniqueness query used by AppDao."""
import sqlite3

con = sqlite3.connect(":memory:")
con.execute("CREATE TABLE invoices(id INTEGER PRIMARY KEY, invoiceNumber TEXT, kind TEXT)")
con.execute("INSERT INTO invoices(invoiceNumber, kind) VALUES (?, ?)", ("S-100", "SALE"))
query = "SELECT COUNT(*) FROM invoices WHERE kind = ? AND trim(invoiceNumber) = trim(?) COLLATE NOCASE AND id != ?"
assert con.execute(query, ("SALE", "s-100", 0)).fetchone()[0] == 1
assert con.execute(query, ("PURCHASE", "s-100", 0)).fetchone()[0] == 0
assert con.execute(query, ("SALE", "S-100", 1)).fetchone()[0] == 0
assert con.execute(query, ("SALE", "S-101", 0)).fetchone()[0] == 0
# Whitespace around an existing or newly entered number must not bypass uniqueness.
con.execute("INSERT INTO invoices(invoiceNumber, kind) VALUES (?, ?)", (" S-200 ", "SALE"))
assert con.execute(query, ("SALE", "S-200", 0)).fetchone()[0] == 1
assert con.execute(query, ("SALE", " S-200 ", 0)).fetchone()[0] == 1
print("invoice-number SQL checks: PASS")
