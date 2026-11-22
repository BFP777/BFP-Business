package `in`.bhajan.bfpbusinesspro.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SupplierBillOcrParserTest {
    @Test fun parsesSimpleSupplierRowsAndCommaAmounts() {
        val rows = SupplierBillOcrParser.parse("""
            Item Description Qty Rate Amount
            Cotton Suit Design A 2 1,250.00 2,500.00
            Printed Suit B 1 850.00 850.00
            Grand Total 3,350.00
        """.trimIndent())
        assertEquals(2, rows.size)
        assertEquals("Cotton Suit Design A", rows[0].description)
        assertEquals(2.0, rows[0].quantity, 0.0)
        assertEquals(1250.0, rows[0].rateRupees, 0.0)
    }

    @Test fun rejectsAmbiguousOrNonItemRows() {
        val rows = SupplierBillOcrParser.parse("""
            Invoice No 12345
            GSTIN 02ABCDE1234F1Z5
            Total Amount 9,999.00
            Item X 2 100.00 900.00
        """.trimIndent())
        assertTrue(rows.isEmpty())
    }

    @Test fun deduplicatesRepeatedOcrLines() {
        val rows = SupplierBillOcrParser.parse("Suit A 2 500.00 1,000.00\nSuit A 2 500.00 1,000.00")
        assertEquals(1, rows.size)
    }
    @Test fun extractsSupplierBillMetadataAndReconcilesTotals() {
        val text = """
            Invoice No: ABC/2026/19
            Date: 18/11/2026
            GSTIN: 02ABCDE1234F1Z5
            Suit Design A 2 1,000.00 2,000.00
            Suit Design B 1 500.00 500.00
            CGST 225.00
            SGST 225.00
            Grand Total 2,950.00
        """.trimIndent()
        val metadata = SupplierBillVerifier.extract(text)
        assertEquals("ABC/2026/19", metadata.invoiceNumber)
        assertEquals("18/11/2026", metadata.invoiceDate)
        assertEquals("02ABCDE1234F1Z5", metadata.gstin)
        assertEquals(2950.0, metadata.invoiceTotalRupees!!, 0.0)
        val checks = SupplierBillVerifier.checks(metadata, SupplierBillOcrParser.parse(text), "02ABCDE1234F1Z5")
        assertTrue(checks.any { it.label == "GSTIN" && it.passed == true })
        assertTrue(checks.any { it.label == "कुल राशि" && it.passed == true })
    }

    @Test fun flagsSupplierGstinMismatchAndTotalDifference() {
        val metadata = SupplierBillVerifier.extract("GSTIN: 02ABCDE1234F1Z5\nGrand Total 999.00")
        val checks = SupplierBillVerifier.checks(metadata, emptyList(), "03ABCDE1234F1Z6")
        assertTrue(checks.any { it.label == "GSTIN" && it.passed == false })
        assertTrue(checks.any { it.label == "कुल राशि" && it.passed == null })
    }

}
