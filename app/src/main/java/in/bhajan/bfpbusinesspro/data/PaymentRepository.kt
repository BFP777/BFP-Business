package `in`.bhajan.bfpbusinesspro.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.first

/** Keeps invoice paidPaise derived from linked payment rows, never independently edited. */
class PaymentRepository(private val database: BfpDatabase) {
    private val dao = database.dao()

    data class PaymentInput(
        val partyId: Long,
        val invoiceId: Long? = null,
        val direction: String,
        val mode: String,
        val amountPaise: Long,
        val dateEpochMillis: Long = System.currentTimeMillis(),
        val note: String = "",
        val bankAccountId: Long? = null
    )

    suspend fun create(input: PaymentInput): Long = database.withTransaction {
        validate(input)
        val id = dao.insertPayment(input.toEntity())
        input.invoiceId?.let { refreshInvoicePaid(it) }
        id
    }

    suspend fun update(paymentId: Long, input: PaymentInput) = database.withTransaction {
        val old = dao.getPayment(paymentId) ?: error("भुगतान एंट्री नहीं मिली")
        validate(input, paymentId)
        dao.updatePayment(old.copy(
            partyId = input.partyId, invoiceId = input.invoiceId,
            direction = input.direction, mode = input.mode,
            amountPaise = input.amountPaise, dateEpochMillis = input.dateEpochMillis,
            note = input.note, bankAccountId = input.bankAccountId
        ))
        (listOfNotNull(old.invoiceId, input.invoiceId)).distinct().forEach { refreshInvoicePaid(it) }
    }

    suspend fun delete(paymentId: Long) = database.withTransaction {
        val old = dao.getPayment(paymentId) ?: return@withTransaction
        dao.deletePayment(old)
        old.invoiceId?.let { refreshInvoicePaid(it) }
    }

    private suspend fun validate(input: PaymentInput, editingId: Long? = null) {
        require(input.partyId > 0) { "पार्टी चुनें" }
        require(input.direction == "IN" || input.direction == "OUT") { "भुगतान का प्रकार गलत है" }
        require(input.mode in setOf("CASH", "UPI", "BANK")) { "भुगतान माध्यम चुनें" }
        if (input.mode == "BANK") {
            require(input.bankAccountId != null && dao.getBankAccount(input.bankAccountId) != null) { "बैंक अकाउंट चुनें" }
        } else require(input.bankAccountId == null) { "बैंक अकाउंट केवल BANK माध्यम में चुनें" }
        require(input.amountPaise > 0) { "भुगतान राशि शून्य से अधिक होनी चाहिए" }
        require(input.dateEpochMillis > 0) { "तारीख सही नहीं है" }
        val party = dao.observeParties().first().firstOrNull { it.id == input.partyId }
            ?: error("पार्टी नहीं मिली")
        require(party.partyType == "BOTH" ||
            (input.direction == "IN" && party.partyType == "CUSTOMER") ||
            (input.direction == "OUT" && party.partyType == "SUPPLIER")) {
            if (input.direction == "IN") "Payment In के लिए ग्राहक चुनें" else "Payment Out के लिए सप्लायर चुनें"
        }
        if (input.invoiceId != null) {
            val invoice = dao.getInvoice(input.invoiceId) ?: error("संबंधित बिल नहीं मिला")
            require(invoice.partyId == input.partyId) { "बिल और पार्टी अलग-अलग हैं" }
            require((invoice.kind == "SALE" && input.direction == "IN") || (invoice.kind == "PURCHASE" && input.direction == "OUT")) {
                "SALE में Payment In और PURCHASE में Payment Out ही मान्य है"
            }
            val oldPayment = editingId?.let { dao.getPayment(it) }
            val amountToSubtract = if (oldPayment?.invoiceId == invoice.id) oldPayment.amountPaise else 0L
            val alreadyPaid = dao.sumInvoicePayments(invoice.id) - amountToSubtract
            require(alreadyPaid + input.amountPaise <= invoice.totalPaise) { "भुगतान बिल की बाकी राशि से अधिक है" }
        }
    }

    private suspend fun refreshInvoicePaid(invoiceId: Long) {
        val invoice = dao.getInvoice(invoiceId) ?: return
        val paid = dao.sumInvoicePayments(invoiceId)
        require(paid <= invoice.totalPaise) { "भुगतान बिल की कुल राशि से अधिक हो गया" }
        dao.updateInvoice(invoice.copy(paidPaise = paid))
    }

    private fun PaymentInput.toEntity() = PaymentEntity(
        partyId = partyId, invoiceId = invoiceId, direction = direction,
        mode = mode, amountPaise = amountPaise,
        dateEpochMillis = dateEpochMillis, note = note, bankAccountId = bankAccountId
    )
}
