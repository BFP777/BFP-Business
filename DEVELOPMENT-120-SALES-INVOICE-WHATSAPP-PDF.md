# DEV120 — Sales Invoice PDF WhatsApp Sharing

- Adds a customer-facing PDF copy for saved SALE invoices.
- After saving a sale, the app asks whether to open WhatsApp with the PDF attached; the user can choose to send now or later.
- Adds a manual **WhatsApp पर PDF भेजें** action to the saved sales-bill list.
- The PDF includes the business and customer details, invoice number/date, line items, rates, tax, discount, total, paid amount, and balance.
- Uses FileProvider cache-path access and temporary read grants; no public file exposure.
- WhatsApp does not permit silent auto-send through this app flow. The app prepares the PDF and opens the chat/share flow; the user must review and tap Send.
- Existing PDF/Print, Edit, Delete, purchase camera upload, OCR, ledger, and backup features remain.
- Version: 5.63.0 (versionCode 563).
