package `in`.bhajan.bfpbusinesspro.data

import androidx.room.withTransaction

/** Employee and monthly payroll ledger. Every payout is a real cash/UPI/bank outflow. */
class EmployeePayrollRepository(private val database: BfpDatabase) {
    private val dao = database.dao()

    data class PaymentInput(
        val salaryMonthId: Long,
        val employeeId: Long,
        val amountPaise: Long,
        val dateEpochMillis: Long = System.currentTimeMillis(),
        val mode: String,
        val bankAccountId: Long? = null,
        val note: String = ""
    )

    suspend fun saveEmployee(employee: EmployeeEntity): Long = database.withTransaction {
        val name = employee.name.trim()
        require(name.isNotBlank()) { "कर्मचारी का नाम जरूरी है" }
        require(employee.monthlySalaryPaise >= 0) { "मासिक वेतन शून्य या उससे अधिक होना चाहिए" }
        require(employee.joiningDateEpochMillis > 0) { "Joining तारीख सही नहीं है" }
        if (employee.id == 0L) dao.insertEmployee(employee.copy(name = name))
        else {
            require(dao.getEmployee(employee.id) != null) { "कर्मचारी नहीं मिला" }
            dao.updateEmployee(employee.copy(name = name))
            employee.id
        }
    }

    suspend fun saveSalaryMonth(employeeId: Long, monthKey: String, payablePaise: Long, note: String = ""): Long = database.withTransaction {
        require(monthKey.matches(Regex("\\d{4}-(0[1-9]|1[0-2])"))) { "महीना YYYY-MM फॉर्मेट में चुनें" }
        require(payablePaise >= 0) { "देय वेतन शून्य या उससे अधिक होना चाहिए" }
        require(dao.getEmployee(employeeId) != null) { "कर्मचारी नहीं मिला" }
        val existing = dao.getSalaryMonth(employeeId, monthKey)
        if (existing == null) dao.insertSalaryMonth(SalaryMonthEntity(employeeId = employeeId, monthKey = monthKey, payablePaise = payablePaise, note = note.trim()))
        else {
            val paid = dao.sumSalaryPayments(existing.id)
            require(paid <= payablePaise) { "देय वेतन पहले से दिए गए वेतन से कम नहीं हो सकता" }
            dao.updateSalaryMonth(existing.copy(payablePaise = payablePaise, note = note.trim()))
            existing.id
        }
    }

    suspend fun recordPayment(input: PaymentInput): Long = database.withTransaction {
        validatePayment(input)
        val id = dao.insertSalaryPayment(input.toEntity())
        id
    }

    suspend fun updatePayment(paymentId: Long, input: PaymentInput) = database.withTransaction {
        val old = dao.getSalaryPayment(paymentId) ?: error("वेतन भुगतान नहीं मिला")
        validatePayment(input, paymentId)
        dao.updateSalaryPayment(old.copy(
            salaryMonthId = input.salaryMonthId,
            employeeId = input.employeeId,
            amountPaise = input.amountPaise,
            dateEpochMillis = input.dateEpochMillis,
            mode = input.mode,
            bankAccountId = input.bankAccountId,
            note = input.note.trim()
        ))
    }

    suspend fun deletePayment(paymentId: Long) = database.withTransaction {
        val old = dao.getSalaryPayment(paymentId) ?: return@withTransaction
        dao.deleteSalaryPayment(old)
    }

    private suspend fun validatePayment(input: PaymentInput, editingId: Long? = null) {
        require(input.amountPaise > 0) { "वेतन भुगतान ₹0.01 से अधिक होना चाहिए" }
        require(input.dateEpochMillis > 0) { "भुगतान तारीख सही नहीं है" }
        require(input.mode in setOf("CASH", "UPI", "BANK")) { "भुगतान माध्यम चुनें" }
        val employee = dao.getEmployee(input.employeeId) ?: error("कर्मचारी नहीं मिला")
        val month = dao.getSalaryMonth(input.salaryMonthId) ?: error("वेतन महीना नहीं मिला")
        require(month.employeeId == employee.id) { "वेतन महीना और कर्मचारी मेल नहीं खाते" }
        if (input.mode == "BANK") {
            require(input.bankAccountId != null && dao.getBankAccount(input.bankAccountId) != null) { "बैंक अकाउंट चुनें" }
        } else require(input.bankAccountId == null) { "बैंक अकाउंट केवल BANK माध्यम में चुनें" }
        val old = editingId?.let { dao.getSalaryPayment(it) }
        val oldAmount = if (old?.salaryMonthId == month.id) old.amountPaise else 0L
        val alreadyPaid = dao.sumSalaryPayments(month.id) - oldAmount
        require(alreadyPaid + input.amountPaise <= month.payablePaise) { "भुगतान इस महीने के बाकी वेतन से अधिक है" }
    }

    private fun PaymentInput.toEntity() = SalaryPaymentEntity(
        salaryMonthId = salaryMonthId,
        employeeId = employeeId,
        amountPaise = amountPaise,
        dateEpochMillis = dateEpochMillis,
        mode = mode,
        bankAccountId = bankAccountId,
        note = note.trim()
    )
}
