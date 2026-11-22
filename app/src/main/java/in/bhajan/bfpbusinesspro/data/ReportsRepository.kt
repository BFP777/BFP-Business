package `in`.bhajan.bfpbusinesspro.data

import kotlinx.coroutines.flow.first

/**
 * Read-only reports calculated from the source tables. No report rows are persisted,
 * so invoices/payments are never counted a second time by a separate reporting ledger.
 */
class ReportsRepository(private val database: BfpDatabase) {
    private val dao = database.dao()

    data class MoneyByMode(val cashPaise: Long, val upiPaise: Long, val bankPaise: Long) {
        val totalPaise: Long get() = cashPaise + upiPaise + bankPaise
    }

    data class PeriodReport(
        val fromEpochMillis: Long,
        val toEpochMillis: Long,
        val salePaise: Long,
        val purchasePaise: Long,
        val paymentIn: MoneyByMode,
        val paymentOut: MoneyByMode,
        val expenses: MoneyByMode,
        val debtorPaise: Long,
        val creditorPaise: Long
    )


    data class FinancialStatement(
        val salesNetPaise: Long, val estimatedCogsPaise: Long, val grossProfitPaise: Long,
        val operatingExpensesPaise: Long, val salaryPaise: Long, val estimatedOperatingProfitPaise: Long,
        val stockValuePaise: Long, val cashPaise: Long, val upiPaise: Long, val bankPaise: Long,
        val receivablesPaise: Long, val payablesPaise: Long, val missingCostLines: Int
    )

    /** Management estimate only: current item purchase rates approximate historical COGS. */
    suspend fun financialStatement(fromEpochMillis: Long, toEpochMillis: Long): FinancialStatement {
        require(fromEpochMillis <= toEpochMillis) { "तारीख की सीमा गलत है" }
        val sales = dao.observeInvoices("SALE").first().filter { it.dateEpochMillis in fromEpochMillis..toEpochMillis }
        val items = dao.observeItems().first().associateBy { it.id }
        var cogs = 0L
        var missingCost = 0
        sales.forEach { invoice ->
            dao.getInvoiceLines(invoice.id).forEach { line ->
                val costRate = items[line.itemId]?.purchaseRatePaise ?: 0L
                if (costRate <= 0L) missingCost++ else {
                    val raw = line.quantity * costRate.toDouble()
                    if (raw.isFinite() && raw <= Long.MAX_VALUE.toDouble()) cogs += raw.toLong()
                    else missingCost++
                }
            }
        }
        val cashEntries = dao.observeCashEntries().first().filter { it.dateEpochMillis in fromEpochMillis..toEpochMillis && it.entryType == "EXPENSE" }
        val salary = dao.observeSalaryPayments().first().filter { it.dateEpochMillis in fromEpochMillis..toEpochMillis }.sumOf { it.amountPaise }
        val money = LedgerRepository(database).reconcileMoneyBalances()
        val balances = partyBalances()
        val stockValue = items.values.sumOf { item ->
            val value = item.quantity * item.purchaseRatePaise.toDouble()
            if (value.isFinite() && value > 0 && value < Long.MAX_VALUE.toDouble()) value.toLong() else 0L
        }
        val revenue = sales.sumOf { (it.subtotalPaise - it.discountPaise).coerceAtLeast(0L) }
        val gross = revenue - cogs
        val expenses = cashEntries.sumOf { it.amountPaise }
        return FinancialStatement(revenue, cogs, gross, expenses, salary, gross - expenses - salary,
            stockValue, money.cashPaise, money.upiPaise, money.bankPaise, balances.debtorsPaise,
            balances.creditorsPaise, missingCost)
    }

    data class PartyBalances(val debtorsPaise: Long, val creditorsPaise: Long)

    data class PartyOutstanding(
        val party: PartyEntity,
        val outstandingPaise: Long,
        val billCount: Int
    )

