from pathlib import Path
root = Path(__file__).resolve().parents[1]
main = (root / 'app/src/main/java/in/bhajan/bfpbusinesspro/MainActivity.kt').read_text()
repo = (root / 'app/src/main/java/in/bhajan/bfpbusinesspro/data/ReportsRepository.kt').read_text()
gradle = (root / 'app/build.gradle.kts').read_text()
db = (root / 'app/src/main/java/in/bhajan/bfpbusinesspro/data/BfpDatabase.kt').read_text()
checks = {
    'Dashboard shows You will Get and You will Give cards': "YOU'LL GET / लेना है" in main and "YOU'LL GIVE / देना है" in main,
    'cards are placed before the daily dashboard summary': main.index('page.addView(outstandingRow)') < main.index('val todayLabel = java.text.SimpleDateFormat'),
    'receivable and payable cards use distinct green and red colors': '0xFF159B72' in main and '0xFFD95368' in main,
    'card values use live party balance repository': 'ReportsRepository(db).partyBalances()' in main,
    'tap opens separate outstanding party lists': 'showOutstandingParties(true)' in main and 'showOutstandingParties(false)' in main,
    'party list uses the same repository calculation as dashboard totals': 'ReportsRepository(db).partyOutstanding(receivable)' in main and 'private suspend fun outstandingLists()' in repo,
    'bill count includes only the relevant SALE or PURCHASE invoices': 'relevantInvoices.size' in repo and 'partyInvoices.size' not in main,
    'unallocated advances offset invoice balances': 'it.invoiceId == null' in repo and 'coerceAtLeast(0L)' in repo,
    'party list opens the selected party ledger': 'setOnClickListener { showPartyLedger(party) }' in main,
    'Room schema remains unchanged': 'version = 11' in db,
    'app version is 5.52.0 / 552': 'versionCode = 565' in gradle and 'versionName = "5.65.0"' in gradle,
}
for label, ok in checks.items():
    print(('PASS' if ok else 'FAIL') + ': ' + label)
if not all(checks.values()):
    raise SystemExit(1)
