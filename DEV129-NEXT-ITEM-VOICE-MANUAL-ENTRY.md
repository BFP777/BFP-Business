# DEV129 — Next Item Entry

- Adds an explicit **Next Item** action to the existing manual invoice-item editor.
- After a valid item is saved, the editor clears and focus returns to the Qty field for the next item.
- Continuous voice entry now returns focus/scroll to the same editor after each successfully parsed item, so the next spoken item starts as a fresh draft.
- Previously added invoice lines remain in the item list and are not overwritten.
- Version: 568 (5.68.0).

No dashboard, billing layout, logo, GST, date, bank, or payment logic was intentionally changed.
