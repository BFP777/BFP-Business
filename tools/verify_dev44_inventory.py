from pathlib import Path
root = Path(__file__).resolve().parents[1]
entities = (root / 'app/src/main/java/in/bhajan/bfpbusinesspro/data/Entities.kt').read_text()
db = (root / 'app/src/main/java/in/bhajan/bfpbusinesspro/data/BfpDatabase.kt').read_text()
dao = (root / 'app/src/main/java/in/bhajan/bfpbusinesspro/data/AppDao.kt').read_text()
repo = (root / 'app/src/main/java/in/bhajan/bfpbusinesspro/data/InvoiceRepository.kt').read_text()
ui = (root / 'app/src/main/java/in/bhajan/bfpbusinesspro/MainActivity.kt').read_text()
assert 'Index(value = ["itemCode"], unique = true)' in entities
assert 'val itemCode: String' in entities and 'val itemCodeSnapshot: String' in entities
assert 'val attachmentPath: String' in entities
assert 'version = 11' in db and 'MIGRATION_5_6' in db
assert 'ALTER TABLE invoice_lines ADD COLUMN itemCodeSnapshot TEXT NOT NULL DEFAULT' in db
assert "UPDATE items SET itemCode = 'ITM-' || id" in db
assert 'CREATE UNIQUE INDEX IF NOT EXISTS index_items_itemCode' in db
assert 'getItemByCode(code: String)' in dao and 'findItemCodeConflict' in dao
assert 'validateAndPostStock(input, emptyList())' in repo
assert 'finalQty >= -0.000001' in repo and 'reverseStock(invoice.kind, lines, invoice.invoiceNumber, invoice.dateEpochMillis)' in repo
assert 'itemId = line.itemId' in repo and 'itemCodeSnapshot = line.itemCode.trim()' in repo
assert 'private fun showItems()' in ui and 'private fun itemDialog(existing: ItemEntity?)' in ui
assert 'Item Code से आइटम खोजें' in ui and 'row.itemId' in ui
print('Development 44 Item Code + inventory posting checks: PASS')
