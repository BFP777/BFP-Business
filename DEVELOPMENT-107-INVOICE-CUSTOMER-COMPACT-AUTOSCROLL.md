# Development 107 — Compact Customer Header and Auto-Scrolling Invoice Items

- After a sale customer is selected, the customer search/results panel collapses; the compact header shows only the customer name and the saved address text.
- A Change button reopens customer search so the selected party can still be changed.
- Mobile number and outstanding balance are intentionally omitted from the selected-customer header.
- The existing item counter remains visible above the added-item list.
- After each manual or voice-added invoice line, only the item-list viewport scrolls to the newest row; the customer header and item-entry controls remain fixed.
- No Room schema change. The current PartyEntity has a single address field; any S/O or D/O text already stored in that address is displayed as part of it. There is no separate father/guardian field in the current data model.
- App version advanced to 5.52.0 (versionCode 552).
- Android Gradle build and real-device UI/voice tests must still be run in GitHub Actions/device.
