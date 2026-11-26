package `in`.bhajan.bfpbusinesspro.data

import androidx.room.withTransaction

/**
 * Invoice posting with atomic inventory updates for Item Master-linked lines.
 * Purchase lines must reference a stock item so received quantities cannot be
 * silently omitted from inventory; sales may still contain free-text service lines.
 */
class InvoiceRepository(private val database: BfpDatabase) {
    private val dao = database.dao()

    data class LineInput(
        val itemName: String,
        val quantity: Double,
        val ratePaise: Long,
        val gstRatePercent: Double = 0.0,
        val hsnCode: String = "",
        val itemId: Long = 0L,
        val itemCode: String = ""
    )

    data class InvoiceInput(
        val invoiceNumber: String,
        val partyId: Long,
        val kind: String,
        val dateEpochMillis: Long,
        val discountPaise: Long = 0,
        val taxPaise: Long = 0,
        val note: String = "",
        val sellerStateCode: String = "02",
        val buyerStateCode: String = "02",
        val lines: List<LineInput>,
        val attachmentPath: String = ""
    )

    suspend fun create(input: InvoiceInput): Long = database.withTransaction {
        createInvoiceInTransaction(input)
    }

    /** Creates a sale and its full CASH receipt atomically, so cash and invoice ledgers cannot diverge. */
    suspend fun createCashSale(input: InvoiceInput): Long = database.withTransaction {
        require(input.kind == "SALE") { "CASH भुगतान केवल बिक्री बिल में मान्य है" }
        val invoiceId = createInvoiceInTransaction(input)
        val invoice = dao.getInvoice(invoiceId) ?: error("बिल सेव नहीं हुआ")
        if (invoice.totalPaise > 0) {
            dao.insertPayment(
                PaymentEntity(
                    partyId = invoice.partyId,
                    invoiceId = invoice.id,
                    direction = "IN",
                    mode = "CASH",
                    amountPaise = invoice.totalPaise,
                    dateEpochMillis = invoice.dateEpochMillis,
                    note = "बिक्री बिल ${invoice.invoiceNumber} — CASH"
                )
            )
            dao.updateInvoice(invoice.copy(paidPaise = invoice.totalPaise))
        }
        invoiceId
    }

    private suspend fun createInvoiceInTransaction(input: InvoiceInput): Long {
        validate(input)
        validateAndPostStock(input, emptyList())
        val gst = calculateGst(input)
        val subtotal = gst.subtotalPaise
        val taxTotal = gst.cgstPaise + gst.sgstPaise + gst.igstPaise
        val total = gst.totalPaise

        val invoiceId = dao.insertInvoice(
            InvoiceEntity(
                invoiceNumber = input.invoiceNumber.trim(),
                partyId = input.partyId,
                kind = input.kind,
                dateEpochMillis = input.dateEpochMillis,
                subtotalPaise = subtotal,
                discountPaise = input.discountPaise,
                taxPaise = taxTotal,
                cgstPaise = gst.cgstPaise,
                sgstPaise = gst.sgstPaise,
                igstPaise = gst.igstPaise,
                totalPaise = total,
                paidPaise = 0,
                note = input.note,
                sellerStateCode = input.sellerStateCode,
                buyerStateCode = input.buyerStateCode,
                attachmentPath = input.attachmentPath
            )
        )
        dao.insertInvoiceLines(toEntities(invoiceId, input.lines, gst.lines))
        return invoiceId
    }

    suspend fun update(invoiceId: Long, replacement: InvoiceInput) = database.withTransaction {
        val old = dao.getInvoice(invoiceId) ?: error("बिल नहीं मिला")
        validate(replacement, invoiceId)
        val oldLinesForStock = dao.getInvoiceLines(invoiceId)
        validateAndPostStock(replacement, oldLinesForStock)
        require(old.kind == replacement.kind) { "पुराने बिल का प्रकार नहीं बदला जा सकता" }
        // Linked receipts/payments belong to the original party. Moving a partially
        // settled invoice would silently move the balance away from its payment ledger.
        if (old.partyId != replacement.partyId) {
            require(dao.countPaymentsForInvoice(invoiceId) == 0) {
                "इस बिल में भुगतान जुड़ा है; भुगतान हटाए बिना ग्राहक/सप्लायर नहीं बदला जा सकता"
            }
        }

        val gst = calculateGst(replacement)
        val subtotal = gst.subtotalPaise
        val taxTotal = gst.cgstPaise + gst.sgstPaise + gst.igstPaise
        val total = gst.totalPaise
        // Payment rows are the source of truth. Never trust a stale cached paidPaise
        // value when an invoice is edited; otherwise an edit could preserve a phantom
        // payment or permit a new total below the actual linked receipts/payments.
        val linkedPaidPaise = dao.sumInvoicePayments(invoiceId)
        require(linkedPaidPaise in 0..total) { "जुड़े हुए भुगतान की रकम नए बिल से अधिक है; पहले भुगतान समायोजित करें" }

        dao.updateInvoice(
            old.copy(
                invoiceNumber = replacement.invoiceNumber.trim(),
                partyId = replacement.partyId,
                dateEpochMillis = replacement.dateEpochMillis,
                subtotalPaise = subtotal,
                discountPaise = replacement.discountPaise,
                taxPaise = taxTotal,
                cgstPaise = gst.cgstPaise,
                sgstPaise = gst.sgstPaise,
                igstPaise = gst.igstPaise,
                totalPaise = total,
                paidPaise = linkedPaidPaise,
                note = replacement.note,
                sellerStateCode = replacement.sellerStateCode,
                buyerStateCode = replacement.buyerStateCode,
                attachmentPath = replacement.attachmentPath
            )
        )
        dao.deleteInvoiceLines(invoiceId)
        dao.insertInvoiceLines(toEntities(invoiceId, replacement.lines, gst.lines))
    }

