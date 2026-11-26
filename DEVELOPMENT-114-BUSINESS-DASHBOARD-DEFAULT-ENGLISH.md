# DEV114 — Business Dashboard as Default

- App startup now opens `showDashboardSummary()` directly.
- Legacy `showDashboard()` entry routes to the Business Dashboard, so old HOME entry points no longer show the legacy Quick Links page.
- App version incremented to 5.60.0 (versionCode 560).
- Signed release workflow checks and artifact name updated to DEV114 / 5.60.0.

Note: This change makes the approved Business Dashboard the default. A complete English-only cleanup across every module is a separate, broader UI localization pass and has not been represented as completed here.
