# Development 46 — Employee and Salary Ledger

- Added Employee Master with name, mobile, monthly salary, joining date, active/inactive status, and notes.
- Added one monthly payroll record per employee and month (`YYYY-MM`) with an editable payable salary.
- Added partial salary payment entries with Cash, UPI, or a named Bank account.
- Prevents duplicate employee/month rows and prevents payments or edits from exceeding the monthly payable amount.
- Employee Salary Ledger shows payable, paid, and remaining balance for each month.
- Salary payments can be edited or deleted; deleting a payment reverses its cash/bank impact because balances are calculated from source records.
- Salary outflows are included in Cash, UPI, overall Bank, and named Bank account balances; they also appear in Day Book and are included in expense reports.
- Employee and payroll tables are added through non-destructive Room migration 6→7. Existing records are retained.
- Backup/restore validation now accepts schema v7 and checks the employee/payroll tables for v7 backups.
- Salary is recorded month-by-month by the user; the app does not silently accrue or auto-pay salary.
- Version advanced to 502 / 5.2.0.
