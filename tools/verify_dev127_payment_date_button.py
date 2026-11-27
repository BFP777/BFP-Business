from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
main = (ROOT / "app/src/main/java/in/bhajan/bfpbusinesspro/MainActivity.kt").read_text()
transfer = main[main.index("private fun bankToBankTransferDialog()"):main.index("private fun editBankBalancePicker()")]
payment = main[main.index("private fun paymentDialog("):main.index("private fun titleForPayment(")]
assert "paymentDateButton" not in transfer
assert "val paymentDateButton = Button" in payment
assert "box.addView(paymentDateButton)" in payment
assert "selectedPaymentDateMillis" in payment and "SCHEDULED" in payment
print("DEV127 payment date button scope checks: PASS")
