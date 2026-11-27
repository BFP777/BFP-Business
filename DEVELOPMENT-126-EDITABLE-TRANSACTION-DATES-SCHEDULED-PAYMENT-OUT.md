# DEV126 — Editable transaction dates and scheduled Payment Out

- Sale and Purchase invoice dialogs now expose a calendar date selector. The selected date is stored in `InvoiceEntity.dateEpochMillis` and therefore flows to invoice print/PDF, Day Book and party ledger reporting. Past, current and future dates are selectable.
- Payment In/Out dialogs now expose a payment date selector. Payment Out dated after today is automatically stored with status `SCHEDULED`; it is excluded from paid invoice totals, party ledger, Day Book and cash/UPI/bank balances.
- Scheduled Payment Out rows are visible in the Payment Out list with `SCHEDULED / PENDING` and a `Mark as Paid` action. Confirming Paid changes status to `PAID`, records the actual paid date as the current date, and only then posts the payment to the linked invoice and money balances.
- Existing payment rows migrate as `PAID`, preserving prior records. Room database migration 10→11 adds the status column.
- Dashboard and approved billing layout are retained; only the bill date control is made editable.
- Release: versionCode 565, versionName 5.65.0.