    suspend fun delete(invoiceId: Long) = database.withTransaction {
        val invoice = dao.getInvoice(invoiceId) ?: return@withTransaction
        val linkedPayments = dao.countPaymentsForInvoice(invoiceId)
        require(linkedPayments == 0) { "इस बिल से भुगतान जुड़े हैं। पहले भुगतान हटाएँ या अलग करें।" }
        val lines = dao.getInvoiceLines(invoiceId)
        reverseStock(invoice.kind, lines, invoice.invoiceNumber, invoice.dateEpochMillis)
        dao.deleteInvoiceLines(invoiceId)
        dao.deleteInvoice(invoice)
    }

    private suspend fun validate(input: InvoiceInput, excludeInvoiceId: Long = 0L) {
        val normalizedNumber = input.invoiceNumber.trim()
        require(normalizedNumber.isNotBlank()) { "बिल नंबर जरूरी है" }
        require(input.partyId > 0) { "ग्राहक/सप्लायर चुनें" }
        require(input.kind == "SALE" || input.kind == "PURCHASE") { "बिल का प्रकार गलत है" }
        require(input.attachmentPath.isBlank() || input.kind == "PURCHASE") { "सप्लायर बिल का अटैचमेंट केवल खरीद एंट्री में जोड़ा जा सकता है" }
        val party = dao.getParty(input.partyId) ?: error("चुनी गई पार्टी उपलब्ध नहीं है")
        require(party.partyType == "BOTH" ||
            (input.kind == "SALE" && party.partyType == "CUSTOMER") ||
            (input.kind == "PURCHASE" && party.partyType == "SUPPLIER")) {
            if (input.kind == "SALE") "SALE बिल के लिए ग्राहक चुनें" else "PURCHASE बिल के लिए सप्लायर चुनें"
        }
        if (input.kind == "PURCHASE") {
            val supplierGstin = party.gstNumber.trim().uppercase()
            require(dao.countDuplicateSupplierBills(input.partyId, supplierGstin, normalizedNumber, excludeInvoiceId) == 0) {
                "डुप्लीकेट सप्लायर बिल: इस सप्लायर/इसी GSTIN का यह बिल नंबर पहले से दर्ज है। पुराने बिल की जाँच करें।"
            }
        } else {
            require(dao.countInvoiceNumberConflicts(input.kind, normalizedNumber, excludeInvoiceId) == 0) {
                "इस प्रकार का बिल नंबर पहले से मौजूद है"
            }
        }
        require(input.dateEpochMillis > 0) { "बिल की तारीख सही नहीं है" }
        require(input.lines.isNotEmpty()) { "कम-से-कम एक आइटम जोड़ें" }
        require(input.discountPaise >= 0 && input.taxPaise >= 0) { "छूट और टैक्स शून्य या उससे अधिक होने चाहिए" }
        require(GstCalculator.validState(input.sellerStateCode) && GstCalculator.validState(input.buyerStateCode)) { "राज्य कोड सही दर्ज करें" }
        input.lines.forEach {
            require(it.itemId >= 0) { "आइटम चयन गलत है" }
            if (input.kind == "PURCHASE") {
                require(it.itemId > 0 && it.itemCode.isNotBlank()) {
                    "खरीद में स्टॉक जोड़ने के लिए Item Master से Item Code द्वारा आइटम चुनें"
                }
            }
            require(it.itemCode.isBlank() || it.itemId > 0) { "Item Code से आइटम खोजकर चुनें" }
            if (it.itemId > 0) {
                val item = dao.getItem(it.itemId) ?: error("चुना गया आइटम नहीं मिला")
                require(item.itemCode.isNotBlank() && item.itemCode.equals(it.itemCode.trim(), true)) { "आइटम कोड मेल नहीं खाता" }
            }
            require(it.itemName.isNotBlank() && it.quantity > 0.0 && it.quantity.isFinite() && it.ratePaise >= 0) { "आइटम का नाम, मात्रा या रेट सही नहीं है" }
            require(it.gstRatePercent in 0.0..100.0 && it.gstRatePercent.isFinite()) { "GST दर सही नहीं है" }
        }
    }

