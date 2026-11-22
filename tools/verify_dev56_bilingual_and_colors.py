from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
main = (ROOT / 'app/src/main/java/in/bhajan/bfpbusinesspro/MainActivity.kt').read_text()
gradle = (ROOT / 'app/build.gradle.kts').read_text()
notes = (ROOT / 'DEVELOPMENT-56-NOTES.md').read_text()
checks = {
    'main navigation bilingual': 'HOME / होम' in main and 'DASHBOARD / डैशबोर्ड' in main and 'ITEMS / आइटम' in main and 'MENU / मेन्यू' in main,
    'primary menu bilingual': 'My Business / मेरा व्यवसाय' in main and 'Parties & Accounts / पार्टियाँ और खाते' in main,
    'display language remains English and voice language supports English plus Hindi': 'Display Language: English' in main and 'Voice Input Language: English' in main and 'हिंदी / Hindi' in main,
    'customer SALE and RECEIPT tabs retained': 'SALE / बिक्री' in main and 'RECEIPT / प्राप्ति' in main,
    'WhatsApp draft action explicitly bilingual': 'WhatsApp संदेश तैयार करें / Draft WhatsApp Message' in main,
    'receivable tile is green': 'लेनदार / RECEIVABLES' in main and '0xFF168447.toInt()' in main,
    'payable tile is red': 'देनदारी / PAYABLES' in main and '0xFFC93636.toInt()' in main,
    'report receivable/payable cards have green/red backgrounds': 'background = rounded(0xFF168447.toInt(), 14)' in main and 'background = rounded(0xFFC93636.toInt(), 14)' in main,
    'receivable/payable amounts use existing accounting balances': 'balances.debtorsPaise' in main and 'balances.creditorsPaise' in main,
    'version advanced without database migration': 'versionCode = 551' in gradle and 'versionName = "5.51.0"' in gradle and 'Room schema: 9 (no database migration required)' in notes,
}
for label, ok in checks.items(): print(('PASS' if ok else 'FAIL') + ': ' + label)
if not all(checks.values()): raise SystemExit(1)
print('Development 56 bilingual and balance-color checks: PASS')
