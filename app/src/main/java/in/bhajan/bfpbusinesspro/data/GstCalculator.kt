package `in`.bhajan.bfpbusinesspro.data

import java.math.BigDecimal
import java.math.RoundingMode
import java.math.BigInteger

/** All currency values are integer paise; discount is allocated proportionally per line. */
data class GstBreakdown(
    val taxablePaise: Long,
    val cgstPaise: Long,
    val sgstPaise: Long,
    val igstPaise: Long
) {
    val totalTaxPaise: Long get() = cgstPaise + sgstPaise + igstPaise
    val grossPaise: Long get() = taxablePaise + totalTaxPaise
}

data class GstLineInput(val quantity: Double, val ratePaise: Long, val gstRatePercent: Double)
data class GstLineResult(
    val grossBeforeDiscountPaise: Long,
    val discountPaise: Long,
    val taxablePaise: Long,
    val cgstPaise: Long,
    val sgstPaise: Long,
    val igstPaise: Long,
    val gstRatePercent: Double
) { val totalPaise: Long get() = taxablePaise + cgstPaise + sgstPaise + igstPaise }

data class GstInvoiceResult(val subtotalPaise: Long, val discountPaise: Long, val taxablePaise: Long,
                           val cgstPaise: Long, val sgstPaise: Long, val igstPaise: Long,
                           val totalPaise: Long, val lines: List<GstLineResult>)

object GstCalculator {
    fun validateGstin(value: String): Boolean {
        val gstin = value.trim().uppercase()
        if (gstin.isEmpty()) return true
        return Regex("^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$").matches(gstin)
    }

    fun split(taxablePaise: Long, ratePercent: Double, sellerStateCode: String, buyerStateCode: String): GstBreakdown {
        require(taxablePaise >= 0) { "कर योग्य रकम ऋणात्मक नहीं हो सकती" }
        require(ratePercent in 0.0..100.0 && ratePercent.isFinite()) { "GST दर 0 से 100 के बीच होनी चाहिए" }
        require(validState(sellerStateCode) && validState(buyerStateCode)) { "राज्य कोड दो अंकों का होना चाहिए" }
        val tax = BigDecimal.valueOf(taxablePaise).multiply(BigDecimal.valueOf(ratePercent))
            .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP).longValueExact()
        return if (sellerStateCode == buyerStateCode) {
            val cgst = tax / 2
            GstBreakdown(taxablePaise, cgst, tax - cgst, 0)
        } else GstBreakdown(taxablePaise, 0, 0, tax)
    }

    fun calculateInvoice(lines: List<GstLineInput>, discountPaise: Long,
                         sellerStateCode: String, buyerStateCode: String): GstInvoiceResult {
        require(lines.isNotEmpty()) { "कम-से-कम एक आइटम जरूरी है" }
        require(discountPaise >= 0) { "छूट ऋणात्मक नहीं हो सकती" }
        require(validState(sellerStateCode) && validState(buyerStateCode)) { "राज्य कोड दो अंकों का होना चाहिए" }
        val gross = lines.map {
            require(it.quantity > 0.0 && it.quantity.isFinite() && it.ratePaise >= 0) { "मात्रा या रेट गलत है" }
            require(it.gstRatePercent in 0.0..100.0 && it.gstRatePercent.isFinite()) { "GST दर गलत है" }
            // Use decimal arithmetic for paise rounding; Double multiplication can
            // silently lose precision or saturate when the amount exceeds Long.
            try {
                BigDecimal.valueOf(it.quantity).multiply(BigDecimal.valueOf(it.ratePaise))
                    .setScale(0, RoundingMode.HALF_UP).longValueExact()
            } catch (_: ArithmeticException) {
                throw IllegalArgumentException("आइटम की रकम अधिकतम सीमा से बाहर है")
            }
        }
        val subtotal = gross.fold(0L) { total, amount ->
            try { Math.addExact(total, amount) }
            catch (_: ArithmeticException) { throw IllegalArgumentException("बिल की कुल रकम अधिकतम सीमा से बाहर है") }
        }
        require(discountPaise <= subtotal) { "छूट बिल की रकम से अधिक नहीं हो सकती" }
        val allocations = LongArray(lines.size)
        if (subtotal > 0L && discountPaise > 0L) {
            for (i in gross.indices) allocations[i] = BigDecimal.valueOf(discountPaise).multiply(BigDecimal.valueOf(gross[i]))
                .divide(BigDecimal.valueOf(subtotal), 0, RoundingMode.DOWN).longValueExact()
            var remaining = discountPaise - allocations.sum()
            val denominator = BigInteger.valueOf(subtotal)
            val order = gross.indices.sortedByDescending { i ->
                BigInteger.valueOf(discountPaise).multiply(BigInteger.valueOf(gross[i])).remainder(denominator)
            }
            for (i in order) {
                if (remaining == 0L) break
                if (allocations[i] < gross[i]) { allocations[i]++; remaining-- }
            }
            check(remaining == 0L) { "छूट का बँटवारा नहीं हो सका" }
        }
        val results = lines.mapIndexed { index, line ->
            val allocated = allocations[index]
            val taxable = gross[index] - allocated
            val tax = split(taxable, line.gstRatePercent, sellerStateCode, buyerStateCode)
            GstLineResult(gross[index], allocated, taxable, tax.cgstPaise, tax.sgstPaise, tax.igstPaise, line.gstRatePercent)
        }
        fun checkedSum(values: List<Long>, label: String): Long = values.fold(0L) { total, amount ->
            try { Math.addExact(total, amount) }
            catch (_: ArithmeticException) { throw IllegalArgumentException("$label अधिकतम सीमा से बाहर है") }
        }
        val taxable = checkedSum(results.map { it.taxablePaise }, "कर योग्य रकम")
        val cgst = checkedSum(results.map { it.cgstPaise }, "CGST")
        val sgst = checkedSum(results.map { it.sgstPaise }, "SGST")
        val igst = checkedSum(results.map { it.igstPaise }, "IGST")
        val total = checkedSum(listOf(taxable, cgst, sgst, igst), "बिल की कुल रकम")
        return GstInvoiceResult(subtotal, discountPaise, taxable, cgst, sgst, igst, total, results)
    }

    fun validState(value: String) = value.matches(Regex("[0-9]{2}"))
}
