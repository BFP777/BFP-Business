# DEV119 — Supplier Bill Camera Upload

- Added a dedicated **Take Photo** action to the Purchase entry attachment area.
- Captures supplier bills directly with Android's camera through `ActivityResultContracts.TakePicture` and the existing app-private FileProvider attachment directory.
- Keeps the existing Gallery / PDF document picker.
- Captured images are stored with the Purchase attachment and remain available to reopen, OCR, backup, and restore using the existing attachment-path handling.
- Cancelled/failed camera captures are discarded; replacing a newly selected attachment removes the previous temporary attachment without deleting the original attachment while editing an existing purchase.
- No database migration required; reuses the existing `attachmentPath` field and attachment backup support.
- Version: 5.62.0 (versionCode 562).
