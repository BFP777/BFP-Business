package `in`.bhajan.bfpbusinesspro.data

import kotlinx.coroutines.flow.first

/** Read-only unified view of source transactions. It never creates duplicate ledger/day-book rows. */
class LedgerRepository(private val database: BfpDatabase) {
    private val dao = database.dao()

    data class LedgerRow(
        val dateEpochMillis: Long,
        val reference: String,
        val description: String,
        /** Positive = party owes shop; negative = shop owes party. */
        val debitPaise: Long,
        val creditPaise: Long,
        val balancePaise: Long
    )

    data class PartyLedgerAudit(
        val invoiceCount: Int,
        val invoiceTotalPaise: Long,
        val linkedPaymentPaise: Long,
        val unallocatedReceiptPaise: Long,
        val unallocatedPaymentPaise: Long,
        val storedPaidMismatchCount: Int,
        val invalidLinkCount: Int,
        val issues: List<String>
    )

    /** Reconciles source invoices and payments for one party without creating ledger entries. */
    suspend fun auditPartyLedger(partyId: Long): PartyLedgerAudit {
        val party = dao.getParty(partyId) ?: error("पार्टी नहीं मिली")
        val invoices = (dao.observeInvoices("SALE").first() + dao.observeInvoices("PURCHASE").first())
            .filter { it.partyId == partyId }
        val allPayments = dao.getPartyPayments(partyId)
        val allInvoices = (dao.observeInvoices("SALE").first() + dao.observeInvoices("PURCHASE").first())
            .associateBy { it.id }
        val linked = allPayments.filter { it.invoiceId != null }
        val issues = mutableListOf<String>()
        var invalidLinks = 0
        linked.forEach { payment ->
            val invoice = payment.invoiceId?.let(allInvoices::get)
            val valid = invoice != null && invoice.partyId == partyId &&
                ((invoice.kind == "SALE" && payment.direction == "IN") ||
                 (invoice.kind == "PURCHASE" && payment.direction == "OUT"))
            if (!valid) { invalidLinks++; issues += "PAY-${payment.id}: बिल/पार्टी/भुगतान दिशा का मिलान नहीं" }
        }
        var mismatch = 0
        invoices.forEach { invoice ->
            val linkedTotal = allPayments.filter { it.invoiceId == invoice.id }.sumOf { it.amountPaise }
            if (linkedTotal != invoice.paidPaise) {
                mismatch++
                issues += "${invoice.invoiceNumber}: बिल में दर्ज Paid और भुगतान एंट्री का अंतर ₹${kotlin.math.abs(linkedTotal - invoice.paidPaise) / 100.0}"
            }
            if (linkedTotal > invoice.totalPaise) issues += "${invoice.invoiceNumber}: भुगतान बिल राशि से अधिक"
        }
        val unallocatedReceipts = allPayments.filter { it.invoiceId == null && it.direction == "IN" }.sumOf { it.amountPaise }
        val unallocatedPayments = allPayments.filter { it.invoiceId == null && it.direction == "OUT" }.sumOf { it.amountPaise }
        if (party.partyType == "SUPPLIER") {
            // Supplier advance is Payment Out; customer receipt is not expected for supplier-only parties.
            if (unallocatedReceipts > 0) issues += "सप्लायर खाते में बिना बिल के Payment In दर्ज है"
        }
        if (party.partyType == "CUSTOMER" && unallocatedPayments > 0) issues += "ग्राहक खाते में बिना बिल के Payment Out दर्ज है"
        return PartyLedgerAudit(
            invoiceCount = invoices.size, invoiceTotalPaise = invoices.sumOf { it.totalPaise },
            linkedPaymentPaise = linked.sumOf { it.amountPaise },
            unallocatedReceiptPaise = unallocatedReceipts, unallocatedPaymentPaise = unallocatedPayments,
            storedPaidMismatchCount = mismatch, invalidLinkCount = invalidLinks, issues = issues
        )
    }

    data class BusinessFinancialAudit(
        val salesPaise: Long, val purchasesPaise: Long, val expensesPaise: Long,
        val salaryPaidPaise: Long, val receiptsPaise: Long, val paymentsPaise: Long,
        val stockDesignCount: Int, val stockQuantity: Double,
        val issueCount: Int, val issues: List<String>
    )

