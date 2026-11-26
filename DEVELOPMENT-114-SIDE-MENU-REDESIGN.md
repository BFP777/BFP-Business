# Development 114 — Side Menu Redesign (5.59.0)

## Implemented
- Reorganized the app menu into the approved navigation hierarchy: Dashboard, New Sale (Billing), Business, Parties & Inventory, Reports & Tools, and Settings.
- Added direct menu actions for Purchase, Payment In, Payment Out, Expense, Bank Accounts, Day Book, Parties, Stock/Items, Debtors, Creditors, Reports, Business Trends, Employees, Backup & Restore, and settings.
- Preserved existing screen handlers and repositories; menu actions navigate to existing features rather than duplicating accounting operations.
- Advanced Android version to 5.59.0 / 559. No database schema change.

## Not claimed as complete
- This change updates the navigation menu only. The billing screen has not been reworked in this development step; it still needs a separate implementation and device-level comparison against the approved billing reference.
- Android Gradle compilation, unit tests, lint, and device testing must run in GitHub Actions before treating this ZIP as a build-verified release.
