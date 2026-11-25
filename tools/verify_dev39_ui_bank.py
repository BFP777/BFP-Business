from pathlib import Path
root = Path(__file__).resolve().parents[1]
main = (root / 'app/src/main/java/in/bhajan/bfpbusinesspro/MainActivity.kt').read_text()
entities = (root / 'app/src/main/java/in/bhajan/bfpbusinesspro/data/Entities.kt').read_text()
db = (root / 'app/src/main/java/in/bhajan/bfpbusinesspro/data/BfpDatabase.kt').read_text()
dao = (root / 'app/src/main/java/in/bhajan/bfpbusinesspro/data/AppDao.kt').read_text()
assert 'private fun showDashboard()' in main and 'Recent Transactions' in main and 'Quick Links' in main
assert 'private fun showMenu()' in main and 'HOME' in main and 'DASHBOARD' in main and 'ITEMS' in main and 'MENU' in main
assert 'private fun showBankAccounts()' in main and 'private fun bankAccountDialog' in main
assert 'data class BankAccountEntity' in entities and 'val accountName: String' in entities
assert 'version = 10' in db and 'MIGRATION_3_4' in db and 'MIGRATION_4_5' in db
assert 'observeBankAccounts()' in dao and 'countBankAccountName' in dao
print('Development 39 UI and bank-account source checks: PASS')
