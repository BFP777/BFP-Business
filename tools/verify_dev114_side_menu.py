from pathlib import Path
root = Path(__file__).resolve().parents[1]
main = (root / "app/src/main/java/in/bhajan/bfpbusinesspro/MainActivity.kt").read_text()
start = main.index("    private fun showMenu() {")
end = main.index("    private fun showVoiceInputLanguageSettings()", start)
menu = main[start:end]
checks = {
    "dashboard and billing entries": "Dashboard" in menu and "New Sale (Billing)" in menu,
    "business actions": all(x in menu for x in ["Purchase", "Payment In", "Payment Out", "Expenses", "Bank / बैंक", "Day Book"]),
    "parties and stock": all(x in menu for x in ["Parties (Customers & Suppliers)", "Stock / Items", "Debtors", "Creditors"]),
    "reports and backup": all(x in menu for x in ["Reports", "Business Trends", "Backup & Restore"]),
    "settings retained": all(x in menu for x in ["GST Settings", "Voice Input Language", "Display Language: English"]),
}
for label, ok in checks.items(): print(f"{'PASS' if ok else 'FAIL'}: {label}")
assert all(checks.values())