    /** Cross-module, read-only audit of source records. It never posts or edits transactions. */
    suspend fun auditBusinessFinancials(): BusinessFinancialAudit {
        val parties = dao.observeParties().first().associateBy { it.id }
        val items = dao.observeItems().first()
        val sales = dao.observeInvoices("SALE").first()
        val purchases = dao.observeInvoices("PURCHASE").first()
        val invoices = sales + purchases
        val payments = dao.observePayments().first()
        val cashEntries = dao.observeCashEntries().first()
        val salaryMonths = dao.observeAllSalaryMonths().first().associateBy { it.id }
        val salaryPayments = dao.observeSalaryPayments().first()
        val employees = dao.observeEmployees().first().associateBy { it.id }
        val issues = mutableListOf<String>()
        fun issue(message: String) { if (issues.size < 100) issues += message }

        invoices.forEach { invoice ->
            if (invoice.partyId !in parties) issue("${invoice.invoiceNumber}: पार्टी रिकॉर्ड नहीं मिला")
            if (invoice.totalPaise < 0 || invoice.paidPaise < 0) issue("${invoice.invoiceNumber}: बिल/भुगतान राशि ऋणात्मक है")
            if (invoice.paidPaise > invoice.totalPaise) issue("${invoice.invoiceNumber}: दर्ज Paid राशि बिल से अधिक है")
            val expectedDirection = if (invoice.kind == "SALE") "IN" else "OUT"
            val linked = payments.filter { it.invoiceId == invoice.id }
            linked.forEach { payment ->
                if (payment.partyId != invoice.partyId || payment.direction != expectedDirection)
                    issue("${invoice.invoiceNumber}: Payment-${payment.id} का पार्टी या दिशा से मेल नहीं")
            }
            val linkedTotal = linked.sumOf { it.amountPaise }
            if (linkedTotal != invoice.paidPaise)
                issue("${invoice.invoiceNumber}: Paid field और linked payments में ₹${kotlin.math.abs(linkedTotal - invoice.paidPaise) / 100.0} का अंतर")
        }
        payments.forEach { payment ->
            if (payment.partyId !in parties) issue("Payment-${payment.id}: पार्टी रिकॉर्ड नहीं मिला")
            if (payment.amountPaise <= 0) issue("Payment-${payment.id}: राशि शून्य या ऋणात्मक है")
            if (payment.invoiceId != null && invoices.none { it.id == payment.invoiceId }) issue("Payment-${payment.id}: लिंक किया गया बिल मौजूद नहीं")
        }
        cashEntries.filter { it.amountPaise <= 0 }.forEach { issue("Cash-${it.id}: राशि शून्य या ऋणात्मक है") }
        val bankIds = dao.observeBankAccounts().first().map { it.id }.toSet()
        payments.filter { it.mode == "BANK" }.forEach { if (it.bankAccountId == null || it.bankAccountId !in bankIds) issue("Payment-${it.id}: मान्य बैंक खाता लिंक नहीं") }
        cashEntries.filter { it.mode == "BANK" || it.entryType in setOf("CASH_DEPOSIT", "CASH_WITHDRAWAL") }
            .forEach { if (it.bankAccountId == null || it.bankAccountId !in bankIds) issue("Cash-${it.id}: मान्य बैंक खाता लिंक नहीं") }
        salaryPayments.forEach { salary ->
            if (salary.employeeId !in employees) issue("Salary-${salary.id}: कर्मचारी रिकॉर्ड नहीं मिला")
            val month = salaryMonths[salary.salaryMonthId]
            if (month == null || month.employeeId != salary.employeeId) issue("Salary-${salary.id}: वेतन माह/कर्मचारी लिंक गलत है")
            if (salary.amountPaise <= 0) issue("Salary-${salary.id}: भुगतान राशि शून्य या ऋणात्मक है")
            if (salary.mode == "BANK" && (salary.bankAccountId == null || salary.bankAccountId !in bankIds)) issue("Salary-${salary.id}: मान्य बैंक खाता लिंक नहीं")
        }
        var stockQty = 0.0
        items.forEach { item ->
            stockQty += item.quantity
            val movements = dao.observeStockMovements(item.id).first()
            val movementNet = movements.sumOf { it.quantityDelta }
            if (kotlin.math.abs(movementNet - item.quantity) > 0.000001)
                issue("${item.itemCode.ifBlank { item.name }}: Stock Movement योग और मौजूदा स्टॉक में अंतर")
            if (item.quantity < 0) issue("${item.itemCode.ifBlank { item.name }}: स्टॉक ऋणात्मक है")
        }
        return BusinessFinancialAudit(
            sales.sumOf { it.totalPaise }, purchases.sumOf { it.totalPaise },
            cashEntries.filter { it.entryType == "EXPENSE" }.sumOf { it.amountPaise },
            salaryPayments.sumOf { it.amountPaise },
            payments.filter { it.direction == "IN" }.sumOf { it.amountPaise },
            payments.filter { it.direction == "OUT" }.sumOf { it.amountPaise },
            items.size, stockQty, issues.size, issues
        )
    }

