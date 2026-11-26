from pathlib import Path
root = Path(__file__).resolve().parents[1]
main = (root / 'app/src/main/java/in/bhajan/bfpbusinesspro/MainActivity.kt').read_text()
gradle = (root / 'app/build.gradle.kts').read_text()
dao = (root / 'app/src/main/java/in/bhajan/bfpbusinesspro/data/AppDao.kt').read_text()
checks = {
    'decision assistant screen exists': 'private fun showAiDecisionAssistant()' in main,
    'dashboard entry point': 'addAction(page, "AI DECISION ASSISTANT / AI निर्णय सहायक") { showAiDecisionAssistant() }' in main,
    'menu entry point': 'AI Decision Assistant / AI निर्णय सहायक' in main and 'showAiDecisionAssistant()' in main,
    '30-day sales compared with prior 30 days': 'observeBusinessDashboard(now - 30L * day' in main and 'now - 60L * day' in main,
    'stock advice uses item demand forecast': 'observeItemDemandForecast(now - 60L * day' in main,
    'customer receivables insight': 'observeCustomerInsights().first()' in main,
    'supplier payables insight': 'observeSupplierPurchaseSummaries().first()' in main,
    'supplier rate change insight': 'observeSupplierItemRateChanges().first()' in main,
    'supplier rate DAO uses Room-compatible SELECT without CTE/window functions': 'WITH ranked AS' not in dao and 'ROW_NUMBER() OVER' not in dao and 'AS previousRatePaise' in dao,
    'supplier rate comparison matches same supplier and item': 'priorInvoice.partyId = i.partyId' in dao and 'prior.itemId = il.itemId' in dao and 'newer.itemId = il.itemId' in dao,
    'no automatic posting disclosed': 'कोई खरीद, बिक्री, भुगतान, स्टॉक या अकाउंटिंग एंट्री अपने-आप नहीं बनेगी' in main,
    'no data case disclosed': 'डेटा अभी कम है / More data needed' in main,
    'version 5.23.0 code 522': 'versionCode = 557' in gradle and 'versionName = "5.57.0"' in gradle,
}
for name, ok in checks.items():
    print(('PASS' if ok else 'FAIL') + ': ' + name)
if not all(checks.values()):
    raise SystemExit(1)
print(f'Development 64 checks: PASS ({len(checks)}/{len(checks)})')
