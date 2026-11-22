# Development 45 — Supplier Bill Attachments and Complete Local Backup

- Purchase entry now has an optional supplier bill attachment picker for images and PDFs.
- Selected files are copied into app-private `files/purchase_attachments` storage and database rows store portable relative paths; source-provider permissions are not required after selection. Attachments can be opened through a scoped FileProvider.
- Attachment size is capped at 20 MB and empty/unsupported files are rejected.
- Purchase invoice create/edit stores the attachment path; removing or replacing an attachment cleans up the old private file only after a successful save. Cancelling an unsaved edit cleans up newly copied files.
- Deleting a purchase removes its attached file after the invoice deletion succeeds.
- Backup output is now a ZIP containing the SQLite database and all purchase attachment files.
- Restore validates the database before replacement, stages files, limits compressed backup input to 260 MB and extracted content to 250 MB, rejects unsafe archive paths, and rolls back database/attachment replacement on failure.
- Restore remains compatible with older standalone SQLite `.db` backups. Such legacy backups do not contain attachments.
- Version code/name advanced to 501 / 5.1.0. Room schema remains version 6; no database migration is needed.
- Added Dev45 source-level regression checks and wired them into `tools/verify_all.py`.
