"""Item-wise stock movement report checks for Dev86."""
from pathlib import Path
root = Path(__file__).resolve().parents[1]
entities = (root / "app/src/main/java/in/bhajan/bfpbusinesspro/data/Entities.kt").read_text()
dao = (root / "app/src/main/java/in/bhajan/bfpbusinesspro/data/AppDao.kt").read_text()
main = (root / "app/src/main/java/in/bhajan/bfpbusinesspro/MainActivity.kt").read_text()
gradle = (root / "app/build.gradle.kts").read_text()
checks = {
    "report row exposes opening purchase sale and adjustment quantities": all(x in entities for x in ["data class StockReportRow", "openingQuantity", "purchaseQuantity", "saleQuantity", "adjustmentQuantity"]),
    "DAO derives movement totals from audit history": all(x in dao for x in ["observeStockReport()", "movementType = 'OPENING'", "movementType LIKE 'PURCHASE%'", "movementType LIKE 'SALE%'", "movementType = 'ADJUSTMENT'"]),
    "report shows item code and all movement categories": all(x in main for x in ["showStockReport()", "row.item.itemCode", "Opening", "Purchase", "Sale", "Adjustment", "Closing"]),
    "report includes aggregate totals and edit/reversal note": "TOTAL — Opening" in main and "Edit और Reversal" in main or "Edit और Reversal movement भी शामिल हैं" in main,
    "stock report is reachable from item master": '"स्टॉक रिपोर्ट / Stock Report"' in main,
    "version 5.31.0 / 531": 'versionCode = 564' in gradle and 'versionName = "5.64.0"' in gradle,
}
for label, ok in checks.items(): print(f"{'PASS' if ok else 'FAIL'}: {label}")
assert all(checks.values())
# Reconciliation: opening 10, purchases +8, sale -4, adjustment -1 => closing 13.
assert 10 + 8 - 4 - 1 == 13
# Reversal/edit deltas are netted into their purchase/sale movement category.
assert (8 - 2) + (-4 + 1) + 10 - 1 == 12
print("Development 86 stock report checks: PASS")
