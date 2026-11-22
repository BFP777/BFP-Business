from pathlib import Path

root = Path(__file__).resolve().parents[1]
main = (root / 'app/src/main/java/in/bhajan/bfpbusinesspro/MainActivity.kt').read_text()
repo = (root / 'app/src/main/java/in/bhajan/bfpbusinesspro/data/InvoiceRepository.kt').read_text()

checks = {
    'new sale shows CASH and CREDIT beside bill number': 'addView(number, LinearLayout.LayoutParams(0, -2, 1.15f))' in main and 'text = "CASH"' in main and 'text = "CREDIT"' in main,
    'new sale defaults to CREDIT': 'var saleSettlementMode = "CREDIT"' in main and 'text = "CREDIT"; textSize = 16f; isChecked = true' in main,
    'payment selector only appears for a new sale': 'kind == "SALE" && existing == null' in main,
    'CASH sale uses atomic invoice plus receipt transaction': 'repo.createCashSale(input)' in main and 'database.withTransaction' in repo,
    'CASH receipt is linked to invoice and customer': 'invoiceId = invoice.id' in repo and 'partyId = invoice.partyId' in repo,
    'CASH receipt posts full bill amount as Payment In': 'direction = "IN"' in repo and 'mode = "CASH"' in repo and 'amountPaise = invoice.totalPaise' in repo,
    'CASH receipt updates invoice paid amount': 'paidPaise = invoice.totalPaise' in repo,
    'CREDIT uses ordinary invoice creation without receipt': 'saleSettlementMode == "CASH"' in main and 'else repo.create(input)' in main,
    'purchase flow remains independent of sale payment selector': 'kind == "SALE" && existing == null' in main,
}
for label, ok in checks.items():
    print(f"{'PASS' if ok else 'FAIL'}: {label}")
assert all(checks.values())
print('Development 72 CASH/CREDIT sale checks: PASS')
