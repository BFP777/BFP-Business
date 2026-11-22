package `in`.bhajan.bfpbusinesspro.data

/**
 * Conservative parser for common supplier invoice rows of the form:
 * Description [HSN/code] Qty Rate Amount.
 * It intentionally rejects ambiguous rows; it never posts accounting data.
 */
data class SupplierBillLineCandidate(
    val description: String,
    val quantity: Double,
    val rateRupees: Double,
    val lineAmountRupees: Double
)

object SupplierBillOcrParser {
    private val rowPattern = Regex(
        "(?i)^(.+?)\\s+(\\d+(?:\\.\\d{1,3})?)\\s+(?:₹|rs\\.?|inr)?\\s*([\\d,]+(?:\\.\\d{1,2})?)\\s+(?:₹|rs\\.?|inr)?\\s*([\\d,]+(?:\\.\\d{1,2})?)\\s*$"
    )

    fun parse(text: String): List<SupplierBillLineCandidate> {
        val results = mutableListOf<SupplierBillLineCandidate>()
        val seen = mutableSetOf<String>()
        text.lineSequence().map { it.replace('\u00A0', ' ').replace(Regex("\\s+"), " ").trim() }
            .filter { it.length >= 6 }
            .forEach { line ->
                val match = rowPattern.matchEntire(line) ?: return@forEach
                val description = match.groupValues[1].trim().trim('-', ':', '|')
                if (description.length < 2 || description.none { it.isLetter() }) return@forEach
                val quantity = match.groupValues[2].toDoubleOrNull() ?: return@forEach
                val rate = match.groupValues[3].replace(",", "").toDoubleOrNull() ?: return@forEach
                val amount = match.groupValues[4].replace(",", "").toDoubleOrNull() ?: return@forEach
                if (!quantity.isFinite() || !rate.isFinite() || !amount.isFinite()) return@forEach
                if (quantity <= 0.0 || quantity > 10000.0 || rate < 0.0 || rate > 10_000_000.0 || amount < 0.0) return@forEach
                val expected = quantity * rate
                // Allow GST-inclusive line total, rounding and modest OCR variation.
                if (expected > 0.0 && kotlin.math.abs(amount - expected) / expected > 0.35) return@forEach
                val key = description.lowercase() + "|" + quantity + "|" + rate
                if (seen.add(key)) results += SupplierBillLineCandidate(description, quantity, rate, amount)
            }
        return results
    }
}


/** Review-only metadata and arithmetic checks for supplier-bill OCR. */
data class SupplierBillMetadata(
    val invoiceNumber: String?,
    val invoiceDate: String?,
    val gstin: String?,
    val invoiceTotalRupees: Double?,
    val cgstRupees: Double?,
    val sgstRupees: Double?,
    val igstRupees: Double?
)

data class SupplierBillCheck(val label: String, val passed: Boolean?, val detail: String)

object SupplierBillVerifier {
    private fun amount(text: String, labels: String): Double? {
        val pattern = Regex("(?im)^\\s*(?:$labels)\\s*[:\\-]?\\s*(?:₹|rs\\.?|inr)?\\s*([0-9,]+(?:\\.[0-9]{1,2})?)")
        val raw = pattern.find(text)?.groupValues?.getOrNull(1) ?: return null
        return raw.replace(",", "").toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 }
    }

    fun extract(text: String): SupplierBillMetadata {
        val gstin = Regex("(?i)\\b[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]\\b").find(text)?.value?.uppercase()
        val invoice = Regex("(?im)\\b(?:invoice\\s*(?:no|number)?|bill\\s*(?:no|number)?|inv\\s*no)\\s*[:#.-]?\\s*([A-Z0-9/-]{2,24})").find(text)?.groupValues?.getOrNull(1)
        val date = Regex("(?i)\\b([0-3]?\\d[-/.][01]?\\d[-/.](?:20)?\\d{2})\\b").find(text)?.groupValues?.getOrNull(1)
        return SupplierBillMetadata(invoice, date, gstin,
            amount(text, "grand\\s+total|invoice\\s+total|net\\s+amount|total\\s+amount|total"),
            amount(text, "cgst(?:\\s+amount)?"), amount(text, "sgst(?:\\s+amount)?"), amount(text, "igst(?:\\s+amount)?"))
    }

    fun checks(metadata: SupplierBillMetadata, rows: List<SupplierBillLineCandidate>, supplierGstin: String): List<SupplierBillCheck> {
        val result = mutableListOf<SupplierBillCheck>()
        val scanned = metadata.gstin.orEmpty()
        if (scanned.isBlank()) result += SupplierBillCheck("GSTIN", null, "बिल में GSTIN नहीं मिला")
        else if (!GstCalculator.validateGstin(scanned)) result += SupplierBillCheck("GSTIN", false, "OCR से मिला GSTIN प्रारूप गलत है: $scanned")
        else if (supplierGstin.isNotBlank() && scanned != supplierGstin.trim().uppercase()) result += SupplierBillCheck("GSTIN", false, "बिल GSTIN ($scanned) सप्लायर मास्टर से अलग है (${supplierGstin.trim().uppercase()})")
        else result += SupplierBillCheck("GSTIN", true, "GSTIN का प्रारूप सही है${if (supplierGstin.isNotBlank()) " और सप्लायर मास्टर से मेल खाता है" else ""}")

        val total = metadata.invoiceTotalRupees
        if (total == null) result += SupplierBillCheck("कुल राशि", null, "बिल का कुल योग नहीं पढ़ा जा सका")
        else if (rows.isEmpty()) result += SupplierBillCheck("कुल राशि", null, "आइटम पंक्तियाँ स्पष्ट नहीं हैं; कुल राशि से मिलान संभव नहीं")
        else {
            val lineTotal = rows.sumOf { it.lineAmountRupees }
            val gstTotal = listOfNotNull(metadata.cgstRupees, metadata.sgstRupees, metadata.igstRupees).sum()
            val expectedTotal = lineTotal + gstTotal
            val difference = kotlin.math.abs(total - expectedTotal)
            val allowed = maxOf(2.0, total * 0.01)
            result += SupplierBillCheck("कुल राशि", difference <= allowed,
                "बिल कुल ₹${"%.2f".format(total)} | आइटम ₹${"%.2f".format(lineTotal)} + पढ़ा GST ₹${"%.2f".format(gstTotal)} = ₹${"%.2f".format(expectedTotal)} | अंतर ₹${"%.2f".format(difference)}${if (difference <= allowed) " (सीमा के भीतर)" else " (मैनुअल जाँच जरूरी)"}")
        }
        val gstParts = listOfNotNull(metadata.cgstRupees, metadata.sgstRupees, metadata.igstRupees)
        if (gstParts.isNotEmpty() && metadata.invoiceTotalRupees != null) {
            result += SupplierBillCheck("GST विवरण", gstParts.sum() <= metadata.invoiceTotalRupees,
                "पढ़े गए CGST/SGST/IGST का योग ₹${"%.2f".format(gstParts.sum())}; इसे मूल बिल से मिलाएँ")
        } else result += SupplierBillCheck("GST विवरण", null, "GST राशि पूरी तरह नहीं पढ़ी गई; मूल बिल से जाँचें")
        return result
    }
}