    data class DayBookRow(
        val dateEpochMillis: Long,
        val reference: String,
        val description: String,
        val mode: String,
        val debitPaise: Long,
        val creditPaise: Long
    )

    data class DayBookSummary(
        val openingCashPaise: Long,
        val cashInPaise: Long,
        val cashOutPaise: Long,
        val closingCashPaise: Long,
        val rows: List<DayBookRow>
    )


    data class MoneyReconciliation(
        val cashPaise: Long, val upiPaise: Long, val bankPaise: Long,
        val bankAccountTotalPaise: Long, val unassignedBankMovementCount: Int,
        val issues: List<String>
    )

    /** Read-only reconciliation of cash/UPI/bank source rows and named bank accounts. */
    suspend fun reconcileMoneyBalances(): MoneyReconciliation {
        val payments = dao.observePayments().first()
        val entries = dao.observeCashEntries().first()
        val salaries = dao.observeSalaryPayments().first()
        val accounts = dao.observeBankAccounts().first()
        val accountIds = accounts.map { it.id }.toSet()
        val issues = mutableListOf<String>()
        val missingBank = mutableListOf<String>()
        payments.filter { it.mode == "BANK" }.forEach {
            if (it.bankAccountId == null || it.bankAccountId !in accountIds) {
                missingBank += "PAY-${it.id}"
            }
        }
        entries.filter { it.mode == "BANK" || it.entryType in setOf("CASH_DEPOSIT", "CASH_WITHDRAWAL") }.forEach {
            if (it.bankAccountId == null || it.bankAccountId !in accountIds) {
                missingBank += "CASH-${it.id}"
            }
            if (it.entryType == "BANK_TRANSFER") {
                val destinationId = it.note.split("|", limit = 4).getOrNull(2)?.toLongOrNull()
                if (destinationId == null || destinationId !in accountIds) missingBank += "TRANSFER-TO-${it.id}"
            }
        }
        salaries.filter { it.mode == "BANK" }.forEach {
            if (it.bankAccountId == null || it.bankAccountId !in accountIds) {
                missingBank += "SAL-${it.id}"
            }
        }
        if (missingBank.isNotEmpty()) issues += "${missingBank.size} बैंक एंट्री किसी मान्य बैंक खाते से लिंक नहीं: ${missingBank.take(8).joinToString()}"
        val cash = currentCashBalancePaise()
        val upi = upiBalancePaise()
        val bank = bankBalancePaise()
        val accountTotal = accounts.sumOf { bankAccountBalancePaise(it.id) }
        if (missingBank.isEmpty() && bank != accountTotal) {
            issues += "कुल Bank Balance और सभी बैंक खातों के योग में ₹${kotlin.math.abs(bank - accountTotal) / 100.0} का अंतर"
        }
        if (accounts.isEmpty() && bank != 0L) issues += "बैंक बैलेंस दर्ज है, लेकिन कोई बैंक खाता बनाया नहीं गया"
        return MoneyReconciliation(cash, upi, bank, accountTotal, missingBank.size, issues)
    }

