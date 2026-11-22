package `in`.bhajan.bfpbusinesspro.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index
import androidx.room.Embedded

@Entity(tableName = "parties")
data class PartyEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val mobile: String = "",
    val address: String = "",
    val gstNumber: String = "",
    val partyType: String, // CUSTOMER, SUPPLIER, BOTH
    val birthday: String = "" // Optional YYYY-MM-DD; blank when not provided
)

@Entity(tableName = "items", indices = [Index(value = ["itemCode"], unique = true)])
data class ItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val itemCode: String = "",
    val category: String = "",
    val purchaseRatePaise: Long = 0,
    val saleRatePaise: Long = 0,
    val quantity: Double = 0.0,
    val hsnCode: String = "",
    val gstRatePercent: Double = 0.0,
    val minimumStockQuantity: Double = 2.0
)

data class StockReportRow(
    @Embedded val item: ItemEntity,
    val openingQuantity: Double,
    val purchaseQuantity: Double,
    val saleQuantity: Double,
    val adjustmentQuantity: Double
)

data class StockPeriodReportRow(
    @Embedded val item: ItemEntity,
    val openingQuantity: Double,
    val purchaseQuantity: Double,
    val saleQuantity: Double,
    val adjustmentQuantity: Double,
    val closingQuantity: Double
)

data class GstRateReportRow(
    val kind: String,
    val gstRatePercent: Double,
    val invoiceCount: Int,
    val taxablePaise: Long,
    val cgstPaise: Long,
    val sgstPaise: Long,
    val igstPaise: Long
) { val totalTaxPaise: Long get() = cgstPaise + sgstPaise + igstPaise }

data class GstReconciliationSummaryRow(
    val salesTaxablePaise: Long,
    val salesCgstPaise: Long,
    val salesSgstPaise: Long,
    val salesIgstPaise: Long,
    val purchaseTaxablePaise: Long,
    val purchaseCgstPaise: Long,
    val purchaseSgstPaise: Long,
    val purchaseIgstPaise: Long
) {
    val outputTaxPaise: Long get() = salesCgstPaise + salesSgstPaise + salesIgstPaise
    val inputTaxPaise: Long get() = purchaseCgstPaise + purchaseSgstPaise + purchaseIgstPaise
    val estimatedNetTaxPaise: Long get() = outputTaxPaise - inputTaxPaise
}

data class SupplierItcReportRow(
    val supplierName: String,
    val gstNumber: String,
    val invoiceCount: Int,
    val taxablePaise: Long,
    val cgstPaise: Long,
    val sgstPaise: Long,
    val igstPaise: Long
) { val totalItcPaise: Long get() = cgstPaise + sgstPaise + igstPaise }

data class HsnGstReportRow(
    val kind: String,
    val hsnCode: String,
    val gstRatePercent: Double,
    val lineCount: Int,
    val quantity: Double,
    val taxablePaise: Long,
    val cgstPaise: Long,
    val sgstPaise: Long,
    val igstPaise: Long
) { val totalTaxPaise: Long get() = cgstPaise + sgstPaise + igstPaise }

data class Gstr1InvoiceRow(
    val invoiceNumber: String,
    val dateEpochMillis: Long,
    val customerName: String,
    val customerGstin: String,
    val taxablePaise: Long,
    val cgstPaise: Long,
    val sgstPaise: Long,
    val igstPaise: Long,
    val invoiceTotalPaise: Long
)

data class StockInsightRow(
    @Embedded val item: ItemEntity,
    val soldLast30Days: Double
)

data class ItemPerformanceRow(
    @Embedded val item: ItemEntity,
    val soldLast90Days: Double,
    val lastSaleEpochMillis: Long
)

data class ItemDemandForecastRow(
    @Embedded val item: ItemEntity,
    val soldRecent30Days: Double,
    val soldPrevious30Days: Double
)


@Entity(tableName = "stock_movements", indices = [Index(value = ["itemId", "dateEpochMillis"])])
data class StockMovementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: Long,
    val movementType: String, // OPENING, ADJUSTMENT, PURCHASE, SALE, PURCHASE_EDIT, SALE_EDIT, PURCHASE_REVERSAL, SALE_REVERSAL
    val quantityDelta: Double,
    val previousQuantity: Double,
    val newQuantity: Double,
    val reason: String = "",
    val dateEpochMillis: Long = System.currentTimeMillis(),
    val reference: String = ""
)

@Entity(tableName = "invoices")
data class InvoiceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val invoiceNumber: String,
    val partyId: Long,
    val kind: String, // SALE or PURCHASE
    val dateEpochMillis: Long,
    val subtotalPaise: Long,
    val discountPaise: Long = 0,
    val taxPaise: Long = 0,
    val cgstPaise: Long = 0,
    val sgstPaise: Long = 0,
    val igstPaise: Long = 0,
    val totalPaise: Long,
    val paidPaise: Long = 0,
    val note: String = "",
    val sellerStateCode: String = "02",
    val buyerStateCode: String = "02",
    val attachmentPath: String = ""
)

