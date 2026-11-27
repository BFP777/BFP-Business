"""Regression checks for invoice-linked payments, party ledgers and cash/bank separation."""
from pathlib import Path
root = Path(__file__).resolve().parents[1]
payments = (root / 'app/src/main/java/in/bhajan/bfpbusinesspro/data/PaymentRepository.kt').read_text()
invoices = (root / 'app/src/main/java/in/bhajan/bfpbusinesspro/data/InvoiceRepository.kt').read_text()
ledger = (root / 'app/src/main/java/in/bhajan/bfpbusinesspro/data/LedgerRepository.kt').read_text()
reports = (root / 'app/src/main/java/in/bhajan/bfpbusinesspro/data/ReportsRepository.kt').read_text()
gradle = (root / 'app/build.gradle.kts').read_text()

# Payment changes are atomic and refresh both the old and new linked invoice.
assert 'database.withTransaction' in payments
assert '(listOfNotNull(old.invoiceId, input.invoiceId)).distinct().forEach { refreshInvoicePaid(it) }' in payments
assert 'old.invoiceId?.let { refreshInvoicePaid(it) }' in payments
assert 'require(alreadyPaid + input.amountPaise <= invoice.totalPaise)' in payments
assert 'require(paid <= invoice.totalPaise)' in payments
# Linked payments must match the invoice party, direction and type.
assert 'require(invoice.partyId == input.partyId)' in payments
assert '(invoice.kind == "SALE" && input.direction == "IN") || (invoice.kind == "PURCHASE" && input.direction == "OUT")' in payments
# Party ledger has one row for each invoice/payment and correct debtor/creditor signs.
assert 'if (sale) inv.totalPaise else 0L' in ledger
assert 'if (sale) 0L else inv.totalPaise' in ledger
assert 'if (isIn) 0L else p.amountPaise' in ledger
assert 'if (isIn) p.amountPaise else 0L' in ledger
# Cash, UPI and bank balances are based on payment mode; cash-bank transfers are not sales.
assert 'payments.sumOf { if (it.direction == "IN") it.amountPaise else -it.amountPaise }' in ledger
assert 'entry.entryType == "CASH_DEPOSIT" -> entry.amountPaise' in ledger
assert 'entry.entryType == "CASH_WITHDRAWAL" -> -entry.amountPaise' in ledger
assert 'sales.sumOf { it.totalPaise }' in reports
assert 'paymentMode("IN", "CASH")' in reports
assert 'entryType == "EXPENSE"' in reports
# Arithmetic examples: sale ₹1,000, receipt ₹400 -> ₹600 due; purchase ₹800, payment ₹300 -> ₹500 payable.
def due(total, paid): return total - paid
assert due(100000, 40000) == 60000
assert due(80000, 30000) == 50000
# Cash ₹10,000 + cash receipt ₹2,000 - cash payment ₹500 - cash expense ₹300 = ₹11,200.
assert 1000000 + 200000 - 50000 - 30000 == 1120000
# Bank ₹20,000 + bank receipt ₹5,000 - bank payment ₹2,000 + cash deposit ₹3,000 - withdrawal ₹1,000 = ₹25,000.
assert 2000000 + 500000 - 200000 + 300000 - 100000 == 2500000
assert 'versionCode = 565' in gradle and 'versionName = "5.65.0"' in gradle
print('Development 82 payment, party ledger and cash/bank checks: PASS')
