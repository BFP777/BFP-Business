from pathlib import Path
root = Path(__file__).resolve().parents[1]
main = (root / 'app/src/main/java/in/bhajan/bfpbusinesspro/MainActivity.kt').read_text()
repo = (root / 'app/src/main/java/in/bhajan/bfpbusinesspro/data/InvoiceRepository.kt').read_text()
backup = (root / 'app/src/main/java/in/bhajan/bfpbusinesspro/backup/DatabaseBackup.kt').read_text()
gradle = (root / 'app/build.gradle.kts').read_text()
manifest = (root / 'app/src/main/AndroidManifest.xml').read_text()
file_paths = (root / 'app/src/main/res/xml/file_paths.xml').read_text()
checks = {
    'purchase-only optional attachment picker': 'choosePurchaseAttachment.launch(arrayOf("image/*", "application/pdf"))' in main,
    'attachment can be opened with scoped FileProvider': 'FileProvider.getUriForFile' in main and 'androidx.core.content.FileProvider' in manifest,
    'FileProvider exposes only purchase attachment folder': 'files-path' in file_paths and 'purchase_attachments/' in file_paths,
    'attachment copied into app-private storage': 'File(filesDir, "purchase_attachments")' in main,
    'database stores portable relative attachment path': 'copied.relativeTo(filesDir).invariantSeparatorsPath' in main and 'resolvePurchaseAttachment' in main,
    'attachment size guard': '20L * 1024L * 1024L' in main,
    'purchase invoice stores attachment path': 'attachmentPath = if (kind == "PURCHASE") pendingPurchaseAttachmentPath else ""' in main,
    'sales cannot carry supplier attachment': 'input.attachmentPath.isBlank() || input.kind == "PURCHASE"' in repo,
    'attachment deleted after purchase removal': 'if (invoice.attachmentPath.isNotBlank()) resolvePurchaseAttachment(invoice.attachmentPath).delete()' in main,
    'ZIP backup includes database': 'addFile("database/$DB_NAME", source)'  in backup,
    'ZIP backup includes attachments': 'addFile("attachments/$relative", file)'  in backup,
    'ZIP restore path traversal guard': 'normalized.split(\'/\').none { it == ".." }' in backup,
    'ZIP input compressed size guard': '260L * 1024L * 1024L' in backup,
    'ZIP restore uncompressed size guard': '250L * 1024L * 1024L' in backup,
    'legacy SQLite restore path retained': 'rawTemp.copyTo(legacyDb, overwrite = true)' in backup,
    'backup picker accepts ZIP': 'application/zip' in main,
    'backup validator supports schema v9': 'cursor.getInt(0) in 3..10' in backup and 'salary_payments' in backup,
    'version incremented': 'versionCode = 552' in gradle and 'versionName = "5.52.0"' in gradle,
}
for label, ok in checks.items():
    print(f"{'PASS' if ok else 'FAIL'}: {label}")
assert all(checks.values())
print('Development 45 purchase attachments + ZIP backup checks: PASS')
