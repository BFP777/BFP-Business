from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
main=(ROOT/"app/src/main/java/in/bhajan/bfpbusinesspro/MainActivity.kt").read_text()
gradle=(ROOT/"app/build.gradle.kts").read_text()
db=(ROOT/"app/src/main/java/in/bhajan/bfpbusinesspro/data/BfpDatabase.kt").read_text()
checks={
"bilingual AI Business Intelligence screen":"AI BUSINESS INTELLIGENCE / AI कारोबार विश्लेषण" in main,
"dashboard and menu links":main.count("showAiBusinessIntelligence()")>=3,
"slow movers have stock and no 90-day sales":"it.item.quantity > 0.0 && it.soldLast90Days <= 0.0" in main,
"fast movers ranked by 90-day sales":"sortedByDescending { it.soldLast90Days }" in main,
"slow-mover action is advisory":"डिस्प्ले/ऑफर या अगली खरीद मात्रा की समीक्षा करें" in main,
"supplier purchase rate increases identified":"observeSupplierItemRateChanges().collectLatest" in main and "it.latestRatePaise > it.previousRatePaise" in main,
"absolute and percentage rate increase displayed":"बढ़ोतरी:" in main and "previousRatePaise" in main,
"no automatic accounting changes stated":"कोई बिक्री, खरीद, स्टॉक, भुगतान या अकाउंटिंग एंट्री नहीं बदली जाएगी" in main,
"existing read-only queries and unchanged schema":"observeItemPerformance(cutoff)" in main and "observeSupplierItemRateChanges()" in main and "version = 10" in db,
"version 5.16.0 code 516":"versionCode = 561" in gradle and "versionName = \"5.61.0\"" in gradle,
}
for k,v in checks.items():print(("PASS" if v else "FAIL")+": "+k)
assert all(checks.values()),"Dev61 checks failed"
print(f"Development 61 checks: PASS ({len(checks)}/{len(checks)})")
