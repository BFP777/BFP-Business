# DEVELOPMENT 122 — Party Details and Simple Purchase (No Stock)

## Implemented in this source update
- Reworked the party ledger page into a Party Details-style screen with party name/mobile, edit and delete actions, current balance, statement sharing, transaction search, and transaction cards.
- Added bottom party actions for Take Payment / Make Payment, Add Sale / Add Purchase.
- Supplier purchase action offers two explicit paths: Simple Purchase — No Stock, and Item-wise Purchase — Add Stock.
- Simple Purchase creates a PURCHASE invoice header for the supplier amount with no invoice-line rows. It does not call stock posting and does not change inventory quantities. The resulting purchase remains available in the supplier ledger and as an unpaid purchase for later Payment Out allocation.

## Safety notes
- Existing invoice and payment records are not migrated or deleted.
- Existing item-wise purchase flow remains available and unchanged.
- Simple purchase is recorded as a purchase total, not as item-level stock.
- Statement sharing uses Android's share chooser; the user chooses WhatsApp or another app and confirms sending.

## Validation limitation
This container does not have Gradle or a Gradle wrapper in the supplied archive, so an Android compile/APK build could not be run here. Run the repository's GitHub Actions Android build before installing this source update. Inspect the build result before treating it as release-ready.
