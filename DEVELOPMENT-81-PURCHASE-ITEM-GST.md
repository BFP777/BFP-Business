# Development 81 — Preserve supplier GST per purchase item

- Purchase item entry now shows a GST percentage field for each item, matching the supplier's bill.
- Purchase item summaries show each line's GST rate; tapping a row restores that rate for editing.
- Saving a purchase sends each row's own GST rate to the invoice repository, preserving mixed supplier rates within one purchase bill.
- Supplier-bill OCR matched item GST remains attached to the corresponding purchase line.
- Sales remain different: per-item GST entry stays hidden and one final invoice GST rate is applied uniformly across all sale lines.
- No Room schema change; app version advanced to 5.26.0 / versionCode 526.
- Full offline/source verification suite covers the separate purchase and sales GST paths.
- Android Gradle build and on-device UI testing have not been run in this environment.
