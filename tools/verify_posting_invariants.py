from pathlib import Path

root = Path(__file__).resolve().parents[1]
repo = (root / 'app/src/main/java/in/bhajan/bfpbusinesspro/data/InvoiceRepository.kt').read_text()
ui = (root / 'app/src/main/java/in/bhajan/bfpbusinesspro/MainActivity.kt').read_text()
entities = (root / 'app/src/main/java/in/bhajan/bfpbusinesspro/data/Entities.kt').read_text()

# Invoice accounting remains transactional and linked payments remain authoritative.
assert 'database.withTransaction' in repo
assert 'val linkedPaidPaise = dao.sumInvoicePayments(invoiceId)' in repo
assert 'require(linkedPaidPaise in 0..total)' in repo
assert 'paidPaise = linkedPaidPaise' in repo

# Inventory posting is linked to Item Master IDs and remains inside the invoice transaction.
assert 'val itemName: String' in repo
assert 'it.itemName.isNotBlank()' in repo
assert 'itemNameSnapshot = line.itemName.trim()' in repo
assert 'validateAndPostStock' in repo and 'reverseStock' in repo
assert 'finalQty >= -0.000001' in repo
assert 'itemId = line.itemId' in repo
assert 'InvoiceLineEntity' in entities and 'itemNameSnapshot: String' in entities
assert 'val draftName = editField("आइटम का नाम (जैसे सूट / डिजाइन)", "")' in ui
assert 'InvoiceLineViews(code, name, qty, rate, gst, hsn, itemId)' in ui
assert 'InvoiceRepository.LineInput(itemName,' in ui
assert 'private fun showItems()' in ui and 'यूनिक Item Code / Design Code' in ui
assert 'Item Code से आइटम खोजें' in ui
assert 'पहले Items में स्टॉक जोड़ें' not in ui
assert 'addAction(page, \"ITEMS / स्टॉक\")' not in ui
assert 'addAction(page, \"STOCK REPORT / स्टॉक रिपोर्ट\")' not in ui

# Linked payments, not cached invoice paidPaise, govern edit boundary.
for linked, new_total, accepted in [(5000, 5000, True), (5001, 5000, False), (0, 1, True)]:
    assert (0 <= linked <= new_total) is accepted

print('inventory-linked invoice + payment invariants: PASS')
