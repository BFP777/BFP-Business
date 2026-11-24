from pathlib import Path
root = Path(__file__).resolve().parents[1]
main = (root / "app/src/main/java/in/bhajan/bfpbusinesspro/MainActivity.kt").read_text(encoding="utf-8")
gradle = (root / "app/build.gradle.kts").read_text(encoding="utf-8")
checks = {
    "approved dashboard KPI cards": all(x in main for x in ["TOTAL SALES", "TOTAL PURCHASE", "CASH BALANCE", "OUTSTANDING"]),
    "green debtors and red creditors": "DEBTORS  •  RECEIVABLE" in main and "CREDITORS  •  PAYABLE" in main,
    "six dashboard quick actions": all(x in main for x in ["NEW SALE", "PURCHASE", "PAYMENT IN", "PAYMENT OUT", "EXPENSE", "BANK"]),
    "dashboard shortcuts": all(x in main for x in ["PARTIES", "STOCK / ITEMS", "DAY BOOK", "REPORTS"]),
    "navy BFP brand header": 'setBackgroundColor(0xFF071F55.toInt())' in main and 'BFP Business Pro' in main,
    "approved five-tab bottom navigation": all(x in main for x in ['"⌂\\\\nHOME"', '"＋\\\\nSALE"', '"▤\\\\nPURCHASE"', '"♙\\\\nPARTIES"', '"▥\\\\nREPORTS"']),
    "continuous single-screen billing": 'addView(bodyScroll, LinearLayout.LayoutParams(-1, 0, 1f))' in main and 'addView(box)' in main,
    "customer WhatsApp action": 'text = "WhatsApp"' in main and 'https://wa.me/$phone' in main,
    "separate item entry fields and actions": all(x in main for x in ['मात्रा / Quantity', 'आइटम का नाम (जैसे सूट / डिजाइन)', 'रेट (₹)', 'text = "Save Item"', 'text = "Save & New"', 'text = "Item List"']),
    "optional final bill GST preserved": 'GST लगाएँ (5% / दर बदल सकते हैं)' in main and 'saleGstEnabled' in main,
    "Save Bill action": '.setNeutralButton("Save Bill", null)' in main,
    "version 5.61.0 / 561": 'versionCode = 561' in gradle and 'versionName = "5.61.0"' in gradle,
}
for label, ok in checks.items():
    print(f"{'PASS' if ok else 'FAIL'}: {label}")
assert all(checks.values())
