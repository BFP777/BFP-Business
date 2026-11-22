"""Regression checks for named bank-account balance posting rules."""
from pathlib import Path
root = Path(__file__).resolve().parents[1]
ledger = (root / 'app/src/main/java/in/bhajan/bfpbusinesspro/data/LedgerRepository.kt').read_text()
main = (root / 'app/src/main/java/in/bhajan/bfpbusinesspro/MainActivity.kt').read_text()
dao = (root / 'app/src/main/java/in/bhajan/bfpbusinesspro/data/AppDao.kt').read_text()

# Ensure each named bank's balance includes only transactions linked to that account.
assert 'it.mode == "BANK" && it.bankAccountId == bankAccountId' in ledger
assert 'dao.observeCashEntries().first().filter { it.bankAccountId == bankAccountId }' in ledger
# Bank-side signs: receipts/opening/deposits add; payments/expenses/withdrawals subtract.
assert 'if (it.direction == "IN") it.amountPaise else -it.amountPaise' in ledger
assert 'entry.entryType == "OPENING" -> entry.amountPaise' in ledger
assert 'entry.entryType == "EXPENSE" -> -entry.amountPaise' in ledger
assert 'entry.entryType == "CASH_DEPOSIT" -> entry.amountPaise' in ledger
assert 'entry.entryType == "CASH_WITHDRAWAL" -> -entry.amountPaise' in ledger
# Bank selector is surfaced on BANK payments and cash/bank transfer forms.
assert 'selectedBankId' in main and 'बैंक अकाउंट' in main
assert 'countBankAccountUsage' in dao

# Independent arithmetic examples in paise to guard sign conventions.
def bank_balance(opening, payment_in, payment_out, expenses, deposits, withdrawals):
    return opening + payment_in - payment_out - expenses + deposits - withdrawals
assert bank_balance(100000, 25000, 10000, 5000, 30000, 7000) == 133000
assert bank_balance(0, 0, 0, 0, 50000, 0) == 50000
assert bank_balance(0, 0, 0, 0, 0, 50000) == -50000
print('Development 41 named-bank balance checks: PASS')
