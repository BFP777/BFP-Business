from pathlib import Path
root = Path(__file__).resolve().parents[1]
main = (root / 'app/src/main/java/in/bhajan/bfpbusinesspro/MainActivity.kt').read_text()
checks = {
    'sale has searchable customer picker': 'ग्राहक का नाम या मोबाइल खोजें' in main and 'addTextChangedListener' in main,
    'customer list shows mobile and outstanding': 'मोबाइल उपलब्ध नहीं' in main and 'बकाया ₹' in main,
    'new customer can be created inside sale flow': '+ Add New Party / नया ग्राहक जोड़ें' in main and 'insertParty(customer)' in main,
    'new customer is selected immediately': 'chooseCustomer(saved)' in main,
    'sale can start without an existing customer': 'parties.isEmpty() && kind == "PURCHASE"' in main,
    'sale save requires a selected customer': 'पहले ग्राहक चुनें या नया ग्राहक जोड़ें' in main,
    'purchase keeps its original party spinner': 'kind == "PURCHASE") Spinner' in main and 'party!!.selectedItemPosition' in main,
    'purchase OCR still uses supplier GSTIN': 'showOcrDraftDialog(extracted, note, itemMaster, parties.getOrNull(party!!.selectedItemPosition)?.gstNumber.orEmpty())' in main,
    'display-language setting remains English-only': 'Display Language: English' in main,
    'Hindi and English voice input remain available': 'arrayOf("English (Default)", "हिंदी / Hindi")' in main,
}
for label, ok in checks.items(): print(f"{'PASS' if ok else 'FAIL'}: {label}")
assert all(checks.values())
print('Development 67 Sale Customer Picker checks: PASS')
