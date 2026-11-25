from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
repo = (ROOT / "app/src/main/java/in/bhajan/bfpbusinesspro/data/LedgerRepository.kt").read_text()
activity = (ROOT / "app/src/main/java/in/bhajan/bfpbusinesspro/MainActivity.kt").read_text()
gradle = (ROOT / "app/build.gradle.kts").read_text()
checks = {
    "party ledger audit reconciles invoice paid against linked payments": "linkedTotal != invoice.paidPaise" in repo,
    "audit detects invalid party/invoice/direction links": "भुगतान दिशा का मिलान नहीं" in repo,
    "unallocated receipts and payments are reported separately": "unallocatedReceiptPaise" in repo and "unallocatedPaymentPaise" in repo,
    "ledger UI shows audit summary and issues": "LEDGER AUDIT / खाता मिलान" in activity and "audit.issues.forEach" in activity,
    "no opening balance field invented": "openingBalancePaise" not in (ROOT / "app/src/main/java/in/bhajan/bfpbusinesspro/data/Entities.kt").read_text(),
    "version 5.43.0 / 537": 'versionCode = 562' in gradle and 'versionName = "5.62.0"' in gradle,
}
for label, ok in checks.items():
    print(("PASS" if ok else "FAIL") + " — " + label)
assert all(checks.values())
