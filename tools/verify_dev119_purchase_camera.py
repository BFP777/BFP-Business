from pathlib import Path
root = Path(__file__).resolve().parents[1]
main = (root / 'app/src/main/java/in/bhajan/bfpbusinesspro/MainActivity.kt').read_text()
gradle = (root / 'app/build.gradle.kts').read_text()
checks = {
    'direct camera capture contract': 'ActivityResultContracts.TakePicture()' in main,
    'camera uses scoped FileProvider URI': 'FileProvider.getUriForFile(this, "$packageName.fileprovider", photo)' in main,
    'camera file is in private purchase attachment folder': 'File(filesDir, "purchase_attachments")' in main and 'supplier-camera-' in main,
    'camera success stores relative path': 'captured.relativeTo(filesDir).invariantSeparatorsPath' in main,
    'cancelled capture is deleted': 'captured?.delete()' in main,
    'gallery and PDF picker retained': 'choosePurchaseAttachment.launch(arrayOf("image/*", "application/pdf"))' in main,
    'separate camera button visible in purchase form': '📷 कैमरे से फोटो लें' in main,
    'existing attachment open and OCR actions retained': 'openPurchaseAttachment(pendingPurchaseAttachmentPath)' in main and 'बिल से टेक्स्ट पढ़ें (OCR)' in main,
    'version bumped': 'versionCode = 565' in gradle and 'versionName = "5.65.0"' in gradle,
}
for label, ok in checks.items():
    print(f"{'PASS' if ok else 'FAIL'}: {label}")
assert all(checks.values())
print('DEV119 supplier bill camera upload checks: PASS')
