# DEVELOPMENT-116 — Whole Bill Delete From Bill Details

## Change
- Added a red **DELETE COMPLETE BILL** action directly to the saved bill details dialog.
- The action asks for explicit confirmation before deleting.
- It calls the existing `InvoiceRepository.delete(invoice.id)` transaction, which removes the invoice and all invoice lines together, removes payments linked to that invoice, and posts stock reversal movements.
- Unlinked party payments are left untouched.
- A purchase attachment file is removed only after the database deletion succeeds.
- The saved-bills list continues to expose its existing Edit / Delete actions.

## Safety
- Database deletion remains inside the repository's Room transaction.
- If stock reversal fails (for example, deleting a purchase would make stock negative), the transaction fails and the UI reports the error.
- No schema migration or version change is needed for this UI-only addition.

## Source baseline
`BFP-Business-DEV115-View-Saved-Bills-Source.zip`
