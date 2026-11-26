from pathlib import Path
root = Path(__file__).resolve().parents[1]
calc = (root / 'app/src/main/java/in/bhajan/bfpbusinesspro/data/GstCalculator.kt').read_text()
tests = (root / 'app/src/test/java/in/bhajan/bfpbusinesspro/data/GstCalculatorTest.kt').read_text()
gradle = (root / 'app/build.gradle.kts').read_text()
checks = {
    'line amount uses BigDecimal rather than Double multiplication': 'BigDecimal.valueOf(it.quantity).multiply(BigDecimal.valueOf(it.ratePaise))' in calc and 'Math.round(it.quantity * it.ratePaise)' not in calc,
    'fractional paise rounding is HALF_UP and exact Long conversion': '.setScale(0, RoundingMode.HALF_UP).longValueExact()' in calc,
    'single-line overflow is rejected with a clear error': 'आइटम की रकम अधिकतम सीमा से बाहर है' in calc,
    'invoice subtotal uses checked addition': 'Math.addExact(total, amount)' in calc and 'बिल की कुल रकम अधिकतम सीमा से बाहर है' in calc,
    'taxable and tax totals use checked addition': 'checkedSum(results.map { it.taxablePaise }' in calc and 'checkedSum(results.map { it.cgstPaise }' in calc and 'checkedSum(results.map { it.sgstPaise }' in calc and 'checkedSum(results.map { it.igstPaise }' in calc,
    'tests cover fractional rounding and line overflow': 'fractionalQuantityUsesPaiseHalfUpRounding' in tests and 'rejectsSingleLineAmountBeyondLongRange' in tests,
    'test covers subtotal overflow across multiple lines': 'rejectsInvoiceSubtotalOverflowAcrossLines' in tests,
    'version advanced without Room schema change': 'versionCode = 562' in gradle and 'versionName = "5.62.0"' in gradle,
}
for label, ok in checks.items():
    print(f"{'PASS' if ok else 'FAIL'}: {label}")
assert all(checks.values())
print('Development 79 GST precision + overflow checks: PASS')