    /** Inventory is posted atomically with the invoice. Manual free-text lines (itemId=0) do not affect stock. */
    private suspend fun validateAndPostStock(input: InvoiceInput, oldLines: List<InvoiceLineEntity>) {
        val oldByItem = oldLines.filter { it.itemId > 0 }.groupBy { it.itemId }
        val newByItem = input.lines.filter { it.itemId > 0 }.groupBy { it.itemId }
        val ids = (oldByItem.keys + newByItem.keys).toSet()
        for (id in ids) {
            val item = dao.getItem(id) ?: error("स्टॉक वाला आइटम नहीं मिला")
            val oldQty = oldByItem[id].orEmpty().sumOf { it.quantity }
            val newQty = newByItem[id].orEmpty().sumOf { it.quantity }
            val afterReverse = if (input.kind == "SALE") item.quantity + oldQty else item.quantity - oldQty
            val finalQty = if (input.kind == "SALE") afterReverse - newQty else afterReverse + newQty
            require(finalQty >= -0.000001) { "${item.name} (${item.itemCode}) का स्टॉक पर्याप्त नहीं है" }
            require(finalQty.isFinite()) { "स्टॉक मात्रा सही नहीं है" }
            val safeFinalQty = finalQty.coerceAtLeast(0.0)
            dao.updateItem(item.copy(quantity = safeFinalQty))
            val delta = safeFinalQty - item.quantity
            if (kotlin.math.abs(delta) > 0.0000001) {
                dao.insertStockMovement(StockMovementEntity(
                    itemId = id,
                    movementType = if (oldLines.isEmpty()) input.kind else "${input.kind}_EDIT",
                    quantityDelta = delta,
                    previousQuantity = item.quantity,
                    newQuantity = safeFinalQty,
                    reason = "${if (input.kind == "PURCHASE") "खरीद" else "बिक्री"} बिल ${input.invoiceNumber.trim()}",
                    dateEpochMillis = input.dateEpochMillis,
                    reference = input.invoiceNumber.trim()
                ))
            }
        }
    }

    private suspend fun reverseStock(kind: String, lines: List<InvoiceLineEntity>, invoiceNumber: String, dateEpochMillis: Long) {
        lines.filter { it.itemId > 0 }.groupBy { it.itemId }.forEach { (id, rows) ->
            val item = dao.getItem(id) ?: error("स्टॉक वाला आइटम नहीं मिला")
            val qty = rows.sumOf { it.quantity }
            val restored = if (kind == "SALE") item.quantity + qty else item.quantity - qty
            require(restored >= -0.000001) { "पुराने खरीद बिल को हटाने से स्टॉक ऋणात्मक हो जाएगा" }
            val safeRestored = restored.coerceAtLeast(0.0)
            dao.updateItem(item.copy(quantity = safeRestored))
            val delta = safeRestored - item.quantity
            if (kotlin.math.abs(delta) > 0.0000001) {
                dao.insertStockMovement(StockMovementEntity(
                    itemId = id,
                    movementType = if (kind == "SALE") "SALE_REVERSAL" else "PURCHASE_REVERSAL",
                    quantityDelta = delta,
                    previousQuantity = item.quantity,
                    newQuantity = safeRestored,
                    reason = "बिल हटाया: $invoiceNumber",
                    dateEpochMillis = dateEpochMillis,
                    reference = invoiceNumber
                ))
            }
        }
    }

    private fun calculateGst(input: InvoiceInput): GstInvoiceResult = GstCalculator.calculateInvoice(
        input.lines.map { GstLineInput(it.quantity, it.ratePaise, it.gstRatePercent) },
        input.discountPaise, input.sellerStateCode, input.buyerStateCode
    )

    private suspend fun toEntities(invoiceId: Long, lines: List<LineInput>, gstLines: List<GstLineResult>): List<InvoiceLineEntity> =
        lines.mapIndexed { index, line ->
            val gst = gstLines[index]
            InvoiceLineEntity(
                invoiceId = invoiceId,
                itemId = line.itemId,
                itemNameSnapshot = line.itemName.trim(),
                quantity = line.quantity,
                itemCodeSnapshot = line.itemCode.trim(),
                ratePaise = line.ratePaise,
                lineTotalPaise = gst.grossBeforeDiscountPaise,
                gstRatePercent = line.gstRatePercent,
                taxablePaise = gst.taxablePaise,
                cgstPaise = gst.cgstPaise,
                sgstPaise = gst.sgstPaise,
                igstPaise = gst.igstPaise,
                hsnCode = line.hsnCode
            )
        }

}
