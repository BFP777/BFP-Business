# DEV113 — Dashboard redesign

## Approved reference
Implemented the user's supplied Dashboard screenshot as the visual reference.

## Changes
- Reworked the common top header to a navy BFP Business Pro header with a white BFP mark.
- Changed the common bottom navigation to HOME, SALE, PURCHASE, and REPORTS.
- Rebuilt the Dashboard summary with six cards: Total Sales, Total Purchase, Cash Balance, Outstanding, Debtors/Receivable, and Creditors/Payable.
- Added six colored Quick Actions: New Sale, Purchase, Payment In, Payment Out, Expense, and Bank.
- Added Stock / Items, Day Book, and Reports shortcuts.
- Kept amounts connected to the database: lifetime sales and purchases, current cash balance, and party receivable/payable balances.
- Used a scrollable content area so the lower shortcuts remain reachable without overlapping the bottom navigation.

## Notes
- App versionCode/versionName were intentionally not changed.
- This source update is based on DEV112 Final Source ZIP.
- Build validation still needs to be run by Android Gradle/GitHub Actions; this environment did not have a Gradle executable or wrapper in the supplied ZIP.
