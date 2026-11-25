from pathlib import Path
root = Path(__file__).resolve().parents[1]
main = (root / "app/src/main/java/in/bhajan/bfpbusinesspro/MainActivity.kt").read_text()
repo = (root / "app/src/main/java/in/bhajan/bfpbusinesspro/data/InvoiceRepository.kt").read_text()
assert 'private fun showPartyDirectory(receivable: Boolean)' in main
assert 'DEBTORS / CUSTOMERS' in main and 'CREDITORS / SUPPLIERS' in main
assert 'Simple Purchase — Amount Only (No Stock)' in main
assert 'Item-wise Purchase — Add Stock' in main
assert 'Simple Purchase (No Stock)' in main
assert 'itemId = 0L, itemCode = ""' in main
assert 'Purchase amount-only lines' in repo
assert 'if (input.kind == "PURCHASE" && it.itemId > 0)' in repo
assert 'val oldByItem = oldLines.filter { it.itemId > 0 }' in repo
assert 'val newByItem = input.lines.filter { it.itemId > 0 }' in repo
nav = main[main.index('val nav = LinearLayout'):main.index('shell.addView(nav)')]
assert 'PARTIES' not in nav
assert 'showPartyDirectory(true)' in main and 'showPartyDirectory(false)' in main
print('DEV121 separate Debtors/Creditors + Simple Purchase no-stock checks: PASS')
