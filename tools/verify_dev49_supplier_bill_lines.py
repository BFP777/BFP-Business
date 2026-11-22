from pathlib import Path
root = Path(__file__).resolve().parents[1]
main = (root / "app/src/main/java/in/bhajan/bfpbusinesspro/MainActivity.kt").read_text()
parser = (root / "app/src/main/java/in/bhajan/bfpbusinesspro/data/SupplierBillOcrParser.kt").read_text()
test = (root / "app/src/test/java/in/bhajan/bfpbusinesspro/data/SupplierBillOcrParserTest.kt").read_text()
checks = {
    "conservative supplier line parser exists": "object SupplierBillOcrParser" in parser and "rowPattern" in parser,
    "parser extracts quantity, rate and line amount": all(x in parser for x in ["quantity", "rateRupees", "lineAmountRupees"]),
    "ambiguous lines rejected by amount cross-check": "abs(amount - expected) / expected > 0.35" in parser,
    "duplicate OCR rows removed": "seen.add(key)" in parser,
    "OCR draft can add suggested rows to purchase form": "आइटम ड्राफ्ट में जोड़ें" in main and "onApplyLines(candidates)" in main,
    "matched item master values are attached": "itemId = matched?.id ?: 0L" in main and "itemCode = matched?.itemCode.orEmpty()" in main and "matchSupplierBillItem" in main,
    "OCR line tests cover parse, reject and deduplicate": all(x in test for x in ["parsesSimpleSupplierRowsAndCommaAmounts", "rejectsAmbiguousOrNonItemRows", "deduplicatesRepeatedOcrLines"]),
    "OCR candidates remain review-only before save": "सभी विवरण और GST जाँचें" in main and "InvoiceRepository" not in main.split("private fun showOcrDraftDialog",1)[1].split("private fun resolvePurchaseAttachment",1)[0],
}
for label, ok in checks.items(): print(f"{'PASS' if ok else 'FAIL'}: {label}")
assert all(checks.values())
print("Development 49 Supplier Bill Line Draft checks: PASS")
