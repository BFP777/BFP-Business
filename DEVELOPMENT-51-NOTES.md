# Development 51 — Duplicate Purchase Bill Detection

- Purchase duplicate checks compare normalized bill numbers case-insensitively.
- A duplicate is flagged when the existing purchase belongs to the same supplier party OR the same non-empty supplier GSTIN.
- The current invoice is excluded during edits, so editing a saved bill does not flag itself.
- Sales invoice-number validation remains unchanged.
- Validation runs before stock posting and database writes; duplicates are blocked with a Hindi explanation.
- Same bill number can be used by unrelated suppliers when their GSTIN/party differs.
- Offline source verification added to `tools/verify_all.py`.
