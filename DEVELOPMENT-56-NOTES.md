# Development 56 — Bilingual UI & Receivable/Payable Color Clarity

Version: 5.11.0 (versionCode 511)
Room schema: 9 (no database migration required)

## Features
- Main navigation and primary menu sections show Hindi + English labels together.
- Menu includes an App Language / ऐप की भाषा information item confirming bilingual labels.
- Customer details keep the full profile and the separate SALE / बिक्री and RECEIPT / प्राप्ति tabs.
- WhatsApp action label is explicitly bilingual and remains user-triggered; it only opens a prefilled draft and never sends automatically.
- Dashboard balance tiles show लेनदार / RECEIVABLES in green and देनदारी / PAYABLES in red, with explanatory Hindi text and live balances.
- Reports use the same green/red color convention and bilingual labels.
- Receivable amount is sourced from customer sales outstanding; payable amount is sourced from supplier purchase outstanding. Existing accounting calculations are unchanged.
- No Room schema change; no existing customer, invoice, payment, or ledger data is migrated or rewritten.

## Verification
- Dev54 WhatsApp CTA check now verifies the exact Hindi action label while retaining English translation.
- Customer SALE and RECEIPT tab checks remain enabled.
- New Dev56 checks cover bilingual navigation/menu, green receivable and red payable labels/colors, and unchanged Room schema.
- Full offline regression suite is run before packaging.
- Android Gradle/KSP compilation and on-device UI testing still require GitHub Actions / an Android device.
