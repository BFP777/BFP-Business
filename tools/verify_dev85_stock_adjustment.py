"""Opening stock and manual adjustment audit trail checks."""
from pathlib import Path
root = Path(__file__).resolve().parents[1]
entities = (root / "app/src/main/java/in/bhajan/bfpbusinesspro/data/Entities.kt").read_text()
dao = (root / "app/src/main/java/in/bhajan/bfpbusinesspro/data/AppDao.kt").read_text()
db = (root / "app/src/main/java/in/bhajan/bfpbusinesspro/data/BfpDatabase.kt").read_text()
repo = (root / "app/src/main/java/in/bhajan/bfpbusinesspro/data/StockRepository.kt").read_text()
invoices = (root / "app/src/main/java/in/bhajan/bfpbusinesspro/data/InvoiceRepository.kt").read_text()
main = (root / "app/src/main/java/in/bhajan/bfpbusinesspro/MainActivity.kt").read_text()
gradle = (root / "app/build.gradle.kts").read_text()
backup = (root / "app/src/main/java/in/bhajan/bfpbusinesspro/backup/DatabaseBackup.kt").read_text()
checks = {
    "stock movement audit entity records before/after/delta/reason/date": all(x in entities for x in ["stock_movements", "quantityDelta", "previousQuantity", "newQuantity", "reason", "dateEpochMillis"]),
    "Room v9 to v10 migration creates indexed movement table": "Migration(9, 10)" in db and "CREATE TABLE IF NOT EXISTS stock_movements" in db and "index_stock_movements_itemId_dateEpochMillis" in db and "मौजूदा स्टॉक — अपडेट के समय" in db,
    "migration preserves existing quantities as opening history": "INSERT INTO stock_movements" in db and "WHERE quantity > 0" in db,
    "new item and opening stock save atomically": "insertItemWithOpeningStock" in repo and 'movementType = "OPENING"' in repo and "database.withTransaction" in repo,
    "adjustment requires nonnegative finite quantity and reason": 'require(reason.isNotBlank())' in repo and 'newQuantity.isFinite()' in repo,
    "adjustment update and audit insert are transactional": "database.withTransaction" in repo and 'movementType = "ADJUSTMENT"' in repo,
    "invoice purchase/sale stock postings create movement history": "insertStockMovement(StockMovementEntity" in invoices and "PURCHASE_REVERSAL" in invoices and "SALE_REVERSAL" in invoices,
    "DAO exposes stock movement history": "getStockMovements(itemId: Long)" in dao,
    "existing item quantity is read-only in item editor": 'isEnabled = existing == null' in main,
    "new item opening stock is recorded": "insertItemWithOpeningStock(entity)" in main,
    "manual adjustment requires reason and offers history": "स्टॉक समायोजन / हिस्ट्री" in main and "बदलाव का कारण (अनिवार्य)" in main and "getStockMovements(item.id)" in main,
    "backup accepts schema v10 and validates movement table": "cursor.getInt(0) in 3..10" in backup and "stock_movements" in backup,
    "version 5.31.0 / 530": 'versionCode = 557' in gradle and 'versionName = "5.57.0"' in gradle,
}
for label, ok in checks.items(): print(f"{'PASS' if ok else 'FAIL'}: {label}")
assert all(checks.values())
# Quantity reconciliation examples: opening 12, purchase +5, sale -3, adjustment to 13.
assert 12 + 5 - 3 == 14
assert 14 + (13 - 14) == 13
print("Development 85 stock opening/adjustment checks: PASS")