    /** Current cash balance in paise, using only cash-mode movements. */
    suspend fun currentCashBalancePaise(): Long {
        val payments = dao.observePayments().first().filter { it.mode == "CASH" }
        val entries = dao.observeCashEntries().first().filter { it.mode == "CASH" }
        val salaryOut = dao.observeSalaryPayments().first().filter { it.mode == "CASH" }.sumOf { it.amountPaise }
        val paymentNet = payments.sumOf { if (it.direction == "IN") it.amountPaise else -it.amountPaise }
        val entryNet = entries.sumOf { when (it.entryType) {
            "OPENING", "CASH_WITHDRAWAL" -> it.amountPaise
            else -> -it.amountPaise
        } }
        return paymentNet + entryNet - salaryOut
    }

    /** Current UPI balance: UPI receipts minus UPI payments and UPI expenses. */
    suspend fun upiBalancePaise(): Long {
        val payments = dao.observePayments().first().filter { it.mode == "UPI" }
        val upiEntries = dao.observeCashEntries().first().filter { it.mode == "UPI" }
        val salaryOut = dao.observeSalaryPayments().first().filter { it.mode == "UPI" }.sumOf { it.amountPaise }
        val paymentNet = payments.sumOf { if (it.direction == "IN") it.amountPaise else -it.amountPaise }
        val entryNet = upiEntries.sumOf { entry -> when (entry.entryType) {
            "OPENING" -> entry.amountPaise
            "EXPENSE" -> -entry.amountPaise
            else -> 0L
        } }
        return paymentNet + entryNet - salaryOut
    }

    /** Current bank balance in paise. Cash deposits increase bank; cash withdrawals reduce it. */
    suspend fun bankBalancePaise(): Long {
        val payments = dao.observePayments().first().filter { it.mode == "BANK" }
        val entries = dao.observeCashEntries().first()
        val salaryOut = dao.observeSalaryPayments().first().filter { it.mode == "BANK" }.sumOf { it.amountPaise }
        val paymentNet = payments.sumOf { if (it.direction == "IN") it.amountPaise else -it.amountPaise }
        val entryNet = entries.sumOf { entry -> when {
            entry.mode == "BANK" && entry.entryType == "OPENING" -> entry.amountPaise
            entry.mode == "BANK" && entry.entryType == "EXPENSE" -> -entry.amountPaise
            entry.entryType == "CASH_DEPOSIT" -> entry.amountPaise
            entry.entryType == "CASH_WITHDRAWAL" -> -entry.amountPaise
            entry.entryType == "BANK_ADJUSTMENT_IN" -> entry.amountPaise
            entry.entryType == "BANK_ADJUSTMENT_OUT" -> -entry.amountPaise
            else -> 0L
        } }
        return paymentNet + entryNet - salaryOut
    }

    /** Bank balance isolated to one named account. */
    suspend fun bankAccountBalancePaise(bankAccountId: Long): Long {
        val payments = dao.observePayments().first().filter { it.mode == "BANK" && it.bankAccountId == bankAccountId }
        val entries = dao.observeCashEntries().first().filter { it.bankAccountId == bankAccountId }
        val salaryOut = dao.observeSalaryPayments().first().filter { it.mode == "BANK" && it.bankAccountId == bankAccountId }.sumOf { it.amountPaise }
        val paymentNet = payments.sumOf { if (it.direction == "IN") it.amountPaise else -it.amountPaise }
        val entryNet = entries.sumOf { entry -> when {
            entry.mode == "BANK" && entry.entryType == "OPENING" -> entry.amountPaise
            entry.mode == "BANK" && entry.entryType == "EXPENSE" -> -entry.amountPaise
            entry.entryType == "CASH_DEPOSIT" -> entry.amountPaise
            entry.entryType == "CASH_WITHDRAWAL" -> -entry.amountPaise
            entry.entryType == "BANK_ADJUSTMENT_IN" -> if (entry.mode == "BANK") entry.amountPaise else 0L
            entry.entryType == "BANK_ADJUSTMENT_OUT" -> if (entry.mode == "BANK") -entry.amountPaise else 0L
            entry.entryType == "BANK_TRANSFER" -> {
                val parts = entry.note.split("|", limit = 4)
                val sourceId = parts.getOrNull(1)?.toLongOrNull()
                val destinationId = parts.getOrNull(2)?.toLongOrNull()
                when (bankAccountId) { sourceId -> -entry.amountPaise; destinationId -> entry.amountPaise; else -> 0L }
            }
            else -> 0L
        } }
        return paymentNet + entryNet - salaryOut
    }

