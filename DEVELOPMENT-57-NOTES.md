# Development 57 — Hindi/English Voice Entry + Barcode/QR Item Lookup

Version: 5.12.0 (versionCode 512)
Room schema: 9 (no database migration required)

## Features
- Invoice line editor now offers Hindi and English speech recognition for the item/design name field.
- Speech input is an optional convenience: it only fills the editable draft field; it never saves an invoice or posts stock, cash, bank, or ledger entries.
- Invoice line editor now offers Google Code Scanner for common barcodes and QR codes.
- A scanned value is looked up against the existing Item Master `itemCode` using the existing DAO; on a match, the item name, sale/purchase rate, GST, HSN, and item link are populated into the current editable invoice line.
- Unknown codes show a small message and do not create a new item or alter accounting records.
- Scanner cancellation is harmless. Google Play services provides the scanner UI; the app does not request direct camera permission.
- Existing typed Item Code search remains available.
- No Room schema changes, no database migration, and no automatic posting.

## Limitations / validation
- Speech recognition availability and accuracy depend on the device's installed speech service and language packs.
- Google Code Scanner requires a compatible Google Play services version and its scanner module; the first scan may download the module.
- The app must have the barcode/QR value stored as the item's Item Code for automatic matching.
- Offline source checks and manifest/Gradle static checks are run. Android Gradle/KSP compilation and real-device voice/scanner tests require GitHub Actions / a physical Android device.
