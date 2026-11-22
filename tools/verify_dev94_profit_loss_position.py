from pathlib import Path
root=Path(__file__).resolve().parents[1]
repo=(root/'app/src/main/java/in/bhajan/bfpbusinesspro/data/ReportsRepository.kt').read_text()
main=(root/'app/src/main/java/in/bhajan/bfpbusinesspro/MainActivity.kt').read_text()
gradle=(root/'app/build.gradle.kts').read_text()
db=(root/'app/src/main/java/in/bhajan/bfpbusinesspro/data/BfpDatabase.kt').read_text()
checks={
 'period-based P&L statement': 'suspend fun financialStatement(' in repo and 'estimatedOperatingProfitPaise' in repo,
 'sales revenue excludes GST and discounts': 'subtotalPaise - it.discountPaise' in repo,
 'COGS uses explicit estimated current item purchase rate': 'purchaseRatePaise' in repo and 'missingCostLines' in repo,
 'operating expenses and salary are separate': 'operatingExpensesPaise' in repo and 'salaryPaise' in repo,
 'financial position includes liquid balances, stock, receivables and payables': all(x in repo for x in ['money.cashPaise','money.upiPaise','money.bankPaise','stockValue','balances.debtorsPaise','balances.creditorsPaise']),
 'UI labels statement as estimate and discloses limitations': 'not a certified statutory P&L or legal Balance Sheet' in main and 'historical batch costs' in main,
 'accessible from Reports': 'showFinancialStatement()' in main and 'Profit & Loss + Financial Position' in main,
 'version 5.43.0 / 539': 'versionCode = 550' in gradle and 'versionName = "5.50.0"' in gradle,
 'Room schema unchanged': 'version = 10' in db,
}
for k,v in checks.items(): print(('PASS' if v else 'FAIL')+': '+k)
assert all(checks.values())
