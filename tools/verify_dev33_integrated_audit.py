"""Source-level integration invariants for Development 33."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
app_dao = (root / "app/src/main/java/in/bhajan/bfpbusinesspro/data/AppDao.kt").read_text()
backup = (root / "app/src/main/java/in/bhajan/bfpbusinesspro/backup/DatabaseBackup.kt").read_text()
ledger = (root / "app/src/main/java/in/bhajan/bfpbusinesspro/data/LedgerRepository.kt").read_text()
reports = (root / "app/src/main/java/in/bhajan/bfpbusinesspro/data/ReportsRepository.kt").read_text()
payments = (root / "app/src/main/java/in/bhajan/bfpbusinesspro/data/PaymentRepository.kt").read_text()
invoices = (root / "app/src/main/java/in/bhajan/bfpbusinesspro/data/InvoiceRepository.kt").read_text()
db = (root / "app/src/main/java/in/bhajan/bfpbusinesspro/data/BfpDatabase.kt").read_text()

# Invoice number uniqueness trims both sides and is case-insensitive.
assert "trim(invoiceNumber) = trim(:normalizedNumber) COLLATE NOCASE" in app_dao
# Backup rejects corrupt, wrong-version, or incomplete business databases before restore.
assert 'PRAGMA integrity_check' in backup and 'PRAGMA user_version' in backup
assert 'containsAll(requiredTables)' in backup
assert 'BfpDatabase.get(context).openHelper.writableDatabase' in backup
# Explicit migrations are registered; no destructive fallback is enabled.
assert '.addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)' in db
assert 'fallbackToDestructiveMigration' not in db
# Payment records are authoritative for invoice paid totals.
assert 'dao.sumInvoicePayments(invoice.id)' in payments
assert 'dao.sumInvoicePayments(invoiceId)' in invoices
# Day Book excludes invoices from cash movement totals and only uses cash-mode payments.
assert 'payments.filter { it.mode == "CASH"' in ledger
assert 'opening + periodCashIn - periodCashOut' in ledger
# Outstanding reports include linked invoice paid values and unallocated advances.
assert 'it.totalPaise - it.paidPaise' in reports
assert 'val direction = if (receivable) "IN" else "OUT"' in reports
assert 'it.invoiceId == null && it.direction == direction' in reports
print('Development 33 integrated source audit: PASS')
