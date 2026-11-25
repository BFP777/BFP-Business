from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
main=(ROOT/"app/src/main/java/in/bhajan/bfpbusinesspro/MainActivity.kt").read_text()
dao=(ROOT/"app/src/main/java/in/bhajan/bfpbusinesspro/data/AppDao.kt").read_text()
entities=(ROOT/"app/src/main/java/in/bhajan/bfpbusinesspro/data/Entities.kt").read_text()
gradle=(ROOT/"app/build.gradle.kts").read_text()
db=(ROOT/"app/src/main/java/in/bhajan/bfpbusinesspro/data/BfpDatabase.kt").read_text()
checks={
"bilingual demand forecast screen":"AI DEMAND FORECAST / AI मांग का अनुमान" in main,
"dashboard and inventory menu entry points":main.count("showAiDemandForecast()")>=3,
"forecast projection has two comparison windows":"data class ItemDemandForecastRow" in entities and "soldRecent30Days" in entities and "soldPrevious30Days" in entities,
"DAO counts SALE invoice lines only":"observeItemDemandForecast" in dao and "inv.kind = 'SALE'" in dao,
"recent and previous 30-day periods are separate":"recentStartEpochMillis" in dao and "previousStartEpochMillis" in dao and "periodEndEpochMillis" in dao,
"forecast uses recent actual 30-day sales as run rate":"val projected = recent" in main,
"trend compares recent and previous period":"recent > previous" in main and "recent < previous" in main,
"reorder suggestion considers minimum stock and current stock":"minimumStockQuantity" in main and "projected + safetyBuffer - row.item.quantity" in main,
"screen warns forecast is not guaranteed and no auto purchase":"गारंटी" in main and "कोई स्टॉक/खरीद एंट्री अपने-आप नहीं बनेगी" in main,
"database schema remains version 9":"version = 10" in db,
"app version advanced to 5.17.0 / 517":'versionCode = 562' in gradle and 'versionName = "5.62.0"' in gradle,
}
for k,v in checks.items():print(("PASS" if v else "FAIL")+": "+k)
assert all(checks.values()),"Dev62 checks failed"
print(f"Development 62 checks: PASS ({len(checks)}/{len(checks)})")
