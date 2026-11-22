# BFP Business Pro 5.0 — Development 17

## Added in this increment
- Expense entry screen with CASH / UPI / BANK mode.
- Opening balance entry for CASH and BANK accounts.
- Cash-to-bank deposit and bank-to-cash withdrawal entries.
- Current cash and bank balances calculated from the existing source transaction tables.
- Day Book now displays the current bank balance alongside the day's cash summary.

## Accounting rules used
- Cash opening increases cash; bank opening increases bank.
- Expense decreases only the selected payment account.
- Cash deposit decreases cash and increases bank.
- Cash withdrawal increases cash and decreases bank.
- Payment In increases its selected account; Payment Out decreases its selected account.
- UPI is shown as a payment mode but is not included in the cash or bank balance totals.

## Limitations
- Cash/Bank entries are append-only in this increment; edit/delete UI is not included yet.
- UPI wallet balance is not calculated as a separate account.
- Bank reconciliation against an actual bank statement is not implemented.
- Android build and device testing have not been run in this environment because Android SDK / Gradle wrapper are unavailable.
