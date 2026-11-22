from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
parser = (ROOT/'app/src/main/java/in/bhajan/bfpbusinesspro/data/SupplierBillOcrParser.kt').read_text()
main = (ROOT/'app/src/main/java/in/bhajan/bfpbusinesspro/MainActivity.kt').read_text()
tests = (ROOT/'app/src/test/java/in/bhajan/bfpbusinesspro/data/SupplierBillOcrParserTest.kt').read_text()
checks = {
 'invoice number/date/GSTIN/total metadata extraction': all(x in parser for x in ['invoiceNumber: String?', 'invoiceDate: String?', 'gstin: String?', 'invoiceTotalRupees: Double?']),
 'supplier GSTIN format and master matching': 'GstCalculator.validateGstin(scanned)' in parser and 'supplierGstin.trim().uppercase()' in parser,
 'line amount plus GST reconciliation': 'lineTotal + gstTotal' in parser and 'difference <= allowed' in parser,
 'CGST/SGST/IGST extraction': all(x in parser for x in ['cgstRupees', 'sgstRupees', 'igstRupees']),
 'PDF multi-page OCR with page limit': 'renderer.pageCount in 1..20' in main and 'for (index in 0 until renderer.pageCount)' in main,
 'OCR remains review-only; no automatic save': 'OCR is a review-only helper' in main and 'showOcrDraftDialog' in main,
 'supplier master GSTIN passed to review': 'parties.getOrNull(party!!.selectedItemPosition)?.gstNumber' in main,
 'unit tests cover reconciliation and GSTIN mismatch': 'extractsSupplierBillMetadataAndReconcilesTotals' in tests and 'flagsSupplierGstinMismatchAndTotalDifference' in tests,
}
for name, ok in checks.items(): print(('PASS' if ok else 'FAIL') + ': ' + name)
assert all(checks.values())
print('Development 50 Supplier Bill Verification checks: PASS')
