# BFP Business Pro 5.0 — Development 18

## Added in this increment
- Cash/Bank/UPI transaction rows now offer Edit and Delete actions.
- Editing preserves the original transaction date and record ID; amount, mode (where applicable), and note are updated.
- Deleting a cash entry removes that source transaction so computed balances recalculate from source rows.
- Added a separate UPI net balance calculation: UPI Payment In minus UPI Payment Out minus UPI expenses.
- Money Management screen displays Cash, UPI net, and Bank balances separately.

## Accounting scope and cautions
- UPI is a net movement summary, not a bank-statement reconciliation.
- Opening balances remain supported for Cash and Bank; opening UPI is not added in this increment.
- Cash deposit/withdrawal remains represented as a paired transfer movement in the existing cash-entry source model.
- Deleting an entry changes historical balances and should be done only to correct a mistaken entry.

## Verification
- Source files and ZIP structure inspected.
- Android compilation/device testing has not been performed because this environment has no Gradle executable/wrapper and no Android SDK installation.

## Development 19 — Invoice print/PDF
- Added a per-invoice PDF/Print action using Android's system print framework.
- Print layout includes shop name, bill type/number/date, party contact/address/GSTIN, item rows, subtotal, discount, GST/Tax amount, total, paid and balance.
- Party GSTIN and address are read from the party record; they are not fabricated.
- This is a printable invoice layout, not a certified GST tax invoice: seller GSTIN/address, place of supply, HSN/SAC, tax rate and CGST/SGST/IGST breakup are not currently stored in the data model.
- Android compilation and device print testing remain pending because this environment has no Android SDK or Gradle wrapper.
