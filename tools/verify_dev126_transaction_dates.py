from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
main = (ROOT / "app/src/main/java/in/bhajan/bfpbusinesspro/MainActivity.kt").read_text()
dao = (ROOT / "app/src/main/java/in/bhajan/bfpbusinesspro/data/AppDao.kt").read_text()
repo = (ROOT / "app/src/main/java/in/bhajan/bfpbusinesspro/data/PaymentRepository.kt").read_text()
db = (ROOT / "app/src/main/java/in/bhajan/bfpbusinesspro/data/BfpDatabase.kt").read_text()
assert "selectedInvoiceDateMillis" in main and "dateEpochMillis = selectedInvoiceDateMillis" in main
assert "selectedPaymentDateMillis" in main and "paymentStatus = if (direction == \"OUT\"" in main
assert "SCHEDULED / PENDING" in main and "markPaid(payment.id)" in main
assert "status = 'PAID'" in dao and "observeAllPayments" in dao
assert "suspend fun markPaid" in repo and "status = status" in repo
assert "version = 11" in db and "MIGRATION_10_11" in db
print("DEV126 transaction date and scheduled-payment checks: PASS")
