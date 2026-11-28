DEV131 — Smart Voice Billing and Saved Item Suggestions

Based on DEV130 source. Changes in this revision:
- Item Name field opens the saved sale-item suggestions immediately when tapped/focused while blank.
- Saved items are shown from the existing bfp_saved_sale_item_suggestions_v1 preference store; no new inventory records are created by opening the list.
- Typing filters saved items by prefix or contained text.
- Voice recognition defaults to hi-IN when no voice language preference has previously been saved. Existing explicit user language preferences remain respected.
- Version: code 572, name 5.68.3.

Voice phrase target: “नौ सूट छह सौ रुपये” => quantity 9, item “सूट”, rate 600. The existing parser contains Hindi number-word handling and Devanagari digit normalization. This source ZIP has not been compiled or APK-tested; run the repository workflow and test on-device before production use.