@Entity(tableName = "invoice_lines")
data class InvoiceLineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val invoiceId: Long,
    val itemId: Long,
    val itemNameSnapshot: String,
    val quantity: Double,
    val itemCodeSnapshot: String = "",
    val ratePaise: Long,
    val lineTotalPaise: Long,
    val gstRatePercent: Double = 0.0,
    val taxablePaise: Long = 0,
    val cgstPaise: Long = 0,
    val sgstPaise: Long = 0,
    val igstPaise: Long = 0,
    val hsnCode: String = ""
)

@Entity(tableName = "payments")
data class PaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val partyId: Long,
    val invoiceId: Long? = null,
    val direction: String, // IN or OUT
    val mode: String, // CASH, UPI, BANK
    val amountPaise: Long,
    val dateEpochMillis: Long,
    val note: String = "",
    val bankAccountId: Long? = null
)

@Entity(tableName = "cash_entries")
data class CashEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entryType: String, // OPENING, EXPENSE, CASH_DEPOSIT, CASH_WITHDRAWAL
    val mode: String, // CASH, UPI, BANK
    val amountPaise: Long,
    val dateEpochMillis: Long,
    val note: String = "",
    val bankAccountId: Long? = null
)


@Entity(tableName = "business_profile")
data class BusinessProfileEntity(
    @PrimaryKey val id: Int = 1,
    val businessName: String = "BHAJAN FASHION POINT",
    val address: String = "",
    val mobile: String = "",
    val gstin: String = "",
    val stateCode: String = "02",
    val invoicePrefix: String = "BFP"
)


@Entity(tableName = "bank_accounts")
data class BankAccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountName: String
)


@Entity(tableName = "employees")
data class EmployeeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val mobile: String = "",
    val monthlySalaryPaise: Long = 0,
    val joiningDateEpochMillis: Long = System.currentTimeMillis(),
    val active: Boolean = true,
    val note: String = ""
)

@Entity(tableName = "salary_months", indices = [Index(value = ["employeeId", "monthKey"], unique = true)])
data class SalaryMonthEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val employeeId: Long,
    val monthKey: String, // YYYY-MM
    val payablePaise: Long,
    val note: String = ""
)

@Entity(tableName = "salary_payments", indices = [Index(value = ["salaryMonthId"]), Index(value = ["bankAccountId"])])
data class SalaryPaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val salaryMonthId: Long,
    val employeeId: Long,
    val amountPaise: Long,
    val dateEpochMillis: Long,
    val mode: String, // CASH, UPI, BANK
    val bankAccountId: Long? = null,
    val note: String = ""
)


data class SupplierPurchaseSummaryRow(
    val partyId: Long,
    val supplierName: String,
    val gstNumber: String,
    val billCount: Int,
    val totalPurchasePaise: Long,
    val outstandingPaise: Long,
    val latestPurchaseEpochMillis: Long
)

data class SupplierItemRateChangeRow(
    val partyId: Long,
    val supplierName: String,
    val itemName: String,
    val itemCode: String,
    val latestRatePaise: Long,
    val previousRatePaise: Long,
    val latestPurchaseEpochMillis: Long
)


data class CustomerInsightRow(
    val partyId: Long,
    val customerName: String,
    val mobile: String,
    val birthday: String,
    val billCount: Int,
    val totalSalesPaise: Long,
    val outstandingPaise: Long,
    val lastSaleEpochMillis: Long
)

data class CustomerDesignPreferenceRow(
    val partyId: Long,
    val customerName: String,
    val itemName: String,
    val itemCode: String,
    val totalQuantity: Double,
    val totalAmountPaise: Long
)


data class CustomerPurchaseHistoryRow(
    val invoiceNumber: String,
    val dateEpochMillis: Long,
    val totalPaise: Long,
    val paidPaise: Long
)


data class BusinessDashboardRow(
    val salesPaise: Long,
    val purchasesPaise: Long,
    val receiptsPaise: Long,
    val expensesPaise: Long,
    val estimatedGrossProfitPaise: Long
)


data class MonthlyBusinessTrendRow(
    val monthKey: String,
    val salesPaise: Long,
    val purchasesPaise: Long,
    val expensesPaise: Long,
    val estimatedGrossProfitPaise: Long
)

data class MonthlyProfitabilityRow(
    val monthKey: String,
    val salesPaise: Long,
    val estimatedGrossProfitPaise: Long,
    val expensesPaise: Long,
    val salaryPaidPaise: Long,
    val estimatedOperatingSurplusPaise: Long
)
