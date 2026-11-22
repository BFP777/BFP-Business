# Development 62 – AI Demand Forecast

- Added a bilingual, read-only AI Demand Forecast screen from Dashboard and Inventory Intelligence.
- Compares each design actual SALE quantity in the most recent 30 days with the preceding 30-day period.
- Uses the latest 30-day quantity as a simple next-30-day run-rate estimate; labels growth/decline/steady sales against the prior period.
- Calculates a review-only possible replenishment quantity as forecast demand + Item Master minimum stock − current on-hand stock, floored at zero.
- Discloses that seasonality, supplier lead time, returns, future orders and other factors are not modeled; this is not a guaranteed forecast.
- No automatic purchase order, stock update, or accounting entry is created.
- Added a Room read-only query and projection only; database schema remains version 9, no migration.
- App version advanced to 5.17.0 (versionCode 517).
- Added focused verifier and registered it in the full offline suite.
- APK build and device testing remain pending until Gradle 8.9 and Android SDK are available.
