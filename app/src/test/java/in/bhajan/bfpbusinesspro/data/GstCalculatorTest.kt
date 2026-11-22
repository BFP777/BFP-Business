package `in`.bhajan.bfpbusinesspro.data

import org.junit.Assert.assertEquals
import org.junit.Test

class GstCalculatorTest {
    @Test fun intraStateInvoiceSplitsTaxAndDiscountAcrossLines() {
        val result = GstCalculator.calculateInvoice(
            listOf(GstLineInput(1.0, 100_000, 5.0), GstLineInput(1.0, 200_000, 12.0)),
            30_000, "02", "02"
        )
        assertEquals(300_000L, result.subtotalPaise)
        assertEquals(270_000L, result.taxablePaise)
        assertEquals(26_100L, result.cgstPaise + result.sgstPaise)
        assertEquals(0L, result.igstPaise)
        assertEquals(296_100L, result.totalPaise)
        assertEquals(270_000L, result.lines.sumOf { it.taxablePaise })
    }

    @Test fun interStateInvoiceUsesIgstOnly() {
        val result = GstCalculator.calculateInvoice(listOf(GstLineInput(1.0, 100_000, 5.0)), 0, "02", "07")
        assertEquals(0L, result.cgstPaise)
        assertEquals(0L, result.sgstPaise)
        assertEquals(5_000L, result.igstPaise)
        assertEquals(105_000L, result.totalPaise)
    }

    @Test fun discountRoundingNeverCreatesNegativeLineDiscount() {
        val result = GstCalculator.calculateInvoice(
            List(4) { GstLineInput(1.0, 1, 0.0) }, 2, "02", "02"
        )
        assertEquals(2L, result.discountPaise)
        assertEquals(2L, result.lines.sumOf { it.discountPaise })
        assertEquals(0L, result.lines.minOf { it.taxablePaise })
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsDiscountAboveSubtotal() {
        GstCalculator.calculateInvoice(listOf(GstLineInput(1.0, 100, 5.0)), 101, "02", "02")
    }
    @Test fun fractionalQuantityUsesPaiseHalfUpRounding() {
        val result = GstCalculator.calculateInvoice(listOf(GstLineInput(0.333, 100, 0.0)), 0, "02", "02")
        assertEquals(33L, result.subtotalPaise)
        assertEquals(33L, result.totalPaise)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsSingleLineAmountBeyondLongRange() {
        GstCalculator.calculateInvoice(
            listOf(GstLineInput(2.0, Long.MAX_VALUE, 0.0)), 0, "02", "02"
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvoiceSubtotalOverflowAcrossLines() {
        GstCalculator.calculateInvoice(
            listOf(GstLineInput(1.0, Long.MAX_VALUE / 2 + 1, 0.0), GstLineInput(1.0, Long.MAX_VALUE / 2 + 1, 0.0)),
            0, "02", "02"
        )
    }

    @Test fun acceptsValidFormatGstinAndOptionalBlank() {
        assertEquals(true, GstCalculator.validateGstin("02ABCDE1234F1Z5"))
        assertEquals(true, GstCalculator.validateGstin(""))
        assertEquals(true, GstCalculator.validateGstin("   "))
    }

    @Test fun rejectsMalformedGstin() {
        assertEquals(false, GstCalculator.validateGstin("02ABCDE1234F1Z"))
        assertEquals(false, GstCalculator.validateGstin("XXABCDE1234F1Z5"))
        assertEquals(true, GstCalculator.validateGstin("02abcde1234f1z5"))
    }

}
