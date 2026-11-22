# Development 84 — Stock Summary

- Item Master / Stock screen now shows a live summary across all Item Master records.
- Summary includes design count, total quantity, low-stock design count, and estimated stock value using each item's purchase rate.
- The valuation is explicitly labelled as an estimate and excludes GST, discounts, and batch-level costing.
- Existing purchase/sale invoice posting, edit reversal/reposting, and delete reversal logic is retained unchanged.
- Version: 5.29.0 (versionCode 529).

Offline checks verify the summary fields and that the stock posting transaction paths remain present.
