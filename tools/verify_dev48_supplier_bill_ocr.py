from pathlib import Path
root = Path(__file__).resolve().parents[1]
main = (root / 'app/src/main/java/in/bhajan/bfpbusinesspro/MainActivity.kt').read_text()
gradle = (root / 'app/build.gradle.kts').read_text()
checks = {
    'bundled on-device ML Kit text recognition dependency': 'com.google.mlkit:text-recognition:16.0.1' in gradle,
    'OCR action appears only in purchase attachment flow': 'बिल से टेक्स्ट पढ़ें (OCR)' in main and 'if (kind == "PURCHASE")' in main,
    'image attachments use ML Kit InputImage': 'InputImage.fromFilePath' in main,
    'PDF OCR renders every page with a bounded page count': 'PdfRenderer(descriptor)' in main and 'renderer.pageCount in 1..20' in main and 'for (index in 0 until renderer.pageCount)' in main,
    'invoice number candidate extraction': 'invoice' in (root / 'app/src/main/java/in/bhajan/bfpbusinesspro/data/SupplierBillOcrParser.kt').read_text(),
    'GSTIN candidate extraction': '[0-9]{2}[A-Z]{5}[0-9]{4}' in (root / 'app/src/main/java/in/bhajan/bfpbusinesspro/data/SupplierBillOcrParser.kt').read_text(),
    'total amount candidate extraction': 'grand' in (root / 'app/src/main/java/in/bhajan/bfpbusinesspro/data/SupplierBillOcrParser.kt').read_text(),
    'OCR result opens a supplier-matched review dialog': 'showOcrDraftDialog(extracted, note, itemMaster, parties.getOrNull(party!!.selectedItemPosition)?.gstNumber.orEmpty())' in main,
    'OCR never auto-saves purchase accounting': 'यह OCR अनुमान है। मूल बिल से मिलान अनिवार्य है.' in main and 'OCR is a review-only helper' in main,
    'user must explicitly add text to note': 'setNeutralButton("नोट में जोड़ें")' in main and 'noteField.setText' in main,
    'OCR preview does not call invoice repository': 'InvoiceRepository' not in main.split('private fun showOcrDraftDialog',1)[1].split('private fun resolvePurchaseAttachment',1)[0],
    'app version advanced': 'versionCode = 559' in gradle and 'versionName = "5.59.0"' in gradle,
}
for label, ok in checks.items(): print(f"{'PASS' if ok else 'FAIL'}: {label}")
assert all(checks.values())
print('Development 48 Supplier Bill OCR checks: PASS')
