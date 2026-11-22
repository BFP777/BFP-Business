# Development 77 — Voice Quantity, Fixed Item Editor and Counter

## Changes
- Voice phrase parser now returns three separate values: item name, quantity and rate.
- Supports Hindi/English number words and currency phrases such as “पाँच सूट पाँच सौ रुपये” / “five suits five hundred rupees”.
- Billing dialog now keeps one fixed item-entry editor at the top. Added items appear as compact numbered summaries below it.
- A small item-count button is displayed at the right side of the added-items heading and updates after each successful addition.
- Manual add and voice add both append an item; selecting an item summary loads it back into the editor for correction.
- Continuous voice entry commits only a complete name/quantity/rate draft and then starts listening for the next item. Invoice posting still requires the explicit Save action.
- Existing invoice lines and purchase OCR draft lines continue to populate the appended item list.

## Verification
- Source-level voice and item-editor checks updated for the new workflow.
- Full offline source verification suite run after changes.
- Android Gradle build is not claimed: this source archive does not include the Gradle wrapper and Gradle is unavailable in the environment.
