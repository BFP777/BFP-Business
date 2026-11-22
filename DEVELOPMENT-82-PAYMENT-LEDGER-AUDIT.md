# Development 82 — Payment, Party Ledger and Cash/Bank Audit

- Kept the existing atomic payment create/update/delete flow.
- Added regression checks that linked payments match invoice party and direction, and cannot exceed invoice total.
- Verified that changing a payment refreshes both its former and new linked invoices.
- Verified party-ledger debit/credit signs for sales, purchases, incoming receipts and outgoing supplier payments.
- Verified cash, UPI and bank balances remain separated by payment mode; cash-bank transfers affect the respective balances without becoming sales or expenses.
- Added arithmetic fixtures for customer due, supplier payable, cash balance and named-bank balance.
- No application behavior or Room schema changed in this audit-only step.
- App version advanced to 5.27.0 (code 527) for the next integrated build.
