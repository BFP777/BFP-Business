# DEV132 — Continuous Smart Voice Billing

Base: DEV131 Smart Voice + Saved Item Suggestions.

Version: 5.68.4 (versionCode 573).

## Changes
- A single final speech transcript can be split into multiple sale bill lines when each spoken item name matches a saved Item Master name or a previously saved sale-item suggestion.
- Quantity and trailing rate are parsed separately for each recognized item phrase.
- Canonical saved item names are written to the bill instead of raw speech-recognition spelling.
- Final speech transcripts are queued and processed sequentially so a later recognition callback does not overwrite an earlier draft while it is being committed.
- Duplicate identical final transcripts received within 2.5 seconds are ignored.
- If a known item catalogue exists but a spoken item cannot be matched, the app does not collapse the entire multi-item sentence into one incorrect bill line; it asks the user to check the item name or enter it manually.
- Existing single-item fallback remains only when no saved item names exist.

## Example
Spoken: “नौ सूट छह सौ रुपये, दो नाइट सूट बारह सौ रुपये, तीन पैंट शर्ट आठ सौ रुपये”

Expected, when these names exist in Saved Items / Item Master:
- सूट — Qty 9 — Rate ₹600
- नाइट सूट — Qty 2 — Rate ₹1,200
- पैंट शर्ट — Qty 3 — Rate ₹800

## Important test limitation
Android SpeechRecognizer still supplies the recognized text. This update improves parsing, matching, queueing, and duplicate protection; it does not replace the phone's speech-to-text engine with ChatGPT or guarantee 100% speech recognition. Device-level voice tests and a successful GitHub Actions build are still required.
