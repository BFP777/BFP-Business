# Development 83 — Purchase to Stock Item Linking

- Purchase invoice lines must be selected from Item Master using a valid Item Code.
- Free-text-only purchase lines are rejected with a Hindi validation message so purchases cannot silently bypass inventory.
- Sale free-text lines remain supported.
- Purchase per-item GST rates remain unchanged and continue to follow the supplier bill.
- Inventory posting remains inside the invoice database transaction.
- Version: 5.28.0 (versionCode 528).

Offline source checks cover the purchase Item Master requirement, code-to-item validation, transactional stock posting, purchase GST preservation, and the UI item-code lookup entry point.
