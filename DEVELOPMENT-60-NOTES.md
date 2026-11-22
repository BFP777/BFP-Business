# Development 60 – Business Trends & Monthly Charts

- Added a read-only six-month business trend query grouped by the device's local calendar month.
- Sales and purchases are sourced from their respective invoice kinds; expenses are sourced only from EXPENSE cash entries.
- Estimated gross profit is calculated from SALE invoice-line quantities and current Item Master sale/purchase rates. It is an estimate, not historical batch costing or net profit.
- Added a bilingual dashboard/menu entry with a native Android Canvas grouped bar chart and monthly breakdown for sales, purchases, expenses, and estimated gross profit.
- Receipts remain excluded from sales and expense totals; the trend screen does not post or modify accounting entries.
- Room schema remains version 9; app version advanced to 5.15.0 (versionCode 515).
- Offline source verification includes an exact SQLite execution fixture for monthly grouping and accounting separation.
- Android Gradle build/APK and device testing remain pending in an environment with Gradle 8.9 and Android SDK.
