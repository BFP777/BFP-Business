package `in`.bhajan.bfpbusinesspro.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [PartyEntity::class, ItemEntity::class, InvoiceEntity::class, InvoiceLineEntity::class, PaymentEntity::class, CashEntryEntity::class, BusinessProfileEntity::class, BankAccountEntity::class, EmployeeEntity::class, SalaryMonthEntity::class, SalaryPaymentEntity::class, StockMovementEntity::class],
    version = 11,
    exportSchema = true
)
abstract class BfpDatabase : RoomDatabase() {
    abstract fun dao(): AppDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE invoice_lines ADD COLUMN hsnCode TEXT NOT NULL DEFAULT ''")
            }
        }
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE invoices ADD COLUMN sellerStateCode TEXT NOT NULL DEFAULT '02'")
                database.execSQL("ALTER TABLE invoices ADD COLUMN buyerStateCode TEXT NOT NULL DEFAULT '02'")
            }
        }
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS bank_accounts (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, accountName TEXT NOT NULL)")
            }
        }
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE payments ADD COLUMN bankAccountId INTEGER")
                database.execSQL("ALTER TABLE cash_entries ADD COLUMN bankAccountId INTEGER")
            }
        }
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE items ADD COLUMN itemCode TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE invoice_lines ADD COLUMN itemCodeSnapshot TEXT NOT NULL DEFAULT ''")
                database.execSQL("UPDATE items SET itemCode = 'ITM-' || id WHERE trim(itemCode) = ''")
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_items_itemCode ON items(itemCode)")
                database.execSQL("ALTER TABLE invoices ADD COLUMN attachmentPath TEXT NOT NULL DEFAULT ''")
            }
        }
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS employees (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, mobile TEXT NOT NULL, monthlySalaryPaise INTEGER NOT NULL, joiningDateEpochMillis INTEGER NOT NULL, active INTEGER NOT NULL, note TEXT NOT NULL)")
                database.execSQL("CREATE TABLE IF NOT EXISTS salary_months (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, employeeId INTEGER NOT NULL, monthKey TEXT NOT NULL, payablePaise INTEGER NOT NULL, note TEXT NOT NULL)")
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_salary_months_employeeId_monthKey ON salary_months(employeeId, monthKey)")
                database.execSQL("CREATE TABLE IF NOT EXISTS salary_payments (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, salaryMonthId INTEGER NOT NULL, employeeId INTEGER NOT NULL, amountPaise INTEGER NOT NULL, dateEpochMillis INTEGER NOT NULL, mode TEXT NOT NULL, bankAccountId INTEGER, note TEXT NOT NULL)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_salary_payments_salaryMonthId ON salary_payments(salaryMonthId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_salary_payments_bankAccountId ON salary_payments(bankAccountId)")
            }
        }
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE items ADD COLUMN minimumStockQuantity REAL NOT NULL DEFAULT 2.0")
            }
        }
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE parties ADD COLUMN birthday TEXT NOT NULL DEFAULT ''")
            }
        }
        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS stock_movements (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, itemId INTEGER NOT NULL, movementType TEXT NOT NULL, quantityDelta REAL NOT NULL, previousQuantity REAL NOT NULL, newQuantity REAL NOT NULL, reason TEXT NOT NULL, dateEpochMillis INTEGER NOT NULL, reference TEXT NOT NULL)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_stock_movements_itemId_dateEpochMillis ON stock_movements(itemId, dateEpochMillis)")
                database.execSQL("INSERT INTO stock_movements (itemId, movementType, quantityDelta, previousQuantity, newQuantity, reason, dateEpochMillis, reference) SELECT id, 'OPENING', quantity, 0.0, quantity, 'मौजूदा स्टॉक — अपडेट के समय', CAST(strftime('%s','now') AS INTEGER) * 1000, '' FROM items WHERE quantity > 0")
            }
        }
        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE payments ADD COLUMN status TEXT NOT NULL DEFAULT 'PAID'")
            }
        }
        @Volatile private var INSTANCE: BfpDatabase? = null
        fun get(context: Context): BfpDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(context.applicationContext, BfpDatabase::class.java, "bfp_business_pro_5.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11)
                .build().also { INSTANCE = it }
        }

        @Synchronized
        fun closeForMaintenance() {
            INSTANCE?.close()
            INSTANCE = null
        }
    }
}
