# DEV127 — Payment date control scope fix

Fixed the Kotlin compile error caused by adding `paymentDateButton` to the bank-to-bank transfer dialog where it was out of scope. The date button is now added inside `paymentDialog`, where it is declared, so Payment In/Out date selection is visible and functional. Sale/Purchase invoice date picker and scheduled Payment Out logic from DEV126 are retained. Scheduled payments remain excluded from paid-only ledger and balance queries until marked Paid.

Release: versionCode 566, versionName 5.66.0. Dashboard, approved billing layout, and bank module are otherwise unchanged.
