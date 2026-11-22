"""Regression checks for named-bank visibility in Day Book rows."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
src = (ROOT / "app/src/main/java/in/bhajan/bfpbusinesspro/data/LedgerRepository.kt").read_text()
assert 'val bankNames = dao.observeBankAccounts().first().associate { it.id to it.accountName }' in src
assert 'p.bankAccountId?.let(bankNames::get)' in src
assert 'e.bankAccountId?.let(bankNames::get)' in src
assert '" — बैंक खाता उपलब्ध नहीं"' in src
assert '"Cash से Bank में जमा$bankLabel"' in src
assert '"Bank से Cash निकासी$bankLabel"' in src
assert '"Opening Balance (${e.mode})$bankLabel"' in src
# Displayed labels must remain descriptive without exposing account number/IFSC fields.
assert 'accountName' in src
assert 'accountNumber' not in src and 'ifsc' not in src.lower()
print("Development 42 Day Book bank-name checks: PASS")
