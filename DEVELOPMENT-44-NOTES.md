# Development 44 — Item Master and Inventory Posting

- Added a unique Item Code / Design Code to Item Master.
- Existing item rows receive a stable migration code (`ITM-<id>`) so the database migration preserves existing records.
- Added Item Master / Stock screen with create and edit support.
- Sales and purchase invoice lines can be linked to an Item Master record by Item Code lookup.
- Linked sales reduce stock; linked purchases increase stock.
- Editing an invoice posts only the stock difference; deleting an invoice reverses its stock movement.
- Stock changes and invoice changes run inside the same Room transaction. A sale cannot reduce linked stock below zero.
- Manual free-text invoice lines remain supported for compatibility and do not affect stock unless linked to an Item Code.
- Added optional `attachmentPath` storage field to the invoice schema as groundwork for supplier bill attachments; the picker, file-copy lifecycle and attachment-aware backup are not implemented in this increment.
- Database schema advanced from version 5 to 6 with a non-destructive migration. The database filename remains unchanged to allow Room to run the migration on existing installations.
- Updated offline verification scripts for schema v6 and new inventory behavior.
