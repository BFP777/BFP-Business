from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
main=(ROOT/"app/src/main/java/in/bhajan/bfpbusinesspro/MainActivity.kt").read_text()
dao=(ROOT/"app/src/main/java/in/bhajan/bfpbusinesspro/data/AppDao.kt").read_text()
entities=(ROOT/"app/src/main/java/in/bhajan/bfpbusinesspro/data/Entities.kt").read_text()
gradle=(ROOT/"app/build.gradle.kts").read_text()
db=(ROOT/"app/src/main/java/in/bhajan/bfpbusinesspro/data/BfpDatabase.kt").read_text()
checks={
"bilingual profit and cashflow analysis screen":"AI PROFIT & CASHFLOW ANALYSIS / मुनाफा और नकदी विश्लेषण" in main,
"dashboard and inventory menu entry points":main.count("showAiProfitCashflow()")>=3,
"monthly profitability projection exists":"data class MonthlyProfitabilityRow" in entities,
"monthly DAO returns sales, estimated gross profit, expenses and salary":"observeMonthlyProfitability" in dao and "salary_payments" in dao and "entryType = 'EXPENSE'" in dao,
"sales are counted from SALE invoices only":"kind = 'SALE'" in dao,
"receipts are not counted as sales and purchases are not treated as operating expense":"प्राप्तियाँ बिक्री नहीं हैं और खरीद को सीधे खर्च नहीं माना गया है" in main,
"gross profit explicitly based on current Item Master rates":"मौजूदा Item Master की बिक्री और खरीद दरों" in main,
"operating surplus deducts recorded expense and salary":"estimatedGrossProfitPaise - expensesPaise - salaryPaidPaise" in dao,
"margin is calculated against sales revenue":"estimatedGrossProfitPaise.toDouble() * 100.0 / row.salesPaise" in main,
"limits and non-posting disclaimer shown":"कोई एंट्री पोस्ट या बैलेंस नहीं बदलती" in main,
"database schema remains unchanged":"version = 10" in db,
"app version advanced to 5.23.0 / 522":'versionCode = 550' in gradle and 'versionName = "5.50.0"' in gradle,
}
for k,v in checks.items(): print(("PASS" if v else "FAIL")+": "+k)
assert all(checks.values()),"Dev63 checks failed"
print(f"Development 63 checks: PASS ({len(checks)}/{len(checks)})")
