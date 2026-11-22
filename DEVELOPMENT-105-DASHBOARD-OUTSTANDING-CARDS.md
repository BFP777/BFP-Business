# Dev105 — Dashboard Outstanding Summary Cards

- Added two prominent summary cards at the top of the Business Dashboard: **You'll Get / लेना है** and **You'll Give / देना है**.
- Receivables card uses green styling; Payables card uses red styling.
- Amounts are loaded from `ReportsRepository.partyBalances()` so they reflect invoice outstanding balances net of unallocated advances, rather than sales/purchase turnover.
- Tapping either card opens a dedicated party-wise outstanding list, sorted by amount. Selecting a party opens its existing party ledger.
- No database schema change; Room remains at version 10.
- App version advanced to 5.50.0 / 550.
- Static source verifier added: `tools/verify_dev105_dashboard_outstanding.py`.

## DEV105 audit correction
- Dashboard summary totals and party-wise outstanding lists now share the same `ReportsRepository` calculation.
- Party bill counts now include only the relevant document type (SALE for receivables, PURCHASE for payables); mixed customer/supplier parties no longer show unrelated bills in the count.
- The outstanding verifier now checks these consistency and bill-count rules.
- No database schema change; app version remains 5.50.0 / 550 because this is a correction within DEV105.
