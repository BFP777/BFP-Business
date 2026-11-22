from pathlib import Path
root = Path(__file__).resolve().parents[1]
main = (root / "app/src/main/java/in/bhajan/bfpbusinesspro/MainActivity.kt").read_text()
entities = (root / "app/src/main/java/in/bhajan/bfpbusinesspro/data/Entities.kt").read_text()
db = (root / "app/src/main/java/in/bhajan/bfpbusinesspro/data/BfpDatabase.kt").read_text()
repo = (root / "app/src/main/java/in/bhajan/bfpbusinesspro/data/PaymentRepository.kt").read_text()
ledger = (root / "app/src/main/java/in/bhajan/bfpbusinesspro/data/LedgerRepository.kt").read_text()
assert "val bankAccountId: Long? = null" in entities
assert "ALTER TABLE payments ADD COLUMN bankAccountId INTEGER" in db
assert "ALTER TABLE cash_entries ADD COLUMN bankAccountId INTEGER" in db
assert "MIGRATION_4_5" in db
assert "बैंक अकाउंट चुनें" in repo and "dao.getBankAccount(input.bankAccountId)" in repo
assert "bankAccountBalancePaise" in ledger
assert "PaymentRepository.PaymentInput" in main and "selectedBankId" in main
assert "countBankAccountUsage" in main
print("Development 40 bank-account transaction linking checks: PASS")