    suspend fun partyLedger(partyId: Long): List<LedgerRow> {
        require(dao.observeParties().first().any { it.id == partyId }) { "पार्टी नहीं मिली" }
        val invoices = (dao.observeInvoices("SALE").first() + dao.observeInvoices("PURCHASE").first())
            .filter { it.partyId == partyId }
        val payments = dao.getPartyPayments(partyId)
        data class Entry(val date: Long, val ref: String, val desc: String, val debit: Long, val credit: Long, val id: Long)
        val entries = mutableListOf<Entry>()
        invoices.forEach { inv ->
            val sale = inv.kind == "SALE"
            entries += Entry(inv.dateEpochMillis, inv.invoiceNumber, if (sale) "बिक्री बिल" else "खरीद बिल",
                if (sale) inv.totalPaise else 0L, if (sale) 0L else inv.totalPaise, inv.id)
        }
        payments.forEach { p ->
            val isIn = p.direction == "IN"
            entries += Entry(p.dateEpochMillis, "PAY-${p.id}", if (isIn) "प्राप्त भुगतान (${p.mode})" else "किया गया भुगतान (${p.mode})",
                if (isIn) 0L else p.amountPaise, if (isIn) p.amountPaise else 0L, 1_000_000_000L + p.id)
        }
        var balance = 0L
        return entries.sortedWith(compareBy<Entry> { it.date }.thenBy { it.id }).map { e ->
            balance += e.debit - e.credit
            LedgerRow(e.date, e.ref, e.desc, e.debit, e.credit, balance)
        }
    }

