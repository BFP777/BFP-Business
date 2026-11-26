from pathlib import Path
root = Path(__file__).resolve().parents[1]
main = (root / "app/src/main/java/in/bhajan/bfpbusinesspro/MainActivity.kt").read_text()
paths = (root / "app/src/main/res/xml/file_paths.xml").read_text()
gradle = (root / "app/build.gradle.kts").read_text()
checks = {
    "manual WhatsApp PDF action on saved sale bills": "WhatsApp पर PDF भेजें" in main,
    "PDF generated from saved invoice data": "private suspend fun shareInvoicePdf(invoice: InvoiceEntity)" in main,
    "PDF share intent": "Intent(Intent.ACTION_SEND)" in main and 'type = "application/pdf"' in main,
    "WhatsApp package targeted first": 'send.setPackage("com.whatsapp")' in main,
    "share chooser fallback": 'Intent.createChooser(send, "बिल PDF शेयर करें")' in main,
    "post-save share prompt": "क्या इस बिल की PDF कॉपी अभी WhatsApp" in main,
    "cache directory exposed only through FileProvider": 'cache-path name="invoice_share_cache" path="invoice_shares/"' in paths,
    "version bumped": 'versionCode = 563' in gradle and 'versionName = "5.63.0"' in gradle,
}
for label, ok in checks.items(): print(f"{'PASS' if ok else 'FAIL'}: {label}")
assert all(checks.values())
print("DEV120 invoice PDF WhatsApp source checks: PASS")
