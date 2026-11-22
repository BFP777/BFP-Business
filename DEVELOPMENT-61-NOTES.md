# Development 61 – AI Business Intelligence

- Added a bilingual, read-only AI Business Intelligence screen reachable from Dashboard and Inventory Intelligence menu.
- Highlights slow-moving designs: positive on-hand stock with no recorded sale in the last 90 days.
- Ranks fast-moving designs by recorded sale quantity in the last 90 days.
- Highlights supplier/item purchase-rate increases using the existing latest-vs-previous purchase comparison and shows absolute and percentage changes.
- Suggestions are informational prompts to review display/offers, stock planning, and supplier rates; they do not create purchase orders or modify stock, sales, payments, or accounting.
- Uses existing Room projections; no entity, table, or schema migration added (Room schema remains version 9).
- App version advanced to 5.16.0 (versionCode 516).
- Added focused offline source verifier and included it in the complete verification suite.
- Android Gradle build/APK and device testing remain pending in an environment with Gradle 8.9 and Android SDK.