    suspend fun dayBook(fromEpochMillis: Long, toEpochMillis: Long): DayBookSummary {
        require(fromEpochMillis <= toEpochMillis) { "तारीख की सीमा गलत है" }
        val sales = dao.observeInvoices("SALE").first()
        val purchases = dao.observeInvoices("PURCHASE").first()
        val payments = dao.observePayments().first()
        val salaryPayments = dao.observeSalaryPayments().first()
        val employees = dao.observeEmployees().first().associate { it.id to it.name }
        val cashEntries = dao.observeCashEntries().first()
        val bankNames = dao.observeBankAccounts().first().associate { it.id to it.accountName }
        val all = mutableListOf<DayBookRow>()
        sales.filter { it.dateEpochMillis in fromEpochMillis..toEpochMillis }.forEach {
            all += DayBookRow(it.dateEpochMillis, it.invoiceNumber, "बिक्री बिल दर्ज — ₹${it.totalPaise / 100.0}", "BILL", 0L, 0L)
        }
        purchases.filter { it.dateEpochMillis in fromEpochMillis..toEpochMillis }.forEach {
            all += DayBookRow(it.dateEpochMillis, it.invoiceNumber, "खरीद बिल दर्ज — ₹${it.totalPaise / 100.0}", "BILL", 0L, 0L)
        }
        payments.filter { it.dateEpochMillis in fromEpochMillis..toEpochMillis }.forEach { p ->
            val incoming = p.direction == "IN"
            val bankLabel = if (p.mode == "BANK") p.bankAccountId?.let(bankNames::get)?.let { " — $it" }
                ?: " — बैंक खाता उपलब्ध नहीं" else ""
            all += DayBookRow(p.dateEpochMillis, "PAY-${p.id}", (if (incoming) "Payment In" else "Payment Out") + bankLabel, p.mode,
                if (incoming) 0L else p.amountPaise, if (incoming) p.amountPaise else 0L)
        }
        salaryPayments.filter { it.dateEpochMillis in fromEpochMillis..toEpochMillis }.forEach { salary ->
            val employeeName = employees[salary.employeeId] ?: "कर्मचारी #${salary.employeeId}"
            val bankLabel = if (salary.mode == "BANK") salary.bankAccountId?.let(bankNames::get)?.let { " — $it" }.orEmpty() else ""
            all += DayBookRow(salary.dateEpochMillis, "SAL-${salary.id}", "वेतन भुगतान — $employeeName$bankLabel", salary.mode, salary.amountPaise, 0L)
        }
        cashEntries.filter { it.dateEpochMillis in fromEpochMillis..toEpochMillis }.forEach { e ->
            val inflow = e.entryType == "OPENING" || e.entryType == "CASH_WITHDRAWAL"
            val bankLabel = e.bankAccountId?.let(bankNames::get)?.let { " — $it" }
                ?: if (e.entryType in setOf("CASH_DEPOSIT", "CASH_WITHDRAWAL") || e.mode == "BANK") " — बैंक खाता उपलब्ध नहीं" else ""
            val description = when (e.entryType) {
                "OPENING" -> "Opening Balance (${e.mode})$bankLabel"
                "EXPENSE" -> "व्यापार खर्च (${e.mode})$bankLabel"
                "CASH_DEPOSIT" -> "Cash से Bank में जमा$bankLabel"
                "CASH_WITHDRAWAL" -> "Bank से Cash निकासी$bankLabel"
                else -> "अन्य कैश/बैंक एंट्री (${e.entryType})$bankLabel"
            }
            val displayMode = when (e.entryType) {
                "CASH_DEPOSIT" -> "CASH → BANK"
                "CASH_WITHDRAWAL" -> "BANK → CASH"
                else -> e.mode
            }
            all += DayBookRow(e.dateEpochMillis, "CASH-${e.id}", description, displayMode,
                if (inflow) 0L else e.amountPaise, if (inflow) e.amountPaise else 0L)
        }
        val ordered = all.sortedWith(compareBy<DayBookRow> { it.dateEpochMillis }.thenBy { it.reference })
        // Cash balance is based only on cash movements, not credit invoices or UPI/Bank transfers.
        val priorCashPayments = payments.filter { it.mode == "CASH" && it.dateEpochMillis < fromEpochMillis }
        val priorCashEntries = cashEntries.filter { it.mode == "CASH" && it.dateEpochMillis < fromEpochMillis }
        val priorSalaryCash = salaryPayments.filter { it.mode == "CASH" && it.dateEpochMillis < fromEpochMillis }.sumOf { it.amountPaise }
        val opening = priorCashPayments.sumOf { if (it.direction == "IN") it.amountPaise else -it.amountPaise } +
            priorCashEntries.sumOf { when (it.entryType) { "OPENING", "CASH_WITHDRAWAL" -> it.amountPaise; else -> -it.amountPaise } } - priorSalaryCash
        val periodCashIn = payments.filter { it.mode == "CASH" && it.dateEpochMillis in fromEpochMillis..toEpochMillis && it.direction == "IN" }.sumOf { it.amountPaise } +
            cashEntries.filter { it.mode == "CASH" && it.dateEpochMillis in fromEpochMillis..toEpochMillis && it.entryType in setOf("OPENING", "CASH_WITHDRAWAL") }.sumOf { it.amountPaise }
        val periodSalaryCash = salaryPayments.filter { it.mode == "CASH" && it.dateEpochMillis in fromEpochMillis..toEpochMillis }.sumOf { it.amountPaise }
        val periodCashOut = payments.filter { it.mode == "CASH" && it.dateEpochMillis in fromEpochMillis..toEpochMillis && it.direction == "OUT" }.sumOf { it.amountPaise } +
            cashEntries.filter { it.mode == "CASH" && it.dateEpochMillis in fromEpochMillis..toEpochMillis && it.entryType !in setOf("OPENING", "CASH_WITHDRAWAL") }.sumOf { it.amountPaise } + periodSalaryCash
        return DayBookSummary(opening, periodCashIn, periodCashOut, opening + periodCashIn - periodCashOut, ordered)
    }
}