    suspend fun period(fromEpochMillis: Long, toEpochMillis: Long): PeriodReport {
        require(fromEpochMillis <= toEpochMillis) { "तारीख की सीमा गलत है" }
        val sales = dao.observeInvoices("SALE").first().filter { it.dateEpochMillis in fromEpochMillis..toEpochMillis }
        val purchases = dao.observeInvoices("PURCHASE").first().filter { it.dateEpochMillis in fromEpochMillis..toEpochMillis }
        val payments = dao.observePayments().first().filter { it.dateEpochMillis in fromEpochMillis..toEpochMillis }
        val cashEntries = dao.observeCashEntries().first().filter { it.dateEpochMillis in fromEpochMillis..toEpochMillis }
        val salaryPayments = dao.observeSalaryPayments().first().filter { it.dateEpochMillis in fromEpochMillis..toEpochMillis }
        val balances = partyBalances()

        fun paymentMode(direction: String, mode: String) = payments
            .filter { it.direction == direction && it.mode == mode }.sumOf { it.amountPaise }
        fun expenseMode(mode: String) = cashEntries
            .filter { it.entryType == "EXPENSE" && it.mode == mode }.sumOf { it.amountPaise } +
            salaryPayments.filter { it.mode == mode }.sumOf { it.amountPaise }

        return PeriodReport(
            fromEpochMillis = fromEpochMillis,
            toEpochMillis = toEpochMillis,
            salePaise = sales.sumOf { it.totalPaise },
            purchasePaise = purchases.sumOf { it.totalPaise },
            paymentIn = MoneyByMode(paymentMode("IN", "CASH"), paymentMode("IN", "UPI"), paymentMode("IN", "BANK")),
            paymentOut = MoneyByMode(paymentMode("OUT", "CASH"), paymentMode("OUT", "UPI"), paymentMode("OUT", "BANK")),
            expenses = MoneyByMode(expenseMode("CASH"), expenseMode("UPI"), expenseMode("BANK")),
            debtorPaise = balances.debtorsPaise,
            creditorPaise = balances.creditorsPaise
        )
    }

    /**
     * Outstanding is invoice balance minus unallocated advances.
     * The dashboard totals and party-wise lists share this one calculation so
     * the figures cannot drift apart. Bill counts include only the relevant
     * invoice kind (SALE for receivables, PURCHASE for payables).
     */
    private suspend fun outstandingLists(): Pair<List<PartyOutstanding>, List<PartyOutstanding>> {
        val parties = dao.observeParties().first()
        val sales = dao.observeInvoices("SALE").first()
        val purchases = dao.observeInvoices("PURCHASE").first()
        val payments = dao.observePayments().first()

        fun build(receivable: Boolean): List<PartyOutstanding> {
            val direction = if (receivable) "IN" else "OUT"
            return parties.mapNotNull { party ->
                val relevantInvoices = (if (receivable) sales else purchases).filter { it.partyId == party.id }
                val invoiceDue = relevantInvoices.sumOf {
                    (it.totalPaise - it.paidPaise).coerceAtLeast(0L)
                }
                val unallocatedAdvance = payments
                    .filter { it.partyId == party.id && it.invoiceId == null && it.direction == direction }
                    .sumOf { it.amountPaise }
                val due = (invoiceDue - unallocatedAdvance).coerceAtLeast(0L)
                if (due > 0L) PartyOutstanding(party, due, relevantInvoices.size) else null
            }.sortedByDescending { it.outstandingPaise }
        }

        return build(true) to build(false)
    }

    suspend fun partyOutstanding(receivable: Boolean): List<PartyOutstanding> {
        val lists = outstandingLists()
        return if (receivable) lists.first else lists.second
    }

    suspend fun partyBalances(): PartyBalances {
        val (receivables, payables) = outstandingLists()
        return PartyBalances(
            debtorsPaise = receivables.sumOf { it.outstandingPaise },
            creditorsPaise = payables.sumOf { it.outstandingPaise }
        )
    }
}
