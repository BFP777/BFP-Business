# Development 30 — Cash, UPI, Bank & Day Book Integrity

## Scope
This step focuses on cash/bank movement presentation and safe cash-entry amount handling.

## Changes
- Day Book cash/bank entries now show human-readable descriptions instead of raw internal entry keys.
- Cash-to-bank deposits are displayed explicitly as `CASH → BANK`.
- Bank-to-cash withdrawals are displayed explicitly as `BANK → CASH`.
- Opening balances and expenses identify their CASH/UPI/BANK mode in the Day Book description.
- Cash-entry amount conversion now rejects non-finite/overflowing rupee values and values that round below one paisa.
- Cash-entry save validates the allowed entry type and payment mode before writing.
- Existing balance rules remain source-of-truth based: customer/supplier payments affect the selected payment mode; expenses affect their selected mode; cash deposits reduce cash and increase bank; withdrawals increase cash and reduce bank.

## Verification
- `python3 tools_verify_daybook.py` — PASS.
- Regression scenarios cover opening cash, cash receipt, expense, cash deposit, bank withdrawal, and ensure UPI receipts do not change cash.
- Source-level checks below are performed before packaging.

## Limits
- No Android Gradle build, Room integration test, emulator/device test, or APK build was possible in this environment because the project has no Gradle wrapper/system Gradle and no Android SDK configured.
- The regression script independently checks the movement arithmetic; it is not a substitute for a full Android integration test.
