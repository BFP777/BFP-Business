from pathlib import Path
import re
import sqlite3
root = Path(__file__).resolve().parents[1]
entities = (root / 'app/src/main/java/in/bhajan/bfpbusinesspro/data/Entities.kt').read_text()
db = (root / 'app/src/main/java/in/bhajan/bfpbusinesspro/data/BfpDatabase.kt').read_text()
dao = (root / 'app/src/main/java/in/bhajan/bfpbusinesspro/data/AppDao.kt').read_text()
repo = (root / 'app/src/main/java/in/bhajan/bfpbusinesspro/data/EmployeePayrollRepository.kt').read_text()
ledger = (root / 'app/src/main/java/in/bhajan/bfpbusinesspro/data/LedgerRepository.kt').read_text()
reports = (root / 'app/src/main/java/in/bhajan/bfpbusinesspro/data/ReportsRepository.kt').read_text()
main = (root / 'app/src/main/java/in/bhajan/bfpbusinesspro/MainActivity.kt').read_text()
checks = {
    'employee master entity': '@Entity(tableName = "employees")' in entities,
    'unique employee-month payroll': 'Index(value = ["employeeId", "monthKey"], unique = true)' in entities,
    'salary payment records have mode and bank account': 'val mode: String, // CASH, UPI, BANK' in entities and 'val bankAccountId: Long? = null' in entities,
    'non-destructive payroll schema v7 migration retained': 'Migration(6, 7)' in db and 'MIGRATION_6_7' in db and 'version = 10' in db and 'MIGRATION_7_8' in db,
    'DAO employee and payroll access': 'observeEmployees()' in dao and 'observeSalaryPayments()' in dao and 'sumSalaryPayments' in dao,
    'salary month format validated': 'Regex("\\\\d{4}-(0[1-9]|1[0-2])")' in repo,
    'partial salary cannot exceed monthly payable': 'alreadyPaid + input.amountPaise <= month.payablePaise' in repo,
    'bank mode requires a valid named bank': 'dao.getBankAccount(input.bankAccountId) != null' in repo,
    'cash balance deducts salary': 'return paymentNet + entryNet - salaryOut' in ledger,
    'UPI and bank balances deduct salary': ledger.count('return paymentNet + entryNet - salaryOut') >= 3,
    'named bank balance deducts salary': 'it.bankAccountId == bankAccountId' in ledger and 'sumOf { it.amountPaise }' in ledger,
    'Day Book includes salary payment rows': 'वेतन भुगतान — $employeeName' in ledger,
    'Day Book cash out includes salary': '+ periodSalaryCash' in ledger,
    'reports include salary in expenses': 'salaryPayments.filter { it.mode == mode }.sumOf { it.amountPaise }' in reports,
    'bank account cannot be deleted while salary payment uses it': 'salary_payments WHERE bankAccountId = :id' in dao,
    'menu exposes employee payroll': 'Employees / Salary Ledger' in main,
    'employee master and monthly ledger screens': 'private fun showEmployees()' in main and 'private fun showEmployeeLedger' in main,
    'salary payment create/edit/delete': 'recordPayment(input)' in main and 'updatePayment(existing.id, input)' in main and 'deletePayment(payment.id)' in main,
}
# Execute the actual 6->7 migration SQL in SQLite and validate the unique month key.
migration = db.split("private val MIGRATION_6_7", 1)[1].split("private val MIGRATION_7_8", 1)[0]
statements = re.findall(r'database.execSQL\("(.*?)"\)', migration)
connection = sqlite3.connect(":memory:")
for statement in statements:
    connection.execute(statement)
created = {row[0] for row in connection.execute("SELECT name FROM sqlite_master WHERE type='table'")}
checks['migration creates all payroll tables'] = {'employees', 'salary_months', 'salary_payments'} <= created
connection.execute("INSERT INTO employees VALUES (1,'Test','',100000,1,1,'')")
connection.execute("INSERT INTO salary_months VALUES (1,1,'2026-11',100000,'')")
try:
    connection.execute("INSERT INTO salary_months VALUES (2,1,'2026-11',100000,'')")
    duplicate_blocked = False
except sqlite3.IntegrityError:
    duplicate_blocked = True
checks['SQLite enforces one payroll row per employee per month'] = duplicate_blocked
connection.close()
for label, ok in checks.items():
    print(f"{'PASS' if ok else 'FAIL'}: {label}")
assert all(checks.values())
print('Development 46 employee payroll checks: PASS')
