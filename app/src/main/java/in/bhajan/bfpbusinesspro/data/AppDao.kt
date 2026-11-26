package `in`.bhajan.bfpbusinesspro.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Delete
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {
    @Query("SELECT " +
        "(SELECT COALESCE(SUM(totalPaise),0) FROM invoices WHERE kind = 'SALE' AND dateEpochMillis >= :startEpochMillis AND dateEpochMillis < :endEpochMillis) AS salesPaise, " +
        "(SELECT COALESCE(SUM(totalPaise),0) FROM invoices WHERE kind = 'PURCHASE' AND dateEpochMillis >= :startEpochMillis AND dateEpochMillis < :endEpochMillis) AS purchasesPaise, " +
        "(SELECT COALESCE(SUM(amountPaise),0) FROM payments WHERE direction = 'IN' AND dateEpochMillis >= :startEpochMillis AND dateEpochMillis < :endEpochMillis) AS receiptsPaise, " +
        "(SELECT COALESCE(SUM(amountPaise),0) FROM cash_entries WHERE entryType = 'EXPENSE' AND dateEpochMillis >= :startEpochMillis AND dateEpochMillis < :endEpochMillis) AS expensesPaise, " +
        "(SELECT COALESCE(SUM(il.quantity * (it.saleRatePaise - it.purchaseRatePaise)),0) FROM invoice_lines il INNER JOIN invoices inv ON inv.id = il.invoiceId INNER JOIN items it ON it.id = il.itemId WHERE inv.kind = 'SALE' AND inv.dateEpochMillis >= :startEpochMillis AND inv.dateEpochMillis < :endEpochMillis AND it.saleRatePaise > 0 AND it.purchaseRatePaise > 0) AS estimatedGrossProfitPaise")
    fun observeBusinessDashboard(startEpochMillis: Long, endEpochMillis: Long): Flow<BusinessDashboardRow>

    @Query("SELECT monthKey, SUM(salesPaise) AS salesPaise, SUM(purchasesPaise) AS purchasesPaise, SUM(expensesPaise) AS expensesPaise, SUM(estimatedGrossProfitPaise) AS estimatedGrossProfitPaise FROM (" +
        "SELECT strftime('%Y-%m', dateEpochMillis / 1000, 'unixepoch', 'localtime') AS monthKey, totalPaise AS salesPaise, 0 AS purchasesPaise, 0 AS expensesPaise, 0 AS estimatedGrossProfitPaise FROM invoices WHERE kind = 'SALE' AND dateEpochMillis >= :startEpochMillis AND dateEpochMillis < :endEpochMillis UNION ALL " +
        "SELECT strftime('%Y-%m', dateEpochMillis / 1000, 'unixepoch', 'localtime') AS monthKey, 0, totalPaise, 0, 0 FROM invoices WHERE kind = 'PURCHASE' AND dateEpochMillis >= :startEpochMillis AND dateEpochMillis < :endEpochMillis UNION ALL " +
        "SELECT strftime('%Y-%m', dateEpochMillis / 1000, 'unixepoch', 'localtime') AS monthKey, 0, 0, amountPaise, 0 FROM cash_entries WHERE entryType = 'EXPENSE' AND dateEpochMillis >= :startEpochMillis AND dateEpochMillis < :endEpochMillis UNION ALL " +
        "SELECT strftime('%Y-%m', inv.dateEpochMillis / 1000, 'unixepoch', 'localtime') AS monthKey, 0, 0, 0, COALESCE(SUM(il.quantity * (it.saleRatePaise - it.purchaseRatePaise)),0) FROM invoices inv INNER JOIN invoice_lines il ON il.invoiceId = inv.id INNER JOIN items it ON it.id = il.itemId WHERE inv.kind = 'SALE' AND inv.dateEpochMillis >= :startEpochMillis AND inv.dateEpochMillis < :endEpochMillis AND it.saleRatePaise > 0 AND it.purchaseRatePaise > 0 GROUP BY strftime('%Y-%m', inv.dateEpochMillis / 1000, 'unixepoch', 'localtime')" +
        ") GROUP BY monthKey ORDER BY monthKey")
    fun observeMonthlyBusinessTrends(startEpochMillis: Long, endEpochMillis: Long): Flow<List<MonthlyBusinessTrendRow>>
    @Query("SELECT monthKey, SUM(salesPaise) AS salesPaise, SUM(estimatedGrossProfitPaise) AS estimatedGrossProfitPaise, SUM(expensesPaise) AS expensesPaise, SUM(salaryPaidPaise) AS salaryPaidPaise, SUM(estimatedGrossProfitPaise - expensesPaise - salaryPaidPaise) AS estimatedOperatingSurplusPaise FROM (" +
        "SELECT strftime('%Y-%m', dateEpochMillis / 1000, 'unixepoch', 'localtime') AS monthKey, totalPaise AS salesPaise, 0 AS estimatedGrossProfitPaise, 0 AS expensesPaise, 0 AS salaryPaidPaise FROM invoices WHERE kind = 'SALE' AND dateEpochMillis >= :startEpochMillis AND dateEpochMillis < :endEpochMillis UNION ALL " +
        "SELECT strftime('%Y-%m', inv.dateEpochMillis / 1000, 'unixepoch', 'localtime') AS monthKey, 0, COALESCE(SUM(il.quantity * (it.saleRatePaise - it.purchaseRatePaise)),0), 0, 0 FROM invoices inv INNER JOIN invoice_lines il ON il.invoiceId = inv.id INNER JOIN items it ON it.id = il.itemId WHERE inv.kind = 'SALE' AND inv.dateEpochMillis >= :startEpochMillis AND inv.dateEpochMillis < :endEpochMillis AND it.saleRatePaise > 0 AND it.purchaseRatePaise > 0 GROUP BY strftime('%Y-%m', inv.dateEpochMillis / 1000, 'unixepoch', 'localtime') UNION ALL " +
        "SELECT strftime('%Y-%m', dateEpochMillis / 1000, 'unixepoch', 'localtime'), 0, 0, amountPaise, 0 FROM cash_entries WHERE entryType = 'EXPENSE' AND dateEpochMillis >= :startEpochMillis AND dateEpochMillis < :endEpochMillis UNION ALL " +
        "SELECT strftime('%Y-%m', dateEpochMillis / 1000, 'unixepoch', 'localtime'), 0, 0, 0, amountPaise FROM salary_payments WHERE dateEpochMillis >= :startEpochMillis AND dateEpochMillis < :endEpochMillis" +
        ") GROUP BY monthKey ORDER BY monthKey")
    fun observeMonthlyProfitability(startEpochMillis: Long, endEpochMillis: Long): Flow<List<MonthlyProfitabilityRow>>

    @Query("SELECT * FROM bank_accounts WHERE id = :id") suspend fun getBankAccount(id: Long): BankAccountEntity?
    @Insert suspend fun insertBankAccount(value: BankAccountEntity): Long
    @Update suspend fun updateBankAccount(value: BankAccountEntity)
    @Delete suspend fun deleteBankAccount(value: BankAccountEntity)
    @Query("SELECT * FROM bank_accounts ORDER BY accountName COLLATE NOCASE") fun observeBankAccounts(): Flow<List<BankAccountEntity>>
    @Query("SELECT COUNT(*) FROM bank_accounts WHERE accountName = :name COLLATE NOCASE AND id != :excludeId") suspend fun countBankAccountName(name: String, excludeId: Long): Int
    @Query("SELECT (SELECT COUNT(*) FROM payments WHERE bankAccountId = :id) + (SELECT COUNT(*) FROM cash_entries WHERE bankAccountId = :id) + (SELECT COUNT(*) FROM salary_payments WHERE bankAccountId = :id)") suspend fun countBankAccountUsage(id: Long): Int

    @Insert suspend fun insertParty(value: PartyEntity): Long
    @Update suspend fun updateParty(value: PartyEntity)
    @Delete suspend fun deleteParty(value: PartyEntity)
    @Query("SELECT * FROM parties ORDER BY name COLLATE NOCASE") fun observeParties(): Flow<List<PartyEntity>>
    @Query("SELECT COUNT(*) FROM parties") suspend fun countParties(): Int
    @Query("SELECT * FROM parties WHERE id = :partyId LIMIT 1") suspend fun getParty(partyId: Long): PartyEntity?
    @Query("SELECT COUNT(*) FROM invoices WHERE partyId = :partyId") suspend fun countPartyInvoices(partyId: Long): Int
    @Query("SELECT COUNT(*) FROM payments WHERE partyId = :partyId") suspend fun countPartyPayments(partyId: Long): Int
    @Query("SELECT p.id AS partyId, p.name AS customerName, p.mobile AS mobile, p.birthday AS birthday, COUNT(i.id) AS billCount, COALESCE(SUM(i.totalPaise),0) AS totalSalesPaise, COALESCE(SUM(i.totalPaise - i.paidPaise),0) AS outstandingPaise, COALESCE(MAX(i.dateEpochMillis),0) AS lastSaleEpochMillis FROM parties p INNER JOIN invoices i ON i.partyId = p.id AND i.kind = 'SALE' WHERE p.partyType IN ('CUSTOMER', 'BOTH') GROUP BY p.id, p.name, p.mobile, p.birthday ORDER BY totalSalesPaise DESC, p.name COLLATE NOCASE")
    fun observeCustomerInsights(): Flow<List<CustomerInsightRow>>
    @Query("SELECT * FROM parties WHERE partyType IN ('CUSTOMER', 'BOTH') AND trim(birthday) != '' ORDER BY name COLLATE NOCASE")
    fun observeCustomerBirthdays(): Flow<List<PartyEntity>>
    @Query("SELECT invoiceNumber, dateEpochMillis, totalPaise, paidPaise FROM invoices WHERE partyId = :partyId AND kind = 'SALE' ORDER BY dateEpochMillis DESC, id DESC LIMIT 10")
    suspend fun getCustomerPurchaseHistory(partyId: Long): List<CustomerPurchaseHistoryRow>
    @Query("SELECT p.id AS partyId, p.name AS customerName, il.itemNameSnapshot AS itemName, il.itemCodeSnapshot AS itemCode, SUM(il.quantity) AS totalQuantity, SUM(il.lineTotalPaise) AS totalAmountPaise FROM parties p INNER JOIN invoices i ON i.partyId = p.id AND i.kind = 'SALE' INNER JOIN invoice_lines il ON il.invoiceId = i.id WHERE p.id = :partyId GROUP BY p.id, p.name, il.itemNameSnapshot, il.itemCodeSnapshot ORDER BY totalQuantity DESC, totalAmountPaise DESC LIMIT 10")
    suspend fun getCustomerDesignPreferences(partyId: Long): List<CustomerDesignPreferenceRow>
    @Query("SELECT p.id AS partyId, p.name AS customerName, il.itemNameSnapshot AS itemName, il.itemCodeSnapshot AS itemCode, SUM(il.quantity) AS totalQuantity, SUM(il.lineTotalPaise) AS totalAmountPaise FROM parties p INNER JOIN invoices i ON i.partyId = p.id AND i.kind = 'SALE' INNER JOIN invoice_lines il ON il.invoiceId = i.id WHERE p.partyType IN ('CUSTOMER', 'BOTH') GROUP BY p.id, p.name, il.itemNameSnapshot, il.itemCodeSnapshot ORDER BY totalQuantity DESC, totalAmountPaise DESC LIMIT 20")
    fun observeTopCustomerDesigns(): Flow<List<CustomerDesignPreferenceRow>>

    @Insert suspend fun insertItem(value: ItemEntity): Long
    @Update suspend fun updateItem(value: ItemEntity)
    @Delete suspend fun deleteItem(value: ItemEntity)
    @Query("SELECT * FROM items ORDER BY name COLLATE NOCASE") fun observeItems(): Flow<List<ItemEntity>>
    @Query("SELECT * FROM items WHERE id = :itemId") suspend fun getItem(itemId: Long): ItemEntity?
    @Query("SELECT * FROM items WHERE itemCode = :code COLLATE NOCASE LIMIT 1") suspend fun getItemByCode(code: String): ItemEntity?
    @Query("SELECT * FROM items WHERE itemCode = :code COLLATE NOCASE AND id != :excludeId LIMIT 1") suspend fun findItemCodeConflict(code: String, excludeId: Long): ItemEntity?
    @Query("SELECT COUNT(*) FROM items") suspend fun countItems(): Int
    @Query("SELECT items.*, " +
        "COALESCE((SELECT SUM(sm.quantityDelta) FROM stock_movements sm WHERE sm.itemId = items.id AND sm.movementType = 'OPENING'),0) AS openingQuantity, " +
        "COALESCE((SELECT SUM(sm.quantityDelta) FROM stock_movements sm WHERE sm.itemId = items.id AND sm.movementType LIKE 'PURCHASE%'),0) AS purchaseQuantity, " +
        "COALESCE((SELECT SUM(sm.quantityDelta) FROM stock_movements sm WHERE sm.itemId = items.id AND sm.movementType LIKE 'SALE%'),0) AS saleQuantity, " +
        "COALESCE((SELECT SUM(sm.quantityDelta) FROM stock_movements sm WHERE sm.itemId = items.id AND sm.movementType = 'ADJUSTMENT'),0) AS adjustmentQuantity " +
        "FROM items ORDER BY items.name COLLATE NOCASE")
    fun observeStockReport(): Flow<List<StockReportRow>>
    @Query("SELECT items.*, " +
        "COALESCE((SELECT SUM(sm.quantityDelta) FROM stock_movements sm WHERE sm.itemId = items.id AND sm.dateEpochMillis < :startEpochMillis),0) AS openingQuantity, " +
        "COALESCE((SELECT SUM(sm.quantityDelta) FROM stock_movements sm WHERE sm.itemId = items.id AND sm.dateEpochMillis >= :startEpochMillis AND sm.dateEpochMillis < :endEpochMillis AND sm.movementType LIKE 'PURCHASE%'),0) AS purchaseQuantity, " +
        "COALESCE((SELECT SUM(sm.quantityDelta) FROM stock_movements sm WHERE sm.itemId = items.id AND sm.dateEpochMillis >= :startEpochMillis AND sm.dateEpochMillis < :endEpochMillis AND sm.movementType LIKE 'SALE%'),0) AS saleQuantity, " +
        "COALESCE((SELECT SUM(sm.quantityDelta) FROM stock_movements sm WHERE sm.itemId = items.id AND sm.dateEpochMillis >= :startEpochMillis AND sm.dateEpochMillis < :endEpochMillis AND sm.movementType = 'ADJUSTMENT'),0) AS adjustmentQuantity, " +
        "COALESCE((SELECT SUM(sm.quantityDelta) FROM stock_movements sm WHERE sm.itemId = items.id AND sm.dateEpochMillis < :endEpochMillis),0) AS closingQuantity " +
        "FROM items ORDER BY items.name COLLATE NOCASE")
    suspend fun getStockPeriodReport(startEpochMillis: Long, endEpochMillis: Long): List<StockPeriodReportRow>
    @Insert suspend fun insertStockMovement(value: StockMovementEntity): Long
    @Query("SELECT * FROM stock_movements WHERE itemId = :itemId ORDER BY dateEpochMillis DESC, id DESC") suspend fun getStockMovements(itemId: Long): List<StockMovementEntity>
    @Query("SELECT * FROM stock_movements WHERE itemId = :itemId ORDER BY dateEpochMillis DESC, id DESC") fun observeStockMovements(itemId: Long): Flow<List<StockMovementEntity>>
    @Query("SELECT COUNT(*) FROM stock_movements WHERE itemId = :itemId AND movementType = 'OPENING'") suspend fun countOpeningStockMovements(itemId: Long): Int
    @Query("SELECT items.*, COALESCE((SELECT SUM(il.quantity) FROM invoice_lines il INNER JOIN invoices inv ON inv.id = il.invoiceId WHERE il.itemId = items.id AND inv.kind = 'SALE' AND inv.dateEpochMillis >= :sinceEpochMillis), 0) AS soldLast30Days FROM items ORDER BY items.name COLLATE NOCASE")
    fun observeStockInsights(sinceEpochMillis: Long): Flow<List<StockInsightRow>>
    @Query("SELECT items.*, COALESCE((SELECT SUM(il.quantity) FROM invoice_lines il INNER JOIN invoices inv ON inv.id = il.invoiceId WHERE il.itemId = items.id AND inv.kind = 'SALE' AND inv.dateEpochMillis >= :sinceEpochMillis), 0) AS soldLast90Days, COALESCE((SELECT MAX(inv.dateEpochMillis) FROM invoice_lines il INNER JOIN invoices inv ON inv.id = il.invoiceId WHERE il.itemId = items.id AND inv.kind = 'SALE'), 0) AS lastSaleEpochMillis FROM items ORDER BY items.name COLLATE NOCASE")
    fun observeItemPerformance(sinceEpochMillis: Long): Flow<List<ItemPerformanceRow>>
    @Query("SELECT items.*, COALESCE((SELECT SUM(il.quantity) FROM invoice_lines il INNER JOIN invoices inv ON inv.id = il.invoiceId WHERE il.itemId = items.id AND inv.kind = 'SALE' AND inv.dateEpochMillis >= :recentStartEpochMillis AND inv.dateEpochMillis < :periodEndEpochMillis), 0) AS soldRecent30Days, COALESCE((SELECT SUM(il.quantity) FROM invoice_lines il INNER JOIN invoices inv ON inv.id = il.invoiceId WHERE il.itemId = items.id AND inv.kind = 'SALE' AND inv.dateEpochMillis >= :previousStartEpochMillis AND inv.dateEpochMillis < :recentStartEpochMillis), 0) AS soldPrevious30Days FROM items ORDER BY items.name COLLATE NOCASE")
    fun observeItemDemandForecast(previousStartEpochMillis: Long, recentStartEpochMillis: Long, periodEndEpochMillis: Long): Flow<List<ItemDemandForecastRow>>
    @Query("SELECT COUNT(*) FROM invoice_lines WHERE itemId = :itemId") suspend fun countItemInvoiceLines(itemId: Long): Int

    @Insert suspend fun insertInvoice(value: InvoiceEntity): Long
    @Update suspend fun updateInvoice(value: InvoiceEntity)
    @Query("SELECT * FROM invoices WHERE id = :id") suspend fun getInvoice(id: Long): InvoiceEntity?
    @Query("SELECT COUNT(*) FROM invoices WHERE kind = :kind AND trim(invoiceNumber) = trim(:normalizedNumber) COLLATE NOCASE AND id != :excludeId") suspend fun countInvoiceNumberConflicts(kind: String, normalizedNumber: String, excludeId: Long): Int
    @Query("SELECT COUNT(*) FROM invoices i INNER JOIN parties p ON p.id = i.partyId WHERE i.kind = 'PURCHASE' AND trim(i.invoiceNumber) = trim(:normalizedNumber) COLLATE NOCASE AND i.id != :excludeId AND (i.partyId = :partyId OR (:supplierGstin != '' AND upper(replace(trim(p.gstNumber), ' ', '')) = upper(replace(trim(:supplierGstin), ' ', ''))))") suspend fun countDuplicateSupplierBills(partyId: Long, supplierGstin: String, normalizedNumber: String, excludeId: Long): Int
    @Delete suspend fun deleteInvoice(value: InvoiceEntity)
    @Query("SELECT p.id AS partyId, p.name AS supplierName, p.gstNumber AS gstNumber, COUNT(i.id) AS billCount, COALESCE(SUM(i.totalPaise),0) AS totalPurchasePaise, COALESCE(SUM(i.totalPaise - i.paidPaise),0) AS outstandingPaise, COALESCE(MAX(i.dateEpochMillis),0) AS latestPurchaseEpochMillis FROM parties p INNER JOIN invoices i ON i.partyId = p.id AND i.kind = 'PURCHASE' WHERE p.partyType IN ('SUPPLIER', 'BOTH') GROUP BY p.id, p.name, p.gstNumber ORDER BY totalPurchasePaise DESC, p.name COLLATE NOCASE")
    fun observeSupplierPurchaseSummaries(): Flow<List<SupplierPurchaseSummaryRow>>
@Query("SELECT i.partyId AS partyId, p.name AS supplierName, il.itemNameSnapshot AS itemName, il.itemCodeSnapshot AS itemCode, il.ratePaise AS latestRatePaise, COALESCE((SELECT prior.ratePaise FROM invoice_lines prior INNER JOIN invoices priorInvoice ON priorInvoice.id = prior.invoiceId WHERE priorInvoice.partyId = i.partyId AND prior.itemId = il.itemId AND priorInvoice.kind = 'PURCHASE' AND (priorInvoice.dateEpochMillis < i.dateEpochMillis OR (priorInvoice.dateEpochMillis = i.dateEpochMillis AND priorInvoice.id < i.id) OR (priorInvoice.dateEpochMillis = i.dateEpochMillis AND priorInvoice.id = i.id AND prior.id < il.id)) ORDER BY priorInvoice.dateEpochMillis DESC, priorInvoice.id DESC, prior.id DESC LIMIT 1), 0) AS previousRatePaise, i.dateEpochMillis AS latestPurchaseEpochMillis FROM invoices i INNER JOIN parties p ON p.id = i.partyId INNER JOIN invoice_lines il ON il.invoiceId = i.id WHERE i.kind = 'PURCHASE' AND il.itemId > 0 AND NOT EXISTS (SELECT 1 FROM invoice_lines newer INNER JOIN invoices newerInvoice ON newerInvoice.id = newer.invoiceId WHERE newerInvoice.partyId = i.partyId AND newer.itemId = il.itemId AND newerInvoice.kind = 'PURCHASE' AND (newerInvoice.dateEpochMillis > i.dateEpochMillis OR (newerInvoice.dateEpochMillis = i.dateEpochMillis AND newerInvoice.id > i.id) OR (newerInvoice.dateEpochMillis = i.dateEpochMillis AND newerInvoice.id = i.id AND newer.id > il.id))) AND EXISTS (SELECT 1 FROM invoice_lines prior INNER JOIN invoices priorInvoice ON priorInvoice.id = prior.invoiceId WHERE priorInvoice.partyId = i.partyId AND prior.itemId = il.itemId AND priorInvoice.kind = 'PURCHASE' AND (priorInvoice.dateEpochMillis < i.dateEpochMillis OR (priorInvoice.dateEpochMillis = i.dateEpochMillis AND priorInvoice.id < i.id) OR (priorInvoice.dateEpochMillis = i.dateEpochMillis AND priorInvoice.id = i.id AND prior.id < il.id))) ORDER BY i.dateEpochMillis DESC, i.id DESC, il.id DESC")
     fun observeSupplierItemRateChanges(): Flow<List<SupplierItemRateChangeRow>>
    @Query("SELECT " +
        "COALESCE((SELECT SUM(il.taxablePaise) FROM invoices i INNER JOIN invoice_lines il ON il.invoiceId=i.id WHERE i.kind='SALE' AND i.dateEpochMillis >= :startEpochMillis AND i.dateEpochMillis < :endEpochMillis),0) AS salesTaxablePaise, " +
        "COALESCE((SELECT SUM(il.cgstPaise) FROM invoices i INNER JOIN invoice_lines il ON il.invoiceId=i.id WHERE i.kind='SALE' AND i.dateEpochMillis >= :startEpochMillis AND i.dateEpochMillis < :endEpochMillis),0) AS salesCgstPaise, " +
        "COALESCE((SELECT SUM(il.sgstPaise) FROM invoices i INNER JOIN invoice_lines il ON il.invoiceId=i.id WHERE i.kind='SALE' AND i.dateEpochMillis >= :startEpochMillis AND i.dateEpochMillis < :endEpochMillis),0) AS salesSgstPaise, " +
        "COALESCE((SELECT SUM(il.igstPaise) FROM invoices i INNER JOIN invoice_lines il ON il.invoiceId=i.id WHERE i.kind='SALE' AND i.dateEpochMillis >= :startEpochMillis AND i.dateEpochMillis < :endEpochMillis),0) AS salesIgstPaise, " +
        "COALESCE((SELECT SUM(il.taxablePaise) FROM invoices i INNER JOIN invoice_lines il ON il.invoiceId=i.id WHERE i.kind='PURCHASE' AND i.dateEpochMillis >= :startEpochMillis AND i.dateEpochMillis < :endEpochMillis),0) AS purchaseTaxablePaise, " +
        "COALESCE((SELECT SUM(il.cgstPaise) FROM invoices i INNER JOIN invoice_lines il ON il.invoiceId=i.id WHERE i.kind='PURCHASE' AND i.dateEpochMillis >= :startEpochMillis AND i.dateEpochMillis < :endEpochMillis),0) AS purchaseCgstPaise, " +
        "COALESCE((SELECT SUM(il.sgstPaise) FROM invoices i INNER JOIN invoice_lines il ON il.invoiceId=i.id WHERE i.kind='PURCHASE' AND i.dateEpochMillis >= :startEpochMillis AND i.dateEpochMillis < :endEpochMillis),0) AS purchaseSgstPaise, " +
        "COALESCE((SELECT SUM(il.igstPaise) FROM invoices i INNER JOIN invoice_lines il ON il.invoiceId=i.id WHERE i.kind='PURCHASE' AND i.dateEpochMillis >= :startEpochMillis AND i.dateEpochMillis < :endEpochMillis),0) AS purchaseIgstPaise")
    suspend fun getGstReconciliationSummary(startEpochMillis: Long, endEpochMillis: Long): GstReconciliationSummaryRow

    @Query("SELECT p.name AS supplierName, p.gstNumber AS gstNumber, COUNT(DISTINCT i.id) AS invoiceCount, COALESCE(SUM(il.taxablePaise),0) AS taxablePaise, COALESCE(SUM(il.cgstPaise),0) AS cgstPaise, COALESCE(SUM(il.sgstPaise),0) AS sgstPaise, COALESCE(SUM(il.igstPaise),0) AS igstPaise FROM invoices i INNER JOIN parties p ON p.id=i.partyId INNER JOIN invoice_lines il ON il.invoiceId=i.id WHERE i.kind='PURCHASE' AND i.dateEpochMillis >= :startEpochMillis AND i.dateEpochMillis < :endEpochMillis GROUP BY p.id,p.name,p.gstNumber ORDER BY p.name COLLATE NOCASE")
    suspend fun getSupplierItcReport(startEpochMillis: Long, endEpochMillis: Long): List<SupplierItcReportRow>

    @Query("SELECT i.kind AS kind, il.gstRatePercent AS gstRatePercent, COUNT(DISTINCT i.id) AS invoiceCount, COALESCE(SUM(il.taxablePaise),0) AS taxablePaise, COALESCE(SUM(il.cgstPaise),0) AS cgstPaise, COALESCE(SUM(il.sgstPaise),0) AS sgstPaise, COALESCE(SUM(il.igstPaise),0) AS igstPaise FROM invoices i INNER JOIN invoice_lines il ON il.invoiceId = i.id WHERE i.kind IN ('SALE','PURCHASE') AND i.dateEpochMillis >= :startEpochMillis AND i.dateEpochMillis < :endEpochMillis GROUP BY i.kind, il.gstRatePercent ORDER BY i.kind, il.gstRatePercent")
    suspend fun getGstRateReport(startEpochMillis: Long, endEpochMillis: Long): List<GstRateReportRow>
    @Query("SELECT i.kind AS kind, COALESCE(NULLIF(TRIM(il.hsnCode),''),'UNSPECIFIED') AS hsnCode, il.gstRatePercent AS gstRatePercent, COUNT(il.id) AS lineCount, COALESCE(SUM(il.quantity),0) AS quantity, COALESCE(SUM(il.taxablePaise),0) AS taxablePaise, COALESCE(SUM(il.cgstPaise),0) AS cgstPaise, COALESCE(SUM(il.sgstPaise),0) AS sgstPaise, COALESCE(SUM(il.igstPaise),0) AS igstPaise FROM invoices i INNER JOIN invoice_lines il ON il.invoiceId = i.id WHERE i.kind IN ('SALE','PURCHASE') AND i.dateEpochMillis >= :startEpochMillis AND i.dateEpochMillis < :endEpochMillis GROUP BY i.kind, COALESCE(NULLIF(TRIM(il.hsnCode),''),'UNSPECIFIED'), il.gstRatePercent ORDER BY i.kind, hsnCode, il.gstRatePercent")
    suspend fun getHsnGstReport(startEpochMillis: Long, endEpochMillis: Long): List<HsnGstReportRow>
    @Query("SELECT i.invoiceNumber AS invoiceNumber, i.dateEpochMillis AS dateEpochMillis, p.name AS customerName, p.gstNumber AS customerGstin, COALESCE(SUM(il.taxablePaise),0) AS taxablePaise, COALESCE(SUM(il.cgstPaise),0) AS cgstPaise, COALESCE(SUM(il.sgstPaise),0) AS sgstPaise, COALESCE(SUM(il.igstPaise),0) AS igstPaise, i.totalPaise AS invoiceTotalPaise FROM invoices i INNER JOIN parties p ON p.id = i.partyId INNER JOIN invoice_lines il ON il.invoiceId = i.id WHERE i.kind = 'SALE' AND i.dateEpochMillis >= :startEpochMillis AND i.dateEpochMillis < :endEpochMillis GROUP BY i.id, i.invoiceNumber, i.dateEpochMillis, p.name, p.gstNumber, i.totalPaise ORDER BY i.dateEpochMillis, i.invoiceNumber")
    suspend fun getGstr1InvoiceRows(startEpochMillis: Long, endEpochMillis: Long): List<Gstr1InvoiceRow>
    @Query("SELECT * FROM invoices WHERE kind = :kind ORDER BY dateEpochMillis DESC, id DESC") fun observeInvoices(kind: String): Flow<List<InvoiceEntity>>
    @Query("SELECT * FROM invoices WHERE id = :id") suspend fun getInvoiceById(id: Long): InvoiceEntity?
    @Insert suspend fun insertInvoiceLines(values: List<InvoiceLineEntity>)
    @Query("SELECT * FROM invoice_lines WHERE invoiceId = :invoiceId") suspend fun getInvoiceLines(invoiceId: Long): List<InvoiceLineEntity>
    @Query("DELETE FROM invoice_lines WHERE invoiceId = :invoiceId") suspend fun deleteInvoiceLines(invoiceId: Long)

    @Insert suspend fun insertPayment(value: PaymentEntity): Long
    @Update suspend fun updatePayment(value: PaymentEntity)
    @Delete suspend fun deletePayment(value: PaymentEntity)
    @Query("SELECT * FROM payments WHERE id = :id") suspend fun getPayment(id: Long): PaymentEntity?
    @Query("SELECT * FROM payments WHERE partyId = :partyId ORDER BY dateEpochMillis DESC, id DESC") suspend fun getPartyPayments(partyId: Long): List<PaymentEntity>
    @Query("SELECT COALESCE(SUM(amountPaise),0) FROM payments WHERE invoiceId = :invoiceId") suspend fun sumInvoicePayments(invoiceId: Long): Long
    @Query("SELECT * FROM payments ORDER BY dateEpochMillis DESC, id DESC") fun observePayments(): Flow<List<PaymentEntity>>
    @Query("SELECT COUNT(*) FROM payments WHERE invoiceId = :invoiceId") suspend fun countPaymentsForInvoice(invoiceId: Long): Int
    @Query("DELETE FROM payments WHERE invoiceId = :invoiceId") suspend fun deleteInvoicePayments(invoiceId: Long): Int
    @Insert suspend fun insertCashEntry(value: CashEntryEntity): Long
    @Update suspend fun updateCashEntry(value: CashEntryEntity)
    @Delete suspend fun deleteCashEntry(value: CashEntryEntity)
    @Query("SELECT * FROM cash_entries WHERE id = :id") suspend fun getCashEntry(id: Long): CashEntryEntity?
    @Query("SELECT * FROM cash_entries ORDER BY dateEpochMillis DESC, id DESC") fun observeCashEntries(): Flow<List<CashEntryEntity>>


    @Insert suspend fun insertEmployee(value: EmployeeEntity): Long
    @Update suspend fun updateEmployee(value: EmployeeEntity)
    @Query("SELECT * FROM employees WHERE id = :id") suspend fun getEmployee(id: Long): EmployeeEntity?
    @Query("SELECT * FROM employees ORDER BY active DESC, name COLLATE NOCASE") fun observeEmployees(): Flow<List<EmployeeEntity>>
    @Query("SELECT COUNT(*) FROM salary_months WHERE employeeId = :employeeId") suspend fun countEmployeeSalaryMonths(employeeId: Long): Int

    @Insert suspend fun insertSalaryMonth(value: SalaryMonthEntity): Long
    @Update suspend fun updateSalaryMonth(value: SalaryMonthEntity)
    @Query("SELECT * FROM salary_months WHERE id = :id") suspend fun getSalaryMonth(id: Long): SalaryMonthEntity?
    @Query("SELECT * FROM salary_months WHERE employeeId = :employeeId AND monthKey = :monthKey LIMIT 1") suspend fun getSalaryMonth(employeeId: Long, monthKey: String): SalaryMonthEntity?
    @Query("SELECT * FROM salary_months WHERE employeeId = :employeeId ORDER BY monthKey DESC") fun observeSalaryMonths(employeeId: Long): Flow<List<SalaryMonthEntity>>
    @Query("SELECT * FROM salary_months ORDER BY monthKey DESC, id DESC") fun observeAllSalaryMonths(): Flow<List<SalaryMonthEntity>>

    @Insert suspend fun insertSalaryPayment(value: SalaryPaymentEntity): Long
    @Update suspend fun updateSalaryPayment(value: SalaryPaymentEntity)
    @Delete suspend fun deleteSalaryPayment(value: SalaryPaymentEntity)
    @Query("SELECT * FROM salary_payments WHERE id = :id") suspend fun getSalaryPayment(id: Long): SalaryPaymentEntity?
    @Query("SELECT * FROM salary_payments WHERE salaryMonthId = :salaryMonthId ORDER BY dateEpochMillis DESC, id DESC") suspend fun getSalaryPayments(salaryMonthId: Long): List<SalaryPaymentEntity>
    @Query("SELECT * FROM salary_payments WHERE employeeId = :employeeId ORDER BY dateEpochMillis DESC, id DESC") suspend fun getEmployeeSalaryPayments(employeeId: Long): List<SalaryPaymentEntity>
    @Query("SELECT * FROM salary_payments ORDER BY dateEpochMillis DESC, id DESC") fun observeSalaryPayments(): Flow<List<SalaryPaymentEntity>>
    @Query("SELECT COALESCE(SUM(amountPaise),0) FROM salary_payments WHERE salaryMonthId = :salaryMonthId") suspend fun sumSalaryPayments(salaryMonthId: Long): Long

    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE) suspend fun saveBusinessProfile(value: BusinessProfileEntity)
    @Query("SELECT * FROM business_profile WHERE id = 1") suspend fun getBusinessProfile(): BusinessProfileEntity?

    @Query("SELECT COALESCE(SUM(CASE WHEN kind='SALE' THEN totalPaise-paidPaise ELSE 0 END),0) FROM invoices") suspend fun totalDebtorsPaise(): Long
    @Query("SELECT COALESCE(SUM(CASE WHEN kind='PURCHASE' THEN totalPaise-paidPaise ELSE 0 END),0) FROM invoices") suspend fun totalCreditorsPaise(): Long
}
