package `in`.bhajan.bfpbusinesspro

import `in`.bhajan.bfpbusinesspro.data.GstRateReportRow

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.RecognitionListener
import android.os.Handler
import android.os.Looper
import android.content.pm.PackageManager
import android.Manifest
import java.io.File
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import android.print.PrintAttributes
import android.print.PrintManager
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.work.WorkManager
import `in`.bhajan.bfpbusinesspro.backup.AutoBackupScheduler
import `in`.bhajan.bfpbusinesspro.backup.DatabaseBackup
import `in`.bhajan.bfpbusinesspro.backup.BackupHistory
import `in`.bhajan.bfpbusinesspro.backup.CloudBackupManager
import `in`.bhajan.bfpbusinesspro.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private val io = Executors.newSingleThreadExecutor()
    private lateinit var db: BfpDatabase
    private lateinit var root: LinearLayout
    private var printWebView: WebView? = null
    private var pendingVoiceInput: EditText? = null
    private var pendingVoiceHandler: ((String) -> Unit)? = null
    private var continuousVoiceEntry = false
    private var startNextVoiceEntry: (() -> Unit)? = null
    private val voiceInput = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull().orEmpty()
            if (spoken.isNotBlank()) {
                pendingVoiceInput?.setText(spoken)
                pendingVoiceHandler?.invoke(spoken)
            } else toast("आवाज़ समझ नहीं आई। फिर कोशिश करें।")
        } else if (continuousVoiceEntry) {
            continuousVoiceEntry = false
            toast("Voice Entry रोक दी गई।")
        }
        pendingVoiceInput = null
        pendingVoiceHandler = null
    }
    private val createBackup = registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri -> if (uri != null) runBackup(uri) }
    private val chooseBackup = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) runRestore(uri) }
    private val chooseAutoBackupFolder = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                getSharedPreferences("bfp_auto_backup", MODE_PRIVATE).edit().putString("tree_uri", uri.toString()).apply()
                AutoBackupScheduler.schedule(this)
                toast("Automatic Backup फोल्डर सेव हो गया। रोज़ाना बैकअप शेड्यूल किया गया है।")
                showBackupMenu()
            } catch (e: Exception) { toast("फोल्डर अनुमति सेव नहीं हुई: ${e.message}") }
        }
    }
    private var pendingReportBytes: ByteArray = byteArrayOf()
    private val createStockCsv = registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) lifecycleScope.launch { try { withContext(Dispatchers.IO) { contentResolver.openOutputStream(uri)?.use { it.write(pendingReportBytes) } ?: error("फाइल नहीं खुली") }; toast("Excel-compatible CSV रिपोर्ट सेव हो गई") } catch (e: Exception) { toast("Export असफल: ${e.message}") } }
    }
    private val createStockPdf = registerForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) lifecycleScope.launch { try { withContext(Dispatchers.IO) { contentResolver.openOutputStream(uri)?.use { it.write(pendingReportBytes) } ?: error("फाइल नहीं खुली") }; toast("PDF रिपोर्ट सेव हो गई") } catch (e: Exception) { toast("Export असफल: ${e.message}") } }
    }
    private var pendingPurchaseAttachmentPath: String = ""
    private var editingOriginalAttachmentPath: String = ""
    private var purchaseAttachmentStatusView: TextView? = null
    private var purchaseAttachmentButtonView: Button? = null
    private var purchaseAttachmentOpenButtonView: Button? = null
    private val choosePurchaseAttachment = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) lifecycleScope.launch {
            try {
                val copied = withContext(Dispatchers.IO) { copyPurchaseAttachment(uri) }
                val previous = pendingPurchaseAttachmentPath
                pendingPurchaseAttachmentPath = copied.relativeTo(filesDir).invariantSeparatorsPath
                purchaseAttachmentStatusView?.text = "अटैच: ${copied.name}"
                purchaseAttachmentButtonView?.text = "दूसरी फोटो / PDF चुनें"
                purchaseAttachmentOpenButtonView?.visibility = View.VISIBLE
                if (previous.isNotBlank() && previous != copied.absolutePath && previous != editingOriginalAttachmentPath) withContext(Dispatchers.IO) { resolvePurchaseAttachment(previous).delete() }
                toast("सप्लायर बिल अटैच हो गया: ${copied.name}")
            } catch (e: Exception) { toast(e.message ?: "अटैचमेंट कॉपी नहीं हो सका") }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = BfpDatabase.get(this)
        showDashboard()
    }

    private fun base(title: String): LinearLayout {
        val shell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFFE4F3FF.toInt())
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12)); setBackgroundColor(0xFFFFFFFF.toInt())
            elevation = dp(3).toFloat()
        }
        val mark = TextView(this).apply {
            text = "B"; textSize = 19f; gravity = Gravity.CENTER
            setTextColor(0xFFFFFFFF.toInt()); typeface = android.graphics.Typeface.DEFAULT_BOLD
            background = rounded(0xFF202638.toInt(), 50); layoutParams = LinearLayout.LayoutParams(dp(42), dp(42))
        }
        header.addView(mark)
        header.addView(TextView(this).apply {
            text = "BHAJAN FASHION POINT"; textSize = 17f; maxLines = 1
            setTextColor(0xFF34394B.toInt()); typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(dp(12), 0, 0, 0)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(headerIcon("♧") { showMenu() })
        header.addView(headerIcon("⚙") { showMenu() })
        shell.addView(header)

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(18))
            setBackgroundColor(0xFFE4F3FF.toInt())
        }
        root.addView(TextView(this).apply {
            text = title; textSize = 20f; gravity = Gravity.START
            setTextColor(0xFF34394B.toInt()); typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(dp(6), dp(8), dp(6), dp(14))
        })
        shell.addView(ScrollView(this).apply { isFillViewport = true; addView(root) }, LinearLayout.LayoutParams(-1, 0, 1f))

        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            setBackgroundColor(0xFFFFFFFF.toInt()); elevation = dp(8).toFloat(); setPadding(dp(4), dp(7), dp(4), dp(7))
        }
        listOf("⌂\nHOME / होम", "▥\nDASHBOARD / डैशबोर्ड", "◇\nITEMS / आइटम", "☰\nMENU / मेन्यू").forEachIndexed { index, label ->
            nav.addView(TextView(this).apply {
                text = label; gravity = Gravity.CENTER; textSize = 12f
                setTextColor(if ((title.startsWith("BHAJAN") && index == 0) || (title.contains("DASHBOARD") && index == 1) || (title.contains("ITEM") && index == 2) || (title.contains("MENU") && index == 3)) 0xFF0879C9.toInt() else 0xFF454958.toInt())
                typeface = android.graphics.Typeface.DEFAULT_BOLD; setPadding(0, dp(4), 0, dp(4))
                setOnClickListener { when (index) { 0 -> showDashboard(); 1 -> showDashboardSummary(); 2 -> showItems(); 3 -> showMenu() } }
            }, LinearLayout.LayoutParams(0, dp(52), 1f))
        }
        shell.addView(nav)
        setContentView(shell)
        return root
    }

    private fun rounded(color: Int, radius: Int = 16): android.graphics.drawable.GradientDrawable =
        android.graphics.drawable.GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }

    private fun headerIcon(symbol: String, action: () -> Unit) = TextView(this).apply {
        text = symbol; textSize = 24f; gravity = Gravity.CENTER; setTextColor(0xFF626779.toInt())
        setPadding(dp(8), 0, dp(8), 0); setOnClickListener { action() }
    }

    private fun addAction(parent: LinearLayout, label: String, color: Int = 0xFF245A78.toInt(), action: () -> Unit) {
        parent.addView(Button(this).apply {
            text = label; isAllCaps = false; setTextColor(0xFFFFFFFF.toInt())
            backgroundTintList = android.content.res.ColorStateList.valueOf(color)
            setOnClickListener { action() }
        }, LinearLayout.LayoutParams(-1, dp(50)).apply { bottomMargin = dp(7) })
    }

    private fun showDashboard() {
        val page = base("HOME")
        val quick = sectionCard("Quick Links")
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        listOf("＋\nAdd Txn", "▤\nSale Report", "⚙\nTxn Settings", "›\nShow All").forEachIndexed { i, label ->
            actions.addView(TextView(this).apply {
                text = label; textSize = 13f; gravity = Gravity.CENTER; setTextColor(0xFF414657.toInt())
                setPadding(dp(2), dp(12), dp(2), dp(10))
                setOnClickListener { when (i) { 0 -> showInvoices("SALE"); 1 -> showReports(); 2 -> showGstSettings(); else -> showAllTransactions() } }
            }, LinearLayout.LayoutParams(0, dp(76), 1f))
        }
        quick.addView(actions); page.addView(quick)

        val search = EditText(this).apply {
            hint = "⌕  Search for a transaction"; textSize = 15f; setSingleLine(true)
            setPadding(dp(16), dp(10), dp(16), dp(10)); background = rounded(0xFFFFFFFF.toInt(), 18)
        }
        page.addView(search, LinearLayout.LayoutParams(-1, dp(54)).apply { topMargin = dp(14); bottomMargin = dp(12) })

        page.addView(sectionCard("Recent Transactions").apply {
            addView(TextView(this@MainActivity).apply { text = "हाल की बिक्री, खरीद, पेमेंट और खर्च यहाँ दिखाई देंगे। पूरी सूची के लिए Show All दबाएँ।"; textSize = 13f; setTextColor(0xFF85899A.toInt()); setPadding(0, dp(8), 0, dp(8)) })
            addView(actionChip("Show All Transactions") { showAllTransactions() })
        })
        val recent = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        page.addView(recent)
        lifecycleScope.launch {
            try {
                val sales = withContext(Dispatchers.IO) { db.dao().observeInvoices("SALE").first() }
                val purchases = withContext(Dispatchers.IO) { db.dao().observeInvoices("PURCHASE").first() }
                val latest = (sales + purchases).sortedByDescending { it.dateEpochMillis }.take(8)
                recent.removeAllViews()
                if (latest.isEmpty()) recent.addView(sectionCard("अभी कोई Transaction नहीं है"))
                latest.forEach { invoice ->
                    val party = withContext(Dispatchers.IO) { db.dao().getParty(invoice.partyId)?.name ?: "पार्टी" }
                    val kind = if (invoice.kind == "SALE") "SALE" else "PURCHASE"
                    val date = java.text.SimpleDateFormat("dd MMM, yy", java.util.Locale("en", "IN")).format(java.util.Date(invoice.dateEpochMillis))
                    val row = sectionCard(party).apply {
                        addView(TextView(this@MainActivity).apply { text = "$kind  •  $date"; textSize = 12f; setTextColor(0xFF85899A.toInt()) })
                        addView(TextView(this@MainActivity).apply { text = "₹${invoice.totalPaise / 100.0}     ${if (invoice.totalPaise > invoice.paidPaise) "बाकी ₹${(invoice.totalPaise-invoice.paidPaise)/100.0}" else "Paid"}"; textSize = 16f; typeface = android.graphics.Typeface.DEFAULT_BOLD; setTextColor(0xFF34394B.toInt()); setPadding(0, dp(8), 0, 0) })
                        setOnClickListener { showInvoices(invoice.kind) }
                    }
                    recent.addView(row)
                }
            } catch (_: Exception) { recent.addView(emptyLabel("ट्रांजेक्शन लोड नहीं हो सके")) }
        }
        val sale = Button(this).apply {
            text = "₹  Add New Sale"; isAllCaps = false; textSize = 16f; typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(-1); background = rounded(0xFFEA1744.toInt(), 28); setOnClickListener { showInvoices("SALE") }
        }
        page.addView(sale, LinearLayout.LayoutParams(-1, dp(54)).apply { topMargin = dp(10); bottomMargin = dp(12) })
    }

    private fun showDashboardSummary() {
        val page = base("BUSINESS DASHBOARD / बिजनेस डैशबोर्ड")
        // Dev105: prominent receivable/payable overview cards at the top of Dashboard.
        val outstandingRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun outstandingCard(title: String, subtitle: String, color: Int, action: () -> Unit) = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(12), dp(10), dp(12))
            background = rounded(0xFFFFFFFF.toInt(), 18)
            elevation = dp(2).toFloat()
            addView(TextView(this@MainActivity).apply { text = title; textSize = 14f; typeface = android.graphics.Typeface.DEFAULT_BOLD; setTextColor(color) })
            addView(TextView(this@MainActivity).apply { text = "₹ 0.00"; tag = "outstanding_amount"; textSize = 20f; typeface = android.graphics.Typeface.DEFAULT_BOLD; setTextColor(0xFF34394B.toInt()); setPadding(0, dp(7), 0, dp(3)) })
            addView(TextView(this@MainActivity).apply { text = subtitle; textSize = 11f; setTextColor(0xFF777D8D.toInt()) })
            setOnClickListener { action() }
        }
        val getCard = outstandingCard("↙  YOU'LL GET / लेना है", "ग्राहकों से बकाया • Receivables", 0xFF159B72.toInt()) { showOutstandingParties(true) }
        val giveCard = outstandingCard("↗  YOU'LL GIVE / देना है", "सप्लायरों को बकाया • Payables", 0xFFD95368.toInt()) { showOutstandingParties(false) }
        outstandingRow.addView(getCard, LinearLayout.LayoutParams(0, dp(112), 1f).apply { setMargins(dp(2), dp(3), dp(5), dp(10)) })
        outstandingRow.addView(giveCard, LinearLayout.LayoutParams(0, dp(112), 1f).apply { setMargins(dp(5), dp(3), dp(2), dp(10)) })
        page.addView(outstandingRow)
        lifecycleScope.launch {
            try {
                val balances = withContext(Dispatchers.IO) { ReportsRepository(db).partyBalances() }
                (getCard.getChildAt(1) as TextView).text = "₹ ${String.format(java.util.Locale("en", "IN"), "%,.2f", balances.debtorsPaise / 100.0)}"
                (giveCard.getChildAt(1) as TextView).text = "₹ ${String.format(java.util.Locale("en", "IN"), "%,.2f", balances.creditorsPaise / 100.0)}"
            } catch (e: Exception) { toast(e.message ?: "बकाया राशि लोड नहीं हुई") }
        }
        val todayLabel = java.text.SimpleDateFormat("EEEE, dd MMM yyyy", java.util.Locale("en", "IN")).format(java.util.Date())
        page.addView(TextView(this).apply { text = "आज का कारोबार / TODAY'S BUSINESS\n$todayLabel"; textSize = 14f; setTextColor(0xFF596174.toInt()); setPadding(dp(4), dp(4), dp(4), dp(12)) })
        val startCal = java.util.Calendar.getInstance().apply { set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0); set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0) }
        val startMillis = startCal.timeInMillis
        val endMillis = startMillis + 24L * 60L * 60L * 1000L
        fun metric(label: String, amount: Long, color: Int, action: () -> Unit) = TextView(this).apply {
            text = "$label\n₹${String.format(java.util.Locale("en", "IN"), "%,.2f", amount / 100.0)}"
            textSize = 15f; gravity = Gravity.CENTER; typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(0xFFFFFFFF.toInt()); setPadding(dp(8), dp(15), dp(8), dp(15)); background = rounded(color, 16); setOnClickListener { action() }
        }
        val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        page.addView(grid)
        fun addMetricRow(left: View, right: View) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            val lp = LinearLayout.LayoutParams(0, dp(92), 1f).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) }
            row.addView(left, lp); row.addView(right, LinearLayout.LayoutParams(0, dp(92), 1f).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) }); grid.addView(row)
        }
        addMetricRow(metric("SALE / बिक्री", 0, 0xFF0879C9.toInt()) { showInvoices("SALE") }, metric("RECEIPTS / प्राप्तियाँ", 0, 0xFF168447.toInt()) { showPayments("IN") })
        addMetricRow(metric("PURCHASE / खरीद", 0, 0xFF596174.toInt()) { showInvoices("PURCHASE") }, metric("EXPENSE / खर्च", 0, 0xFFC74646.toInt()) { showExpenses() })
        page.addView(metric("EST. GROSS PROFIT / अनुमानित सकल मुनाफा", 0, 0xFF236B58.toInt()) { showProfitAndDeadStock() }, LinearLayout.LayoutParams(-1, dp(84)).apply { topMargin = dp(4); bottomMargin = dp(8) })
        page.addView(TextView(this).apply { text = "ध्यान दें: प्राप्तियाँ बिक्री से अलग हैं। मुनाफा मौजूदा Item Master दरों पर अनुमान है; यह वास्तविक ऐतिहासिक लागत या नेट प्रॉफिट नहीं है।"; textSize = 12f; setTextColor(0xFF6C7180.toInt()); setPadding(dp(6), dp(8), dp(6), dp(12)) })
        addAction(page, "SALE / बिक्री खोलें") { showInvoices("SALE") }
        addAction(page, "PURCHASE / खरीद खोलें") { showInvoices("PURCHASE") }
        addAction(page, "RECEIPTS / प्राप्तियाँ खोलें") { showPayments("IN") }
        addAction(page, "EXPENSES / खर्च खोलें") { showExpenses() }
        addAction(page, "DAY BOOK / रोज़नामचा") { showDayBook() }
        addAction(page, "REPORTS / रिपोर्ट") { showReports() }
        addAction(page, "BUSINESS TRENDS / कारोबार के ग्राफ") { showBusinessTrends() }
        addAction(page, "AI DECISION ASSISTANT / AI निर्णय सहायक") { showAiDecisionAssistant() }
        addAction(page, "AI BUSINESS INTELLIGENCE / AI कारोबार विश्लेषण") { showAiBusinessIntelligence() }
        addAction(page, "AI DEMAND FORECAST / मांग का अनुमान") { showAiDemandForecast() }
        addAction(page, "AI PROFIT & CASHFLOW ANALYSIS / मुनाफा और नकदी विश्लेषण") { showAiProfitCashflow() }
        lifecycleScope.launch {
            try {
                val totals = withContext(Dispatchers.IO) { db.dao().observeBusinessDashboard(startMillis, endMillis).first() }
                (grid.getChildAt(0) as LinearLayout).let { (it.getChildAt(0) as TextView).text = "SALE / बिक्री\n₹${totals.salesPaise / 100.0}"; (it.getChildAt(1) as TextView).text = "RECEIPTS / प्राप्तियाँ\n₹${totals.receiptsPaise / 100.0}" }
                (grid.getChildAt(1) as LinearLayout).let { (it.getChildAt(0) as TextView).text = "PURCHASE / खरीद\n₹${totals.purchasesPaise / 100.0}"; (it.getChildAt(1) as TextView).text = "EXPENSE / खर्च\n₹${totals.expensesPaise / 100.0}" }
                (grid.getChildAt(2) as LinearLayout).let { (it.getChildAt(0) as TextView).text = "EST. GROSS PROFIT / अनुमानित सकल मुनाफा\n₹${totals.estimatedGrossProfitPaise / 100.0}" }

            } catch (e: Exception) { toast(e.message ?: "Dashboard डेटा लोड नहीं हुआ") }
        }
    }

    private fun showOutstandingParties(receivable: Boolean) {
        val title = if (receivable) "YOU'LL GET / ग्राहक से लेना है" else "YOU'LL GIVE / सप्लायर को देना है"
        val page = base(title)
        addAction(page, "← Dashboard / डैशबोर्ड") { showDashboardSummary() }
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        page.addView(list)
        lifecycleScope.launch {
            try {
                val data = withContext(Dispatchers.IO) {
                    ReportsRepository(db).partyOutstanding(receivable)
                }
                list.removeAllViews()
                val total = data.sumOf { it.outstandingPaise }
                list.addView(card("कुल बकाया / TOTAL OUTSTANDING\n₹${String.format(java.util.Locale("en", "IN"), "%,.2f", total / 100.0)}\nपार्टियाँ: ${data.size}"))
                if (data.isEmpty()) list.addView(emptyLabel("अभी कोई बकाया राशि दर्ज नहीं है।"))
                data.forEach { row ->
                    val party = row.party
                    val due = row.outstandingPaise
                    list.addView(card("${party.name}\nबकाया / Outstanding: ₹${String.format(java.util.Locale("en", "IN"), "%,.2f", due / 100.0)}\nबिल / Bills: ${row.billCount}\n${party.mobile}").apply {
                        setTextColor(if (receivable) 0xFF168447.toInt() else 0xFFC74646.toInt())
                        setOnClickListener { showPartyLedger(party) }
                    })
                }
            } catch (e: Exception) { list.removeAllViews(); list.addView(emptyLabel(e.message ?: "बकाया सूची लोड नहीं हुई")) }
        }
    }

    private fun showBusinessTrends() {
        val page = base("BUSINESS TRENDS / कारोबार के रुझान")
        page.addView(TextView(this).apply { text = "पिछले 6 महीनों की बिक्री, खरीद और खर्च की तुलना / Six-month comparison"; textSize = 13f; setTextColor(0xFF687083.toInt()); setPadding(dp(4), dp(4), dp(4), dp(10)) })
        val now = java.util.Calendar.getInstance()
        val endCal = (now.clone() as java.util.Calendar).apply { set(java.util.Calendar.DAY_OF_MONTH, 1); set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0); set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0); add(java.util.Calendar.MONTH, 1) }
        val startCal = (endCal.clone() as java.util.Calendar).apply { add(java.util.Calendar.MONTH, -6) }
        val chartHolder = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(8), dp(8), dp(8), dp(8)); background = rounded(0xFFF7F8FB.toInt(), 16) }
        page.addView(chartHolder, LinearLayout.LayoutParams(-1, dp(280)).apply { bottomMargin = dp(10) })
        val legend = TextView(this).apply { text = "■ SALE / बिक्री     ■ PURCHASE / खरीद     ■ EXPENSE / खर्च"; textSize = 11f; setTextColor(0xFF596174.toInt()); setPadding(dp(4), dp(6), dp(4), dp(10)) }
        page.addView(legend)
        val rowsBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        page.addView(rowsBox)
        page.addView(TextView(this).apply { text = "अनुमानित सकल मुनाफा मौजूदा Item Master दरों पर है; यह ऐतिहासिक लागत या नेट प्रॉफिट नहीं है। प्राप्तियाँ बिक्री में शामिल नहीं हैं। / Profit is an estimate; receipts are not sales."; textSize = 11f; setTextColor(0xFF747B8B.toInt()); setPadding(dp(5), dp(12), dp(5), dp(8)) })
        addAction(page, "← BUSINESS DASHBOARD / डैशबोर्ड") { showDashboardSummary() }
        lifecycleScope.launch {
            try {
                val data = withContext(Dispatchers.IO) { db.dao().observeMonthlyBusinessTrends(startCal.timeInMillis, endCal.timeInMillis).first() }
                val byMonth = data.associateBy { it.monthKey }
                val months = (0 until 6).map { offset -> (startCal.clone() as java.util.Calendar).apply { add(java.util.Calendar.MONTH, offset) } }
                val values = months.map { cal ->
                    val key = java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.US).format(cal.time)
                    val r = byMonth[key]
                    MonthlyBusinessTrendRow(key, r?.salesPaise ?: 0L, r?.purchasesPaise ?: 0L, r?.expensesPaise ?: 0L, r?.estimatedGrossProfitPaise ?: 0L)
                }
                val labels = months.map { java.text.SimpleDateFormat("MMM", java.util.Locale("en", "IN")).format(it.time) }
                val chart = object : View(this@MainActivity) {
                    private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
                    override fun onDraw(canvas: android.graphics.Canvas) {
                        super.onDraw(canvas)
                        val maxValue = (values.maxOfOrNull { maxOf(it.salesPaise, it.purchasesPaise, it.expensesPaise) } ?: 0L).coerceAtLeast(1L).toFloat()
                        val left = dp(12).toFloat(); val right = width - dp(8).toFloat(); val top = dp(18).toFloat(); val bottom = height - dp(34).toFloat()
                        paint.color = 0xFFD9DEE8.toInt(); paint.strokeWidth = dp(1).toFloat(); canvas.drawLine(left, bottom, right, bottom, paint)
                        val groupWidth = (right - left) / 6f; val barWidth = groupWidth * 0.19f
                        values.forEachIndexed { i, r ->
                            val center = left + groupWidth * (i + 0.5f)
                            val amounts = listOf(r.salesPaise, r.purchasesPaise, r.expensesPaise)
                            val colors = listOf(0xFF1876C9.toInt(), 0xFF687386.toInt(), 0xFFC74646.toInt())
                            amounts.forEachIndexed { j, amount ->
                                val h = (bottom - top) * (amount.toFloat() / maxValue)
                                paint.color = colors[j]
                                val x = center + (j - 1) * (barWidth + dp(2))
                                canvas.drawRoundRect(x, bottom - h, x + barWidth, bottom, dp(3).toFloat(), dp(3).toFloat(), paint)
                            }
                            paint.color = 0xFF596174.toInt(); paint.textSize = dp(10).toFloat(); paint.textAlign = android.graphics.Paint.Align.CENTER
                            canvas.drawText(labels[i], center, height - dp(10).toFloat(), paint)
                        }
                    }
                }
                chartHolder.addView(chart, LinearLayout.LayoutParams(-1, -1))
                rowsBox.removeAllViews()
                values.asReversed().forEachIndexed { index, r ->
                    val label = labels[5 - index]
                    val row = sectionCard("$label  •  SALE ₹${r.salesPaise / 100.0}   |   PURCHASE ₹${r.purchasesPaise / 100.0}   |   EXPENSE ₹${r.expensesPaise / 100.0}").apply {
                        addView(TextView(this@MainActivity).apply { text = "अनुमानित सकल मुनाफा / Est. Gross Profit: ₹${r.estimatedGrossProfitPaise / 100.0}"; textSize = 12f; setTextColor(0xFF236B58.toInt()); setPadding(0, dp(5), 0, 0) })
                    }
                    rowsBox.addView(row)
                }
                if (values.all { it.salesPaise == 0L && it.purchasesPaise == 0L && it.expensesPaise == 0L }) rowsBox.addView(emptyLabel("इस अवधि में कोई बिक्री, खरीद या खर्च दर्ज नहीं है।"))
            } catch (e: Exception) { toast(e.message ?: "Business trends लोड नहीं हुए") }
        }
    }

    private fun showAiBusinessIntelligence() {
        val page = base("AI BUSINESS INTELLIGENCE / AI कारोबार विश्लेषण")
        addAction(page, "← DASHBOARD / डैशबोर्ड") { showDashboardSummary() }
        page.addView(TextView(this).apply {
            text = "यह रिकॉर्ड-आधारित स्मार्ट विश्लेषण है, भविष्य की गारंटी नहीं। सभी सुझाव केवल जानकारी के लिए हैं। आपकी अनुमति के बिना कोई बिक्री, खरीद, स्टॉक, भुगतान या अकाउंटिंग एंट्री नहीं बदली जाएगी। / Read-only insights; no automatic posting."
            textSize = 13f; setTextColor(0xFF455A64.toInt()); setPadding(dp(12), dp(8), dp(12), dp(12))
        })
        val slowList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val fastList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val rateList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        page.addView(sectionCard("SLOW-MOVING DESIGNS / धीमी बिक्री वाले डिजाइन").apply { addView(slowList) })
        page.addView(sectionCard("FAST-MOVING DESIGNS / तेज़ बिक्री वाले डिजाइन").apply { addView(fastList) })
        page.addView(sectionCard("PURCHASE RATE INCREASES / खरीद रेट में बढ़ोतरी").apply { addView(rateList) })
        val cutoff = System.currentTimeMillis() - 90L * 24 * 60 * 60 * 1000
        lifecycleScope.launch {
            db.dao().observeItemPerformance(cutoff).collectLatest { rows ->
                slowList.removeAllViews(); fastList.removeAllViews()
                if (rows.isEmpty()) {
                    slowList.addView(emptyLabel("पहले Item Master में सूट / डिजाइन जोड़ें।"))
                    fastList.addView(emptyLabel("अभी डिजाइन डेटा उपलब्ध नहीं है।"))
                    return@collectLatest
                }
                val slow = rows.filter { it.item.quantity > 0.0 && it.soldLast90Days <= 0.0 }.sortedByDescending { it.item.quantity }
                val fast = rows.filter { it.soldLast90Days > 0.0 }.sortedByDescending { it.soldLast90Days }
                slowList.addView(card("90 दिन में बिक्री नहीं, लेकिन स्टॉक मौजूद: ${slow.size} डिजाइन"))
                if (slow.isEmpty()) slowList.addView(emptyLabel("मौजूदा रिकॉर्ड में ऐसा कोई डिजाइन नहीं मिला।"))
                slow.take(20).forEach { row ->
                    val item=row.item
                    val lastSale=if(row.lastSaleEpochMillis<=0L) "पहले बिक्री दर्ज नहीं" else "अंतिम बिक्री: "+java.text.SimpleDateFormat("dd-MM-yyyy",java.util.Locale.US).format(java.util.Date(row.lastSaleEpochMillis))
                    slowList.addView(menuRow("${item.name} (${item.itemCode})\nस्टॉक: ${item.quantity} पीस  |  $lastSale\nसुझाव: डिस्प्ले/ऑफर या अगली खरीद मात्रा की समीक्षा करें") { itemDialog(item) })
                }
                fastList.addView(card("90 दिन में बिक्री वाले डिजाइन: ${fast.size}"))
                if(fast.isEmpty()) fastList.addView(emptyLabel("अभी 90 दिन की बिक्री उपलब्ध नहीं है।"))
                fast.take(10).forEachIndexed { index,row -> fastList.addView(menuRow("${index+1}. ${row.item.name} (${row.item.itemCode})\n90 दिन बिक्री: ${row.soldLast90Days} पीस  |  मौजूदा स्टॉक: ${row.item.quantity}\nसुझाव: उपलब्ध स्टॉक और अगली खरीद योजना की समीक्षा करें") { itemDialog(row.item) }) }
            }
        }
        lifecycleScope.launch {
            db.dao().observeSupplierItemRateChanges().collectLatest { rows ->
                rateList.removeAllViews()
                val increases=rows.filter { it.latestRatePaise > it.previousRatePaise }.sortedByDescending { it.latestRatePaise-it.previousRatePaise }
                if(increases.isEmpty()) rateList.addView(emptyLabel("तुलना योग्य खरीद रिकॉर्ड में अभी रेट बढ़ोतरी नहीं मिली।"))
                else {
                    rateList.addView(card("पिछली तुलना में रेट बढ़े: ${increases.size} supplier-item records"))
                    increases.take(20).forEach { row ->
                        val delta=row.latestRatePaise-row.previousRatePaise
                        val percent=if(row.previousRatePaise>0L) delta.toDouble()*100.0/row.previousRatePaise else 0.0
                        rateList.addView(card("${row.supplierName} • ${row.itemName} (${row.itemCode})\nपहले: ₹${row.previousRatePaise/100.0}  |  अब: ₹${row.latestRatePaise/100.0}\nबढ़ोतरी: ₹${delta/100.0} (${String.format(java.util.Locale.US,"%.1f",percent)}%)\nसुझाव: अगली खरीद से पहले सप्लायर रेट की पुष्टि करें।"))
                    }
                }
            }
        }
    }

    private fun showAiDemandForecast() {
        val page = base("AI DEMAND FORECAST / AI मांग का अनुमान")
        addAction(page, "← DASHBOARD / डैशबोर्ड") { showDashboardSummary() }
        page.addView(TextView(this).apply {
            text = "अनुमान पिछले 30 दिनों की वास्तविक बिक्री पर आधारित है। पिछले 30 दिनों की तुलना उससे पहले के 30 दिनों से की जाती है। यह सरल रन-रेट अनुमान है, गारंटी या AI द्वारा तय खरीद आदेश नहीं। सभी सुझाव केवल समीक्षा के लिए हैं; कोई स्टॉक/खरीद एंट्री अपने-आप नहीं बनेगी।"
            textSize = 13f; setTextColor(0xFF455A64.toInt()); setPadding(dp(12), dp(8), dp(12), dp(12))
        })
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        page.addView(sectionCard("NEXT 30-DAY DEMAND / अगले 30 दिन की मांग").apply { addView(list) })
        val now = System.currentTimeMillis()
        val day = 24L * 60 * 60 * 1000
        val recentStart = now - 30L * day
        val previousStart = now - 60L * day
        lifecycleScope.launch {
            db.dao().observeItemDemandForecast(previousStart, recentStart, now).collectLatest { rows ->
                list.removeAllViews()
                val withSales = rows.filter { it.soldRecent30Days > 0.0 || it.soldPrevious30Days > 0.0 }
                if (withSales.isEmpty()) {
                    list.addView(emptyLabel("पिछले 60 दिनों में बिक्री रिकॉर्ड नहीं मिली। मांग का अनुमान बनाने के लिए पहले बिक्री दर्ज करें।"))
                    return@collectLatest
                }
                val sorted = withSales.sortedByDescending { it.soldRecent30Days }
                list.addView(card("बिक्री डेटा वाले डिजाइन: ${withSales.size}  |  अवधि: पिछले 60 दिन"))
                sorted.take(40).forEach { row ->
                    val recent = row.soldRecent30Days
                    val previous = row.soldPrevious30Days
                    val trend = when {
                        previous <= 0.0 && recent > 0.0 -> "नई बिक्री / पहले की अवधि में बिक्री नहीं"
                        previous <= 0.0 -> "तुलना उपलब्ध नहीं"
                        recent > previous -> "बिक्री बढ़ी ${String.format(java.util.Locale.US, "%.1f", (recent - previous) * 100.0 / previous)}%"
                        recent < previous -> "बिक्री घटी ${String.format(java.util.Locale.US, "%.1f", (previous - recent) * 100.0 / previous)}%"
                        else -> "बिक्री लगभग स्थिर"
                    }
                    val projected = recent
                    val safetyBuffer = row.item.minimumStockQuantity.coerceAtLeast(0.0)
                    val suggested = (projected + safetyBuffer - row.item.quantity).coerceAtLeast(0.0)
                    val advice = if (suggested > 0.0) "समीक्षा हेतु संभावित खरीद: ${String.format(java.util.Locale.US, "%.1f", suggested)} पीस"
                        else "मौजूदा स्टॉक अनुमानित मांग + न्यूनतम स्टॉक के बराबर/अधिक"
                    list.addView(menuRow(
                        "${row.item.name} (${row.item.itemCode})\n" +
                        "पिछले 30 दिन बिक्री: ${recent} पीस | उससे पहले: ${previous} पीस\n" +
                        "अगले 30 दिन अनुमान: ${projected} पीस | मौजूदा स्टॉक: ${row.item.quantity} पीस\n" +
                        "$trend\n$advice"
                    ) { itemDialog(row.item) })
                }
                list.addView(TextView(this@MainActivity).apply {
                    text = "नोट: अनुमान = हाल के 30 दिनों की बिक्री। संभावित खरीद मात्रा = अनुमान + Item Master का न्यूनतम स्टॉक − उपलब्ध स्टॉक (न्यूनतम शून्य)। सप्लायर लीड-टाइम, सीजन, रिटर्न और भविष्य के ऑर्डर शामिल नहीं हैं। खरीद से पहले स्वयं पुष्टि करें।"
                    textSize = 12f; setTextColor(0xFF607D8B.toInt()); setPadding(dp(12), dp(10), dp(12), dp(10))
                })
            }
        }
    }

    private fun showAiProfitCashflow() {
        val page = base("AI PROFIT & CASHFLOW ANALYSIS / मुनाफा और नकदी विश्लेषण")
        addAction(page, "← DASHBOARD / डैशबोर्ड") { showDashboardSummary() }
        page.addView(TextView(this).apply {
            text = "यह छह महीने का रिकॉर्ड-आधारित वित्तीय विश्लेषण है। अनुमानित सकल मुनाफा मौजूदा Item Master की बिक्री और खरीद दरों से निकाला गया है; यह वास्तविक ऐतिहासिक लागत या ऑडिटेड लाभ नहीं है। अनुमानित परिचालन शेष = अनुमानित सकल मुनाफा − दर्ज खर्च − दर्ज वेतन भुगतान। प्राप्तियाँ बिक्री नहीं हैं और खरीद को सीधे खर्च नहीं माना गया है।"
            textSize = 13f; setTextColor(0xFF455A64.toInt()); setPadding(dp(12), dp(8), dp(12), dp(12))
        })
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        page.addView(sectionCard("MONTHLY PROFITABILITY / मासिक मुनाफा विश्लेषण").apply { addView(list) })
        val now = System.currentTimeMillis()
        val calendar = java.util.Calendar.getInstance().apply {
            timeInMillis = now
            set(java.util.Calendar.DAY_OF_MONTH, 1)
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
            add(java.util.Calendar.MONTH, -5)
        }
        val start = calendar.timeInMillis
        lifecycleScope.launch {
            db.dao().observeMonthlyProfitability(start, now + 1L).collectLatest { rows ->
                list.removeAllViews()
                if (rows.isEmpty()) {
                    list.addView(emptyLabel("पिछले छह महीनों में बिक्री/खर्च/वेतन का रिकॉर्ड उपलब्ध नहीं है।"))
                    return@collectLatest
                }
                rows.forEach { row ->
                    val margin = if (row.salesPaise > 0L) row.estimatedGrossProfitPaise.toDouble() * 100.0 / row.salesPaise else 0.0
                    val surplusColor = if (row.estimatedOperatingSurplusPaise >= 0L) 0xFF168447.toInt() else 0xFFC93636.toInt()
                    val monthName = try {
                        val parts = row.monthKey.split("-")
                        java.text.SimpleDateFormat("MMM yyyy", java.util.Locale("en", "IN")).format(
                            java.util.GregorianCalendar(parts[0].toInt(), parts[1].toInt() - 1, 1).time
                        )
                    } catch (_: Exception) { row.monthKey }
                    val details = "बिक्री: ₹${row.salesPaise / 100.0}\n" +
                        "अनुमानित सकल मुनाफा: ₹${row.estimatedGrossProfitPaise / 100.0}  |  अनुमानित मार्जिन: ${String.format(java.util.Locale.US, "%.1f", margin)}%\n" +
                        "दर्ज खर्च: ₹${row.expensesPaise / 100.0}  |  वेतन भुगतान: ₹${row.salaryPaidPaise / 100.0}"
                    list.addView(sectionCard(monthName).apply {
                        addView(TextView(this@MainActivity).apply {
                            text = details; textSize = 13f; setTextColor(0xFF455A64.toInt()); setPadding(dp(8), dp(6), dp(8), dp(6))
                        })
                        addView(TextView(this@MainActivity).apply {
                            text = "अनुमानित परिचालन शेष: ₹${row.estimatedOperatingSurplusPaise / 100.0}"
                            textSize = 15f; typeface = android.graphics.Typeface.DEFAULT_BOLD
                            setTextColor(surplusColor); setPadding(dp(8), dp(6), dp(8), dp(8))
                        })
                    })
                }
            }
        }
        page.addView(TextView(this).apply {
            text = "सीमाएँ: स्टॉक का वास्तविक FIFO/बैच लागत, टैक्स समायोजन, मालिक की निकासी, ऋण/ब्याज, depreciation और दर्ज न किए गए खर्च इसमें शामिल नहीं हैं। नकदी प्रवाह का वास्तविक बैंक/कैश बैलेंस देखने के लिए अलग Cashbook और Bank स्क्रीन का उपयोग करें। यह स्क्रीन कोई एंट्री पोस्ट या बैलेंस नहीं बदलती।"
            textSize = 12f; setTextColor(0xFF607D8B.toInt()); setPadding(dp(12), dp(10), dp(12), dp(12))
        })
    }

    private fun showAiDecisionAssistant() {
        val page = base("AI DECISION ASSISTANT / AI निर्णय सहायक")
        addAction(page, "← DASHBOARD / डैशबोर्ड") { showDashboardSummary() }
        page.addView(TextView(this).apply {
            text = "आपके दर्ज कारोबार डेटा के आधार पर प्राथमिकता वाले सुझाव। हर सुझाव सलाह मात्र है—कोई खरीद, बिक्री, भुगतान, स्टॉक या अकाउंटिंग एंट्री अपने-आप नहीं बनेगी। डेटा अधूरा हो तो सुझाव भी सीमित होंगे। / Read-only decision support; owner approval always required."
            textSize = 13f; setTextColor(0xFF455A64.toInt()); setPadding(dp(12), dp(8), dp(12), dp(12))
        })
        val summary = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        page.addView(sectionCard("TODAY'S PRIORITIES / आज की प्राथमिकताएँ").apply { addView(summary) })
        val now = System.currentTimeMillis()
        val day = 24L * 60L * 60L * 1000L
        lifecycleScope.launch {
            summary.removeAllViews()
            try {
                val dao = db.dao()
                val current = withContext(Dispatchers.IO) { dao.observeBusinessDashboard(now - 30L * day, now + 1L).first() }
                val previous = withContext(Dispatchers.IO) { dao.observeBusinessDashboard(now - 60L * day, now - 30L * day).first() }
                val stock = withContext(Dispatchers.IO) { dao.observeItemDemandForecast(now - 60L * day, now - 30L * day, now + 1L).first() }
                val customers = withContext(Dispatchers.IO) { dao.observeCustomerInsights().first() }
                val suppliers = withContext(Dispatchers.IO) { dao.observeSupplierPurchaseSummaries().first() }
                val rates = withContext(Dispatchers.IO) { dao.observeSupplierItemRateChanges().first() }
                fun rupees(value: Long) = "₹" + String.format(java.util.Locale("en", "IN"), "%,.2f", value / 100.0)
                fun addPriority(title: String, detail: String, action: (() -> Unit)? = null, color: Int = 0xFF36566F.toInt()) {
                    val card = sectionCard(title).apply {
                        addView(TextView(this@MainActivity).apply { text = detail; textSize = 13f; setTextColor(0xFF455A64.toInt()); setPadding(dp(8), dp(6), dp(8), dp(8)) })
                        if (action != null) addView(actionChip("संबंधित स्क्रीन खोलें / Review") { action() })
                    }
                    card.setBackgroundColor(0xFFF5F8FA.toInt())
                    summary.addView(card)
                }
                if (current.salesPaise == 0L && previous.salesPaise == 0L && stock.isEmpty()) {
                    addPriority("डेटा अभी कम है / More data needed", "पहले बिक्री, खरीद और स्टॉक की नियमित एंट्री करें। पर्याप्त रिकॉर्ड आने पर यह स्क्रीन प्राथमिकताएँ दिखाएगी।", { showInvoices("SALE") })
                } else {
                    val salesChange = if (previous.salesPaise > 0L) (current.salesPaise - previous.salesPaise).toDouble() * 100.0 / previous.salesPaise else Double.NaN
                    if (current.salesPaise > 0L || previous.salesPaise > 0L) {
                        val trend = if (salesChange.isNaN()) "पिछली 30-दिन अवधि में तुलना योग्य बिक्री नहीं मिली।" else "पिछले 30 दिन: ${rupees(current.salesPaise)}; उससे पहले: ${rupees(previous.salesPaise)} (${if (salesChange >= 0) "+" else ""}${String.format(java.util.Locale.US, "%.1f", salesChange)}%)."
                        val advice = when { salesChange.isNaN() -> "अगले महीने तुलना के लिए बिक्री रिकॉर्ड जारी रखें।"; salesChange < -10.0 -> "बिक्री में 10% से अधिक गिरावट है—दुकान के लोकप्रिय डिजाइन, ग्राहक फॉलो-अप और सीजन की समीक्षा करें।"; salesChange > 10.0 -> "बिक्री बढ़ रही है—तेज बिकने वाले डिजाइन का स्टॉक और सप्लायर लीड-टाइम जाँचें।"; else -> "बिक्री लगभग स्थिर है—मार्जिन और स्टॉक उपलब्धता पर ध्यान दें।" }
                        addPriority("बिक्री का रुझान / Sales trend", "$trend\nसुझाव: $advice", { showBusinessTrends() }, if (!salesChange.isNaN() && salesChange < -10) 0xFFC74646.toInt() else 0xFF168447.toInt())
                    }
                    val urgentStock = stock.filter { it.soldRecent30Days > 0.0 && it.item.quantity < it.soldRecent30Days + it.item.minimumStockQuantity }
                        .sortedByDescending { (it.soldRecent30Days + it.item.minimumStockQuantity - it.item.quantity).coerceAtLeast(0.0) }
                    if (urgentStock.isNotEmpty()) {
                        val top = urgentStock.take(5).joinToString("\n") { "• ${it.item.name} (${it.item.itemCode}): स्टॉक ${it.item.quantity}, हालिया 30-दिन बिक्री ${it.soldRecent30Days}, न्यूनतम ${it.item.minimumStockQuantity}" }
                        addPriority("स्टॉक प्राथमिकता / Stock to review", "${urgentStock.size} डिजाइन में स्टॉक हालिया मांग + न्यूनतम सीमा से कम है।\n$top\nसुझाव: खरीद मात्रा और सप्लायर उपलब्धता स्वयं जाँचें।", { showAiDemandForecast() }, 0xFFC74646.toInt())
                    }
                    val overdueCustomers = customers.filter { it.outstandingPaise > 0L }.sortedByDescending { it.outstandingPaise }
                    if (overdueCustomers.isNotEmpty()) {
                        val totalDue = overdueCustomers.sumOf { it.outstandingPaise }
                        val top = overdueCustomers.take(5).joinToString("\n") { "• ${it.customerName}: ${rupees(it.outstandingPaise)}" }
                        addPriority("ग्राहक बकाया / Customer receivables", "कुल दर्ज बकाया ${rupees(totalDue)} (${overdueCustomers.size} ग्राहक)।\n$top\nसुझाव: बिल और प्राप्ति मिलान करके विनम्र भुगतान फॉलो-अप करें।", { showCustomerInsights() }, 0xFF168447.toInt())
                    }
                    val supplierDue = suppliers.filter { it.outstandingPaise > 0L }.sortedByDescending { it.outstandingPaise }
                    if (supplierDue.isNotEmpty()) {
                        val totalDue = supplierDue.sumOf { it.outstandingPaise }
                        val top = supplierDue.take(5).joinToString("\n") { "• ${it.supplierName}: ${rupees(it.outstandingPaise)}" }
                        addPriority("सप्लायर भुगतान / Supplier payables", "कुल दर्ज बकाया ${rupees(totalDue)} (${supplierDue.size} सप्लायर)।\n$top\nसुझाव: due date, उपलब्ध कैश/बैंक और बिल मिलान देखकर भुगतान प्राथमिकता तय करें।", { showLedgerPicker() }, 0xFFC74646.toInt())
                    }
                    val increased = rates.filter { it.latestRatePaise > it.previousRatePaise }.sortedByDescending { it.latestRatePaise - it.previousRatePaise }
                    if (increased.isNotEmpty()) {
                        val top = increased.take(5).joinToString("\n") { row ->
                            val pct = if (row.previousRatePaise > 0L) (row.latestRatePaise - row.previousRatePaise).toDouble() * 100.0 / row.previousRatePaise else 0.0
                            "• ${row.itemName} (${row.itemCode}), ${row.supplierName}: ${String.format(java.util.Locale.US, "%.1f", pct)}% बढ़ोतरी"
                        }
                        addPriority("खरीद रेट की समीक्षा / Supplier rate review", "पिछली तुलना में खरीद दर बढ़े हुए ${increased.size} सप्लायर-डिजाइन रिकॉर्ड।\n$top\nसुझाव: अगली खरीद से पहले पुराने बिल और वैकल्पिक सप्लायर का रेट जाँचें।", { showSupplierInsights() })
                    }
                    if (summary.childCount == 0) addPriority("कोई तात्कालिक संकेत नहीं / No urgent signal", "उपलब्ध रिकॉर्ड में अभी ऊपर दिए गए मानदंडों के अनुसार कोई प्राथमिकता नहीं मिली। नियमित एंट्री जारी रखें।")
                }
                addPriority("महत्वपूर्ण सीमा / Important limitation", "यह नियम-आधारित निर्णय सहायक है, कोई गारंटीशुदा भविष्यवाणी नहीं। बिक्री अवधि, वर्तमान Item Master दर, बकाया और उपलब्ध दर्ज रिकॉर्ड पर निर्भर है। बैंक/Cashbook का वास्तविक बैलेंस अलग स्क्रीन में जाँचें।")
            } catch (e: Exception) {
                summary.removeAllViews()
                summary.addView(emptyLabel(e.message ?: "निर्णय सुझाव लोड नहीं हो सके।"))
            }
        }
    }

    private fun showAllTransactions() {
        val page = base("ALL TRANSACTIONS")
        addAction(page, "SALE") { showInvoices("SALE") }
        addAction(page, "PURCHASE") { showInvoices("PURCHASE") }
        addAction(page, "PAYMENT IN") { showPayments("IN") }
        addAction(page, "PAYMENT OUT") { showPayments("OUT") }
        addAction(page, "EXPENSES") { showExpenses() }
    }

    private fun showMenu() {
        val page = base("MENU / मेन्यू")
        fun group(title: String, entries: List<Pair<String, () -> Unit>>) {
            val card = sectionCard(title)
            entries.forEach { (label, action) -> card.addView(menuRow(label, action)) }
            page.addView(card)
        }
        group("My Business / मेरा व्यवसाय", listOf("Sale / बिक्री" to { showInvoices("SALE") }, "Purchase / खरीद" to { showInvoices("PURCHASE") }, "Expenses / खर्च" to { showExpenses() }, "Other Income / अन्य आय" to { showPayments("IN") }, "Reports / रिपोर्ट" to { showReports() }, "Business Trends / कारोबार के ग्राफ" to { showBusinessTrends() }))
        group("Parties & Accounts / पार्टियाँ और खाते", listOf("Customers / Suppliers / ग्राहक और सप्लायर" to { showParties() }, "Party Ledger / पार्टी खाता" to { showLedgerPicker() }, "Day Book / रोज़नामचा" to { showDayBook() }, "Bank / बैंक" to { showBankManagement() }, "Bank Accounts / बैंक खाते" to { showBankAccounts() }))
        group("Staff & Payroll / कर्मचारी और वेतन", listOf("Employees / Salary Ledger / कर्मचारी और वेतन खाता" to { showEmployees() }))
        group("Inventory Intelligence / स्टॉक विश्लेषण", listOf("Smart Stock / Reorder Suggestions / स्मार्ट स्टॉक" to { showSmartStock() }, "Per-Suit Profit & Dead Stock / प्रति सूट मुनाफा और रुका स्टॉक" to { showProfitAndDeadStock() }, "Supplier Purchase Insights / सप्लायर खरीद रिपोर्ट" to { showSupplierInsights() }, "AI Decision Assistant / AI निर्णय सहायक" to { showAiDecisionAssistant() }, "AI Business Intelligence / AI कारोबार विश्लेषण" to { showAiBusinessIntelligence() }, "AI Demand Forecast / मांग का अनुमान" to { showAiDemandForecast() }, "AI Profit & Cashflow Analysis / मुनाफा और नकदी विश्लेषण" to { showAiProfitCashflow() }, "Item Master / Design Codes / आइटम और डिजाइन कोड" to { showItems() }))
        group("Customer Intelligence / ग्राहक जानकारी", listOf("Customer Insights / ग्राहक विश्लेषण" to { showCustomerInsights() }))
        group("Utilities / उपयोगिताएँ", listOf("GST Settings / GST सेटिंग्स" to { showGstSettings() }, "Voice Input Language: English" to { showVoiceInputLanguageSettings() }, "Backup / Restore / बैकअप" to { showBackupMenu() }, "Display Language: English" to { AlertDialog.Builder(this).setTitle("Display Language").setMessage("English is the only display language.").setPositiveButton("OK", null).show() }))
    }

    private fun showVoiceInputLanguageSettings() {
        val prefs = getSharedPreferences("bfp_settings", MODE_PRIVATE)
        val current = prefs.getString("voice_input_language", "en-IN") ?: "en-IN"
        val selected = if (current == "hi-IN") 0 else 1
        AlertDialog.Builder(this)
            .setTitle("Voice Input Language / वॉइस भाषा")
            .setSingleChoiceItems(arrayOf("English (Default)", "हिंदी / Hindi"), selected) { dialog, which ->
                val locale = if (which == 1) "hi-IN" else "en-IN"
                prefs.edit().putString("voice_input_language", locale).apply()
                dialog.dismiss()
                toast(if (locale == "hi-IN") "Voice input: हिंदी" else "Voice input: English")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showEmployees() {
        val page = base("EMPLOYEES / PAYROLL")
        addAction(page, "+ नया कर्मचारी जोड़ें", 0xFF2E8B57.toInt()) { employeeDialog(null) }
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        page.addView(list)
        lifecycleScope.launch {
            try {
                val employees = withContext(Dispatchers.IO) { db.dao().observeEmployees().first() }
                if (employees.isEmpty()) list.addView(emptyLabel("अभी कर्मचारी नहीं जोड़े गए हैं"))
                employees.forEach { employee ->
                    val row = card("${employee.name}${if (employee.active) "" else " (Inactive)"}\nमोबाइल: ${employee.mobile.ifBlank { "—" }}\nमासिक वेतन: ₹${employee.monthlySalaryPaise / 100.0}\nटैप करें: Salary Ledger / Edit")
                    row.setOnClickListener {
                        val options = if (employee.active) arrayOf("Salary Ledger", "Edit Employee", "Deactivate") else arrayOf("Salary Ledger", "Edit Employee", "Activate")
                        AlertDialog.Builder(this@MainActivity).setTitle(employee.name).setItems(options) { _, which ->
                            when (which) {
                                0 -> showEmployeeLedger(employee)
                                1 -> employeeDialog(employee)
                                2 -> lifecycleScope.launch(Dispatchers.IO) {
                                    try {
                                        EmployeePayrollRepository(db).saveEmployee(employee.copy(active = !employee.active))
                                        withContext(Dispatchers.Main) { showEmployees() }
                                    } catch (e: Exception) { withContext(Dispatchers.Main) { toast(e.message ?: "कर्मचारी अपडेट नहीं हुआ") } }
                                }
                            }
                        }.show()
                    }
                    list.addView(row)
                }
            } catch (e: Exception) { toast(e.message ?: "कर्मचारी सूची नहीं खुली") }
        }
    }

    private fun employeeDialog(existing: EmployeeEntity?) {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(8), dp(18), 0) }
        val name = editField("कर्मचारी का नाम", existing?.name.orEmpty())
        val mobile = editField("मोबाइल नंबर (वैकल्पिक)", existing?.mobile.orEmpty())
        val salary = editField("मासिक वेतन (₹)", (existing?.monthlySalaryPaise?.div(100.0) ?: 0.0).toString())
        val note = editField("नोट (वैकल्पिक)", existing?.note.orEmpty())
        listOf(name, mobile, salary, note).forEach { box.addView(it) }
        AlertDialog.Builder(this).setTitle(if (existing == null) "नया कर्मचारी" else "कर्मचारी एडिट")
            .setView(box).setNegativeButton("रद्द", null).setPositiveButton("सेव", null).create().also { dialog ->
                dialog.setOnShowListener {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    val n = name.text.toString().trim()
                    val monthly = salary.text.toString().toDoubleOrNull()
                    if (n.isBlank() || monthly == null || !monthly.isFinite() || monthly < 0 || monthly * 100.0 > Long.MAX_VALUE.toDouble()) {
                        toast("कर्मचारी का नाम और सही मासिक वेतन दर्ज करें"); return@setOnClickListener
                    }
                    val employee = EmployeeEntity(
                        id = existing?.id ?: 0L, name = n, mobile = mobile.text.toString().trim(),
                        monthlySalaryPaise = kotlin.math.round(monthly * 100.0).toLong(),
                        joiningDateEpochMillis = existing?.joiningDateEpochMillis ?: System.currentTimeMillis(),
                        active = existing?.active ?: true, note = note.text.toString().trim()
                    )
                    lifecycleScope.launch {
                        try {
                            withContext(Dispatchers.IO) { EmployeePayrollRepository(db).saveEmployee(employee) }
                            dialog.dismiss(); showEmployees(); toast("कर्मचारी सेव हो गया")
                        } catch (e: Exception) { toast(e.message ?: "कर्मचारी सेव नहीं हुआ") }
                    }
                } }
                dialog.show()
            }
    }

    private fun showEmployeeLedger(employee: EmployeeEntity) {
        val page = base("SALARY LEDGER — ${employee.name}")
        addAction(page, "+ महीने का वेतन दर्ज करें", 0xFF2E8B57.toInt()) { salaryMonthDialog(employee, null) }
        addAction(page, "← Employees") { showEmployees() }
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        page.addView(list)
        lifecycleScope.launch {
            try {
                val months = withContext(Dispatchers.IO) { db.dao().observeSalaryMonths(employee.id).first() }
                if (months.isEmpty()) list.addView(emptyLabel("अभी वेतन महीना दर्ज नहीं है। पहले महीने का वेतन बनाएं।"))
                months.forEach { month ->
                    val paid = withContext(Dispatchers.IO) { db.dao().sumSalaryPayments(month.id) }
                    val due = (month.payablePaise - paid).coerceAtLeast(0L)
                    val row = card("${month.monthKey}\nदेय वेतन: ₹${month.payablePaise / 100.0}\nदिया गया: ₹${paid / 100.0}   |   बाकी: ₹${due / 100.0}\nटैप करें: भुगतान / Edit")
                    row.setOnClickListener {
                        AlertDialog.Builder(this@MainActivity).setTitle("${employee.name} — ${month.monthKey}")
                            .setItems(arrayOf("वेतन भुगतान दर्ज करें", "भुगतान देखें", "महीने का वेतन एडिट")) { _, which ->
                                when (which) {
                                    0 -> salaryPaymentDialog(employee, month, null)
                                    1 -> showSalaryMonthPayments(employee, month)
                                    2 -> salaryMonthDialog(employee, month)
                                }
                            }.show()
                    }
                    list.addView(row)
                }
            } catch (e: Exception) { toast(e.message ?: "Salary Ledger नहीं खुला") }
        }
    }

    private fun salaryMonthDialog(employee: EmployeeEntity, existing: SalaryMonthEntity?) {
        val currentMonth = java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.US).format(java.util.Date())
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(8), dp(18), 0) }
        val month = editField("महीना (YYYY-MM)", existing?.monthKey ?: currentMonth)
        val payable = editField("इस महीने का देय वेतन (₹)", ((existing?.payablePaise ?: employee.monthlySalaryPaise) / 100.0).toString())
        val note = editField("नोट", existing?.note.orEmpty())
        listOf(month, payable, note).forEach { box.addView(it) }
        AlertDialog.Builder(this).setTitle("${employee.name} — Monthly Salary")
            .setView(box).setNegativeButton("रद्द", null).setPositiveButton("सेव", null).create().also { dialog ->
                dialog.setOnShowListener {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    val amount = payable.text.toString().toDoubleOrNull()
                    if (amount == null || !amount.isFinite() || amount < 0 || amount * 100.0 > Long.MAX_VALUE.toDouble()) { toast("वेतन राशि सही दर्ज करें"); return@setOnClickListener }
                    lifecycleScope.launch {
                        try {
                            withContext(Dispatchers.IO) { EmployeePayrollRepository(db).saveSalaryMonth(employee.id, month.text.toString().trim(), kotlin.math.round(amount * 100.0).toLong(), note.text.toString()) }
                            dialog.dismiss(); showEmployeeLedger(employee); toast("महीने का वेतन सेव हो गया")
                        } catch (e: Exception) { toast(e.message ?: "महीने का वेतन सेव नहीं हुआ") }
                    }
                } }
                dialog.show()
            }
    }

    private fun salaryPaymentDialog(employee: EmployeeEntity, month: SalaryMonthEntity, existing: SalaryPaymentEntity?) {
        lifecycleScope.launch {
            val bankAccounts = withContext(Dispatchers.IO) { db.dao().observeBankAccounts().first() }
            val box = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(8), dp(18), 0) }
            val amount = editField("भुगतान राशि (₹)", (existing?.amountPaise?.div(100.0) ?: ((month.payablePaise - withContext(Dispatchers.IO) { db.dao().sumSalaryPayments(month.id) }).coerceAtLeast(0L) / 100.0)).toString())
            val mode = Spinner(this@MainActivity).apply { adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("CASH", "UPI", "BANK")); setSelection(listOf("CASH", "UPI", "BANK").indexOf(existing?.mode ?: "CASH").coerceAtLeast(0)) }
            val bank = Spinner(this@MainActivity).apply { adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, bankAccounts.map { it.accountName }); setSelection(bankAccounts.indexOfFirst { it.id == existing?.bankAccountId }.coerceAtLeast(0)); visibility = if (mode.selectedItem.toString() == "BANK") View.VISIBLE else View.GONE }
            mode.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) { bank.visibility = if (mode.selectedItem.toString() == "BANK") View.VISIBLE else View.GONE }
            }
            val note = editField("नोट", existing?.note.orEmpty())
            box.addView(amount); box.addView(TextView(this@MainActivity).apply { text = "भुगतान माध्यम" }); box.addView(mode)
            box.addView(TextView(this@MainActivity).apply { text = "बैंक अकाउंट" }); box.addView(bank); box.addView(note)
            val dialog = AlertDialog.Builder(this@MainActivity).setTitle("${employee.name} — ${month.monthKey} वेतन भुगतान").setView(box).setNegativeButton("रद्द", null).setPositiveButton("सेव", null).create()
            dialog.setOnShowListener {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val value = amount.text.toString().toDoubleOrNull()
                if (value == null || value <= 0 || !value.isFinite() || value * 100.0 > Long.MAX_VALUE.toDouble()) { toast("भुगतान राशि सही दर्ज करें"); return@setOnClickListener }
                val selectedMode = mode.selectedItem.toString()
                if (selectedMode == "BANK" && bankAccounts.isEmpty()) { toast("पहले Bank Accounts में बैंक जोड़ें"); return@setOnClickListener }
                val input = EmployeePayrollRepository.PaymentInput(
                    salaryMonthId = month.id, employeeId = employee.id,
                    amountPaise = kotlin.math.round(value * 100.0).toLong(),
                    dateEpochMillis = existing?.dateEpochMillis ?: System.currentTimeMillis(),
                    mode = selectedMode, bankAccountId = if (selectedMode == "BANK") bankAccounts.getOrNull(bank.selectedItemPosition)?.id else null,
                    note = note.text.toString().trim()
                )
                lifecycleScope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            val repo = EmployeePayrollRepository(db)
                            if (existing == null) repo.recordPayment(input) else repo.updatePayment(existing.id, input)
                        }
                        dialog.dismiss(); showEmployeeLedger(employee); toast("वेतन भुगतान सेव हो गया")
                    } catch (e: Exception) { toast(e.message ?: "वेतन भुगतान सेव नहीं हुआ") }
                }
            } }
            dialog.show()
        }
    }

    private fun showSalaryMonthPayments(employee: EmployeeEntity, month: SalaryMonthEntity) {
        lifecycleScope.launch {
            val payments = withContext(Dispatchers.IO) { db.dao().getSalaryPayments(month.id) }
            val labels = payments.map { payment ->
                val date = java.text.SimpleDateFormat("dd-MM-yyyy", java.util.Locale.US).format(java.util.Date(payment.dateEpochMillis))
                "$date | ${payment.mode} | ₹${payment.amountPaise / 100.0} ${payment.note}"
            }.toTypedArray()
            if (payments.isEmpty()) { toast("इस महीने कोई भुगतान नहीं है"); return@launch }
            AlertDialog.Builder(this@MainActivity).setTitle("${employee.name} — ${month.monthKey}").setItems(labels) { _, index ->
                val payment = payments[index]
                AlertDialog.Builder(this@MainActivity).setItems(arrayOf("Edit", "Delete")) { _, action ->
                    if (action == 0) salaryPaymentDialog(employee, month, payment)
                    else AlertDialog.Builder(this@MainActivity).setMessage("यह वेतन भुगतान हट जाएगा और कैश/बैंक बैलेंस वापस अपडेट होगा।")
                        .setNegativeButton("रद्द", null).setPositiveButton("हटाएँ") { _, _ -> lifecycleScope.launch {
                            try { withContext(Dispatchers.IO) { EmployeePayrollRepository(db).deletePayment(payment.id) }; showEmployeeLedger(employee) }
                            catch (e: Exception) { toast(e.message ?: "भुगतान हटाया नहीं जा सका") }
                        } }.show()
                }.show()
            }.show()
        }
    }

    private fun showBackupMenu() {
        val page = base("BACKUP / RESTORE")
        val prefs = getSharedPreferences("bfp_auto_backup", MODE_PRIVATE)
        val folder = prefs.getString("tree_uri", null)
        addAction(page, "Manual Backup बनाएं") { createBackup.launch("BFP-Backup-${System.currentTimeMillis()}.zip") }
        addAction(page, "अभी Automatic Backup चलाएँ / Retry") {
            if (prefs.getString("tree_uri", null) == null) {
                toast("पहले Google Drive या Cloud Folder चुनें")
                chooseAutoBackupFolder.launch(null)
            } else {
                AutoBackupScheduler.runNow(this)
                toast("Backup अभी कतार में लगाया गया है। इंटरनेट और Folder उपलब्ध होने पर चलेगा।")
                showBackupMenu()
            }
        }
        addAction(page, "Backup Restore") { chooseBackup.launch(arrayOf("application/zip", "application/x-sqlite3", "application/octet-stream", "*/*")) }
        addAction(page, "Cloud Backup सूची / Manage Backups") { showCloudBackupManager() }
        addAction(page, "Storage Monitor / पुराने Backup की समीक्षा") { showBackupStorageMonitor() }
        addAction(page, if (folder == null) "Google Drive / Cloud Folder चुनें" else "Backup Folder बदलें") { chooseAutoBackupFolder.launch(null) }
        addAction(page, if (AutoBackupScheduler.isEnabled(this)) "Automatic Backup बंद करें" else "Automatic Backup चालू करें") {
            if (AutoBackupScheduler.isEnabled(this)) {
                AutoBackupScheduler.cancel(this)
                toast("Automatic Backup बंद कर दिया गया")
                showBackupMenu()
            } else if (folder == null) {
                toast("पहले Google Drive या अन्य Cloud Folder चुनें")
                chooseAutoBackupFolder.launch(null)
            } else {
                AutoBackupScheduler.schedule(this)
                toast("Automatic Backup रोज़ाना शेड्यूल हो गया")
                showBackupMenu()
            }
        }
        val lastSuccess = prefs.getLong("last_success_epoch", 0L)
        val lastText = if (lastSuccess > 0L) java.text.SimpleDateFormat("dd-MM-yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date(lastSuccess)) else "अभी तक कोई सफल ऑटो बैकअप नहीं"
        val failureAt = prefs.getLong("last_failure_epoch", 0L)
        val health = when {
            lastSuccess <= 0L && failureAt > 0L -> "Backup Health: ⚠ पिछला प्रयास असफल रहा"
            lastSuccess <= 0L -> "Backup Health: ⚠ अभी तक सफल बैकअप नहीं"
            System.currentTimeMillis() - lastSuccess > 48L * 60L * 60L * 1000L -> "Backup Health: ⚠ 48 घंटे से अधिक पुराना बैकअप"
            failureAt > lastSuccess -> "Backup Health: ⚠ आखिरी प्रयास में समस्या; पिछला सफल बैकअप उपलब्ध"
            else -> "Backup Health: ✓ हाल का सफल बैकअप उपलब्ध"
        }
        val status = "Backup Folder: ${if (folder == null) "चुना नहीं गया" else "सेट है"}\nAutomatic Backup: ${if (AutoBackupScheduler.isEnabled(this)) "चालू" else "बंद"}\nLast Automatic Backup: $lastText\nLast Status: ${prefs.getString("last_status", "कोई रिकॉर्ड नहीं") ?: "कोई रिकॉर्ड नहीं"}"
        page.addView(TextView(this).apply { text = status + "\n" + health + "\n\n" + BackupHistory.summary(this@MainActivity) + "\nनोट: Google Drive में बैकअप के लिए ऊपर Folder चुनें। बैकअप इंटरनेट/डिवाइस उपलब्धता के अनुसार रोज़ाना चलेगा।"; textSize = 14f; setTextColor(0xFF414657.toInt()); setPadding(dp(16), dp(12), dp(16), dp(12)) })
    }

    private fun showCloudBackupManager() {
        val folder = getSharedPreferences("bfp_auto_backup", MODE_PRIVATE).getString("tree_uri", null)
        if (folder == null) {
            AlertDialog.Builder(this).setTitle("Cloud Backup सूची").setMessage("पहले Google Drive या Cloud Folder चुनें।")
                .setPositiveButton("Folder चुनें") { _, _ -> chooseAutoBackupFolder.launch(null) }
                .setNegativeButton("बंद", null).show()
            return
        }
        io.execute {
            val backups = runCatching { CloudBackupManager.list(this) }.getOrElse { emptyList() }
            runOnUiThread {
                if (backups.isEmpty()) {
                    AlertDialog.Builder(this).setTitle("Cloud Backup सूची").setMessage("इस फोल्डर में कोई पहचानी गई BFP ZIP Backup फाइल नहीं मिली।")
                        .setPositiveButton("ठीक", null).show()
                    return@runOnUiThread
                }
                val labels = backups.map { it.displayLine() }.toTypedArray()
                AlertDialog.Builder(this).setTitle("Cloud Backups (${backups.size})")
                    .setItems(labels) { _, index ->
                        val selected = backups[index]
                        val size = selected.displayLine()
                        AlertDialog.Builder(this).setTitle("Backup हटाने की पुष्टि")
                            .setMessage("क्या आप यह बैकअप Cloud Folder से स्थायी रूप से हटाना चाहते हैं?\n\n$size\n\nकम-से-कम एक BFP Backup हमेशा सुरक्षित रखा जाएगा।")
                            .setNegativeButton("रद्द", null)
                            .setPositiveButton("हटाएँ") { _, _ -> io.execute {
                                try {
                                    val deleted = CloudBackupManager.delete(this, selected.uri, userConfirmed = true)
                                    BackupHistory.record(this, "Cloud Cleanup", "सफल", deleted)
                                    runOnUiThread { toast("Backup हटाया गया: $deleted"); showCloudBackupManager() }
                                } catch (e: Exception) {
                                    runOnUiThread { AlertDialog.Builder(this).setMessage("Backup नहीं हटाया गया: ${e.message}").setPositiveButton("ठीक", null).show() }
                                }
                            } }.show()
                    }.setNegativeButton("बंद", null).show()
            }
        }
    }

    private fun showBackupStorageMonitor() {
        val folder = getSharedPreferences("bfp_auto_backup", MODE_PRIVATE).getString("tree_uri", null)
        if (folder == null) {
            AlertDialog.Builder(this).setTitle("Backup Storage Monitor")
                .setMessage("पहले Google Drive या Cloud Folder चुनें।")
                .setPositiveButton("Folder चुनें") { _, _ -> chooseAutoBackupFolder.launch(null) }
                .setNegativeButton("बंद", null).show()
            return
        }
        io.execute {
            val summary = runCatching { CloudBackupManager.storageSummary(this) }.getOrElse { error ->
                runOnUiThread { AlertDialog.Builder(this).setTitle("Storage Monitor").setMessage("Backup सूची नहीं पढ़ी जा सकी: ${error.message}").setPositiveButton("ठीक", null).show() }
                return@execute
            }
            runOnUiThread {
                val dateFormat = java.text.SimpleDateFormat("dd-MM-yyyy HH:mm", java.util.Locale.getDefault())
                val oldest = if (summary.oldestBackupAt > 0L) dateFormat.format(java.util.Date(summary.oldestBackupAt)) else "—"
                val newest = if (summary.newestBackupAt > 0L) dateFormat.format(java.util.Date(summary.newestBackupAt)) else "—"
                val message = "पहचाने गए BFP Backups: ${summary.backupCount}" +
                    "\nइनका कुल आकार: ${CloudBackupManager.formatBytes(summary.totalBytes)}" +
                    "\nसबसे पुराना: $oldest\nसबसे नया: $newest" +
                    "\n\nRetention सुझाव: नवीनतम ${summary.keepCount} बैकअप रखें।" +
                    "\nसमीक्षा योग्य पुराने बैकअप: ${summary.cleanupCandidates.size}" +
                    "\nसंभावित जगह: ${CloudBackupManager.formatBytes(summary.candidateBytes)}" +
                    "\n\nनोट: यह केवल पहचानी गई BFP ZIP फाइलों का आकार है, पूरे Google Drive की खाली जगह नहीं। कोई फाइल अपने-आप नहीं हटेगी।"
                val builder = AlertDialog.Builder(this).setTitle("Backup Storage Monitor").setMessage(message).setPositiveButton("ठीक", null)
                if (summary.cleanupCandidates.isNotEmpty()) builder.setNeutralButton("पुराने Backup देखें") { _, _ ->
                    val candidates = summary.cleanupCandidates
                    val labels = candidates.map { it.displayLine() }.toTypedArray()
                    AlertDialog.Builder(this).setTitle("समीक्षा योग्य Backup (${candidates.size})")
                        .setItems(labels) { _, index ->
                            val selected = candidates[index]
                            AlertDialog.Builder(this).setTitle("पुराना Backup हटाएँ?")
                                .setMessage("यह फाइल स्थायी रूप से हटेगी। पहले सुनिश्चित करें कि आपके पास नया और वैध बैकअप मौजूद है।\n\n${selected.displayLine()}")
                                .setNegativeButton("रद्द", null)
                                .setPositiveButton("हटाएँ") { _, _ -> io.execute {
                                    try {
                                        val deleted = CloudBackupManager.delete(this, selected.uri, userConfirmed = true)
                                        BackupHistory.record(this, "Retention Cleanup", "सफल", deleted)
                                        runOnUiThread { toast("पुराना Backup हटाया गया: $deleted"); showBackupStorageMonitor() }
                                    } catch (e: Exception) {
                                        runOnUiThread { AlertDialog.Builder(this).setMessage("Backup नहीं हटाया गया: ${e.message}").setPositiveButton("ठीक", null).show() }
                                    }
                                } }.show()
                        }.setNegativeButton("बंद", null).show()
                }
                builder.show()
            }
        }
    }

    private fun sectionCard(title: String) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(14), dp(16), dp(14))
        background = rounded(0xFFFFFFFF.toInt(), 18); elevation = dp(2).toFloat()
        addView(TextView(this@MainActivity).apply { text = title; textSize = 18f; typeface = android.graphics.Typeface.DEFAULT_BOLD; setTextColor(0xFF34394B.toInt()) })
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) }
    }

    private fun actionChip(label: String, action: () -> Unit) = TextView(this).apply {
        text = label; textSize = 14f; typeface = android.graphics.Typeface.DEFAULT_BOLD; setTextColor(0xFF0879C9.toInt())
        setPadding(0, dp(8), 0, dp(4)); setOnClickListener { action() }
    }

    private fun menuRow(label: String, action: () -> Unit) = TextView(this).apply {
        text = "◇   $label                              ›"; textSize = 16f; setTextColor(0xFF414657.toInt())
        setPadding(0, dp(15), 0, dp(15)); setOnClickListener { action() }
        background = android.graphics.drawable.ColorDrawable(0xFFFFFFFF.toInt())
    }


    private fun showBankAccounts() {
        val page = base("BANK ACCOUNTS")
        addAction(page, "+ बैंक अकाउंट का नाम जोड़ें", 0xFF0879C9.toInt()) { bankAccountDialog(null) }
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        page.addView(list)
        lifecycleScope.launch {
            db.dao().observeBankAccounts().collectLatest { accounts ->
                list.removeAllViews()
                if (accounts.isEmpty()) list.addView(sectionCard("अभी कोई बैंक अकाउंट नहीं जोड़ा गया है"))
                accounts.forEach { account ->
                    val row = card("🏦  ${account.accountName}\nटैप करके Edit या Delete करें")
                    row.setOnClickListener {
                        AlertDialog.Builder(this@MainActivity).setTitle(account.accountName)
                            .setItems(arrayOf("Edit", "Delete")) { _, which ->
                                if (which == 0) bankAccountDialog(account)
                                else AlertDialog.Builder(this@MainActivity).setMessage("${account.accountName} हटाएँ?")
                                    .setNegativeButton("रद्द", null).setPositiveButton("Delete") { _, _ -> lifecycleScope.launch {
                                        val used = withContext(Dispatchers.IO) { db.dao().countBankAccountUsage(account.id) }
                                        if (used > 0) toast("इस बैंक अकाउंट से $used एंट्री जुड़ी हैं। पहले उन एंट्री को बदलें।")
                                        else withContext(Dispatchers.IO) { db.dao().deleteBankAccount(account) }
                                    } }.show()
                            }.show()
                    }
                    list.addView(row)
                }
            }
        }
    }

    private fun bankAccountDialog(existing: BankAccountEntity?) {
        val name = editField("बैंक अकाउंट का नाम (जैसे SBI / HDFC Bank)", existing?.accountName.orEmpty())
        AlertDialog.Builder(this).setTitle(if (existing == null) "नया बैंक अकाउंट" else "बैंक नाम बदलें")
            .setView(name).setNegativeButton("रद्द", null).setPositiveButton("Save", null).create().also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val value = name.text.toString().trim()
                        if (value.isBlank()) { name.error = "बैंक का नाम लिखें"; return@setOnClickListener }
                        lifecycleScope.launch {
                            try {
                                val duplicate = withContext(Dispatchers.IO) { db.dao().countBankAccountName(value, existing?.id ?: 0L) }
                                if (duplicate > 0) { name.error = "यह बैंक नाम पहले से मौजूद है"; return@launch }
                                withContext(Dispatchers.IO) {
                                    if (existing == null) db.dao().insertBankAccount(BankAccountEntity(accountName = value))
                                    else db.dao().updateBankAccount(existing.copy(accountName = value))
                                }
                                dialog.dismiss()
                            } catch (e: Exception) { toast(e.message ?: "बैंक नाम सेव नहीं हुआ") }
                        }
                    }
                }
                dialog.show()
            }
    }

    private fun showLedgerPicker() {
        lifecycleScope.launch {
            val parties = withContext(Dispatchers.IO) { db.dao().observeParties().first() }
            if (parties.isEmpty()) { toast("पहले पार्टी जोड़ें"); return@launch }
            AlertDialog.Builder(this@MainActivity).setTitle("किस पार्टी का खाता देखें?")
                .setItems(parties.map { it.name }.toTypedArray()) { _, which -> showPartyLedger(parties[which]) }.show()
        }
    }

    private fun showPartyLedger(party: PartyEntity) {
        val page = base("Party Details")
        val profile = sectionCard(party.name)
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        top.addView(TextView(this).apply {
            text = if (party.mobile.isBlank()) "Add Phone" else party.mobile
            textSize = 15f; typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(0xFF0879C9.toInt()); setOnClickListener { partyDialog(party) }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        top.addView(headerIcon("✎") { partyDialog(party) })
        top.addView(headerIcon("⋮") {
            AlertDialog.Builder(this).setItems(arrayOf("Edit Party", "Delete Party")) { _, which ->
                if (which == 0) partyDialog(party) else confirmDeleteParty(party)
            }.show()
        })
        profile.addView(top)
        val balanceLabel = TextView(this).apply {
            text = "Balance: calculating…"; textSize = 17f
            typeface = android.graphics.Typeface.DEFAULT_BOLD; setTextColor(0xFF303648.toInt())
            setPadding(0, dp(10), 0, dp(4))
        }
        profile.addView(balanceLabel)
        val creditLimitPrefs = getSharedPreferences("bfp_party_credit_limits", MODE_PRIVATE)
        val creditLimitLabel = TextView(this).apply {
            textSize = 13f; setTextColor(0xFF0879C9.toInt())
            setPadding(0, dp(4), 0, dp(4))
            setOnClickListener {
                val current = creditLimitPrefs.getLong(party.id.toString(), 0L)
                val field = editField("Credit Limit (₹)", if (current > 0L) (current / 100.0).toString() else "")
                field.inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
                AlertDialog.Builder(this@MainActivity).setTitle("Set Credit Limit")
                    .setView(field).setNegativeButton("Cancel", null)
                    .setNeutralButton("Clear", null).setPositiveButton("Save", null).create().also { dlg ->
                        dlg.setOnShowListener {
                            dlg.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                                creditLimitPrefs.edit().remove(party.id.toString()).apply()
                                text = "Credit Limit: Not Set"; dlg.dismiss()
                            }
                            dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                                val parsed = field.text.toString().trim().toDoubleOrNull()
                                if (parsed == null || parsed < 0.0) { field.error = "सही रकम दर्ज करें"; return@setOnClickListener }
                                val paise = (parsed * 100.0).toLong()
                                if (paise == 0L) creditLimitPrefs.edit().remove(party.id.toString()).apply()
                                else creditLimitPrefs.edit().putLong(party.id.toString(), paise).apply()
                                text = if (paise == 0L) "Credit Limit: Not Set" else "Credit Limit: ₹${paise / 100.0}"
                                dlg.dismiss()
                            }
                        }
                        dlg.show()
                    }
            }
        }
        fun refreshCreditLimit() {
            val value = creditLimitPrefs.getLong(party.id.toString(), 0L)
            creditLimitLabel.text = if (value > 0L) "Credit Limit: ₹${value / 100.0}  •  Edit" else "Credit Limit: Not Set  •  Set"
        }
        refreshCreditLimit()
        profile.addView(creditLimitLabel)
        val statement = Button(this).apply {
            text = "Send Statement"; isAllCaps = false
            setOnClickListener {
                lifecycleScope.launch {
                    try {
                        val rows = withContext(Dispatchers.IO) { LedgerRepository(db).partyLedger(party.id) }
                        val body = buildString {
                            append("BFP Business Pro — Party Statement\n")
                            append("Party: ${party.name}\nPhone: ${party.mobile}\n\n")
                            rows.forEach { row ->
                                val date = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale("en", "IN")).format(java.util.Date(row.dateEpochMillis))
                                append("$date | ${row.reference} | ${row.description} | Debit ₹${row.debitPaise / 100.0} | Credit ₹${row.creditPaise / 100.0} | Balance ₹${kotlin.math.abs(row.balancePaise) / 100.0}\n")
                            }
                        }
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"; putExtra(Intent.EXTRA_SUBJECT, "Statement - ${party.name}")
                            putExtra(Intent.EXTRA_TEXT, body)
                        }
                        startActivity(Intent.createChooser(send, "Send Statement"))
                    } catch (e: Exception) { toast(e.message ?: "Statement share नहीं हो सका") }
                }
            }
        }
        profile.addView(statement)
        page.addView(profile)

        val search = EditText(this).apply {
            hint = "Search Transactions"; setSingleLine(true)
            setPadding(dp(14), dp(8), dp(14), dp(8)); background = rounded(0xFFFFFFFF.toInt(), 12)
        }
        page.addView(search, LinearLayout.LayoutParams(-1, dp(50)).apply { topMargin = dp(10); bottomMargin = dp(8) })
        val transactions = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        page.addView(transactions)
        var ledgerRows: List<LedgerRepository.LedgerRow> = emptyList()
        var partyInvoices: List<InvoiceEntity> = emptyList()
        fun renderTransactions() {
            transactions.removeAllViews()
            val query = search.text.toString().trim().lowercase()
            val filtered = ledgerRows.filter { query.isBlank() || "${it.reference} ${it.description}".lowercase().contains(query) }
            if (filtered.isEmpty()) transactions.addView(emptyLabel(if (ledgerRows.isEmpty()) "No transactions yet" else "No matching transactions"))
            filtered.asReversed().forEach { row ->
                val date = java.text.SimpleDateFormat("dd MMM yy • hh:mm a", java.util.Locale("en", "IN")).format(java.util.Date(row.dateEpochMillis))
                val amount = if (row.debitPaise > 0) row.debitPaise else row.creditPaise
                val paid = row.balancePaise == 0L
                val card = sectionCard(row.description.ifBlank { "Transaction" })
                card.addView(TextView(this).apply {
                    text = "${row.reference}     $date"; textSize = 12f; setTextColor(0xFF85899B.toInt())
                    setPadding(0, dp(4), 0, dp(6))
                })
                card.addView(TextView(this).apply {
                    text = (if (paid) "PAID" else "BALANCE DUE") + "     Total: ₹${kotlin.math.abs(amount) / 100.0}     Balance: ₹${kotlin.math.abs(row.balancePaise) / 100.0}"
                    textSize = 14f; typeface = android.graphics.Typeface.DEFAULT_BOLD
                    setTextColor(if (paid) 0xFF159B72.toInt() else 0xFF303648.toInt())
                })
                val invoice = partyInvoices.firstOrNull { it.invoiceNumber == row.reference }
                if (invoice != null) {
                    card.setOnClickListener {
                        AlertDialog.Builder(this@MainActivity).setTitle(invoice.invoiceNumber)
                            .setItems(arrayOf("PDF / Print", "Share Details")) { _, which ->
                                if (which == 0) lifecycleScope.launch { printInvoice(invoice) }
                                else {
                                    val shareText = "BFP Business Pro\n${party.name}\n${invoice.kind} ${invoice.invoiceNumber}\nTotal: ₹${invoice.totalPaise / 100.0}\nPaid: ₹${invoice.paidPaise / 100.0}\nBalance: ₹${(invoice.totalPaise - invoice.paidPaise) / 100.0}"
                                    val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, shareText) }
                                    startActivity(Intent.createChooser(send, "Share Transaction"))
                                }
                            }.show()
                    }
                }
                transactions.addView(card)
            }
        }
        search.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { renderTransactions() }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            setPadding(dp(3), dp(6), dp(3), dp(6)); setBackgroundColor(0xFFFFFFFF.toInt()); elevation = dp(7).toFloat()
        }
        fun partyAction(label: String, color: Int, action: () -> Unit) = Button(this).apply {
            text = label; isAllCaps = false; textSize = 12f; setTextColor(0xFFFFFFFF.toInt())
            backgroundTintList = android.content.res.ColorStateList.valueOf(color); setOnClickListener { action() }
        }
        val supplier = party.partyType == "SUPPLIER"
        actions.addView(partyAction(if (supplier) "Make Pay…" else "Take Pay…", 0xFF0879C9.toInt()) {
            paymentDialog(if (supplier) "OUT" else "IN", null, party.id)
        }, LinearLayout.LayoutParams(0, dp(48), 1f))
        actions.addView(partyAction("+", 0xFF0879C9.toInt()) {
            if (supplier) {
                AlertDialog.Builder(this).setTitle("Add Purchase")
                    .setItems(arrayOf("Item-wise Purchase — Add Stock", "Simple Purchase — No Stock")) { _, which ->
                        if (which == 0) invoiceDialog("PURCHASE", null, preselectedPartyId = party.id)
                        else simplePurchaseDialog(party.id)
                    }.show()
            } else invoiceDialog("SALE", null, preselectedPartyId = party.id)
        }, LinearLayout.LayoutParams(dp(58), dp(48)))
        actions.addView(partyAction(if (supplier) "Add Purchase" else "Add Sale", 0xFFE51D43.toInt()) {
            if (supplier) choosePurchaseEntryMode(party.id)
            else invoiceDialog("SALE", null, preselectedPartyId = party.id)
        }, LinearLayout.LayoutParams(0, dp(48), 1f))
        page.addView(actions)
        // Move party actions outside the scroll area so they remain pinned at the bottom.
        val partyScroll = page.parent as? ScrollView
        val partyShell = partyScroll?.parent as? LinearLayout
        if (partyScroll != null && partyShell != null) {
            page.removeView(actions)
            partyShell.addView(actions, LinearLayout.LayoutParams(-1, -2))
        }

        lifecycleScope.launch {
            try {
                val repository = LedgerRepository(db)
                val audit = withContext(Dispatchers.IO) { repository.auditPartyLedger(party.id) }
                ledgerRows = withContext(Dispatchers.IO) { repository.partyLedger(party.id) }
                partyInvoices = withContext(Dispatchers.IO) {
                    (db.dao().observeInvoices("SALE").first() + db.dao().observeInvoices("PURCHASE").first())
                        .filter { it.partyId == party.id }
                }
                val closing = ledgerRows.lastOrNull()?.balancePaise ?: 0L
                balanceLabel.text = "Balance: ₹${kotlin.math.abs(closing) / 100.0}  " + when {
                    closing == 0L -> "Clear"
                    closing > 0L -> if (supplier) "Advance / Credit" else "Due from Customer"
                    else -> if (supplier) "Payable to Supplier" else "Advance Received"
                }
                if (audit.issues.isNotEmpty()) transactions.addView(TextView(this@MainActivity).apply {
                    text = "Ledger check: ${audit.issues.size} issue(s) need review"
                    setTextColor(0xFFC74646.toInt()); textSize = 12f
                }, 0)
                renderTransactions()
            } catch (e: Exception) { toast(e.message ?: "Party Details लोड नहीं हुए") }
        }
    }


    private fun choosePurchaseEntryMode(partyId: Long) {
        AlertDialog.Builder(this).setTitle("Purchase Entry Type")
            .setItems(arrayOf("Simple Purchase — No Stock", "Item-wise Purchase — Add Stock")) { _, which ->
                if (which == 0) simplePurchaseDialog(partyId)
                else invoiceDialog("PURCHASE", null, preselectedPartyId = partyId)
            }.show()
    }

    /** Posts a supplier purchase total without creating item rows or changing inventory. */
    private fun simplePurchaseDialog(partyId: Long) {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), 0) }
        val amount = editField("Purchase Amount (₹)", "")
        amount.inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        val reference = editField("Supplier Bill / Reference (optional)", "")
        val note = editField("Description / Note (optional)", "Simple purchase — no stock")
        box.addView(TextView(this).apply { text = "यह एंट्री केवल सप्लायर का कुल अमाउंट दर्ज करेगी। कोई आइटम या स्टॉक नहीं बढ़ेगा।"; textSize = 14f; setTextColor(0xFF455A64.toInt()); setPadding(0, 0, 0, dp(8)) })
        box.addView(amount); box.addView(reference); box.addView(note)
        val dialog = AlertDialog.Builder(this).setTitle("Simple Purchase — No Stock")
            .setView(box).setNegativeButton("Cancel", null).setPositiveButton("Save Purchase", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val value = amount.text.toString().trim().toDoubleOrNull()
                if (value == null || value <= 0.0) { amount.error = "सही रकम दर्ज करें"; return@setOnClickListener }
                val refText = reference.text.toString().trim()
                val noteText = note.text.toString().trim()
                lifecycleScope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            val supplier = db.dao().getParty(partyId) ?: error("Supplier not found")
                            require(supplier.partyType == "SUPPLIER" || supplier.partyType == "BOTH") { "चुनी गई पार्टी सप्लायर नहीं है" }
                            val paise = (value * 100.0).toLong()
                            val number = refText.ifBlank { "SP-${System.currentTimeMillis()}" }
                            db.dao().insertInvoice(InvoiceEntity(
                                invoiceNumber = number, partyId = partyId, kind = "PURCHASE",
                                dateEpochMillis = System.currentTimeMillis(), subtotalPaise = paise,
                                totalPaise = paise, paidPaise = 0L,
                                note = listOf("SIMPLE_PURCHASE_NO_STOCK", noteText).filter { it.isNotBlank() }.joinToString(" | ")
                            ))
                        }
                        dialog.dismiss(); toast("Simple Purchase सेव हो गई — Stock में कोई बदलाव नहीं हुआ")
                        db.dao().getParty(partyId)?.let { showPartyLedger(it) }
                    } catch (e: Exception) { toast(e.message ?: "Purchase सेव नहीं हुई") }
                }
            }
        }
        dialog.show()
    }

    private fun showDayBook() {
        val page = base("DAY BOOK / दिनवार हिसाब")
        addAction(page, "← Dashboard") { showDashboard() }
        addAction(page, "GST / Tax Report — GST रिपोर्ट") { showGstReport() }
        addAction(page, "Complete Business Financial Audit / समग्र वित्तीय ऑडिट", 0xFF315B7D.toInt()) { showBusinessFinancialAudit() }
        val selectedDate = java.util.Calendar.getInstance()
        val dateLabel = TextView(this).apply {
            textSize = 16f; setTextColor(0xFF173B57.toInt()); gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(8))
        }
        page.addView(dateLabel)
        val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        page.addView(controls)
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        page.addView(list)

        fun dayBounds(calendar: java.util.Calendar): Pair<Long, Long> {
            val startCal = (calendar.clone() as java.util.Calendar).apply {
                set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
            }
            val endCal = (startCal.clone() as java.util.Calendar).apply { add(java.util.Calendar.DAY_OF_MONTH, 1); add(java.util.Calendar.MILLISECOND, -1) }
            return startCal.timeInMillis to endCal.timeInMillis
        }

        fun loadDay() {
            val dateFormat = java.text.SimpleDateFormat("dd-MM-yyyy", java.util.Locale("en", "IN"))
            dateLabel.text = "चुनी गई तारीख: ${dateFormat.format(selectedDate.time)}"
            list.removeAllViews()
            list.addView(emptyLabel("हिसाब लोड हो रहा है…"))
            lifecycleScope.launch {
                try {
                    val (start, end) = dayBounds(selectedDate)
                    val repo = LedgerRepository(db)
                    val summary = withContext(Dispatchers.IO) { repo.dayBook(start, end) }
                    val bankBalance = withContext(Dispatchers.IO) { repo.bankBalancePaise() }
                    val upiBalance = withContext(Dispatchers.IO) { repo.upiBalancePaise() }
                    list.removeAllViews()
                    list.addView(card("Cash Summary\nOpening Cash: ₹${summary.openingCashPaise / 100.0}\nCash In: ₹${summary.cashInPaise / 100.0}\nCash Out: ₹${summary.cashOutPaise / 100.0}\nClosing Cash: ₹${summary.closingCashPaise / 100.0}\nवर्तमान UPI Balance: ₹${upiBalance / 100.0}\nवर्तमान Bank Balance: ₹${bankBalance / 100.0}"))
                    if (summary.rows.isEmpty()) list.addView(emptyLabel("इस तारीख को कोई लेन-देन नहीं है"))
                    summary.rows.forEach { row ->
                        val time = java.text.SimpleDateFormat("hh:mm a", java.util.Locale("en", "IN")).format(java.util.Date(row.dateEpochMillis))
                        list.addView(card("$time  |  ${row.reference}\n${row.description} (${row.mode})\nDebit: ₹${row.debitPaise / 100.0}  |  Credit: ₹${row.creditPaise / 100.0}"))
                    }
                } catch (e: Exception) {
                    list.removeAllViews()
                    list.addView(emptyLabel(e.message ?: "Day Book लोड नहीं हुई"))
                }
            }
        }

        fun navButton(label: String, color: Int, action: () -> Unit) {
            controls.addView(Button(this).apply {
                text = label; isAllCaps = false; textSize = 12f
                backgroundTintList = android.content.res.ColorStateList.valueOf(color)
                setTextColor(0xFFFFFFFF.toInt()); setOnClickListener { action() }
            }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { setMargins(dp(2), dp(2), dp(2), dp(6)) })
        }
        navButton("◀ पिछला", 0xFF607D8B.toInt()) { selectedDate.add(java.util.Calendar.DAY_OF_MONTH, -1); loadDay() }
        navButton("आज", 0xFF2E8B57.toInt()) { selectedDate.timeInMillis = System.currentTimeMillis(); loadDay() }
        navButton("अगला ▶", 0xFF607D8B.toInt()) { selectedDate.add(java.util.Calendar.DAY_OF_MONTH, 1); loadDay() }
        addAction(page, "तारीख चुनें", 0xFF245A78.toInt()) {
            DatePickerDialog(this, { _, year, month, day ->
                selectedDate.set(java.util.Calendar.YEAR, year)
                selectedDate.set(java.util.Calendar.MONTH, month)
                selectedDate.set(java.util.Calendar.DAY_OF_MONTH, day)
                loadDay()
            }, selectedDate.get(java.util.Calendar.YEAR), selectedDate.get(java.util.Calendar.MONTH), selectedDate.get(java.util.Calendar.DAY_OF_MONTH)).show()
        }
        loadDay()
    }

    private fun showReports() {
        val page = base("REPORTS / बिजनेस रिपोर्ट")
        addAction(page, "← Dashboard") { showDashboard() }
        addAction(page, "Profit & Loss + Financial Position / लाभ-हानि और वित्तीय स्थिति") { showFinancialStatement() }
        // Dev96: one report hub linking the existing source-of-truth reports.
        page.addView(sectionCard("BUSINESS REPORTS / कारोबार की रिपोर्टें").apply {
            addView(emptyLabel("हर रिपोर्ट मूल बिल, भुगतान, स्टॉक और खर्च रिकॉर्ड से बनती है। रिपोर्ट खोलने से कोई अकाउंटिंग एंट्री नहीं बनती।"))
            addView(Button(this@MainActivity).apply { text = "GST & Tax Report / GST रिपोर्ट"; setOnClickListener { showGstReport() } })
            addView(Button(this@MainActivity).apply { text = "Stock Movement Report / स्टॉक रिपोर्ट"; setOnClickListener { showStockReport() } })
            addView(Button(this@MainActivity).apply { text = "Party Ledger / ग्राहक-सप्लायर खाता"; setOnClickListener { showLedgerPicker() } })
            addView(Button(this@MainActivity).apply { text = "Cash & Bank Reconciliation / कैश-बैंक मिलान"; setOnClickListener { showMoneyReconciliation() } })
            addView(Button(this@MainActivity).apply { text = "Complete Financial Audit / समग्र वित्तीय ऑडिट"; setOnClickListener { showBusinessFinancialAudit() } })
            addView(Button(this@MainActivity).apply { text = "Business Trends / कारोबार के ग्राफ"; setOnClickListener { showBusinessTrends() } })
            addView(Button(this@MainActivity).apply { text = "Day Book / रोज़नामचा"; setOnClickListener { showDayBook() } })
        })
        val rangeLabel = TextView(this).apply { textSize = 14f; setTextColor(0xFF173B57.toInt()); setPadding(0, dp(8), 0, dp(8)) }
        page.addView(rangeLabel)
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        page.addView(list)

        val calendar = java.util.Calendar.getInstance()
        val end = calendar.timeInMillis
        calendar.set(java.util.Calendar.DAY_OF_MONTH, 1)
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0); calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0); calendar.set(java.util.Calendar.MILLISECOND, 0)
        var from = calendar.timeInMillis
        var to = end

        fun dateText(value: Long): String = java.text.SimpleDateFormat("dd-MM-yyyy", java.util.Locale("en", "IN")).format(java.util.Date(value))
        fun load() {
            rangeLabel.text = "अवधि: ${dateText(from)} से ${dateText(to)}"
            lifecycleScope.launch {
                try {
                    val report = withContext(Dispatchers.IO) { ReportsRepository(db).period(from, to) }
                    list.removeAllViews()
                    list.addView(card("बिक्री: ₹${report.salePaise / 100.0}\nखरीद: ₹${report.purchasePaise / 100.0}"))
                    list.addView(card("Payment In\nCash: ₹${report.paymentIn.cashPaise / 100.0}  |  UPI: ₹${report.paymentIn.upiPaise / 100.0}  |  Bank: ₹${report.paymentIn.bankPaise / 100.0}\nकुल प्राप्त: ₹${report.paymentIn.totalPaise / 100.0}"))
                    list.addView(card("Payment Out\nCash: ₹${report.paymentOut.cashPaise / 100.0}  |  UPI: ₹${report.paymentOut.upiPaise / 100.0}  |  Bank: ₹${report.paymentOut.bankPaise / 100.0}\nकुल भुगतान: ₹${report.paymentOut.totalPaise / 100.0}"))
                    list.addView(card("खर्च\nCash: ₹${report.expenses.cashPaise / 100.0}  |  UPI: ₹${report.expenses.upiPaise / 100.0}  |  Bank: ₹${report.expenses.bankPaise / 100.0}\nकुल खर्च: ₹${report.expenses.totalPaise / 100.0}"))
                    list.addView(card("लेनदार / RECEIVABLES — रकम लेनी है: ₹${report.debtorPaise / 100.0}").apply {
                        setTextColor(0xFFFFFFFF.toInt()); background = rounded(0xFF168447.toInt(), 14)
                    })
                    list.addView(card("देनदारी / PAYABLES — रकम देनी है: ₹${report.creditorPaise / 100.0}").apply {
                        setTextColor(0xFFFFFFFF.toInt()); background = rounded(0xFFC93636.toInt(), 14)
                    })
                    list.addView(emptyLabel("रिपोर्ट मूल बिल, भुगतान और खर्च रिकॉर्ड से पढ़ी जाती है; अलग रिपोर्ट एंट्री नहीं बनती।"))
                } catch (e: Exception) { toast(e.message ?: "रिपोर्ट लोड नहीं हुई") }
            }
        }
        addAction(page, "शुरुआती तारीख चुनें") {
            val c = java.util.Calendar.getInstance().apply { timeInMillis = from }
            android.app.DatePickerDialog(this, { _, y, m, d ->
                val picked = java.util.Calendar.getInstance().apply { set(y, m, d, 0, 0, 0); set(java.util.Calendar.MILLISECOND, 0) }.timeInMillis
                if (picked <= to) { from = picked; load() } else toast("शुरुआती तारीख अंतिम तारीख से पहले रखें")
            }, c.get(java.util.Calendar.YEAR), c.get(java.util.Calendar.MONTH), c.get(java.util.Calendar.DAY_OF_MONTH)).show()
        }
        addAction(page, "अंतिम तारीख चुनें") {
            val c = java.util.Calendar.getInstance().apply { timeInMillis = to }
            android.app.DatePickerDialog(this, { _, y, m, d ->
                val picked = java.util.Calendar.getInstance().apply { set(y, m, d, 23, 59, 59); set(java.util.Calendar.MILLISECOND, 999) }.timeInMillis
                if (picked >= from) { to = picked; load() } else toast("अंतिम तारीख शुरुआती तारीख के बाद रखें")
            }, c.get(java.util.Calendar.YEAR), c.get(java.util.Calendar.MONTH), c.get(java.util.Calendar.DAY_OF_MONTH)).show()
        }
        load()
    }

    private fun showFinancialStatement() {
        val page = base("PROFIT & LOSS / लाभ-हानि")
        addAction(page, "← Reports / रिपोर्ट") { showReports() }
        val dateFmt = java.text.SimpleDateFormat("dd-MM-yyyy", java.util.Locale("en", "IN"))
        val calendar = java.util.Calendar.getInstance()
        val endOfToday = calendar.timeInMillis
        calendar.set(java.util.Calendar.DAY_OF_MONTH, 1)
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0); calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0); calendar.set(java.util.Calendar.MILLISECOND, 0)
        var from = calendar.timeInMillis
        var to = endOfToday
        val dateLabel = TextView(this).apply { textSize = 14f; setTextColor(0xFF173B57.toInt()); setPadding(dp(10), dp(8), dp(10), dp(8)) }
        val output = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val pdfButton = Button(this).apply { text = "PDF Export / PDF सेव करें"; isEnabled = false }
        var pdfLines: List<String> = emptyList()
        fun refreshDateLabel() { dateLabel.text = "अवधि: ${dateFmt.format(java.util.Date(from))} से ${dateFmt.format(java.util.Date(to))}" }
        fun chooseDate(initial: Long, onPicked: (Long) -> Unit) {
            val c = java.util.Calendar.getInstance().apply { timeInMillis = initial }
            DatePickerDialog(this, { _, y, m, d ->
                onPicked(java.util.Calendar.getInstance().apply { set(y, m, d, 0, 0, 0); set(java.util.Calendar.MILLISECOND, 0) }.timeInMillis)
                refreshDateLabel()
            }, c.get(java.util.Calendar.YEAR), c.get(java.util.Calendar.MONTH), c.get(java.util.Calendar.DAY_OF_MONTH)).show()
        }
        val dateButtons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        dateButtons.addView(Button(this).apply { text = "शुरुआती तारीख"; setOnClickListener { chooseDate(from) { if (it <= to) from = it else toast("शुरुआती तारीख अंतिम तारीख से पहले रखें") } } }, LinearLayout.LayoutParams(0, -2, 1f))
        dateButtons.addView(Button(this).apply { text = "अंतिम तारीख"; setOnClickListener { chooseDate(to) { picked ->
            val end = java.util.Calendar.getInstance().apply { timeInMillis = picked; set(java.util.Calendar.HOUR_OF_DAY, 23); set(java.util.Calendar.MINUTE, 59); set(java.util.Calendar.SECOND, 59); set(java.util.Calendar.MILLISECOND, 999) }.timeInMillis
            if (end >= from) to = end else toast("अंतिम तारीख शुरुआती तारीख के बाद रखें")
        } } }, LinearLayout.LayoutParams(0, -2, 1f))
        page.addView(sectionCard("रिपोर्ट की अवधि / Report Period").apply { addView(dateLabel); addView(dateButtons); addView(pdfButton) })
        page.addView(sectionCard("वित्तीय रिपोर्ट / Financial Report").apply { addView(output) })
        refreshDateLabel()
        fun loadStatement() {
            if (to < from) { toast("तारीख की सीमा गलत है"); return }
            pdfButton.isEnabled = false
            lifecycleScope.launch {
                try {
                    val report = withContext(Dispatchers.IO) { ReportsRepository(db).financialStatement(from, to) }
                    val money: (Long) -> String = { "₹${String.format(java.util.Locale("en", "IN"), "%.2f", it / 100.0)}" }
                    val lines = listOf(
                        "BHAJAN FASHION POINT - PROFIT & LOSS",
                        "Period: ${dateFmt.format(java.util.Date(from))} to ${dateFmt.format(java.util.Date(to))}",
                        "", "PROFIT & LOSS (MANAGEMENT ESTIMATE)",
                        "Net sales (GST excluded): ${money(report.salesNetPaise)}",
                        "Estimated cost of goods sold: ${money(report.estimatedCogsPaise)}",
                        "Estimated gross profit: ${money(report.grossProfitPaise)}",
                        "Operating expenses: ${money(report.operatingExpensesPaise)}",
                        "Salary payments: ${money(report.salaryPaise)}",
                        "Estimated operating surplus: ${money(report.estimatedOperatingProfitPaise)}",
                        "", "FINANCIAL POSITION (CURRENT SNAPSHOT)",
                        "Cash: ${money(report.cashPaise)}", "UPI: ${money(report.upiPaise)}", "Bank: ${money(report.bankPaise)}",
                        "Stock at current purchase rates: ${money(report.stockValuePaise)}",
                        "Receivables: ${money(report.receivablesPaise)}", "Payables: ${money(report.payablesPaise)}",
                        if (report.missingCostLines > 0) "WARNING: ${report.missingCostLines} sold item lines had no usable purchase cost; profit estimate may be incomplete." else "",
                        "", "LIMITATION: Management estimate only; not a certified statutory P&L or legal Balance Sheet.",
                        "COGS uses current Item Master purchase rates, not historical batch costs.",
                        "Capital, fixed assets, loans, depreciation, all tax liabilities and opening balances are not fully captured.",
                        "Current cash/UPI/bank and stock/party balances are as-of-now snapshots, not period-end historical balances.",
                        "Please have figures reviewed by your CA."
                    ).filter { it.isNotBlank() }
                    pdfLines = lines
                    output.removeAllViews()
                    output.addView(card("बिक्री (GST से पहले): ${money(report.salesNetPaise)}\nबेचे गए माल की अनुमानित लागत: ${money(report.estimatedCogsPaise)}\nसकल लाभ: ${money(report.grossProfitPaise)}\nदुकान खर्च: ${money(report.operatingExpensesPaise)}\nवेतन: ${money(report.salaryPaise)}\nअनुमानित परिचालन लाभ: ${money(report.estimatedOperatingProfitPaise)}"))
                    output.addView(card("आज की वित्तीय स्थिति\nCash: ${money(report.cashPaise)} | UPI: ${money(report.upiPaise)} | Bank: ${money(report.bankPaise)}\nस्टॉक मूल्य: ${money(report.stockValuePaise)}\nग्राहकों से लेना: ${money(report.receivablesPaise)}\nसप्लायर को देना: ${money(report.payablesPaise)}"))
                    if (report.missingCostLines > 0) output.addView(emptyLabel("चेतावनी: ${report.missingCostLines} बिक्री आइटम की खरीद लागत उपलब्ध नहीं थी; लाभ अधूरा हो सकता है।"))
                    output.addView(emptyLabel("महत्वपूर्ण: यह प्रबंधन हेतु अनुमान है, प्रमाणित P&L या कानूनी Balance Sheet नहीं। COGS वर्तमान Item Master खरीद दर से निकाला गया है। Cash/UPI/Bank, स्टॉक और पार्टी बैलेंस आज के snapshot हैं, चुनी गई अवधि के ऐतिहासिक closing balance नहीं। CA से सत्यापन आवश्यक है।"))
                    pdfButton.isEnabled = true
                } catch (e: Exception) { toast(e.message ?: "वित्तीय रिपोर्ट नहीं बन सकी") }
            }
        }
        addAction(page, "रिपोर्ट दिखाएँ / Apply Dates") { loadStatement() }
        pdfButton.setOnClickListener {
            lifecycleScope.launch {
                try {
                    pendingReportBytes = withContext(Dispatchers.IO) {
                        val doc = android.graphics.pdf.PdfDocument()
                        val paint = android.graphics.Paint().apply { textSize = 10f; color = android.graphics.Color.BLACK }
                        var pageNo = 1
                        var pdfPage = doc.startPage(android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, pageNo).create())
                        var canvas = pdfPage.canvas; var y = 38f
                        pdfLines.forEach { line ->
                            if (y > 810f) { doc.finishPage(pdfPage); pageNo++; pdfPage = doc.startPage(android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, pageNo).create()); canvas = pdfPage.canvas; y = 38f }
                            canvas.drawText(line.take(105), 30f, y, paint); y += 17f
                        }
                        doc.finishPage(pdfPage)
                        val bytes = java.io.ByteArrayOutputStream(); doc.writeTo(bytes); doc.close(); bytes.toByteArray()
                    }
                    createStockPdf.launch("BFP-Profit-Loss-${System.currentTimeMillis()}.pdf")
                } catch (e: Exception) { toast("PDF तैयार नहीं हुआ: ${e.message}") }
            }
        }
        loadStatement()
    }

    private fun showBusinessFinancialAudit() {
        lifecycleScope.launch {
            try {
                val audit = withContext(Dispatchers.IO) { LedgerRepository(db).auditBusinessFinancials() }
                val message = buildString {
                    append("बिक्री: ₹${audit.salesPaise / 100.0}\nखरीद: ₹${audit.purchasesPaise / 100.0}\nखर्च: ₹${audit.expensesPaise / 100.0}\nवेतन भुगतान: ₹${audit.salaryPaidPaise / 100.0}\nPayment In: ₹${audit.receiptsPaise / 100.0}\nPayment Out: ₹${audit.paymentsPaise / 100.0}\n\n")
                    append("स्टॉक डिज़ाइन: ${audit.stockDesignCount}\nकुल स्टॉक मात्रा: ${audit.stockQuantity}\n\n")
                    append(if (audit.issueCount == 0) "कोई असंगति नहीं मिली" else "ध्यान दें: ${audit.issueCount} संभावित असंगतियाँ मिलीं")
                    if (audit.issues.isNotEmpty()) {
                        append("\n\n")
                        audit.issues.forEach { append("• $it\n") }
                        if (audit.issueCount >= 100) append("\nपहले 100 मुद्दे दिखाए गए हैं।")
                    }
                    append("\n\nयह ऑडिट मूल रिकॉर्ड से पढ़ता है। बिक्री/खरीद, भुगतान, खर्च या स्टॉक में कोई स्वतः बदलाव नहीं करता। GST/ITC की कानूनी पात्रता या वास्तविक बैंक स्टेटमेंट सत्यापन इसमें शामिल नहीं है।")
                }
                AlertDialog.Builder(this@MainActivity).setTitle("Complete Business Financial Audit")
                    .setMessage(message).setPositiveButton("ठीक है", null).show()
            } catch (e: Exception) { toast(e.message ?: "समग्र वित्तीय ऑडिट नहीं बन सका") }
        }
    }

    private fun showGstReport() {
        val page = base("GST & TAX REPORT / GST रिपोर्ट")
        addAction(page, "← Reports / रिपोर्ट") { showReports() }
        val dateFmt = java.text.SimpleDateFormat("dd-MM-yyyy", java.util.Locale("en", "IN"))
        val cal = java.util.Calendar.getInstance().apply { set(java.util.Calendar.DAY_OF_MONTH, 1); set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0); set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0) }
        var start = cal.timeInMillis
        var end = java.util.Calendar.getInstance().apply { set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0); set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0) }.timeInMillis + 86_400_000L
        val dates = TextView(this).apply { textSize = 14f; setTextColor(0xFF263238.toInt()); setPadding(dp(10), dp(8), dp(10), dp(8)) }
        val output = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        fun refreshDates() { dates.text = "अवधि: ${dateFmt.format(java.util.Date(start))} से ${dateFmt.format(java.util.Date(end - 1))}" }
        fun choose(initial: Long, picked: (Long) -> Unit) {
            val c = java.util.Calendar.getInstance().apply { timeInMillis = initial }
            DatePickerDialog(this, { _, y, m, d -> picked(java.util.Calendar.getInstance().apply { set(y, m, d, 0, 0, 0); set(java.util.Calendar.MILLISECOND, 0) }.timeInMillis); refreshDates() }, c.get(java.util.Calendar.YEAR), c.get(java.util.Calendar.MONTH), c.get(java.util.Calendar.DAY_OF_MONTH)).show()
        }
        val datesRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        datesRow.addView(Button(this).apply { text = "शुरुआती तारीख"; setOnClickListener { choose(start) { if (it < end) start = it else toast("शुरुआती तारीख अंतिम तारीख से पहले रखें") } } }, LinearLayout.LayoutParams(0, -2, 1f))
        datesRow.addView(Button(this).apply { text = "अंतिम तारीख"; setOnClickListener { choose(end - 86_400_000L) { if (it + 86_400_000L > start) end = it + 86_400_000L else toast("अंतिम तारीख शुरुआती तारीख के बाद रखें") } } }, LinearLayout.LayoutParams(0, -2, 1f))
        page.addView(sectionCard("रिपोर्ट अवधि").apply { addView(dates); addView(datesRow) })
        page.addView(sectionCard("GST विवरण / Rate-wise Summary").apply { addView(output) })
        fun money(paise: Long) = "₹${String.format(java.util.Locale("en", "IN"), "%,.2f", paise / 100.0)}"
        fun load() {
            output.removeAllViews(); output.addView(emptyLabel("रिपोर्ट लोड हो रही है…"))
            lifecycleScope.launch {
                try {
                    val rows = withContext(Dispatchers.IO) { db.dao().getGstRateReport(start, end) }
                    output.removeAllViews()
                    val sales = rows.filter { it.kind == "SALE" }; val purchases = rows.filter { it.kind == "PURCHASE" }
                    fun renderGroup(title: String, group: List<GstRateReportRow>) {
                        output.addView(card(title))
                        if (group.isEmpty()) output.addView(emptyLabel("इस अवधि में कोई रिकॉर्ड नहीं"))
                        group.forEach { r -> output.addView(emptyLabel("GST ${r.gstRatePercent}% | बिल ${r.invoiceCount}\nTaxable: ${money(r.taxablePaise)}\nCGST: ${money(r.cgstPaise)} | SGST: ${money(r.sgstPaise)} | IGST: ${money(r.igstPaise)}\nकुल GST: ${money(r.totalTaxPaise)}")) }
                        output.addView(card("${title} कुल GST: ${money(group.sumOf { it.totalTaxPaise })}\nकुल Taxable: ${money(group.sumOf { it.taxablePaise })}"))
                    }
                    renderGroup("SALES GST / बिक्री GST — पूरे बिल की दर", sales)
                    renderGroup("PURCHASE GST / खरीद GST — सप्लायर बिल के अनुसार आइटम-वार दर", purchases)
                    val hsnRows = withContext(Dispatchers.IO) { db.dao().getHsnGstReport(start, end) }
                    output.addView(card("HSN-WISE SUMMARY / HSN और GST दर के अनुसार सारांश"))
                    if (hsnRows.isEmpty()) output.addView(emptyLabel("इस अवधि में HSN/GST रिकॉर्ड नहीं हैं"))
                    hsnRows.forEach { r -> output.addView(emptyLabel("${r.kind} | HSN: ${r.hsnCode} | GST ${r.gstRatePercent}% | Qty ${r.quantity}\nTaxable: ${money(r.taxablePaise)} | CGST: ${money(r.cgstPaise)} | SGST: ${money(r.sgstPaise)} | IGST: ${money(r.igstPaise)}")) }
                    val reconciliation = withContext(Dispatchers.IO) { db.dao().getGstReconciliationSummary(start, end) }
                    val supplierItc = withContext(Dispatchers.IO) { db.dao().getSupplierItcReport(start, end) }
                    output.addView(card("GST RECONCILIATION / पुस्तकों के अनुसार GST मिलान"))
                    output.addView(emptyLabel("Sales Output GST: ${money(reconciliation.outputTaxPaise)}\n  CGST ${money(reconciliation.salesCgstPaise)} | SGST ${money(reconciliation.salesSgstPaise)} | IGST ${money(reconciliation.salesIgstPaise)}"))
                    output.addView(emptyLabel("Purchase Input Tax (संभावित ITC): ${money(reconciliation.inputTaxPaise)}\n  CGST ${money(reconciliation.purchaseCgstPaise)} | SGST ${money(reconciliation.purchaseSgstPaise)} | IGST ${money(reconciliation.purchaseIgstPaise)}"))
                    val netLabel = if (reconciliation.estimatedNetTaxPaise >= 0) "अनुमानित शुद्ध GST देय" else "अतिरिक्त Input Tax (पुस्तकीय अंतर)"
                    output.addView(card("$netLabel: ${money(kotlin.math.abs(reconciliation.estimatedNetTaxPaise))}"))
                    output.addView(card("SUPPLIER-WISE INPUT TAX / सप्लायर अनुसार संभावित ITC"))
                    if (supplierItc.isEmpty()) output.addView(emptyLabel("इस अवधि में Purchase GST रिकॉर्ड नहीं हैं"))
                    supplierItc.forEach { r ->
                        val gstinStatus = if (r.gstNumber.isBlank()) "GSTIN उपलब्ध नहीं" else "GSTIN: ${r.gstNumber}"
                        output.addView(emptyLabel("${r.supplierName} | बिल ${r.invoiceCount} | $gstinStatus\nTaxable: ${money(r.taxablePaise)}\nCGST: ${money(r.cgstPaise)} | SGST: ${money(r.sgstPaise)} | IGST: ${money(r.igstPaise)}\nकुल संभावित ITC: ${money(r.totalItcPaise)}"))
                    }
                    output.addView(emptyLabel("महत्वपूर्ण: यह केवल ऐप की Sales/Purchase पुस्तकों का गणितीय मिलान है। यह GSTR-2B/सरकारी पोर्टल से मिलान नहीं करता और ITC की पात्रता प्रमाणित नहीं करता। Reverse charge, blocked/ineligible ITC, credit/debit notes और पोर्टल डेटा अलग से जाँचें; अंतिम GST देयता/ITC CA से सत्यापित करें। Sales का बिल-स्तरीय GST और Purchase का आइटम-वार GST नियम यथावत हैं।"))
                } catch (e: Exception) { output.removeAllViews(); toast("GST रिपोर्ट नहीं बन सकी: ${e.message}") }
            }
        }
        addAction(page, "GSTR-1 Sales CSV Export / बिक्री डेटा निकालें") {
            lifecycleScope.launch {
                try {
                    val invoices = withContext(Dispatchers.IO) { db.dao().getGstr1InvoiceRows(start, end) }
                    fun csv(v: String) = "\"" + v.replace("\"", "\"\"") + "\""
                    val header = "GSTIN/UIN,Receiver Name,Invoice Number,Invoice Date,Invoice Value,Place Of Supply,Reverse Charge,Invoice Type,Rate,Taxable Value,Integrated Tax,Central Tax,State/UT Tax,Cess\n"
                    val rowsCsv = invoices.joinToString("\n") { r ->
                        val date = dateFmt.format(java.util.Date(r.dateEpochMillis))
                        val gstin = r.customerGstin.trim().uppercase(java.util.Locale.ROOT)
                        val invoiceType = if (gstin.isBlank()) "B2C" else "B2B"
                        listOf(gstin, r.customerName, r.invoiceNumber, date, (r.invoiceTotalPaise / 100.0).toString(), "", "N", invoiceType, "", (r.taxablePaise / 100.0).toString(), (r.igstPaise / 100.0).toString(), (r.cgstPaise / 100.0).toString(), (r.sgstPaise / 100.0).toString(), "0.00").joinToString(",") { csv(it) }
                    }
                    pendingReportBytes = ("\uFEFF" + header + rowsCsv).toByteArray(Charsets.UTF_8)
                    createStockCsv.launch("BFP-GSTR1-Sales-${System.currentTimeMillis()}.csv")
                } catch (e: Exception) { toast("GSTR-1 CSV तैयार नहीं हुई: ${e.message}") }
            }
        }
        refreshDates()
        addAction(page, "रिपोर्ट दिखाएँ / Apply Dates") { load() }
        load()
    }

    private fun showMoneyManagement() = showBankManagement()

    private fun showExpenses() {
        val page = base("EXPENSES")
        addAction(page, "+ खर्च दर्ज करें", 0xFFC74646.toInt()) { cashEntryDialog("EXPENSE") }
        addAction(page, "Expense Report") { showReports() }
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        page.addView(list)
        lifecycleScope.launch {
            try {
                val entries = withContext(Dispatchers.IO) { db.dao().observeCashEntries().first().filter { it.entryType == "EXPENSE" } }
                val total = entries.sumOf { it.amountPaise }
                list.addView(card("कुल खर्च: ₹${total / 100.0}\nकुल एंट्री: ${entries.size}"))
                if (entries.isEmpty()) list.addView(emptyLabel("अभी कोई खर्च दर्ज नहीं है"))
                entries.forEach { entry ->
                    val date = java.text.SimpleDateFormat("dd-MM-yyyy", java.util.Locale("en", "IN")).format(java.util.Date(entry.dateEpochMillis))
                    val bankName = entry.bankAccountId?.let { id -> withContext(Dispatchers.IO) { db.dao().getBankAccount(id)?.accountName } }
                    val row = card("$date | खर्च\n${entry.mode}${bankName?.let { " — $it" }.orEmpty()} — ₹${entry.amountPaise / 100.0}\n${entry.note}\nटैप करके Edit/Delete करें")
                    row.setOnClickListener {
                        AlertDialog.Builder(this@MainActivity).setTitle("खर्च")
                            .setItems(arrayOf("Edit", "Delete")) { _, which ->
                                when (which) {
                                    0 -> cashEntryDialog("EXPENSE", entry)
                                    1 -> AlertDialog.Builder(this@MainActivity).setTitle("खर्च हटाएँ?").setMessage("यह खर्च रिकॉर्ड हट जाएगा।")
                                        .setNegativeButton("रद्द", null).setPositiveButton("Delete") { _, _ -> lifecycleScope.launch {
                                            try { withContext(Dispatchers.IO) { db.dao().deleteCashEntry(entry) }; showExpenses() }
                                            catch (e: Exception) { toast(e.message ?: "खर्च हटाया नहीं जा सका") }
                                        } }.show()
                                }
                            }.show()
                    }
                    list.addView(row)
                }
            } catch (e: Exception) { toast(e.message ?: "खर्च लोड नहीं हुए") }
        }
    }

    private fun showBankManagement() {
        val page = base("BANK")
        addAction(page, "+ Bank Account जोड़ें", 0xFF0879C9.toInt()) { showBankAccounts() }
        addAction(page, "Opening Cash / Bank Balance", 0xFF2E8B57.toInt()) { cashEntryDialog("OPENING") }
        addAction(page, "Cash जमा करें (Bank में)", 0xFF245A78.toInt()) { cashEntryDialog("CASH_DEPOSIT") }
        addAction(page, "Bank से Cash निकालें", 0xFF245A78.toInt()) { cashEntryDialog("CASH_WITHDRAWAL") }
        addAction(page, "Bank Accounts Manage करें") { showBankAccounts() }
        addAction(page, "Cash / Bank Reconciliation जाँचें", 0xFF2E8B57.toInt()) { showMoneyReconciliation() }
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        page.addView(list)
        lifecycleScope.launch {
            try {
                val entries = withContext(Dispatchers.IO) { db.dao().observeCashEntries().first().filter { it.entryType != "EXPENSE" } }
                val bank = withContext(Dispatchers.IO) { LedgerRepository(db).bankBalancePaise() }
                val cash = withContext(Dispatchers.IO) { LedgerRepository(db).currentCashBalancePaise() }
                list.addView(card("वर्तमान Cash Balance: ₹${cash / 100.0}\nकुल Bank Balance: ₹${bank / 100.0}"))
                val bankAccounts = withContext(Dispatchers.IO) { db.dao().observeBankAccounts().first() }
                bankAccounts.forEach { account ->
                    val balance = withContext(Dispatchers.IO) { LedgerRepository(db).bankAccountBalancePaise(account.id) }
                    list.addView(card("🏦 ${account.accountName}\nBalance: ₹${balance / 100.0}"))
                }
                if (entries.isEmpty()) list.addView(emptyLabel("अभी कोई बैंक/ओपनिंग एंट्री नहीं है"))
                entries.forEach { entry ->
                    val title = when (entry.entryType) {
                        "OPENING" -> "Opening Balance"
                        "CASH_DEPOSIT" -> "Cash जमा — Bank में"
                        "CASH_WITHDRAWAL" -> "Bank से Cash निकासी"
                        else -> entry.entryType
                    }
                    val date = java.text.SimpleDateFormat("dd-MM-yyyy", java.util.Locale("en", "IN")).format(java.util.Date(entry.dateEpochMillis))
                    val bankName = entry.bankAccountId?.let { id -> withContext(Dispatchers.IO) { db.dao().getBankAccount(id)?.accountName } }
                    val row = card("$date | $title\n${entry.mode}${bankName?.let { " — $it" }.orEmpty()} — ₹${entry.amountPaise / 100.0}\n${entry.note}\nटैप करके Edit/Delete करें")
                    row.setOnClickListener {
                        AlertDialog.Builder(this@MainActivity).setTitle(title).setItems(arrayOf("Edit", "Delete")) { _, which ->
                            when (which) {
                                0 -> cashEntryDialog(entry.entryType, entry)
                                1 -> AlertDialog.Builder(this@MainActivity).setTitle("एंट्री हटाएँ?").setMessage("यह एंट्री बैलेंस से भी हट जाएगी।")
                                    .setNegativeButton("रद्द", null).setPositiveButton("Delete") { _, _ -> lifecycleScope.launch {
                                        try { withContext(Dispatchers.IO) { db.dao().deleteCashEntry(entry) }; showBankManagement() }
                                        catch (e: Exception) { toast(e.message ?: "एंट्री हटाई नहीं जा सकी") }
                                    } }.show()
                            }
                        }.show()
                    }
                    list.addView(row)
                }
            } catch (e: Exception) { toast(e.message ?: "बैंक हिसाब लोड नहीं हुआ") }
        }
    }

    private fun showMoneyReconciliation() {
        lifecycleScope.launch {
            try {
                val audit = withContext(Dispatchers.IO) { LedgerRepository(db).reconcileMoneyBalances() }
                val status = if (audit.issues.isEmpty()) "कोई असंगति नहीं मिली" else "ध्यान दें: ${audit.issues.size} जाँच संबंधी मुद्दे"
                val message = buildString {
                    append("Cash Balance: ₹${audit.cashPaise / 100.0}\n")
                    append("UPI Balance: ₹${audit.upiPaise / 100.0}\n")
                    append("कुल Bank Balance: ₹${audit.bankPaise / 100.0}\n")
                    append("सभी लिंक बैंक खातों का योग: ₹${audit.bankAccountTotalPaise / 100.0}\n")
                    append("बिना मान्य बैंक खाते की एंट्री: ${audit.unassignedBankMovementCount}\n\n")
                    append(status)
                    if (audit.issues.isNotEmpty()) {
                        append("\n\n")
                        audit.issues.forEach { append("• $it\n") }
                    }
                    append("\nयह केवल पढ़ने/मिलान की रिपोर्ट है; कोई एंट्री या बैलेंस अपने-आप नहीं बदलेगा।")
                }
                AlertDialog.Builder(this@MainActivity).setTitle("Cash / Bank Reconciliation")
                    .setMessage(message).setPositiveButton("ठीक है", null).show()
            } catch (e: Exception) { toast(e.message ?: "Reconciliation रिपोर्ट नहीं बन सकी") }
        }
    }

    private fun cashEntryDialog(entryType: String, existing: CashEntryEntity? = null) {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), 0) }
        val amount = editField("रकम (₹)", existing?.let { (it.amountPaise / 100.0).toString() } ?: "")
        val note = editField("विवरण / नोट", existing?.note ?: "")
        val mode = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, when (entryType) {
                "OPENING" -> listOf("CASH", "UPI", "BANK")
                "CASH_DEPOSIT", "CASH_WITHDRAWAL" -> listOf("CASH")
                else -> listOf("CASH", "UPI", "BANK")
            })
            setSelection((0 until adapter.count).firstOrNull { getItemAtPosition(it).toString() == existing?.mode } ?: 0)
        }
        val bankAccounts = lifecycleScope.run { kotlinx.coroutines.runBlocking { db.dao().observeBankAccounts().first() } }
        val bank = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, bankAccounts.map { it.accountName })
            setSelection(bankAccounts.indexOfFirst { it.id == existing?.bankAccountId }.coerceAtLeast(0))
            visibility = if (mode.selectedItem.toString() == "BANK" || entryType in setOf("CASH_DEPOSIT", "CASH_WITHDRAWAL")) View.VISIBLE else View.GONE
        }
        mode.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                bank.visibility = if (mode.selectedItem.toString() == "BANK" || entryType in setOf("CASH_DEPOSIT", "CASH_WITHDRAWAL")) View.VISIBLE else View.GONE
            }
        }
        box.addView(amount); box.addView(mode); box.addView(TextView(this).apply { text = "बैंक अकाउंट" }); box.addView(bank); box.addView(note)
        val title = when (entryType) {
            "EXPENSE" -> "नया खर्च"
            "OPENING" -> "Opening Balance दर्ज करें"
            "CASH_DEPOSIT" -> "Cash Bank में जमा करें"
            else -> "Bank से Cash निकालें"
        }
        AlertDialog.Builder(this).setTitle(title).setView(box).setNegativeButton("रद्द", null)
            .setPositiveButton("Save", null).create().also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val value = amount.text.toString().trim().toDoubleOrNull()
                        if (value == null || value <= 0.0 || !value.isFinite()) { amount.error = "सही रकम दर्ज करें"; return@setOnClickListener }
                        val paiseValue = value * 100.0
                        if (!paiseValue.isFinite() || paiseValue > Long.MAX_VALUE.toDouble()) { amount.error = "रकम बहुत बड़ी है"; return@setOnClickListener }
                        val paise = kotlin.math.round(paiseValue).toLong()
                        if (paise <= 0L) { amount.error = "रकम ₹0.01 से अधिक होनी चाहिए"; return@setOnClickListener }
                        val selectedMode = mode.selectedItem.toString()
                        if (entryType == "CASH_DEPOSIT" && selectedMode != "CASH") { toast("जमा करने के लिए CASH माध्यम चुनें"); return@setOnClickListener }
                        if (entryType == "CASH_WITHDRAWAL" && selectedMode != "CASH") { toast("निकासी में CASH माध्यम चुनें"); return@setOnClickListener }
                        val storedMode = when (entryType) {
                            "CASH_DEPOSIT", "CASH_WITHDRAWAL" -> "CASH"
                            else -> selectedMode
                        }
                        if (entryType !in setOf("OPENING", "EXPENSE", "CASH_DEPOSIT", "CASH_WITHDRAWAL")) { toast("एंट्री का प्रकार सही नहीं है"); return@setOnClickListener }
                        if (storedMode !in setOf("CASH", "UPI", "BANK")) { toast("भुगतान माध्यम सही नहीं है"); return@setOnClickListener }
                        val needsBank = storedMode == "BANK" || entryType in setOf("CASH_DEPOSIT", "CASH_WITHDRAWAL")
                        if (needsBank && bankAccounts.isEmpty()) { toast("पहले Bank Accounts में बैंक का नाम जोड़ें"); return@setOnClickListener }
                        val selectedBankId = if (needsBank) bankAccounts.getOrNull(bank.selectedItemPosition)?.id else null
                        val entry = CashEntryEntity(id = existing?.id ?: 0, entryType = entryType, mode = storedMode, amountPaise = paise,
                            dateEpochMillis = existing?.dateEpochMillis ?: System.currentTimeMillis(), note = note.text.toString().trim(), bankAccountId = selectedBankId)
                        lifecycleScope.launch {
                            try { withContext(Dispatchers.IO) { if (existing == null) db.dao().insertCashEntry(entry) else db.dao().updateCashEntry(entry) }; dialog.dismiss(); if (entryType == "EXPENSE") showExpenses() else showBankManagement() }
                            catch (e: Exception) { toast(e.message ?: "एंट्री सेव नहीं हुई") }
                        }
                    }
                }
                dialog.show()
            }
    }

    private fun showItems() {
        val page = base("ITEM MASTER / STOCK")
        addAction(page, "+ नया सूट / डिजाइन जोड़ें", 0xFF2E8B57.toInt()) { itemDialog(null) }
        addAction(page, "स्टॉक रिपोर्ट / Stock Report", 0xFF315B7D.toInt()) { showStockReport() }
        addAction(page, "← Dashboard") { showDashboard() }
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        page.addView(list)
        lifecycleScope.launch {
            db.dao().observeItems().collectLatest { items ->
                list.removeAllViews()
                if (items.isEmpty()) list.addView(emptyLabel("अभी कोई आइटम नहीं है। हर डिजाइन को अलग Item Code दें।"))
                if (items.isNotEmpty()) {
                    val totalUnits = items.sumOf { it.quantity }
                    val stockValue = items.sumOf { it.quantity * it.purchaseRatePaise.toDouble() }
                    val lowStockCount = items.count { it.quantity <= it.minimumStockQuantity }
                    list.addView(sectionCard("Stock Summary / स्टॉक सारांश").apply {
                        addView(emptyLabel("कुल डिजाइन: ${items.size}  |  कुल मात्रा: ${String.format(java.util.Locale.US, "%.2f", totalUnits)}\n" +
                            "कम स्टॉक वाले डिजाइन: $lowStockCount\n" +
                            "खरीद रेट के अनुसार अनुमानित स्टॉक मूल्य: ₹${String.format(java.util.Locale("en", "IN"), "%.2f", stockValue / 100.0)}\n" +
                            "यह मूल्य अनुमानित है; इसमें GST/छूट या बैच-वार लागत शामिल नहीं है."))
                    })
                }
                items.forEach { item ->
                    val row = card("${item.name}  |  ${item.itemCode}\nस्टॉक: ${item.quantity}  |  बिक्री रेट: ₹${item.saleRatePaise / 100.0}\nHSN: ${item.hsnCode}")
                    row.setOnClickListener { itemDialog(item) }
                    list.addView(row)
                }
            }
        }
    }

    private fun showStockReport() {
        val page = base("STOCK MOVEMENT REPORT / स्टॉक रिपोर्ट")
        addAction(page, "← Item Master / Stock") { showItems() }
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.DAY_OF_MONTH, 1); cal.set(java.util.Calendar.HOUR_OF_DAY, 0); cal.set(java.util.Calendar.MINUTE, 0); cal.set(java.util.Calendar.SECOND, 0); cal.set(java.util.Calendar.MILLISECOND, 0)
        var startMillis = cal.timeInMillis
        val startCal = java.util.Calendar.getInstance().apply { timeInMillis = startMillis }
        val endCal = java.util.Calendar.getInstance().apply { set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0); set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0) }
        var endMillis = endCal.timeInMillis + 86_400_000L
        val dateFmt = java.text.SimpleDateFormat("dd-MM-yyyy", java.util.Locale("en", "IN"))
        val controls = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(10), dp(12), dp(10)) }
        val dates = TextView(this).apply { textSize = 14f; setTextColor(0xFF263238.toInt()) }
        fun refreshDateText() { dates.text = "From: ${dateFmt.format(java.util.Date(startMillis))}    To: ${dateFmt.format(java.util.Date(endMillis - 1))}" }
        fun chooseDate(initial: Long, onPicked: (Long) -> Unit) {
            val c = java.util.Calendar.getInstance().apply { timeInMillis = initial }
            DatePickerDialog(this, { _, y, m, d ->
                val picked = java.util.Calendar.getInstance().apply { set(y, m, d, 0, 0, 0); set(java.util.Calendar.MILLISECOND, 0) }.timeInMillis
                onPicked(picked); refreshDateText()
            }, c.get(java.util.Calendar.YEAR), c.get(java.util.Calendar.MONTH), c.get(java.util.Calendar.DAY_OF_MONTH)).show()
        }
        val dateButtons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        dateButtons.addView(Button(this).apply { text = "शुरुआती तारीख"; setOnClickListener { chooseDate(startMillis) { startMillis = it } } }, LinearLayout.LayoutParams(0, -2, 1f))
        dateButtons.addView(Button(this).apply { text = "अंतिम तारीख"; setOnClickListener { chooseDate(endMillis - 86_400_000L) { endMillis = it + 86_400_000L } } }, LinearLayout.LayoutParams(0, -2, 1f))
        controls.addView(dates); controls.addView(dateButtons)
        val output = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val exportButtons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val csvButton = Button(this).apply { text = "Excel CSV Export"; isEnabled = false }
        val pdfButton = Button(this).apply { text = "PDF Export"; isEnabled = false }
        exportButtons.addView(csvButton, LinearLayout.LayoutParams(0, -2, 1f)); exportButtons.addView(pdfButton, LinearLayout.LayoutParams(0, -2, 1f))
        page.addView(sectionCard("रिपोर्ट की अवधि").apply { addView(controls); addView(exportButtons) })
        page.addView(sectionCard("आइटम-वार स्टॉक और महीनेवार खरीद-बिक्री").apply { addView(output) })
        refreshDateText()
        var latestCsv = ""; var latestPdfRows = listOf<String>()
        fun loadReport() {
            if (endMillis <= startMillis) { toast("अंतिम तारीख शुरुआती तारीख के बराबर या बाद की रखें"); return }
            csvButton.isEnabled = false; pdfButton.isEnabled = false
            lifecycleScope.launch {
                try {
                    val result = withContext(Dispatchers.IO) {
                        val stock = db.dao().getStockPeriodReport(startMillis, endMillis)
                        val months = db.dao().observeMonthlyBusinessTrends(startMillis, endMillis).first()
                        Pair(stock, months)
                    }
                    output.removeAllViews()
                    val lines = mutableListOf<String>("Item Name,Item Code,Opening Stock,Purchase Qty,Sale Qty,Adjustment Qty,Closing Stock")
                    val pdfLines = mutableListOf("BHAJAN FASHION POINT - STOCK REPORT", "Period: ${dateFmt.format(java.util.Date(startMillis))} to ${dateFmt.format(java.util.Date(endMillis - 1))}", "", "ITEM-WISE STOCK", "Item | Code | Opening | Purchase | Sale | Adjustment | Closing")
                    var op = 0.0; var pu = 0.0; var sa = 0.0; var ad = 0.0; var cl = 0.0
                    result.first.forEach { row ->
                        op += row.openingQuantity; pu += row.purchaseQuantity; sa += row.saleQuantity; ad += row.adjustmentQuantity; cl += row.closingQuantity
                        val vals = listOf(row.item.name, row.item.itemCode, fmtStock(row.openingQuantity), fmtStock(row.purchaseQuantity), fmtStock(row.saleQuantity), fmtStock(row.adjustmentQuantity), fmtStock(row.closingQuantity))
                        lines.add(vals.joinToString(",") { "\"${it.replace("\"", "\"\"")}\"" })
                        pdfLines.add("${row.item.name} | ${row.item.itemCode} | ${fmtStock(row.openingQuantity)} | ${fmtStock(row.purchaseQuantity)} | ${fmtStock(row.saleQuantity)} | ${fmtStock(row.adjustmentQuantity)} | ${fmtStock(row.closingQuantity)}")
                        output.addView(emptyLabel("${row.item.name} (${row.item.itemCode})\nOpening ${fmtStock(row.openingQuantity)} | Purchase +${fmtStock(row.purchaseQuantity)} | Sale ${fmtStock(row.saleQuantity)}\nAdjustment ${fmtStock(row.adjustmentQuantity)} | Closing ${fmtStock(row.closingQuantity)}"))
                    }
                    lines.add(listOf("TOTAL", "", fmtStock(op), fmtStock(pu), fmtStock(sa), fmtStock(ad), fmtStock(cl)).joinToString(","))
                    pdfLines.add("TOTAL | | ${fmtStock(op)} | ${fmtStock(pu)} | ${fmtStock(sa)} | ${fmtStock(ad)} | ${fmtStock(cl)}")
                    output.addView(emptyLabel("TOTAL — Opening ${fmtStock(op)} | Purchase +${fmtStock(pu)} | Sale ${fmtStock(sa)} | Adjustment ${fmtStock(ad)} | Closing ${fmtStock(cl)}\nनोट: चुनी गई अवधि के Purchase/Sale Edit और Reversal movement भी शामिल हैं।"))
                    output.addView(emptyLabel("\nमहीनेवार खरीद-बिक्री (बिल की तारीख के अनुसार)"))
                    lines.add(""); lines.add("Month,Sales Amount INR,Purchases Amount INR,Expenses INR")
                    pdfLines.add(""); pdfLines.add("MONTHLY SALES / PURCHASES / EXPENSES")
                    result.second.forEach { m ->
                        val sales = m.salesPaise / 100.0; val purchases = m.purchasesPaise / 100.0; val expenses = m.expensesPaise / 100.0
                        lines.add("${m.monthKey},${"%.2f".format(java.util.Locale.US, sales)},${"%.2f".format(java.util.Locale.US, purchases)},${"%.2f".format(java.util.Locale.US, expenses)}")
                        pdfLines.add("${m.monthKey} | Sales ₹${"%.2f".format(java.util.Locale.US, sales)} | Purchase ₹${"%.2f".format(java.util.Locale.US, purchases)} | Expense ₹${"%.2f".format(java.util.Locale.US, expenses)}")
                        output.addView(emptyLabel("${m.monthKey} — बिक्री ₹${"%.2f".format(java.util.Locale("en", "IN"), sales)} | खरीद ₹${"%.2f".format(java.util.Locale("en", "IN"), purchases)} | खर्च ₹${"%.2f".format(java.util.Locale("en", "IN"), expenses)}"))
                    }
                    latestCsv = "\uFEFF" + lines.joinToString("\n")
                    latestPdfRows = pdfLines
                    csvButton.isEnabled = true; pdfButton.isEnabled = true
                } catch (e: Exception) { toast("रिपोर्ट नहीं बन सकी: ${e.message}") }
            }
        }
        addAction(page, "रिपोर्ट दिखाएँ / Apply Dates") { loadReport() }
        csvButton.setOnClickListener { pendingReportBytes = latestCsv.toByteArray(Charsets.UTF_8); createStockCsv.launch("BFP-Stock-Report-${System.currentTimeMillis()}.csv") }
        pdfButton.setOnClickListener {
            lifecycleScope.launch {
                try {
                    pendingReportBytes = withContext(Dispatchers.IO) {
                        val doc = android.graphics.pdf.PdfDocument()
                        val paint = android.graphics.Paint().apply { textSize = 10f; color = android.graphics.Color.BLACK }
                        val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, 1).create()
                        var pageNo = 1; var pdfPage = doc.startPage(pageInfo); var canvas = pdfPage.canvas; var y = 36f
                        latestPdfRows.forEach { line ->
                            if (y > 810f) { doc.finishPage(pdfPage); pageNo++; pdfPage = doc.startPage(android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, pageNo).create()); canvas = pdfPage.canvas; y = 36f }
                            canvas.drawText(line.take(105), 28f, y, paint); y += 16f
                        }
                        doc.finishPage(pdfPage); val bytes = java.io.ByteArrayOutputStream(); doc.writeTo(bytes); doc.close(); bytes.toByteArray()
                    }
                    createStockPdf.launch("BFP-Stock-Report-${System.currentTimeMillis()}.pdf")
                } catch (e: Exception) { toast("PDF तैयार नहीं हुआ: ${e.message}") }
            }
        }
        loadReport()
    }

    private fun fmtStock(value: Double): String = String.format(java.util.Locale.US, "%.2f", value)

    private fun itemDialog(existing: ItemEntity?) {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(8), dp(18), 0) }
        val code = editField("यूनिक Item Code / Design Code", existing?.itemCode.orEmpty())
        val name = editField("सूट / डिजाइन का नाम", existing?.name.orEmpty())
        val category = editField("कैटेगरी / ब्रांड (वैकल्पिक)", existing?.category.orEmpty())
        val purchase = editField("खरीद रेट (₹)", (existing?.purchaseRatePaise?.div(100.0) ?: 0.0).toString())
        val sale = editField("बिक्री रेट (₹)", (existing?.saleRatePaise?.div(100.0) ?: 0.0).toString())
        val stock = editField(if (existing == null) "Opening Stock / शुरुआती मात्रा" else "मौजूदा स्टॉक (केवल देखने के लिए)", (existing?.quantity ?: 0.0).toString()).apply { isEnabled = existing == null }
        val minimumStock = editField("कम स्टॉक अलर्ट सीमा", (existing?.minimumStockQuantity ?: 2.0).toString())
        val hsn = editField("HSN Code", existing?.hsnCode.orEmpty())
        listOf(code, name, category, purchase, sale, stock, minimumStock, hsn).forEach { box.addView(it) }
        AlertDialog.Builder(this).setTitle(if (existing == null) "नया आइटम" else "आइटम एडिट")
            .setView(ScrollView(this).apply { addView(box) }).setNegativeButton("रद्द", null)
            .apply {
                if (existing != null) setNeutralButton("स्टॉक समायोजन / हिस्ट्री", null)
                else setNeutralButton("सेव और नया", null)
            }
            .setPositiveButton("सेव", null).create().also { dialog ->
                dialog.setOnShowListener {
                    if (existing != null) {
                        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener { stockAdjustmentDialog(existing) }
                    }

                    fun saveItem(andNew: Boolean) {
                        val c = code.text.toString().trim().uppercase()
                        val n = name.text.toString().trim()
                        val pr = purchase.text.toString().toDoubleOrNull(); val sr = sale.text.toString().toDoubleOrNull()
                        val q = stock.text.toString().toDoubleOrNull(); val minQ = minimumStock.text.toString().toDoubleOrNull()
                        if (c.isBlank() || n.isBlank() || pr == null || sr == null || q == null || minQ == null || pr < 0 || sr < 0 || q < 0 || minQ < 0 || !q.isFinite() || !minQ.isFinite()) {
                            toast("Item Code, नाम, रेट, स्टॉक और अलर्ट सीमा सही भरें"); return
                        }
                        lifecycleScope.launch {
                            try {
                                withContext(Dispatchers.IO) {
                                    val conflict = db.dao().findItemCodeConflict(c, existing?.id ?: 0L)
                                    require(conflict == null) { "यह Item Code पहले से मौजूद है" }
                                    val entity = ItemEntity(id = existing?.id ?: 0L, name = n, itemCode = c, category = category.text.toString().trim(),
                                        purchaseRatePaise = kotlin.math.round(pr * 100).toLong(), saleRatePaise = kotlin.math.round(sr * 100).toLong(),
                                        quantity = q, hsnCode = hsn.text.toString().trim(), gstRatePercent = 0.0, minimumStockQuantity = minQ)
                                    if (existing == null) StockRepository(db).insertItemWithOpeningStock(entity)
                                    else db.dao().updateItem(entity)
                                }
                                if (andNew && existing == null) {
                                    code.text.clear(); name.text.clear(); category.text.clear()
                                    purchase.setText("0.0"); sale.setText("0.0"); stock.setText("0.0")
                                    minimumStock.setText("2.0"); hsn.text.clear()
                                    name.requestFocus()
                                    toast("आइटम सेव हो गया — अगला आइटम भरें")
                                } else {
                                    dialog.dismiss()
                                    toast("आइटम सेव हो गया")
                                }
                            } catch (e: Exception) { toast(e.message ?: "आइटम सेव नहीं हुआ") }
                        }
                    }
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { saveItem(false) }
                    if (existing == null) dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener { saveItem(true) }
                }
                dialog.show()
            }
    }

    private fun stockAdjustmentDialog(item: ItemEntity) {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(8), dp(18), 0) }
        val current = TextView(this).apply { text = "${item.name} (${item.itemCode})\nमौजूदा स्टॉक: ${item.quantity}"; textSize = 15f; setPadding(0, 0, 0, dp(8)) }
        val quantity = editField("नई कुल स्टॉक मात्रा", item.quantity.toString())
        val reason = editField("बदलाव का कारण (अनिवार्य)", "")
        box.addView(current); box.addView(quantity); box.addView(reason)
        AlertDialog.Builder(this).setTitle("Stock Adjustment / स्टॉक समायोजन")
            .setView(box).setNegativeButton("रद्द", null).setNeutralButton("हिस्ट्री", null).setPositiveButton("समायोजन सेव", null)
            .create().also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                        lifecycleScope.launch {
                            val rows = withContext(Dispatchers.IO) { db.dao().getStockMovements(item.id) }
                            val content = if (rows.isEmpty()) "अभी कोई स्टॉक हिस्ट्री नहीं है।" else rows.joinToString("\n\n") { row ->
                                val label = when (row.movementType) { "OPENING" -> "Opening Stock"; "ADJUSTMENT" -> "Manual Adjustment"; else -> row.movementType }
                                "$label | ${java.text.SimpleDateFormat("dd-MM-yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date(row.dateEpochMillis))}\n${row.previousQuantity} → ${row.newQuantity} (बदलाव ${if (row.quantityDelta >= 0) "+" else ""}${row.quantityDelta})\n${row.reason}"
                            }
                            AlertDialog.Builder(this@MainActivity).setTitle("Stock Movement History").setMessage(content).setPositiveButton("बंद", null).show()
                        }
                    }
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val newQty = quantity.text.toString().toDoubleOrNull()
                        val why = reason.text.toString().trim()
                        if (newQty == null || !newQty.isFinite() || newQty < 0 || why.isBlank()) { toast("नई मात्रा और बदलाव का कारण सही भरें"); return@setOnClickListener }
                        lifecycleScope.launch {
                            try {
                                withContext(Dispatchers.IO) { StockRepository(db).adjust(item.id, newQty, why) }
                                dialog.dismiss(); toast("स्टॉक समायोजन सेव हो गया")
                            } catch (e: Exception) { toast(e.message ?: "स्टॉक समायोजन सेव नहीं हुआ") }
                        }
                    }
                }
                dialog.show()
            }
    }

    private fun showCustomerInsights() {
        val page = base("CUSTOMER INTELLIGENCE")
        addAction(page, "← ग्राहक और सप्लायर") { showParties() }
        page.addView(TextView(this).apply {
            text = "ग्राहक की खरीद और पसंद के आधार पर जानकारी। VIP पहचान: 5 या अधिक बिल अथवा ₹25,000 या अधिक कुल खरीद। यह केवल पहचान है; कोई ऑटोमैटिक संदेश, भुगतान या रिकॉर्ड बदलाव नहीं होगा। जन्मदिन पार्टी एडिट में वैकल्पिक रूप से जोड़ सकते हैं।"
            textSize = 13f; setTextColor(0xFF455A64.toInt()); setPadding(dp(12), dp(6), dp(12), dp(12))
        })
        val customerList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        page.addView(sectionCard("ग्राहक खरीद सारांश").apply { addView(customerList) })
        val birthdayList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        page.addView(sectionCard("अगले 30 दिनों के जन्मदिन").apply { addView(birthdayList) })
        val designList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        page.addView(sectionCard("ग्राहकों द्वारा ज्यादा खरीदे गए डिजाइन").apply { addView(designList) })
        lifecycleScope.launch {
            db.dao().observeCustomerInsights().collectLatest { rows ->
                customerList.removeAllViews()
                if (rows.isEmpty()) customerList.addView(emptyLabel("अभी बिक्री वाले ग्राहक रिकॉर्ड उपलब्ध नहीं हैं।"))
                rows.forEach { row ->
                    val vip = row.billCount >= 5 || row.totalSalesPaise >= 2500000L
                    val tag = if (vip) "VIP ग्राहक" else "ग्राहक"
                    val card = card("$tag • ${row.customerName}\nबिल: ${row.billCount}  |  कुल खरीद: ₹${row.totalSalesPaise / 100.0}\nबकाया: ₹${row.outstandingPaise.coerceAtLeast(0) / 100.0}\nमोबाइल: ${row.mobile.ifBlank { "—" }}")
                    card.setOnClickListener { showCustomerDetail(row.partyId, row.customerName) }
                    customerList.addView(card)
                }

            }
        }
        lifecycleScope.launch {
            db.dao().observeCustomerBirthdays().collectLatest { parties ->
                birthdayList.removeAllViews()
                val today = java.util.Calendar.getInstance()
                val todayStart = (today.clone() as java.util.Calendar).apply { set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0); set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0) }
                val upcoming = parties.mapNotNull { party ->
                    try {
                        val parts = party.birthday.split("-").map { it.toInt() }
                        val birthday = (todayStart.clone() as java.util.Calendar).apply { set(java.util.Calendar.MONTH, parts[1] - 1); set(java.util.Calendar.DAY_OF_MONTH, parts[2]) }
                        if (birthday.before(todayStart)) birthday.add(java.util.Calendar.YEAR, 1)
                        val days = ((birthday.timeInMillis - todayStart.timeInMillis) / 86400000L).toInt()
                        if (days in 0..30) party to days else null
                    } catch (_: Exception) { null }
                }.sortedBy { it.second }
                if (upcoming.isEmpty()) birthdayList.addView(emptyLabel("अगले 30 दिनों में कोई जन्मदिन दर्ज नहीं है।"))
                upcoming.forEach { (party, days) -> birthdayList.addView(card("${party.name} • ${party.birthday.substring(8,10)}-${party.birthday.substring(5,7)}\n${if (days == 0) "आज जन्मदिन है" else "$days दिन बाद"}  |  ${party.mobile.ifBlank { "मोबाइल दर्ज नहीं" }}")) }
            }
        }
        lifecycleScope.launch {
            db.dao().observeTopCustomerDesigns().collectLatest { rows ->
                designList.removeAllViews()
                if (rows.isEmpty()) designList.addView(emptyLabel("डिजाइन पसंद दिखाने के लिए बिक्री की आइटम-वाइज एंट्री चाहिए।"))
                rows.forEachIndexed { index, row -> designList.addView(card("${index + 1}. ${row.itemName} (${row.itemCode.ifBlank { "कोड नहीं" }})\nग्राहक: ${row.customerName}  |  मात्रा: ${row.totalQuantity} पीस")) }
            }
        }
    }

    private fun showCustomerDetail(partyId: Long, customerName: String) {
        val page = base("CUSTOMER DETAILS / ग्राहक विवरण")
        addAction(page, "← ग्राहक सूची / Back to Customers") { showCustomerInsights() }
        val profileBox = sectionCard("Customer Profile / ग्राहक की जानकारी")
        val profileText = TextView(this).apply {
            text = customerName
            textSize = 16f
            setTextColor(0xFF34394B.toInt())
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        profileBox.addView(profileText)
        page.addView(profileBox)

        val tabs = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(8), 0, dp(8))
        }
        val saleTab = Button(this).apply { text = "SALE / बिक्री"; isAllCaps = false }
        val receiptTab = Button(this).apply { text = "RECEIPT / प्राप्ति"; isAllCaps = false }
        tabs.addView(saleTab, LinearLayout.LayoutParams(0, dp(50), 1f).apply { marginEnd = dp(5) })
        tabs.addView(receiptTab, LinearLayout.LayoutParams(0, dp(50), 1f).apply { marginStart = dp(5) })
        page.addView(tabs)

        val tabSummary = TextView(this).apply {
            textSize = 13f
            setTextColor(0xFF455A64.toInt())
            setPadding(dp(10), dp(4), dp(10), dp(8))
        }
        page.addView(tabSummary)
        val transactionList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        page.addView(transactionList)

        val dateFormat = java.text.SimpleDateFormat("dd-MM-yyyy", java.util.Locale("en", "IN"))
        var selectedTab = "SALE"
        var customerMobile = ""

        fun styleTabs() {
            saleTab.backgroundTintList = android.content.res.ColorStateList.valueOf(if (selectedTab == "SALE") 0xFF168447.toInt() else 0xFFE0E7E5.toInt())
            saleTab.setTextColor(if (selectedTab == "SALE") 0xFFFFFFFF.toInt() else 0xFF263238.toInt())
            receiptTab.backgroundTintList = android.content.res.ColorStateList.valueOf(if (selectedTab == "RECEIPT") 0xFF2874B8.toInt() else 0xFFE1EAF3.toInt())
            receiptTab.setTextColor(if (selectedTab == "RECEIPT") 0xFFFFFFFF.toInt() else 0xFF263238.toInt())
        }

        fun renderSelectedTab() {
            styleTabs()
            transactionList.removeAllViews()
            if (selectedTab == "SALE") {
                tabSummary.text = "Sales / बिक्री: इस ग्राहक के सभी बिक्री बिल और उनका भुगतान-बकाया यहाँ दिखेगा।"
                addAction(transactionList, "+ नई Sale / नया बिक्री बिल", 0xFF168447.toInt()) {
                    invoiceDialog("SALE", null, preselectedPartyId = partyId)
                }
                lifecycleScope.launch {
                    val invoices = withContext(Dispatchers.IO) {
                        db.dao().observeInvoices("SALE").first().filter { it.partyId == partyId }.sortedByDescending { it.dateEpochMillis }
                    }
                    if (selectedTab != "SALE") return@launch
                    if (invoices.isEmpty()) transactionList.addView(emptyLabel("इस ग्राहक की अभी कोई बिक्री दर्ज नहीं है। / No sales recorded yet."))
                    var totalSales = 0L
                    var totalDue = 0L
                    invoices.forEach { invoice ->
                        val due = (invoice.totalPaise - invoice.paidPaise).coerceAtLeast(0)
                        totalSales += invoice.totalPaise
                        totalDue += due
                        transactionList.addView(card("SALE • ${invoice.invoiceNumber}\nतारीख / Date: ${dateFormat.format(java.util.Date(invoice.dateEpochMillis))}\nकुल / Total: ₹${invoice.totalPaise / 100.0}  |  प्राप्त / Paid: ₹${invoice.paidPaise / 100.0}  |  बाकी / Due: ₹${due / 100.0}"))
                    }
                    transactionList.addView(sectionCard("Sales Summary / बिक्री सारांश").apply {
                        addView(emptyLabel("कुल बिक्री / Total Sales: ₹${totalSales / 100.0}\nकुल बकाया / Total Due: ₹${totalDue / 100.0}\nबिल / Bills: ${invoices.size}"))
                    }, 0)
                }
            } else {
                tabSummary.text = "Receipts / प्राप्त भुगतान: ग्राहक से प्राप्त सभी रकम, माध्यम और तारीख यहाँ दिखेंगे।"
                addAction(transactionList, "+ नई Receipt / भुगतान प्राप्त करें", 0xFF2874B8.toInt()) {
                    paymentDialog("IN", null, preselectedPartyId = partyId)
                }
                lifecycleScope.launch {
                    val receipts = withContext(Dispatchers.IO) {
                        db.dao().getPartyPayments(partyId).filter { it.direction == "IN" }.sortedByDescending { it.dateEpochMillis }
                    }
                    if (selectedTab != "RECEIPT") return@launch
                    if (receipts.isEmpty()) transactionList.addView(emptyLabel("इस ग्राहक से अभी कोई रसीद दर्ज नहीं है। / No receipts recorded yet."))
                    var totalReceipts = 0L
                    receipts.forEach { receipt ->
                        totalReceipts += receipt.amountPaise
                        val linkedBill = receipt.invoiceId?.let { "बिल लिंक / Bill: #$it" } ?: "बिना बिल लिंक / Unlinked receipt"
                        transactionList.addView(card("RECEIPT • ₹${receipt.amountPaise / 100.0}\nतारीख / Date: ${dateFormat.format(java.util.Date(receipt.dateEpochMillis))}  |  माध्यम / Mode: ${receipt.mode}\n$linkedBill${receipt.note.takeIf { it.isNotBlank() }?.let { "\nनोट / Note: $it" }.orEmpty()}"))
                    }
                    transactionList.addView(sectionCard("Receipt Summary / रसीद सारांश").apply {
                        addView(emptyLabel("कुल प्राप्त राशि / Total Received: ₹${totalReceipts / 100.0}\nरसीदें / Receipts: ${receipts.size}"))
                    }, 0)
                }
            }
        }

        saleTab.setOnClickListener { if (selectedTab != "SALE") { selectedTab = "SALE"; renderSelectedTab() } }
        receiptTab.setOnClickListener { if (selectedTab != "RECEIPT") { selectedTab = "RECEIPT"; renderSelectedTab() } }

        lifecycleScope.launch {
            val details = withContext(Dispatchers.IO) {
                val party = db.dao().getParty(partyId)
                val purchaseHistory = db.dao().getCustomerPurchaseHistory(partyId)
                val designPreferences = db.dao().getCustomerDesignPreferences(partyId)
                Triple(party, purchaseHistory, designPreferences)
            }
            customerMobile = details.first?.mobile.orEmpty()
            profileText.text = buildString {
                append(details.first?.name ?: customerName)
                append("\nMobile / मोबाइल: ").append(customerMobile.ifBlank { "—" })
                append("\nAddress / पता: ").append(details.first?.address?.ifBlank { "—" } ?: "—")
                append("\nGSTIN: ").append(details.first?.gstNumber?.ifBlank { "—" } ?: "—")
                append("\nBirthday / जन्मदिन: ").append(details.first?.birthday?.ifBlank { "—" } ?: "—")
            }
            val designText = if (details.third.isEmpty()) "अभी डिजाइन इतिहास उपलब्ध नहीं है।" else details.third.joinToString("\n") { "${it.itemName} (${it.itemCode.ifBlank { "कोड नहीं" }}) — ${it.totalQuantity} पीस" }
            addAction(page, "WhatsApp संदेश तैयार करें / Draft WhatsApp Message", 0xFF168447.toInt()) {
                val preferred = details.third.firstOrNull()
                val preferenceLine = preferred?.let { "आपने पहले ${it.itemName}${it.itemCode.takeIf(String::isNotBlank)?.let { code -> " (कोड $code)" }.orEmpty()} पसंद किया था।" }.orEmpty()
                val offerText = "नमस्ते ${details.first?.name ?: customerName} जी! 🙏\nBHAJAN FASHION POINT में आपके लिए नए सूट और डिजाइन उपलब्ध हैं।\n$preferenceLine\nजब भी समय मिले, हमारे नए कलेक्शन देखिए। धन्यवाद!"
                openWhatsAppDraft(customerMobile, offerText)
            }
            addAction(page, "Preferred Designs / पसंदीदा डिजाइन", 0xFF607D8B.toInt()) {
                AlertDialog.Builder(this@MainActivity).setTitle("${details.first?.name ?: customerName} — Preferred Designs").setMessage(designText).setPositiveButton("OK", null).show()
            }
            renderSelectedTab()
        }
    }

    /** Opens a pre-filled WhatsApp chat only after the user taps the explicit action. It never sends automatically. */
    private fun openWhatsAppDraft(rawMobile: String, message: String) {
        var digits = rawMobile.filter { it.isDigit() }
        if (digits.startsWith("0")) digits = digits.drop(1)
        if (digits.length == 10) digits = "91$digits"
        if (digits.length !in 11..15) {
            toast("ग्राहक का सही मोबाइल नंबर दर्ज करें (10 अंकों का नंबर या देश कोड सहित)")
            return
        }
        val uri = Uri.parse("https://wa.me/$digits?text=${Uri.encode(message)}")
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (_: Exception) {
            toast("WhatsApp खोलने के लिए उपलब्ध नहीं है। संदेश तैयार नहीं हो सका।")
        }
    }

    private fun showSupplierInsights() {
        val page = base("SUPPLIER PURCHASE INSIGHTS")
        addAction(page, "← सप्लायर सूची") { showParties() }
        page.addView(TextView(this).apply {
            text = "यह केवल जानकारी वाली रिपोर्ट है। कोई चेतावनी पॉपअप नहीं, कोई खरीद/भुगतान अपने-आप नहीं बदलेगा। बकाया राशि मौजूदा बिल रिकॉर्ड के अनुसार दिखाई जाती है।"
            textSize = 13f; setTextColor(0xFF455A64.toInt()); setPadding(dp(12), dp(6), dp(12), dp(12))
        })
        val summaryList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        page.addView(sectionCard("सप्लायर के अनुसार कुल खरीद और बकाया").apply { addView(summaryList) })
        val rateList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        page.addView(sectionCard("पिछली खरीद के मुकाबले रेट").apply { addView(rateList) })
        lifecycleScope.launch {
            db.dao().observeSupplierPurchaseSummaries().collectLatest { rows ->
                summaryList.removeAllViews()
                if (rows.isEmpty()) summaryList.addView(emptyLabel("अभी खरीद का रिकॉर्ड उपलब्ध नहीं है।"))
                rows.forEach { row ->
                    val outstanding = row.outstandingPaise.coerceAtLeast(0)
                    summaryList.addView(card("${row.supplierName}\nबिल: ${row.billCount}  |  कुल खरीद: ₹${row.totalPurchasePaise / 100.0}\nबकाया: ₹${outstanding / 100.0}\nGSTIN: ${row.gstNumber.ifBlank { "दर्ज नहीं" }}"))
                }
            }
        }
        lifecycleScope.launch {
            db.dao().observeSupplierItemRateChanges().collectLatest { rows ->
                rateList.removeAllViews()
                val changed = rows.filter { it.latestRatePaise != it.previousRatePaise }
                if (changed.isEmpty()) rateList.addView(emptyLabel("तुलना के लिए अभी दो खरीद रिकॉर्ड वाला कोई बदला हुआ रेट नहीं है।"))
                changed.take(30).forEach { row ->
                    val delta = row.latestRatePaise - row.previousRatePaise
                    val direction = if (delta > 0) "बढ़ा" else "घटा"
                    rateList.addView(card("${row.supplierName} • ${row.itemName} (${row.itemCode})\nपहले: ₹${row.previousRatePaise / 100.0}  |  अब: ₹${row.latestRatePaise / 100.0}\nरेट $direction: ₹${kotlin.math.abs(delta) / 100.0} प्रति पीस"))
                }
                if (rows.size > 30) rateList.addView(emptyLabel("और ${rows.size - 30} आइटम रिपोर्ट में उपलब्ध हैं।"))
            }
        }
    }

    private fun showSmartStock() {
        val page = base("SMART STOCK INTELLIGENCE")
        addAction(page, "← Item Master") { showItems() }
        addAction(page, "+ नया आइटम / Design Code") { itemDialog(null) }
        page.addView(TextView(this).apply {
            text = "स्टॉक अलर्ट और खरीद सुझाव आपके रिकॉर्ड पर आधारित हैं। सुझाव केवल जानकारी हैं—ऐप अपने-आप खरीद ऑर्डर नहीं बनाएगा।"
            textSize = 13f; setTextColor(0xFF455A64.toInt()); setPadding(dp(12), dp(8), dp(12), dp(12))
        })
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        page.addView(list)
        lifecycleScope.launch {
            db.dao().observeStockInsights(System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000).collectLatest { insights ->
                list.removeAllViews()
                if (insights.isEmpty()) list.addView(emptyLabel("पहले Item Master में सूट / डिजाइन जोड़ें।"))
                val low = insights.filter { it.item.quantity <= it.item.minimumStockQuantity }
                val healthy = insights.size - low.size
                list.addView(card("कुल डिजाइन: ${insights.size}  |  कम स्टॉक: ${low.size}  |  पर्याप्त स्टॉक: $healthy"))
                if (low.isEmpty() && insights.isNotEmpty()) list.addView(emptyLabel("अभी किसी डिजाइन का स्टॉक अलर्ट नहीं है।"))
                low.forEach { insight ->
                    val item = insight.item
                    val averageDaily = insight.soldLast30Days / 30.0
                    val demandTarget = kotlin.math.ceil(averageDaily * 30.0)
                    val thresholdTarget = item.minimumStockQuantity
                    val targetStock = maxOf(demandTarget, thresholdTarget)
                    val suggested = kotlin.math.ceil((targetStock - item.quantity).coerceAtLeast(0.0))
                    val reason = if (insight.soldLast30Days > 0.0) "पिछले 30 दिन बिक्री: ${insight.soldLast30Days} पीस" else "पिछले 30 दिन बिक्री रिकॉर्ड नहीं"
                    val suggestion = if (suggested > 0) "सुझाव: लगभग $suggested पीस मंगाने पर विचार करें" else "अभी अतिरिक्त खरीद की जरूरत नहीं"
                    list.addView(card("⚠ ${item.name}  |  ${item.itemCode}\nमौजूदा स्टॉक: ${item.quantity}  |  अलर्ट सीमा: ${item.minimumStockQuantity}\n$reason\n$suggestion\nखरीद रेट: ₹${item.purchaseRatePaise / 100.0}"))
                }
                val movers = insights.filter { it.soldLast30Days > 0.0 }.sortedByDescending { it.soldLast30Days }.take(5)
                if (movers.isNotEmpty()) {
                    list.addView(sectionCard("पिछले 30 दिनों के तेज़ बिकने वाले डिजाइन").apply {
                        movers.forEachIndexed { index, row -> addView(menuRow("${index + 1}. ${row.item.name} (${row.item.itemCode}) — ${row.soldLast30Days} पीस") { itemDialog(row.item) }) }
                    })
                }
            }
        }
    }

    private fun showProfitAndDeadStock() {
        val page = base("PER-SUIT PROFIT & DEAD STOCK / प्रति सूट मुनाफा और रुका स्टॉक")
        addAction(page, "← Inventory / वापस") { showItems() }
        page.addView(TextView(this).apply {
            text = "मुनाफा Item Master के मौजूदा बिक्री रेट − खरीद रेट से अनुमानित है। इसमें छूट, टैक्स, दुकान का खर्च और पुराने बैच की वास्तविक लागत शामिल नहीं है। 90 दिन से बिक्री न होने पर स्टॉक को रुका हुआ दिखाया जाएगा।"
            textSize = 13f; setTextColor(0xFF455A64.toInt()); setPadding(dp(12), dp(8), dp(12), dp(12))
        })
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        page.addView(list)
        val cutoff = System.currentTimeMillis() - 90L * 24 * 60 * 60 * 1000
        lifecycleScope.launch {
            db.dao().observeItemPerformance(cutoff).collectLatest { rows ->
                list.removeAllViews()
                if (rows.isEmpty()) {
                    list.addView(emptyLabel("पहले Item Master में सूट / डिजाइन जोड़ें।"))
                    return@collectLatest
                }
                val withRates = rows.filter { it.item.saleRatePaise > 0L && it.item.purchaseRatePaise > 0L }
                val dead = rows.filter { it.item.quantity > 0.0 && (it.lastSaleEpochMillis == 0L || it.lastSaleEpochMillis < cutoff) }
                list.addView(card("कुल डिजाइन: ${rows.size}  |  मुनाफा रेट उपलब्ध: ${withRates.size}  |  90+ दिन से नहीं बिके: ${dead.size}"))
                list.addView(sectionCard("Per-Suit Profit / प्रति सूट अनुमानित मुनाफा").apply {
                    if (withRates.isEmpty()) addView(emptyLabel("मुनाफा देखने के लिए Item Master में खरीद और बिक्री दोनों रेट भरें।"))
                    withRates.sortedByDescending { it.item.saleRatePaise - it.item.purchaseRatePaise }.take(40).forEach { row ->
                        val item = row.item
                        val profit = item.saleRatePaise - item.purchaseRatePaise
                        val margin = profit.toDouble() * 100.0 / item.saleRatePaise.toDouble()
                        addView(menuRow("${item.name} (${item.itemCode})\nखरीद: ₹${item.purchaseRatePaise / 100.0}  |  बिक्री: ₹${item.saleRatePaise / 100.0}\n90 दिन बिक्री: ${row.soldLast90Days} पीस\nअनुमानित मुनाफा: ₹${profit / 100.0} प्रति सूट  |  मार्जिन: ${String.format(java.util.Locale.US, "%.1f", margin)}%") { itemDialog(item) })
                    }
                })
                list.addView(sectionCard("Dead Stock / रुका हुआ स्टॉक — 90+ दिन").apply {
                    if (dead.isEmpty()) addView(emptyLabel("अभी 90 दिन से अधिक पुराना बिना बिक्री वाला स्टॉक नहीं मिला।"))
                    dead.sortedBy { it.lastSaleEpochMillis }.forEach { row ->
                        val item = row.item
                        val lastSale = if (row.lastSaleEpochMillis == 0L) "अभी तक बिक्री दर्ज नहीं" else "अंतिम बिक्री: " + java.text.SimpleDateFormat("dd-MM-yyyy", java.util.Locale.US).format(java.util.Date(row.lastSaleEpochMillis))
                        addView(menuRow("${item.name} (${item.itemCode})\nस्टॉक: ${item.quantity} पीस  |  $lastSale\nखरीद रेट: ₹${item.purchaseRatePaise / 100.0}") { itemDialog(item) })
                    }
                })
            }
        }
    }

    private fun showParties() {
        val page = base("ग्राहक और सप्लायर")
        addAction(page, "+ नई पार्टी जोड़ें", 0xFF2E8B57.toInt()) { partyDialog(null) }
        addAction(page, "← Dashboard") { showDashboard() }
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        page.addView(list)
        lifecycleScope.launch {
            db.dao().observeParties().collectLatest { parties ->
                list.removeAllViews()
                if (parties.isEmpty()) list.addView(emptyLabel("अभी कोई पार्टी नहीं जोड़ी गई है"))
                parties.forEach { party ->
                    val row = card("${party.name}\n${party.mobile}\nप्रकार: ${party.partyType}")
                    row.setOnClickListener { partyDialog(party) }
                    list.addView(row)
                    addAction(list, "${party.name} — Ledger / Edit / Delete", 0xFF607D8B.toInt()) {
                        AlertDialog.Builder(this@MainActivity).setTitle(party.name)
                            .setItems(arrayOf("खाता देखें", "Edit", "Delete")) { _, which -> when (which) { 0 -> showPartyLedger(party); 1 -> partyDialog(party); 2 -> confirmDeleteParty(party) } }.show()
                    }
                }
            }
        }
    }

    private fun partyDialog(existing: PartyEntity?) {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), 0) }
        val name = editField("पार्टी का नाम", existing?.name.orEmpty()); val mobile = editField("मोबाइल", existing?.mobile.orEmpty())
        val address = editField("पता", existing?.address.orEmpty()); val gst = editField("GST नंबर", existing?.gstNumber.orEmpty())
        val birthday = editField("जन्मदिन (DD-MM-YYYY, वैकल्पिक)", existing?.birthday?.takeIf { it.length == 10 }?.let { "${it.substring(8,10)}-${it.substring(5,7)}-${it.substring(0,4)}" }.orEmpty())
        val type = Spinner(this).apply { adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("CUSTOMER", "SUPPLIER", "BOTH")); setSelection(listOf("CUSTOMER", "SUPPLIER", "BOTH").indexOf(existing?.partyType ?: "CUSTOMER").coerceAtLeast(0)) }
        listOf(name, mobile, address, gst, birthday).forEach { box.addView(it) }; box.addView(type)
        AlertDialog.Builder(this).setTitle(if (existing == null) "नई पार्टी" else "पार्टी एडिट")
            .setView(box).setNegativeButton("रद्द", null).setPositiveButton("Save", null).create().also { dialog ->
            dialog.setOnShowListener {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    val n = name.text.toString().trim()
                    val gstin = gst.text.toString().trim().uppercase()
                    if (n.isBlank()) { name.error = "नाम जरूरी है"; return@setOnClickListener }
                    if (!GstCalculator.validateGstin(gstin)) {
                        gst.error = "GSTIN का फॉर्मेट सही नहीं है"
                        return@setOnClickListener
                    }
                    val birthdayText = birthday.text.toString().trim()
                    val birthdayIso = if (birthdayText.isBlank()) "" else try {
                        require(Regex("\\d{2}-\\d{2}-\\d{4}").matches(birthdayText))
                        val parsed = java.text.SimpleDateFormat("dd-MM-yyyy", java.util.Locale.US).apply { isLenient = false }.parse(birthdayText)
                        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(parsed!!)
                    } catch (_: Exception) { birthday.error = "DD-MM-YYYY सही भरें"; return@setOnClickListener }
                    val value = PartyEntity(id = existing?.id ?: 0, name = n, mobile = mobile.text.toString().trim(), address = address.text.toString().trim(), gstNumber = gstin, partyType = type.selectedItem.toString(), birthday = birthdayIso)
                    lifecycleScope.launch(Dispatchers.IO) { if (existing == null) db.dao().insertParty(value) else db.dao().updateParty(value) }
                    dialog.dismiss()
                } }
                dialog.show()
            }
    }

    private fun confirmDeleteParty(party: PartyEntity) {
        AlertDialog.Builder(this).setTitle("पार्टी हटाएँ?").setMessage("यदि इस पार्टी से बिल या भुगतान जुड़े हैं, तो डेटाबेस संबंध सुरक्षित रखने के लिए हटाना संभव नहीं होना चाहिए।")
            .setNegativeButton("रद्द", null).setPositiveButton("हटाएँ") { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) { val used = db.dao().countPartyInvoices(party.id) + db.dao().countPartyPayments(party.id); if (used > 0) withContext(Dispatchers.Main) { toast("इस पार्टी से $used बिल/भुगतान जुड़े हैं। इसे हटाया नहीं जा सकता।") } else db.dao().deleteParty(party) }
            }.show()
    }


    private fun showGstSettings() {
        lifecycleScope.launch {
            val current = withContext(Dispatchers.IO) { db.dao().getBusinessProfile() ?: BusinessProfileEntity().also { db.dao().saveBusinessProfile(it) } }
            val page = base("GST SETTINGS / व्यवसाय और GST विवरण")
            addAction(page, "← Dashboard") { showDashboard() }
            val box = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
            val businessName = editField("दुकान / व्यवसाय का नाम", current.businessName)
            val address = editField("पूरा पता", current.address)
            val mobile = editField("मोबाइल नंबर", current.mobile)
            val gstin = editField("GSTIN (यदि पंजीकृत हैं)", current.gstin)
            val stateCode = editField("दुकान का 2 अंकों का राज्य कोड (हिमाचल: 02)", current.stateCode)
            val prefix = editField("बिल नंबर Prefix", current.invoicePrefix)
            listOf(businessName, address, mobile, gstin, stateCode, prefix).forEach { box.addView(it) }
            page.addView(box)
            addAction(page, "GST विवरण सेव करें", 0xFF2E8B57.toInt()) {
                val name = businessName.text.toString().trim()
                val gst = gstin.text.toString().trim().uppercase()
                val state = stateCode.text.toString().trim()
                if (name.isBlank() || !state.matches(Regex("[0-9]{2}")) || !GstCalculator.validateGstin(gst) || (gst.isNotBlank() && gst.substring(0, 2) != state)) {
                    toast("व्यवसाय का नाम, 2 अंकों का राज्य कोड और सही GSTIN भरें। GSTIN का पहला राज्य कोड भी मिलना चाहिए।")
                    return@addAction
                }
                val updated = current.copy(businessName = name, address = address.text.toString().trim(), mobile = mobile.text.toString().trim(), gstin = gst, stateCode = state, invoicePrefix = prefix.text.toString().trim().ifBlank { "BFP" })
                lifecycleScope.launch(Dispatchers.IO) { db.dao().saveBusinessProfile(updated); withContext(Dispatchers.Main) { toast("GST और व्यवसाय विवरण सेव हो गए") } }
            }
            page.addView(emptyLabel("GST विवरण बिल में उपयोग होते हैं। बिल की GST गणना आइटम की दर और चुने गए राज्य के आधार पर होती है; प्रिंट/PDF में HSN और GST दरवार CGST/SGST/IGST सारांश शामिल है। अंतिम GST अनुपालन के लिए CA से सत्यापन कराएँ।"))
        }
    }

    private fun showPayments(direction: String) {
        val title = if (direction == "IN") "Payment In / प्राप्त भुगतान" else "Payment Out / किया गया भुगतान"
        val page = base(title)
        addAction(page, "+ नई एंट्री", if (direction == "IN") 0xFF2E8B57.toInt() else 0xFFC74646.toInt()) { paymentDialog(direction, null) }
        addAction(page, "← Dashboard") { showDashboard() }
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        page.addView(list)
        lifecycleScope.launch {
            db.dao().observePayments().collectLatest { payments ->
                list.removeAllViews()
                val filtered = payments.filter { it.direction == direction }
                if (filtered.isEmpty()) list.addView(emptyLabel("अभी कोई भुगतान एंट्री नहीं है"))
                filtered.forEach { payment ->
                    val partyName = withContext(Dispatchers.IO) { db.dao().observeParties().first().firstOrNull { it.id == payment.partyId }?.name ?: "पार्टी #${payment.partyId}" }
                    val billText = payment.invoiceId?.let { "\nबिल #$it" } ?: "\nबिना बिल से लिंक"
                    val bankName = payment.bankAccountId?.let { id -> withContext(Dispatchers.IO) { db.dao().getBankAccount(id)?.accountName } }
                    list.addView(card("$partyName\nरकम: ₹${payment.amountPaise / 100.0} | माध्यम: ${payment.mode}${bankName?.let { " ($it)" }.orEmpty()}$billText\n${java.text.SimpleDateFormat("dd-MM-yyyy", java.util.Locale("hi", "IN")).format(java.util.Date(payment.dateEpochMillis))}"))
                    addAction(list, "Edit / Delete", 0xFF607D8B.toInt()) {
                        AlertDialog.Builder(this@MainActivity).setItems(arrayOf("Edit", "Delete")) { _, choice ->
                            if (choice == 0) paymentDialog(direction, payment)
                            else AlertDialog.Builder(this@MainActivity).setMessage("यह भुगतान हटाने पर बिल का बकाया दोबारा गणना होगा।").setNegativeButton("रद्द", null).setPositiveButton("हटाएँ") { _, _ -> lifecycleScope.launch(Dispatchers.IO) { try { PaymentRepository(db).delete(payment.id); withContext(Dispatchers.Main) { toast("भुगतान हटाया गया") } } catch (e: Exception) { withContext(Dispatchers.Main) { toast(e.message ?: "हटाया नहीं जा सका") } } } }.show()
                        }.show()
                    }
                }
            }
        }
    }

    private fun paymentDialog(direction: String, existing: PaymentEntity?, preselectedPartyId: Long? = null) {
        lifecycleScope.launch {
            val parties = withContext(Dispatchers.IO) { db.dao().observeParties().first() }
            if (parties.isEmpty()) { toast("पहले ग्राहक/सप्लायर जोड़ें"); return@launch }
            val expectedKind = if (direction == "IN") "SALE" else "PURCHASE"
            val invoices = withContext(Dispatchers.IO) { db.dao().observeInvoices(expectedKind).first().filter { it.paidPaise < it.totalPaise || it.id == existing?.invoiceId } }
            val box = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(6), dp(18), 0) }
            val party = Spinner(this@MainActivity).apply { adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, parties.map { it.name }); setSelection(parties.indexOfFirst { it.id == (existing?.partyId ?: preselectedPartyId) }.coerceAtLeast(0)) }
            val billOptions = listOf("बिना बिल लिंक") + invoices.map { "${it.invoiceNumber} — बाकी ₹${(it.totalPaise - it.paidPaise) / 100.0}" }
            val bill = Spinner(this@MainActivity).apply { adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, billOptions); setSelection((invoices.indexOfFirst { it.id == existing?.invoiceId } + 1).coerceAtLeast(0)) }
            val mode = Spinner(this@MainActivity).apply { adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("CASH", "UPI", "BANK")); setSelection(listOf("CASH", "UPI", "BANK").indexOf(existing?.mode ?: "CASH").coerceAtLeast(0)) }
            val bankAccounts = withContext(Dispatchers.IO) { db.dao().observeBankAccounts().first() }
            val bank = Spinner(this@MainActivity).apply {
                adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, bankAccounts.map { it.accountName })
                setSelection(bankAccounts.indexOfFirst { it.id == existing?.bankAccountId }.coerceAtLeast(0))
                visibility = if (mode.selectedItem.toString() == "BANK") View.VISIBLE else View.GONE
            }
            mode.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                    bank.visibility = if (mode.selectedItem.toString() == "BANK") View.VISIBLE else View.GONE
                }
            }
            val amount = editField("रकम (₹)", existing?.amountPaise?.div(100.0)?.toString().orEmpty())
            val note = editField("नोट", existing?.note.orEmpty())
            box.addView(TextView(this@MainActivity).apply { text = "पार्टी" }); box.addView(party)
            box.addView(TextView(this@MainActivity).apply { text = "संबंधित बिल (वैकल्पिक)" }); box.addView(bill)
            box.addView(TextView(this@MainActivity).apply { text = "भुगतान माध्यम" }); box.addView(mode)
            box.addView(TextView(this@MainActivity).apply { text = "बैंक अकाउंट" }); box.addView(bank)
            box.addView(amount); box.addView(note)
            val dialog = AlertDialog.Builder(this@MainActivity).setTitle(if (existing == null) titleForPayment(direction) else "भुगतान एडिट").setView(box).setNegativeButton("रद्द", null).setPositiveButton("सेव", null).create()
            dialog.setOnShowListener {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val value = amount.text.toString().toDoubleOrNull()
                if (value == null || value <= 0) { amount.error = "रकम सही भरें"; return@setOnClickListener }
                val selectedInvoice = if (bill.selectedItemPosition == 0) null else invoices[bill.selectedItemPosition - 1]
                val selectedMode = mode.selectedItem.toString()
                if (selectedMode == "BANK" && bankAccounts.isEmpty()) { toast("पहले Bank Accounts में बैंक का नाम जोड़ें"); return@setOnClickListener }
                val selectedBankId = if (selectedMode == "BANK") bankAccounts.getOrNull(bank.selectedItemPosition)?.id else null
                val input = PaymentRepository.PaymentInput(parties[party.selectedItemPosition].id, selectedInvoice?.id, direction, selectedMode, (value * 100).toLong(), existing?.dateEpochMillis ?: System.currentTimeMillis(), note.text.toString(), selectedBankId)
                lifecycleScope.launch(Dispatchers.IO) { try { val repo = PaymentRepository(db); if (existing == null) repo.create(input) else repo.update(existing.id, input); withContext(Dispatchers.Main) { toast("भुगतान सेव हो गया"); dialog.dismiss() } } catch (e: Exception) { withContext(Dispatchers.Main) { toast(e.message ?: "भुगतान सेव नहीं हुआ") } } }
            } }
            dialog.show()
        }
    }

    private fun titleForPayment(direction: String) = if (direction == "IN") "नया Payment In" else "नया Payment Out"

    private fun showInvoices(kind: String) {
        val title = if (kind == "SALE") "बिक्री बिल" else "खरीद बिल"
        val page = base(title)
        addAction(page, "+ नया बिल बनाएं", 0xFF2E8B57.toInt()) { invoiceDialog(kind, null) }
        addAction(page, "← Dashboard") { showDashboard() }
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        page.addView(list)
        lifecycleScope.launch {
            db.dao().observeInvoices(kind).collectLatest { invoices ->
                list.removeAllViews()
                if (invoices.isEmpty()) list.addView(emptyLabel("अभी कोई बिल नहीं है"))
                invoices.forEach { invoice ->
                    val party = withContext(Dispatchers.IO) { db.dao().observeParties().first().firstOrNull { it.id == invoice.partyId }?.name ?: "पार्टी #${invoice.partyId}" }
                    val row = card("बिल: ${invoice.invoiceNumber}\nपार्टी: $party\nतारीख: ${java.text.SimpleDateFormat("dd-MM-yyyy", java.util.Locale("hi", "IN")).format(java.util.Date(invoice.dateEpochMillis))}\nकुल: ₹${invoice.totalPaise / 100.0}  |  भुगतान: ₹${invoice.paidPaise / 100.0}")
                    list.addView(row)
                    addAction(list, "${invoice.invoiceNumber} — Edit / Delete", 0xFF607D8B.toInt()) {
                        AlertDialog.Builder(this@MainActivity).setItems(arrayOf("PDF / Print", "Edit", "Delete")) { _, which ->
                            if (which == 0) lifecycleScope.launch { printInvoice(invoice) }
                            else if (which == 1) lifecycleScope.launch { val lines = withContext(Dispatchers.IO) { db.dao().getInvoiceLines(invoice.id) }; invoiceDialog(kind, invoice, lines) }
                            else AlertDialog.Builder(this@MainActivity).setMessage("यह बिल हट जाएगा। जुड़े भुगतान पहले हटाने होंगे।").setNegativeButton("रद्द", null).setPositiveButton("हटाएँ") { _, _ -> lifecycleScope.launch(Dispatchers.IO) { try { InvoiceRepository(db).delete(invoice.id); if (invoice.attachmentPath.isNotBlank()) resolvePurchaseAttachment(invoice.attachmentPath).delete(); withContext(Dispatchers.Main) { toast("बिल हटाया गया") } } catch (e: Exception) { withContext(Dispatchers.Main) { toast(e.message ?: "बिल हटाया नहीं जा सका") } } } }.show()
                        }.show()
                    }
                }
            }
        }
    }

    private suspend fun printInvoice(invoice: InvoiceEntity) {
        try {
            val details = withContext(Dispatchers.IO) {
                val party = db.dao().observeParties().first().firstOrNull { it.id == invoice.partyId }
                    ?: error("पार्टी नहीं मिली")
                val lines = db.dao().getInvoiceLines(invoice.id)
                val profile = db.dao().getBusinessProfile() ?: BusinessProfileEntity()
                Triple(party, lines, profile)
            }
            val party = details.first
            val lines = details.second
            val profile = details.third
            val date = java.text.SimpleDateFormat("dd-MM-yyyy", java.util.Locale("en", "IN"))
                .format(java.util.Date(invoice.dateEpochMillis))
            fun esc(value: String) = value.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;")
            fun money(paise: Long) = "₹${java.util.Locale.US.let { "%.2f".format(it, paise / 100.0) }}"
            val rows = lines.joinToString("") { line ->
                "<tr><td>${esc(line.itemNameSnapshot)}</td><td>${esc(line.hsnCode)}</td><td>${line.quantity}</td>" +
                    "<td>${money(line.ratePaise)}</td>" +
                    "<td>${money(line.taxablePaise)}</td>" +
                    "<td>${line.gstRatePercent}%</td><td>${money(line.cgstPaise + line.sgstPaise + line.igstPaise)}</td></tr>"
            }
            // GST-rate-wise summary makes the tax breakup auditable from the saved invoice lines.
            val gstSummary = lines.groupBy { it.gstRatePercent }.toSortedMap().entries.joinToString("") { (rate, rateLines) ->
                val taxable = rateLines.sumOf { it.taxablePaise }
                val cgst = rateLines.sumOf { it.cgstPaise }
                val sgst = rateLines.sumOf { it.sgstPaise }
                val igst = rateLines.sumOf { it.igstPaise }
                "<tr><td>${rate}%</td><td>${money(taxable)}</td><td>${money(cgst)}</td><td>${money(sgst)}</td><td>${money(igst)}</td><td>${money(cgst + sgst + igst)}</td></tr>"
            }
            val kindLabel = if (invoice.kind == "SALE") "SALES INVOICE / बिक्री बिल" else "PURCHASE BILL / खरीद बिल"
            val stateCaption = if (invoice.kind == "SALE") "Place of Supply" else "Supplier State"
            val relevantState = if (invoice.kind == "SALE") invoice.buyerStateCode else invoice.sellerStateCode
            val html = """<!doctype html><html><head><meta charset="utf-8"><style>
                body{font-family:sans-serif;color:#222;padding:24px}h1{font-size:22px;margin-bottom:4px}
                .muted{color:#555;font-size:12px}table{width:100%;border-collapse:collapse;margin-top:20px}
                th,td{border:1px solid #bbb;padding:9px;text-align:left;font-size:13px}th{background:#edf2f5}
                .totals{margin:20px 0 0 auto;width:260px}.totals div{padding:5px;text-align:right}
                .grand{font-weight:bold;border-top:2px solid #333;font-size:17px}
                </style></head><body><h1>${esc(profile.businessName)}</h1><div>${esc(profile.address)} | ${esc(profile.mobile)}</div><div>GSTIN: ${esc(profile.gstin)} | $kindLabel</div>
                <p><b>Bill No:</b> ${esc(invoice.invoiceNumber)} &nbsp; <b>Date:</b> $date<br><b>$stateCaption:</b> ${esc(stateDisplay(relevantState))}</p>
                <p><b>Party:</b> ${esc(party.name)}<br><b>Mobile:</b> ${esc(party.mobile)}<br>
                <b>Address:</b> ${esc(party.address)}<br><b>GSTIN:</b> ${esc(party.gstNumber)}</p>
                <table><thead><tr><th>Item</th><th>HSN</th><th>Qty</th><th>Rate</th><th>Taxable</th><th>GST%</th><th>GST</th></tr></thead><tbody>$rows</tbody></table>
                <h3>GST Rate-wise Summary / GST दर के अनुसार सारांश</h3>
                <table><thead><tr><th>GST Rate</th><th>Taxable Value</th><th>CGST</th><th>SGST</th><th>IGST</th><th>Total Tax</th></tr></thead><tbody>$gstSummary</tbody></table>
                <div class="totals"><div>Subtotal: ₹${"%.2f".format(invoice.subtotalPaise / 100.0)}</div>
                <div>Discount: ₹${"%.2f".format(invoice.discountPaise / 100.0)}</div>
                <div>Taxable Value: ₹${"%.2f".format(invoice.subtotalPaise.minus(invoice.discountPaise) / 100.0)}</div>
                <div>CGST: ₹${"%.2f".format(invoice.cgstPaise / 100.0)}</div>
                <div>SGST: ₹${"%.2f".format(invoice.sgstPaise / 100.0)}</div>
                <div>IGST: ₹${"%.2f".format(invoice.igstPaise / 100.0)}</div>
                <div class="grand">Total: ₹${"%.2f".format(invoice.totalPaise / 100.0)}</div>
                <div>Paid: ₹${"%.2f".format(invoice.paidPaise / 100.0)}</div>
                <div>Balance: ₹${"%.2f".format((invoice.totalPaise - invoice.paidPaise) / 100.0)}</div></div>
                <p><b>Note:</b> ${esc(invoice.note)}</p><p class="muted">Computer generated document</p></body></html>"""
            val webView = WebView(this)
            printWebView = webView // Keep the WebView alive until the Android print job has consumed its adapter.
            webView.settings.javaScriptEnabled = false
            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String?) {
                    try {
                        val manager = getSystemService(Context.PRINT_SERVICE) as PrintManager
                        val adapter = view.createPrintDocumentAdapter("BFP-${invoice.invoiceNumber}")
                        manager.print("BFP-${invoice.invoiceNumber}", adapter,
                            PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                                .setResolution(PrintAttributes.Resolution("bfp", "BFP", 300, 300))
                                .setMinMargins(PrintAttributes.Margins.NO_MARGINS).build())
                    } catch (e: Exception) { toast("Print शुरू नहीं हुआ: ${e.message}") }
                }
            }
            webView.loadDataWithBaseURL(null, html, "text/HTML", "UTF-8", null)
        } catch (e: Exception) { toast(e.message ?: "बिल प्रिंट नहीं हुआ") }
    }

    private fun stateDisplay(code: String): String = indianStates.firstOrNull { it.second == code }?.first ?: code

    private val indianStates = listOf(
        "01 - जम्मू और कश्मीर" to "01", "02 - हिमाचल प्रदेश" to "02", "03 - पंजाब" to "03",
        "04 - चंडीगढ़" to "04", "05 - उत्तराखंड" to "05", "06 - हरियाणा" to "06",
        "07 - दिल्ली" to "07", "08 - राजस्थान" to "08", "09 - उत्तर प्रदेश" to "09",
        "10 - बिहार" to "10", "11 - सिक्किम" to "11", "12 - अरुणाचल प्रदेश" to "12",
        "13 - नागालैंड" to "13", "14 - मणिपुर" to "14", "15 - मिजोरम" to "15",
        "16 - त्रिपुरा" to "16", "17 - मेघालय" to "17", "18 - असम" to "18",
        "19 - पश्चिम बंगाल" to "19", "20 - झारखंड" to "20", "21 - ओडिशा" to "21",
        "22 - छत्तीसगढ़" to "22", "23 - मध्य प्रदेश" to "23", "24 - गुजरात" to "24",
        "26 - दादरा और नगर हवेली तथा दमन और दीव" to "26", "27 - महाराष्ट्र" to "27",
        "28 - आंध्र प्रदेश (पुराना कोड)" to "28", "29 - कर्नाटक" to "29", "30 - गोवा" to "30",
        "31 - लक्षद्वीप" to "31", "32 - केरल" to "32", "33 - तमिलनाडु" to "33",
        "34 - पुडुचेरी" to "34", "35 - अंडमान और निकोबार द्वीपसमूह" to "35",
        "36 - तेलंगाना" to "36", "37 - आंध्र प्रदेश" to "37", "38 - लद्दाख" to "38"
    )

    private fun invoiceDialog(kind: String, existing: InvoiceEntity?, existingLines: List<InvoiceLineEntity> = emptyList(), preselectedPartyId: Long? = null) {
        lifecycleScope.launch {
            val parties = withContext(Dispatchers.IO) { db.dao().observeParties().first().filter { it.partyType == "BOTH" || it.partyType == if (kind == "SALE") "CUSTOMER" else "SUPPLIER" } }
            val businessProfile = withContext(Dispatchers.IO) { db.dao().getBusinessProfile() ?: BusinessProfileEntity() }
            if (parties.isEmpty() && kind == "PURCHASE") { toast("पहले सप्लायर जोड़ें"); return@launch }
            val box = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(6), dp(18), 0) }
            val number = editField("बिल नंबर", existing?.invoiceNumber ?: "${if (kind == "SALE") "S" else "P"}-${System.currentTimeMillis() % 1000000}")
            // New sales default to CREDIT; CASH creates an immediate cash receipt.
            var saleSettlementMode = "CREDIT"
            val saleSettlementPicker = if (kind == "SALE" && existing == null) RadioGroup(this@MainActivity).apply {
                orientation = RadioGroup.HORIZONTAL
                setPadding(0, dp(4), 0, dp(8))
                val cash = RadioButton(this@MainActivity).apply { id = View.generateViewId(); text = "CASH"; textSize = 16f }
                val credit = RadioButton(this@MainActivity).apply { id = View.generateViewId(); text = "CREDIT"; textSize = 16f; isChecked = true }
                addView(cash, RadioGroup.LayoutParams(0, -2, 1f))
                addView(credit, RadioGroup.LayoutParams(0, -2, 1f))
                setOnCheckedChangeListener { _, checkedId -> saleSettlementMode = if (checkedId == cash.id) "CASH" else "CREDIT" }
            } else null
            var selectedPartyId: Long? = existing?.partyId ?: preselectedPartyId
            val party = if (kind == "PURCHASE") Spinner(this@MainActivity).apply {
                adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, parties.map { "${it.name} (${it.mobile})" })
                setSelection(parties.indexOfFirst { it.id == selectedPartyId }.coerceAtLeast(0))
            } else null
            val initialParty = parties.firstOrNull { it.id == selectedPartyId } ?: parties.firstOrNull()
            val initialPartyState = initialParty?.gstNumber?.takeIf { it.length >= 2 }?.substring(0, 2) ?: businessProfile.stateCode
            val initialCounterState = if (kind == "SALE") existing?.buyerStateCode ?: initialPartyState else existing?.sellerStateCode ?: initialPartyState
            val stateSpinner = Spinner(this@MainActivity).apply {
                adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, indianStates.map { it.first })
                setSelection(indianStates.indexOfFirst { it.second == initialCounterState }.coerceAtLeast(indianStates.indexOfFirst { it.second == businessProfile.stateCode }.coerceAtLeast(0)))
            }
            val stateLabel = TextView(this@MainActivity).apply { text = if (kind == "SALE") "Place of Supply / ग्राहक का राज्य" else "Supplier State / सप्लायर का राज्य"; setPadding(0, dp(8), 0, dp(3)) }
            val salePartyPicker = if (kind == "SALE") LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(4), 0, dp(6)) } else null
            if (kind == "SALE") {
                val search = editField("ग्राहक का नाम या मोबाइल खोजें", "")
                val results = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
                val selectedInfo = TextView(this@MainActivity).apply {
                    setPadding(dp(10), dp(7), dp(10), dp(7)); setTextColor(0xFF245A78.toInt())
                    textSize = 15f; maxLines = 3; background = rounded(0xFFE8F5E9.toInt(), 10)
                }
                val changeCustomer = Button(this@MainActivity).apply {
                    text = "बदलें"; isAllCaps = false; textSize = 12f; minHeight = dp(38); minimumHeight = dp(38)
                }
                val customerSummaryRow = LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                    addView(selectedInfo, LinearLayout.LayoutParams(0, -2, 1f))
                    addView(changeCustomer, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(6) })
                }
                val customerSearchPanel = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
                val customerResultsScroll = ScrollView(this@MainActivity).apply {
                    layoutParams = LinearLayout.LayoutParams(-1, dp(170)); isVerticalScrollBarEnabled = true; addView(results)
                }
                val saleInvoices = withContext(Dispatchers.IO) { db.dao().observeInvoices("SALE").first() }
                val customerPayments = withContext(Dispatchers.IO) { db.dao().observePayments().first() }
                val partiesMutable = parties.toMutableList()
                var refreshCustomerRows: (String) -> Unit = {}
                fun chooseCustomer(customer: PartyEntity) {
                    selectedPartyId = customer.id
                    val code = customer.gstNumber.takeIf { it.length >= 2 }?.substring(0, 2) ?: businessProfile.stateCode
                    val idx = indianStates.indexOfFirst { it.second == code }; if (idx >= 0) stateSpinner.setSelection(idx)
                    // Keep the selected customer header intentionally minimal: name only.
                    selectedInfo.text = customer.name
                    selectedInfo.setBackgroundColor(0xFFE8F5E9.toInt())
                    customerSearchPanel.visibility = View.GONE
                    customerSummaryRow.visibility = View.VISIBLE
                    refreshCustomerRows(search.text.toString())
                }
                fun addCustomerInline() {
                    val form = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(6), dp(18), 0) }
                    val n = editField("ग्राहक का नाम *", ""); val m = editField("मोबाइल नंबर", ""); val a = editField("पता", ""); val g = editField("GSTIN (वैकल्पिक)", ""); val b = editField("जन्मदिन DD-MM-YYYY (वैकल्पिक)", "")
                    listOf(n,m,a,g,b).forEach { form.addView(it) }
                    val dlg = AlertDialog.Builder(this@MainActivity).setTitle("नई ग्राहक पार्टी जोड़ें").setView(form).setNegativeButton("रद्द", null).setPositiveButton("सेव करें", null).create()
                    dlg.setOnShowListener { dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val nameText=n.text.toString().trim(); val gstText=g.text.toString().trim().uppercase(); val birthdayText=b.text.toString().trim()
                        if (nameText.isBlank()) { n.error="ग्राहक का नाम जरूरी है"; return@setOnClickListener }
                        if (gstText.isNotBlank() && !GstCalculator.validateGstin(gstText)) { g.error="GSTIN सही नहीं है"; return@setOnClickListener }
                        val birthdayIso = if (birthdayText.isBlank()) "" else try { require(Regex("\\d{2}-\\d{2}-\\d{4}").matches(birthdayText)); val parsed=java.text.SimpleDateFormat("dd-MM-yyyy", java.util.Locale.US).apply { isLenient=false }.parse(birthdayText); java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(parsed!!) } catch (_:Exception) { b.error="DD-MM-YYYY सही भरें"; return@setOnClickListener }
                        lifecycleScope.launch {
                            try {
                                val customer=PartyEntity(name=nameText,mobile=m.text.toString().trim(),address=a.text.toString().trim(),gstNumber=gstText,partyType="CUSTOMER",birthday=birthdayIso)
                                val id=withContext(Dispatchers.IO) { db.dao().insertParty(customer) }
                                val saved=customer.copy(id=id); partiesMutable.add(saved); dlg.dismiss(); chooseCustomer(saved); search.setText(saved.name); toast("नया ग्राहक सेव हो गया")
                            } catch(e:Exception) { toast(e.message ?: "ग्राहक सेव नहीं हुआ") }
                        }
                    } }; dlg.show()
                }
                // Empty-state message is itself the primary Add Customer action.
                selectedInfo.setOnClickListener {
                    if (selectedPartyId == null) addCustomerInline()
                }
                refreshCustomerRows = { query ->
                    results.removeAllViews()
                    partiesMutable.filter { it.name.contains(query, true) || it.mobile.contains(query, true) }.forEach { customer ->
                        val due = (saleInvoices.filter { it.partyId == customer.id }.sumOf { (it.totalPaise-it.paidPaise).coerceAtLeast(0L) } - customerPayments.filter { it.partyId == customer.id && it.direction == "IN" && it.invoiceId == null }.sumOf { it.amountPaise }).coerceAtLeast(0L)
                        val item=TextView(this@MainActivity).apply { text="${customer.name}\n${customer.mobile.ifBlank { "मोबाइल उपलब्ध नहीं" }}  |  बकाया ₹${"%.2f".format(java.util.Locale.US,due/100.0)}"; textSize=14f; setTextColor(0xFF303846.toInt()); setPadding(dp(12),dp(10),dp(12),dp(10)); background=rounded(if(customer.id==selectedPartyId) 0xFFE8F5E9.toInt() else 0xFFF3F5F7.toInt(),12); setOnClickListener { chooseCustomer(customer) } }
                        results.addView(item)
                    }
                    if (results.childCount==0) results.addView(emptyLabel("कोई ग्राहक नहीं मिला"))
                }
                search.addTextChangedListener(object: android.text.TextWatcher { override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){}; override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){refreshCustomerRows(s?.toString().orEmpty())}; override fun afterTextChanged(s:android.text.Editable?){} })
                val picker = salePartyPicker!!
                customerSearchPanel.addView(search)
                customerSearchPanel.addView(customerResultsScroll)
                customerSearchPanel.addView(Button(this@MainActivity).apply {
                    text = "+ नया ग्राहक जोड़ें"; isAllCaps = false
                    setOnClickListener { addCustomerInline() }
                })
                picker.addView(customerSummaryRow)
                picker.addView(customerSearchPanel)
                changeCustomer.setOnClickListener {
                    customerSummaryRow.visibility = View.GONE
                    customerSearchPanel.visibility = View.VISIBLE
                    search.requestFocus()
                }
                if (initialParty != null) chooseCustomer(initialParty) else {
                    selectedInfo.text = "ग्राहक चुनें / Select Customer"
                    selectedInfo.isClickable = false
                    customerSummaryRow.visibility = View.GONE
                    customerSearchPanel.visibility = View.VISIBLE
                }
                refreshCustomerRows("")
            } else {
                party!!.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                    override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
                    override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                        val code = parties[position].gstNumber.takeIf { it.length >= 2 }?.substring(0, 2) ?: businessProfile.stateCode
                        val index = indianStates.indexOfFirst { it.second == code }; if (index >= 0) stateSpinner.setSelection(index)
                    }
                }
            }
            val linesBox = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
            data class InvoiceLineViews(val code: EditText, val name: EditText, val quantity: EditText, val rate: EditText, val gst: EditText, val hsn: EditText, var itemId: Long = 0L, var summary: TextView? = null)
            val rows = mutableListOf<InvoiceLineViews>()
            var itemListScroll: ScrollView? = null
            var editingRow: InvoiceLineViews? = null
            var continuousRecognizer: SpeechRecognizer? = null
            var recognizerListenerReady = false
            var addButton: Button? = null
            var voiceStatus: TextView? = null
            var voiceToggle: Button? = null
            val voiceHandler = Handler(Looper.getMainLooper())
            var restartVoiceRunnable: Runnable? = null
            var startVoiceEntryForCurrentRow: (() -> Unit)? = null
            val entryBox = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(6), 0, dp(8)) }
            val draftCode = editField("Item Code (वैकल्पिक)", "")
            val draftName = editField("आइटम का नाम (जैसे सूट / डिजाइन)", "")
            val draftQty = editField("मात्रा / Quantity", "1")
            val draftRate = editField("रेट (₹)", "")
            val draftPurchaseGst = editField("इस आइटम का GST % (सप्लायर बिल के अनुसार)", "0")
            draftPurchaseGst.visibility = if (kind == "PURCHASE") View.VISIBLE else View.GONE
            val draftHsn = editField("HSN Code (वैकल्पिक)", "")
            // Sale-only, device-local memory of previously sold item names and their last sale rates.
            // This is intentionally separate from the stock Item Master: suggestions must never create stock.
            val savedSaleItemsPanel = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL; visibility = View.GONE
                setPadding(dp(8), dp(4), dp(8), dp(4)); background = rounded(0xFFF5F7FA.toInt(), 10)
            }
            fun readSavedSaleItems(): MutableList<Pair<String, String>> {
                val prefs = getSharedPreferences("bfp_saved_sale_item_suggestions_v1", MODE_PRIVATE)
                val raw = prefs.getString("items", "[]") ?: "[]"
                return try {
                    val array = org.json.JSONArray(raw)
                    (0 until array.length()).mapNotNull { i ->
                        val obj = array.optJSONObject(i) ?: return@mapNotNull null
                        val name = obj.optString("name").trim()
                        if (name.isBlank()) null else name to obj.optString("rate", "")
                    }.toMutableList()
                } catch (_: Exception) { mutableListOf() }
            }
            fun saveSoldItemSuggestion(name: String, rate: String) {
                if (kind != "SALE" || name.isBlank()) return
                val items = readSavedSaleItems()
                val existingIndex = items.indexOfFirst { it.first.equals(name.trim(), ignoreCase = true) }
                if (existingIndex >= 0) items.removeAt(existingIndex)
                items.add(0, name.trim() to rate)
                val array = org.json.JSONArray()
                items.take(300).forEach { (savedName, savedRate) ->
                    array.put(org.json.JSONObject().put("name", savedName).put("rate", savedRate))
                }
                getSharedPreferences("bfp_saved_sale_item_suggestions_v1", MODE_PRIVATE).edit().putString("items", array.toString()).apply()
            }
            fun refreshSavedSaleItemSuggestions(query: String) {
                savedSaleItemsPanel.removeAllViews()
                if (kind != "SALE" || query.isBlank()) { savedSaleItemsPanel.visibility = View.GONE; return }
                val matches = readSavedSaleItems().filter { it.first.startsWith(query.trim(), ignoreCase = true) }.take(8)
                if (matches.isEmpty()) { savedSaleItemsPanel.visibility = View.GONE; return }
                savedSaleItemsPanel.visibility = View.VISIBLE
                savedSaleItemsPanel.addView(TextView(this@MainActivity).apply {
                    text = "पहले बेचे गए आइटम"; textSize = 13f; setTypeface(null, android.graphics.Typeface.BOLD)
                    setTextColor(0xFF37474F.toInt()); setPadding(0, dp(3), 0, dp(5))
                })
                matches.forEach { (savedName, savedRate) ->
                    val suggestion = TextView(this@MainActivity).apply {
                        text = "$savedName    |    पिछला बिक्री रेट: ₹${savedRate.ifBlank { "—" }}"
                        textSize = 14f; setTextColor(0xFF263238.toInt()); setPadding(dp(8), dp(9), dp(8), dp(9))
                        isClickable = true; isFocusable = true
                        setOnClickListener {
                            draftName.setText(savedName); draftName.setSelection(draftName.text.length)
                            if (savedRate.isNotBlank()) draftRate.setText(savedRate)
                            savedSaleItemsPanel.visibility = View.GONE
                        }
                    }
                    savedSaleItemsPanel.addView(suggestion)
                    savedSaleItemsPanel.addView(View(this@MainActivity).apply { setBackgroundColor(0xFFE0E5EA.toInt()) }, LinearLayout.LayoutParams(-1, dp(1)))
                }
            }
            // DEV111: compact, separate Qty / Item Name / Rate row matching the approved billing layout.
            val manualEntryRow = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            draftQty.hint = "Qty"
            draftName.hint = "Item Name"
            draftRate.hint = "Rate (₹)"
            manualEntryRow.addView(draftQty, LinearLayout.LayoutParams(0, dp(52), 0.85f).apply { rightMargin = dp(6) })
            manualEntryRow.addView(draftName, LinearLayout.LayoutParams(0, dp(52), 2.7f).apply { rightMargin = dp(6) })
            manualEntryRow.addView(draftRate, LinearLayout.LayoutParams(0, dp(52), 1.15f))
            entryBox.addView(manualEntryRow)
            entryBox.addView(savedSaleItemsPanel)
            entryBox.addView(draftCode)
            entryBox.addView(draftPurchaseGst)
            entryBox.addView(draftHsn)
            draftName.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { refreshSavedSaleItemSuggestions(s?.toString().orEmpty()) }
                override fun afterTextChanged(s: android.text.Editable?) {}
            })
            val itemListHeader = TextView(this@MainActivity).apply { text = "जोड़ी गई आइटम"; textSize = 14f; setTypeface(null, android.graphics.Typeface.BOLD); setPadding(0, dp(10), 0, dp(4)) }
            val itemCountButton = Button(this@MainActivity).apply { text = "0 आइटम"; textSize = 12f; minHeight = dp(34); minimumHeight = dp(34); setPadding(dp(8), 0, dp(8), 0); isAllCaps = false }
            val listHeader = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; addView(itemListHeader, LinearLayout.LayoutParams(0, -2, 1f)); addView(itemCountButton, LinearLayout.LayoutParams(-2, -2)) }
            fun refreshItemCount() { itemCountButton.text = "${rows.size} आइटम" }
            fun addLine(itemName: String = "", qtyValue: String = "1", ratePaise: Long? = null, gstRate: Double = 0.0, hsnCode: String = "", itemCode: String = "", itemId: Long = 0L) {
                val code = editField("", itemCode).apply { visibility = View.GONE }
                val name = editField("", itemName).apply { visibility = View.GONE }
                val qty = editField("", qtyValue).apply { visibility = View.GONE }
                val rate = editField("", ratePaise?.let { (it / 100.0).toString() }.orEmpty()).apply { visibility = View.GONE }
                val gst = editField("", gstRate.toString()).apply { visibility = View.GONE }
                val hsn = editField("", hsnCode).apply { visibility = View.GONE }
                val row = InvoiceLineViews(code, name, qty, rate, gst, hsn, itemId)
                rows.add(row)
                val summary = TextView(this@MainActivity).apply {
                    textSize = 14f; minLines = 2; setPadding(dp(12), dp(10), dp(12), dp(10)); setTextColor(0xFF263238.toInt()); background = rounded(0xFFF2F5F7.toInt(), 10)
                }
                row.summary = summary
                fun refreshSummary() {
                    val index = rows.indexOf(row) + 1
                    val q = row.quantity.text.toString()
                    val r = row.rate.text.toString().toDoubleOrNull() ?: 0.0
                    val quantity = q.toDoubleOrNull() ?: 0.0
                    summary.text = "$index. ${row.name.text}\nमात्रा (Qty): $q     रेट (Rate): ₹${"%.2f".format(r)}\nकुल (Amount): ₹${"%.2f".format(quantity * r)}" + if (kind == "PURCHASE") "     GST: ${row.gst.text}%" else ""
                }
                refreshSummary()
                val actions = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.END }
                val container = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 0, 0, dp(5)); addView(summary) }
                val edit = Button(this@MainActivity).apply {
                    text = "Edit"; isAllCaps = false; minHeight = dp(36)
                    setOnClickListener {
                        draftCode.setText(row.code.text); draftName.setText(row.name.text); draftQty.setText(row.quantity.text); draftRate.setText(row.rate.text); draftPurchaseGst.setText(row.gst.text); draftHsn.setText(row.hsn.text)
                        editingRow = row
                        addButton?.text = "बदलाव सेव करें"
                        draftName.requestFocus()
                        toast("आइटम एडिट करें और बदलाव सेव करें")
                    }
                }
                val delete = Button(this@MainActivity).apply {
                    text = "हटाएँ"; isAllCaps = false; minHeight = dp(36)
                    setOnClickListener {
                        AlertDialog.Builder(this@MainActivity).setMessage("${row.name.text} हटाएँ?").setNegativeButton("रद्द", null).setPositiveButton("हटाएँ") { _, _ ->
                            rows.remove(row); linesBox.removeView(container); refreshItemCount(); rows.forEachIndexed { i, r -> r.summary?.text = r.summary?.text?.toString()?.replace(Regex("^\\d+\\."), "${i + 1}.") ?: "" }
                            if (editingRow === row) { editingRow = null; addButton?.text = "+ आइटम जोड़ें" }
                        }.show()
                    }
                }
                actions.addView(edit); actions.addView(delete)
                container.addView(actions)
                linesBox.addView(container)
                refreshItemCount()
                itemListScroll?.post { itemListScroll?.fullScroll(View.FOCUS_DOWN) }
            }
            fun commitDraftLine(): Boolean {
                val n = draftName.text.toString().trim(); val q = draftQty.text.toString().trim().toDoubleOrNull(); val r = draftRate.text.toString().trim().toDoubleOrNull()
                val itemGst = if (kind == "PURCHASE") draftPurchaseGst.text.toString().trim().toDoubleOrNull() else 0.0
                if (n.isBlank() || q == null || q <= 0 || r == null || r < 0 || itemGst == null || itemGst !in 0.0..100.0) { toast("आइटम का नाम, मात्रा, रेट और सप्लायर बिल वाला GST सही भरें"); return false }
                val paise = kotlin.math.round(r * 100).toLong()
                if (kind == "SALE") saveSoldItemSuggestion(n, r.toString())
                val target = editingRow
                if (target != null) {
                    target.code.setText(draftCode.text); target.name.setText(n); target.quantity.setText(q.toString()); target.rate.setText(r.toString()); target.gst.setText(itemGst.toString()); target.hsn.setText(draftHsn.text)
                    val summary = target.summary
                    val index = rows.indexOf(target) + 1
                    summary?.text = "$index. $n\nमात्रा (Qty): ${q}     रेट (Rate): ₹${"%.2f".format(r)}\nकुल (Amount): ₹${"%.2f".format(q * r)}" + if (kind == "PURCHASE") "     GST: $itemGst%" else ""
                    editingRow = null; addButton?.text = "+ आइटम जोड़ें"
                    toast("आइटम अपडेट हो गई")
                } else addLine(n, q.toString(), paise, itemGst, draftHsn.text.toString().trim(), draftCode.text.toString().trim())
                draftCode.setText(""); draftName.setText(""); draftQty.setText("1"); draftRate.setText(""); draftPurchaseGst.setText("0"); draftHsn.setText("")
                return true
            }
            fun applyDraftItemCode(scannedOrTypedCode: String) {
                lifecycleScope.launch {
                    val normalized = scannedOrTypedCode.trim()
                    if (normalized.isBlank()) { toast("पहले Item Code डालें या स्कैन करें"); return@launch }
                    val found = withContext(Dispatchers.IO) { db.dao().getItemByCode(normalized) }
                    if (found == null) toast("इस कोड का आइटम नहीं मिला: $normalized") else {
                        draftCode.setText(found.itemCode); draftName.setText(found.name)
                        draftRate.setText(((if (kind == "SALE") found.saleRatePaise else found.purchaseRatePaise) / 100.0).toString())
                        draftHsn.setText(found.hsnCode)
                        toast("आइटम मिला: ${found.name}")
                    }
                }
            }
            val lookup = Button(this@MainActivity).apply { text = "Item Code से आइटम खोजें / Search Item"; setOnClickListener { applyDraftItemCode(draftCode.text.toString()) } }
            val scan = Button(this@MainActivity).apply {
                text = "▦ Scan Barcode / QR"
                setOnClickListener { GmsBarcodeScanning.getClient(this@MainActivity).startScan().addOnSuccessListener { barcode ->
                    val value = barcode.rawValue.orEmpty(); if (value.isBlank()) toast("स्कैन में कोड नहीं मिला") else applyDraftItemCode(value)
                }.addOnCanceledListener { toast("स्कैन रद्द किया गया") }.addOnFailureListener { error -> toast("Scanner उपलब्ध नहीं: ${error.localizedMessage ?: "फिर कोशिश करें"}") } }
            }
            fun processSpokenItem(spoken: String) {
                val parsed = parseVoiceItemPhrase(spoken)
                if (parsed.first.isBlank()) { draftName.setText(spoken.trim()); voiceStatus?.text = "आइटम समझ नहीं आया — जाँचें"; return }
                draftName.setText(parsed.first)
                draftQty.setText(parsed.second ?: "1")
                parsed.third?.let { draftRate.setText(it) }
                if (parsed.third != null) {
                    val added = commitDraftLine()
                    if (added) { voiceStatus?.text = "${rows.size} आइटम जुड़ीं — अगली बोलें"; toast("जुड़ गया: ${parsed.first} | मात्रा ${parsed.second ?: "1"} | रेट ₹${parsed.third}") }
                } else { voiceStatus?.text = "रेट समझ नहीं आया — रेट भरकर जोड़ें"; toast("रेट जाँचकर आइटम जोड़ें") }
            }
            fun stopContinuousVoice() {
                continuousVoiceEntry = false
                restartVoiceRunnable?.let { voiceHandler.removeCallbacks(it) }
                try { continuousRecognizer?.stopListening() } catch (_: Exception) { }
                try { continuousRecognizer?.cancel() } catch (_: Exception) { }
                voiceToggle?.text = "🎙 बोलना शुरू करें"
                voiceStatus?.text = "Voice बंद है"
            }
            fun beginContinuousVoice() {
                if (!SpeechRecognizer.isRecognitionAvailable(this@MainActivity)) { toast("इस फोन में Speech Recognition उपलब्ध नहीं है"); return }
                if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 107)
                    toast("Microphone अनुमति दें, फिर Voice बटन दबाएँ")
                    return
                }
                if (continuousRecognizer == null) continuousRecognizer = SpeechRecognizer.createSpeechRecognizer(this@MainActivity)
                val locale = getSharedPreferences("bfp_settings", MODE_PRIVATE).getString("voice_input_language", "en-IN") ?: "en-IN"
                val speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, locale)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                    putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
                }
                if (!recognizerListenerReady) {
                    continuousRecognizer?.setRecognitionListener(object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) { voiceStatus?.text = "🎙 सुन रहा है… आइटम बोलें" }
                        override fun onBeginningOfSpeech() { voiceStatus?.text = "🎙 आवाज़ मिली…" }
                        override fun onRmsChanged(rmsdB: Float) { }
                        override fun onBufferReceived(buffer: ByteArray?) { }
                        override fun onEndOfSpeech() { voiceStatus?.text = "आवाज़ समझ रहा है…" }
                        override fun onError(error: Int) {
                            if (!continuousVoiceEntry) return
                            if (error != SpeechRecognizer.ERROR_NO_MATCH && error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                                voiceStatus?.text = "Voice में रुकावट — फिर शुरू करें"
                                stopContinuousVoice()
                                return
                            }
                            voiceStatus?.text = "फिर सुनने की तैयारी…"
                            restartVoiceRunnable = Runnable { if (continuousVoiceEntry) try { continuousRecognizer?.startListening(speechIntent) } catch (_: Exception) { stopContinuousVoice() } }
                            voiceHandler.postDelayed(restartVoiceRunnable!!, if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) 350L else 900L)
                        }
                        override fun onResults(results: Bundle?) {
                            if (!continuousVoiceEntry) return
                            val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                            if (spoken.isNotBlank()) processSpokenItem(spoken)
                            restartVoiceRunnable = Runnable { if (continuousVoiceEntry) try { continuousRecognizer?.startListening(speechIntent) } catch (_: Exception) { stopContinuousVoice() } }
                            voiceHandler.postDelayed(restartVoiceRunnable!!, 450L)
                        }
                        override fun onPartialResults(partialResults: Bundle?) { }
                        override fun onEvent(eventType: Int, params: Bundle?) { }
                    })
                    recognizerListenerReady = true
                }
                continuousVoiceEntry = true
                voiceToggle?.text = "■ Voice रोकें"
                try { continuousRecognizer?.startListening(speechIntent) } catch (e: Exception) { stopContinuousVoice(); toast("Voice शुरू नहीं हो सकी: ${e.localizedMessage}") }
            }
            val voicePanel = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(8), dp(5), dp(8), dp(5)); background = rounded(0xFFEAF4FF.toInt(), 12)
            }
            voiceStatus = TextView(this@MainActivity).apply { text = "Voice बंद है"; textSize = 12f; setTextColor(0xFF245A78.toInt()) }
            voiceToggle = Button(this@MainActivity).apply { text = "🎙 बोलना शुरू करें"; isAllCaps = false; minHeight = dp(40); setOnClickListener { if (continuousVoiceEntry) stopContinuousVoice() else beginContinuousVoice() } }
            voicePanel.addView(voiceStatus, LinearLayout.LayoutParams(0, -2, 1f)); voicePanel.addView(voiceToggle)
            entryBox.addView(voicePanel, 0)
            startVoiceEntryForCurrentRow = { beginContinuousVoice() }
            startNextVoiceEntry = { if (continuousVoiceEntry) beginContinuousVoice() }
            if (existingLines.isEmpty()) { /* first item is entered in the fixed editor above */ }
            else existingLines.forEach { line -> addLine(line.itemNameSnapshot, line.quantity.toString(), line.ratePaise, line.gstRatePercent, line.hsnCode, line.itemCodeSnapshot, line.itemId) }
            val discount = editField("छूट (₹)", (existing?.discountPaise?.div(100.0) ?: 0.0).toString())
            val invoiceGst = editField("फाइनल बिल पर GST दर % (पूरे बिल पर)", (existingLines.firstOrNull()?.gstRatePercent ?: 5.0).toString())
            var saleGstEnabled = kind != "SALE" || existing == null || existingLines.any { it.gstRatePercent > 0.0 }
            invoiceGst.visibility = if (kind == "SALE" && saleGstEnabled) View.VISIBLE else View.GONE
            val taxInfo = TextView(this@MainActivity).apply {
                text = if (kind == "SALE") "GST अलग-अलग सूट पर नहीं; फाइनल बिल की कुल टैक्सेबल रकम पर एक ही दर से लगेगा।" else "हर Purchase आइटम का GST सप्लायर के बिल के अनुसार अलग-अलग दर्ज करें।"
                setTextColor(0xFF245A78.toInt()); setPadding(0, dp(6), 0, dp(6))
            }
            val note = editField("नोट", existing?.note.orEmpty())
            pendingPurchaseAttachmentPath = if (kind == "PURCHASE") existing?.attachmentPath.orEmpty() else ""
            editingOriginalAttachmentPath = pendingPurchaseAttachmentPath
            val attachmentStatus = TextView(this@MainActivity).apply {
                text = if (pendingPurchaseAttachmentPath.isBlank()) "कोई सप्लायर बिल अटैच नहीं है (वैकल्पिक)" else "अटैच: ${resolvePurchaseAttachment(pendingPurchaseAttachmentPath).name}"
                setTextColor(0xFF245A78.toInt()); setPadding(0, dp(8), 0, dp(4))
            }
            if (saleSettlementPicker != null) {
                val billTopRow = LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    addView(number, LinearLayout.LayoutParams(0, -2, 1.15f))
                    addView(saleSettlementPicker, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(8) })
                }
                box.addView(billTopRow)
            } else box.addView(number)
            if (kind == "PURCHASE") { box.addView(TextView(this@MainActivity).apply { text = "पार्टी चुनें" }); party?.let { box.addView(it) } }
            box.addView(stateLabel); box.addView(stateSpinner)
            box.addView(entryBox); box.addView(lookup); box.addView(scan)
            val itemActions = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val add = Button(this@MainActivity).apply {
                text = "Save Item"; isAllCaps = false
                setOnClickListener { commitDraftLine() }
            }
            addButton = add
            val saveAndNew = Button(this@MainActivity).apply {
                text = "Save & New"; isAllCaps = false
                setOnClickListener { if (commitDraftLine()) { draftName.requestFocus(); toast("अगला आइटम दर्ज करें") } }
            }
            itemActions.addView(add, LinearLayout.LayoutParams(0, dp(48), 1f).apply { rightMargin = dp(6) })
            itemActions.addView(saveAndNew, LinearLayout.LayoutParams(0, dp(48), 1f))
            box.addView(itemActions)
            box.addView(listHeader); box.addView(linesBox)
            box.addView(discount)
            if (kind == "SALE") {
                val gstToggle = CheckBox(this@MainActivity).apply {
                    text = "GST लगाएँ (5% / दर बदल सकते हैं)"
                    textSize = 15f
                    isChecked = existing == null || existingLines.any { it.gstRatePercent > 0.0 }
                    setPadding(dp(4), dp(8), dp(4), dp(8))
                }
                invoiceGst.setText(if (existingLines.firstOrNull()?.gstRatePercent?.let { it > 0.0 } == true) existingLines.first().gstRatePercent.toString() else "5")
                invoiceGst.visibility = if (gstToggle.isChecked) View.VISIBLE else View.GONE
                gstToggle.isChecked = saleGstEnabled
                gstToggle.setOnCheckedChangeListener { _, checked ->
                    saleGstEnabled = checked
                    invoiceGst.visibility = if (checked) View.VISIBLE else View.GONE
                    taxInfo.text = if (checked) "GST चुना गया है — फाइनल बिल पर निर्धारित दर लागू होगी।" else "बिना GST बिल — GST शून्य रहेगा।"
                }
                box.addView(gstToggle)
                box.addView(invoiceGst)
                box.addView(taxInfo)
            } else {
                box.addView(invoiceGst); box.addView(taxInfo)
            }
            box.addView(note)
            if (kind == "PURCHASE") {
                purchaseAttachmentStatusView = attachmentStatus
                val attachmentButton = Button(this@MainActivity).apply {
                    text = if (pendingPurchaseAttachmentPath.isBlank()) "सप्लायर बिल की फोटो / PDF जोड़ें" else "दूसरी फोटो / PDF चुनें"
                    setOnClickListener { choosePurchaseAttachment.launch(arrayOf("image/*", "application/pdf")) }
                }
                purchaseAttachmentButtonView = attachmentButton
                val removeAttachment = Button(this@MainActivity).apply {
                    text = "अटैचमेंट हटाएँ"
                    setOnClickListener {
                        val old = pendingPurchaseAttachmentPath
                        pendingPurchaseAttachmentPath = ""
                        attachmentStatus.text = "कोई सप्लायर बिल अटैच नहीं है (वैकल्पिक)"
                        attachmentButton.text = "सप्लायर बिल की फोटो / PDF जोड़ें"
                        purchaseAttachmentOpenButtonView?.visibility = View.GONE
                        if (old.isNotBlank() && old != editingOriginalAttachmentPath) lifecycleScope.launch(Dispatchers.IO) { resolvePurchaseAttachment(old).delete() }
                    }
                }
                val scanAttachment = Button(this@MainActivity).apply {
                    text = "बिल से टेक्स्ट पढ़ें (OCR)"
                    isEnabled = pendingPurchaseAttachmentPath.isNotBlank() && resolvePurchaseAttachment(pendingPurchaseAttachmentPath).isFile
                    setOnClickListener {
                        val path = pendingPurchaseAttachmentPath
                        if (path.isBlank()) { toast("पहले सप्लायर बिल की फोटो या PDF जोड़ें"); return@setOnClickListener }
                        isEnabled = false
                        text = "बिल पढ़ा जा रहा है…"
                        lifecycleScope.launch {
                            try {
                                val file = resolvePurchaseAttachment(path)
                                val extracted = extractSupplierBillText(file)
                                if (extracted.isBlank()) throw IllegalStateException("बिल से टेक्स्ट नहीं पढ़ा जा सका। साफ फोटो लेकर फिर कोशिश करें।")
                                val itemMaster = withContext(Dispatchers.IO) { db.dao().observeItems().first() }
                                showOcrDraftDialog(extracted, note, itemMaster, parties.getOrNull(party!!.selectedItemPosition)?.gstNumber.orEmpty()) { candidates ->
                                    linesBox.removeAllViews()
                                    rows.clear()
                                    candidates.forEach { candidate ->
                                        val matched = matchSupplierBillItem(candidate.description, itemMaster)
                                        addLine(
                                            itemName = matched?.name ?: candidate.description,
                                            qtyValue = candidate.quantity.toString(),
                                            ratePaise = kotlin.math.round(candidate.rateRupees * 100.0).toLong(),
                                            gstRate = matched?.gstRatePercent ?: 0.0,
                                            hsnCode = matched?.hsnCode.orEmpty(),
                                            itemCode = matched?.itemCode.orEmpty(),
                                            itemId = matched?.id ?: 0L
                                        )
                                    }
                                    toast("${candidates.size} संभावित आइटम ड्राफ्ट में जोड़े गए। सभी विवरण और GST जाँचें।")
                                }
                            } catch (e: Exception) {
                                toast(e.message ?: "बिल पढ़ने में समस्या हुई")
                            } finally {
                                isEnabled = pendingPurchaseAttachmentPath.isNotBlank()
                                text = "बिल से टेक्स्ट पढ़ें (OCR)"
                            }
                        }
                    }
                }
                val openAttachment = Button(this@MainActivity).apply {
                    text = "अटैच सप्लायर बिल खोलें"
                    visibility = if (pendingPurchaseAttachmentPath.isNotBlank() && resolvePurchaseAttachment(pendingPurchaseAttachmentPath).isFile) View.VISIBLE else View.GONE
                    setOnClickListener { openPurchaseAttachment(pendingPurchaseAttachmentPath) }
                }
                box.addView(attachmentStatus); box.addView(attachmentButton); box.addView(openAttachment); box.addView(scanAttachment); box.addView(removeAttachment)
                purchaseAttachmentOpenButtonView = openAttachment
            }
            // DEV110: one continuous billing canvas. Only the selected customer
            // name stays pinned at the top; item entry, growing item list, totals,
            // GST and notes move together in one scrollable area.
            val customerHeader = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(8), dp(14), dp(8))
                setBackgroundColor(0xFFF7F9FC.toInt())
            }
            if (kind == "SALE") salePartyPicker?.let { customerHeader.addView(it) }
            val bodyScroll = ScrollView(this@MainActivity).apply {
                isFillViewport = false
                isVerticalScrollBarEnabled = true
                addView(box)
            }
            itemListScroll = bodyScroll
            val root = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(0xFFF7F9FC.toInt())
                val brandBar = LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(16), dp(13), dp(16), dp(13))
                    setBackgroundColor(0xFF102B4E.toInt())
                }
                brandBar.addView(TextView(this@MainActivity).apply {
                    text = "BFP Business Pro"
                    textSize = 20f
                    setTextColor(0xFFFFFFFF.toInt())
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                }, LinearLayout.LayoutParams(0, -2, 1f))
                brandBar.addView(TextView(this@MainActivity).apply {
                    text = "BILLING"
                    textSize = 12f
                    setTextColor(0xFFFFFFFF.toInt())
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                })
                addView(brandBar, LinearLayout.LayoutParams(-1, -2))
                addView(customerHeader, LinearLayout.LayoutParams(-1, -2))
                addView(bodyScroll, LinearLayout.LayoutParams(-1, 0, 1f))
            }
            var invoiceSaved = false
            val dialog = AlertDialog.Builder(this@MainActivity)
                .setTitle(if (existing == null) "नया $titleText" else "बिल एडिट")
                .setView(root)
                .setNegativeButton("रद्द", null)
                .setNeutralButton("Save Bill", null)
                .setPositiveButton("Voice", null)
                .create()
            dialog.setOnDismissListener {
                continuousVoiceEntry = false
                restartVoiceRunnable?.let { voiceHandler.removeCallbacks(it) }
                try { continuousRecognizer?.cancel(); continuousRecognizer?.destroy() } catch (_: Exception) { }
                continuousRecognizer = null
                recognizerListenerReady = false
                startNextVoiceEntry = null
                pendingVoiceHandler = null
                pendingVoiceInput = null
                if (!invoiceSaved && pendingPurchaseAttachmentPath.isNotBlank() && pendingPurchaseAttachmentPath != editingOriginalAttachmentPath) resolvePurchaseAttachment(pendingPurchaseAttachmentPath).delete()
                purchaseAttachmentStatusView = null; purchaseAttachmentButtonView = null; purchaseAttachmentOpenButtonView = null
            }
            dialog.setOnShowListener {
                dialog.window?.setLayout(-1, -1)
                dialog.window?.setBackgroundDrawableResource(android.R.color.white)
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                if (draftName.text.toString().isNotBlank() || draftRate.text.toString().isNotBlank()) {
                    if (!commitDraftLine()) return@setOnClickListener
                }
                val lineInputs = rows.mapNotNull { row ->
                    val itemName = row.name.text.toString().trim()
                    val quantity = row.quantity.text.toString().toDoubleOrNull()
                    val rateValue = row.rate.text.toString().toDoubleOrNull()
                    val gstValue = if (kind == "PURCHASE") row.gst.text.toString().toDoubleOrNull() else if (saleGstEnabled) invoiceGst.text.toString().toDoubleOrNull() else 0.0
                    if (itemName.isBlank() || quantity == null || rateValue == null || gstValue == null || gstValue !in 0.0..100.0) null else
                        InvoiceRepository.LineInput(itemName, quantity, kotlin.math.round(rateValue * 100).toLong(), gstValue, row.hsn.text.toString().trim(), row.itemId, row.code.text.toString().trim())
                }
                val d = discount.text.toString().toDoubleOrNull()
                val saleGstValid = kind != "SALE" || !saleGstEnabled || (invoiceGst.text.toString().toDoubleOrNull()?.let { it in 0.0..100.0 } ?: false)
                if (number.text.isNullOrBlank() || lineInputs.size != rows.size || lineInputs.isEmpty() || d == null || d < 0 || !saleGstValid) { toast("हर आइटम का नाम, मात्रा, रेट और छूट सही भरें; बिक्री में फाइनल GST दर भी जाँचें"); return@setOnClickListener }
                val selectedParty = if (kind == "SALE") parties.firstOrNull { it.id == selectedPartyId } else parties.getOrNull(party!!.selectedItemPosition)
                if (selectedParty == null) { toast("पहले ग्राहक चुनें या नया ग्राहक जोड़ें"); return@setOnClickListener }
                val selectedCounterState = indianStates[stateSpinner.selectedItemPosition].second
                val sellerState = if (kind == "SALE") businessProfile.stateCode else selectedCounterState
                val buyerState = if (kind == "SALE") selectedCounterState else businessProfile.stateCode
                val input = InvoiceRepository.InvoiceInput(
                    invoiceNumber = number.text.toString(), partyId = selectedParty.id, kind = kind,
                    dateEpochMillis = existing?.dateEpochMillis ?: System.currentTimeMillis(),
                    discountPaise = kotlin.math.round(d * 100).toLong(), taxPaise = 0,
                    note = note.text.toString(), sellerStateCode = sellerState, buyerStateCode = buyerState,
                    lines = lineInputs,
                    attachmentPath = if (kind == "PURCHASE") pendingPurchaseAttachmentPath else ""
                )
                val previewLines = lineInputs.mapIndexed { index, line ->
                    val qty = line.quantity
                    val rate = line.ratePaise / 100.0
                    val amount = qty * rate
                    "${index + 1}. ${line.itemName}  |  मात्रा: $qty  |  रेट: ₹${"%.2f".format(rate)}  |  कुल: ₹${"%.2f".format(amount)}"
                }
                val subtotalRupees = lineInputs.sumOf { it.quantity * it.ratePaise } / 100.0
                val previewText = buildString {
                    append("बिल नंबर: ${input.invoiceNumber}\n")
                    append("ग्राहक / पार्टी: ${selectedParty.name}\n\n")
                    append(previewLines.joinToString("\n"))
                    append("\n\nआइटम कुल: ₹${"%.2f".format(subtotalRupees)}")
                    append("\nछूट: ₹${"%.2f".format(d)}")
                    append("\nGST: ${if (kind == "SALE") { if (saleGstEnabled) "${invoiceGst.text}%" else "बिना GST" } else "प्रत्येक खरीद आइटम के अनुसार"}")
                    append("\n\nकृपया सभी आइटम, मात्रा और रेट जाँच लें। अभी बिल सेव नहीं हुआ है।")
                }
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("बिल Preview – सेव करने से पहले जाँचें")
                    .setMessage(previewText)
                    .setNegativeButton("वापस Edit करें", null)
                    .setPositiveButton("सही है – फाइनल बिल सेव करें") { _, _ ->
                        lifecycleScope.launch(Dispatchers.IO) { try {
                            val repo = InvoiceRepository(db)
                            if (existing == null) {
                                if (kind == "SALE" && saleSettlementMode == "CASH") repo.createCashSale(input) else repo.create(input)
                            } else repo.update(existing.id, input)
                            invoiceSaved = true
                            if (existing != null && existing.attachmentPath.isNotBlank() && existing.attachmentPath != input.attachmentPath) resolvePurchaseAttachment(existing.attachmentPath).delete()
                            withContext(Dispatchers.Main) { toast("बिल सेव हो गया"); dialog.dismiss() }
                        } catch (e: Exception) { withContext(Dispatchers.Main) { toast(e.message ?: "बिल सेव नहीं हुआ") } } }
                    }.show()
                }
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).visibility = View.GONE
            }
            dialog.show()
            dialog.window?.setLayout(-1, (resources.displayMetrics.heightPixels * 0.92f).toInt())
        }
    }

    /** OCR is a review-only helper: it never posts or saves purchase/accounting entries. */
    private suspend fun extractSupplierBillText(file: File): String = withContext(Dispatchers.IO) {
        require(file.isFile && file.length() <= 20L * 1024L * 1024L) { "बिल फाइल उपलब्ध नहीं है या 20 MB से बड़ी है" }
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            if (file.extension.equals("pdf", true)) {
                val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                try {
                    val renderer = PdfRenderer(descriptor)
                    try {
                        require(renderer.pageCount in 1..20) { "PDF में 1 से 20 पेज होने चाहिए" }
                        val pages = mutableListOf<String>()
                        for (index in 0 until renderer.pageCount) {
                            renderer.openPage(index).use { page ->
                                val scale = minOf(2.0, 2400.0 / maxOf(page.width, page.height))
                                val bitmap = Bitmap.createBitmap((page.width * scale).toInt().coerceAtLeast(1), (page.height * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                                try {
                                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                    pages += recognizeSupplierBillImage(recognizer, InputImage.fromBitmap(bitmap, 0))
                                } finally { bitmap.recycle() }
                            }
                        }
                        pages.mapIndexed { index, text -> "--- PDF पेज ${index + 1} ---\n$text" }.joinToString("\n")
                    } finally { renderer.close() }
                } finally { descriptor.close() }
            } else {
                recognizeSupplierBillImage(recognizer, InputImage.fromFilePath(this@MainActivity, Uri.fromFile(file)))
            }
        } finally { recognizer.close() }
    }

    private suspend fun recognizeSupplierBillImage(
        recognizer: com.google.mlkit.vision.text.TextRecognizer,
        image: InputImage
    ): String = suspendCancellableCoroutine { continuation ->
        recognizer.process(image)
            .addOnSuccessListener { result -> if (continuation.isActive) continuation.resume(result.text) }
            .addOnFailureListener { error -> if (continuation.isActive) continuation.resumeWithException(error) }
    }

    private fun showOcrDraftDialog(
        extractedText: String,
        noteField: EditText,
        itemMaster: List<ItemEntity>,
        supplierGstin: String,
        onApplyLines: (List<SupplierBillLineCandidate>) -> Unit
    ) {
        val metadata = SupplierBillVerifier.extract(extractedText)
        val invoiceNumber = metadata.invoiceNumber ?: "नहीं मिला"
        val gstin = metadata.gstin ?: "नहीं मिला"
        val total = metadata.invoiceTotalRupees?.let { "₹${"%.2f".format(it)}" } ?: "नहीं मिला"
        val invoiceDate = metadata.invoiceDate ?: "नहीं मिला"
        val candidates = SupplierBillOcrParser.parse(extractedText)
        val checks = SupplierBillVerifier.checks(metadata, candidates, supplierGstin)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(8), dp(16), dp(8)) }
        content.addView(TextView(this).apply {
            text = "पहचाने गए संभावित विवरण\nबिल नंबर: $invoiceNumber\nबिल तारीख: $invoiceDate\nGSTIN: $gstin\nकुल राशि: $total\n\nजाँच रिपोर्ट:\n" + checks.joinToString("\n") { "${if (it.passed == true) "✓" else if (it.passed == false) "⚠" else "•"} ${it.label}: ${it.detail}" } + "\n\nसंभावित आइटम पंक्तियाँ: ${candidates.size}\nयह OCR अनुमान है। मूल बिल से मिलान अनिवार्य है."
            textSize = 14f; setTextColor(0xFF263448.toInt())
        })
        if (candidates.isNotEmpty()) {
            content.addView(TextView(this).apply {
                text = "ड्राफ्ट आइटम (सेव नहीं होंगे):\n" + candidates.mapIndexed { index, row ->
                    val matched = matchSupplierBillItem(row.description, itemMaster)
                    "${index + 1}. ${matched?.name ?: row.description} | Qty ${row.quantity} | Rate ₹${"%.2f".format(row.rateRupees)} | ${if (matched != null) "Item Master से मिला" else "Item Master से लिंक नहीं"}"
                }.joinToString("\n")
                setPadding(0, dp(10), 0, dp(10)); textSize = 13f; setTextColor(0xFF245A78.toInt())
            })
        } else {
            content.addView(TextView(this).apply {
                text = "कोई स्पष्ट Item + Qty + Rate + Amount पंक्ति नहीं मिली। कृपया नीचे OCR टेक्स्ट देखकर हाथ से खरीद आइटम भरें।"
                setPadding(0, dp(8), 0, dp(8)); setTextColor(0xFF9A4D00.toInt())
            })
        }
        val preview = EditText(this).apply {
            setText(extractedText); minLines = 7; maxLines = 16; gravity = Gravity.TOP
            hint = "पहचाना गया बिल टेक्स्ट"
        }
        content.addView(preview)
        val scroll = ScrollView(this).apply { addView(content) }
        val builder = AlertDialog.Builder(this)
            .setTitle("सप्लायर बिल OCR – Draft Preview")
            .setView(root)
            .setNegativeButton("बंद करें", null)
            .setNeutralButton("नोट में जोड़ें") { _, _ ->
                val old = noteField.text.toString().trim()
                val newText = preview.text.toString().trim()
                noteField.setText(listOf(old, "OCR बिल टेक्स्ट:", newText).filter { it.isNotBlank() }.joinToString("\n"))
                toast("OCR टेक्स्ट नोट में जोड़ा गया। खरीद सेव करने से पहले विवरण जाँचें।")
            }
        if (candidates.isNotEmpty()) {
            builder.setPositiveButton("${candidates.size} आइटम ड्राफ्ट में जोड़ें") { _, _ ->
                onApplyLines(candidates)
            }
        }
        builder.show()
    }

    private fun matchSupplierBillItem(description: String, items: List<ItemEntity>): ItemEntity? {
        val normalizedDescription = description.uppercase().replace(Regex("[^A-Z0-9]"), "")
        val codeMatch = items.firstOrNull { item ->
            val code = item.itemCode.uppercase().replace(Regex("[^A-Z0-9]"), "")
            code.length >= 3 && normalizedDescription.contains(code)
        }
        if (codeMatch != null) return codeMatch
        val normalizedName = { value: String -> value.uppercase().replace(Regex("[^A-Z0-9]"), "") }
        items.firstOrNull { normalizedName(it.name) == normalizedDescription && normalizedDescription.isNotBlank() }?.let { return it }
        val possible = items.filter { item ->
            val name = normalizedName(item.name)
            name.length >= 5 && (normalizedDescription.contains(name) || name.contains(normalizedDescription))
        }
        return possible.singleOrNull() // Ambiguous partial matches must be linked manually.
    }

    private fun resolvePurchaseAttachment(path: String): File {
        val root = File(filesDir, "purchase_attachments").canonicalFile
        val candidate = if (File(path).isAbsolute) File(path).canonicalFile else File(filesDir, path).canonicalFile
        require(candidate.path.startsWith(root.path + File.separator)) { "अटैचमेंट का फाइल पथ सुरक्षित नहीं है" }
        return candidate
    }

    private fun openPurchaseAttachment(path: String) {
        try {
            val file = resolvePurchaseAttachment(path)
            require(file.isFile) { "अटैचमेंट फाइल नहीं मिली" }
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            val mime = if (file.extension.equals("pdf", true)) "application/pdf" else "image/*"
            startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        } catch (e: Exception) {
            toast(e.message ?: "फाइल खोलने के लिए उपयुक्त ऐप उपलब्ध नहीं है")
        }
    }

    private fun copyPurchaseAttachment(uri: Uri): File {
        val name = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: "supplier-bill"
        val safeName = name.substringAfterLast('/').substringAfterLast('\\').replace(Regex("[^A-Za-z0-9._-]"), "_").take(80).ifBlank { "supplier-bill" }
        val extension = when {
            safeName.substringAfterLast('.', "").lowercase() in setOf("jpg", "jpeg", "png", "webp", "pdf") -> safeName.substringAfterLast('.').lowercase()
            contentResolver.getType(uri) == "application/pdf" -> "pdf"
            contentResolver.getType(uri)?.startsWith("image/") == true -> "img"
            else -> error("केवल फोटो या PDF अटैच करें")
        }
        val directory = File(filesDir, "purchase_attachments").apply { if (!exists()) mkdirs() }
        val baseName = safeName.substringBeforeLast('.', safeName).ifBlank { "supplier-bill" }
        val destination = File(directory, "${UUID.randomUUID()}-$baseName.$extension")
        try {
            contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "चुनी गई फाइल पढ़ी नहीं जा सकी" }
                destination.outputStream().use { output ->
                    val buffer = ByteArray(8192); var total = 0L
                    while (true) {
                        val count = input.read(buffer); if (count < 0) break
                        total += count; require(total <= 20L * 1024L * 1024L) { "अटैचमेंट 20 MB से छोटा होना चाहिए" }
                        output.write(buffer, 0, count)
                    }
                }
            }
            require(destination.length() > 0L) { "चुनी गई फाइल खाली है" }
            return destination
        } catch (failure: Exception) {
            destination.delete()
            throw failure
        }
    }

    private val titleText: String get() = "बिल"

    private fun launchVoiceRecognition(target: EditText, onResult: (String) -> Unit) {
        // English remains the default; the persistent Utilities setting can switch recognition to Hindi.
        val locale = getSharedPreferences("bfp_settings", MODE_PRIVATE).getString("voice_input_language", "en-IN") ?: "en-IN"
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale)
            putExtra(RecognizerIntent.EXTRA_PROMPT, if (locale == "hi-IN") "आइटम का नाम और रेट बोलें" else "Speak item name and rate")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }
        try { pendingVoiceInput = target; pendingVoiceHandler = onResult; voiceInput.launch(intent) }
        catch (_: Exception) { pendingVoiceInput = null; pendingVoiceHandler = null; continuousVoiceEntry = false; toast("इस फोन में Voice Input उपलब्ध नहीं है") }
    }

    /** Splits a spoken item phrase into an item name and an optional trailing price. */
    private fun parseVoiceItemPhrase(raw: String): Triple<String, String?, String?> {
        val originalTokens = raw.replace(Regex("[^\\p{L}\\p{N}.]+"), " ").trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        val tokens = originalTokens.map { it.lowercase(java.util.Locale.ROOT) }
        val small = mapOf(
            "zero" to 0L, "oh" to 0L, "one" to 1L, "two" to 2L, "three" to 3L, "four" to 4L, "five" to 5L, "six" to 6L, "seven" to 7L, "eight" to 8L, "nine" to 9L,
            "ten" to 10L, "eleven" to 11L, "twelve" to 12L, "thirteen" to 13L, "fourteen" to 14L, "fifteen" to 15L, "sixteen" to 16L, "seventeen" to 17L, "eighteen" to 18L, "nineteen" to 19L,
            "बीस" to 20L, "इक्कीस" to 21L, "बाईस" to 22L, "तेईस" to 23L, "चौबीस" to 24L, "पच्चीस" to 25L, "तीस" to 30L, "चालीस" to 40L, "पचास" to 50L, "साठ" to 60L, "सत्तर" to 70L, "अस्सी" to 80L, "नब्बे" to 90L,
            "एक" to 1L, "दो" to 2L, "तीन" to 3L, "चार" to 4L, "पांच" to 5L, "पाँच" to 5L, "छह" to 6L, "सात" to 7L, "आठ" to 8L, "नौ" to 9L, "दस" to 10L, "ग्यारह" to 11L, "बारह" to 12L, "तेरह" to 13L, "चौदह" to 14L, "पंद्रह" to 15L, "सोलह" to 16L, "सत्रह" to 17L, "अठारह" to 18L, "उन्नीस" to 19L
        )
        val tens = mapOf("twenty" to 20L, "thirty" to 30L, "forty" to 40L, "fifty" to 50L, "sixty" to 60L, "seventy" to 70L, "eighty" to 80L, "ninety" to 90L)
        val currency = setOf("rupee", "rupees", "rs", "inr", "रुपया", "रुपये", "रुपए", "रूपये", "रूपए", "रु")
        fun numeric(token: String): Boolean = token.toLongOrNull() != null || token.toDoubleOrNull() != null || token in small || token in tens || token in setOf("hundred", "हंड्रेड", "सौ", "thousand", "हजार", "हज़ार", "लाख")
        fun parseNumber(words: List<String>): Double? {
            var total = 0.0; var current = 0.0
            for (t in words.filterNot { it in currency || it == "and" || it == "और" }) {
                val digit = t.toDoubleOrNull()
                when {
                    digit != null -> current += digit
                    small[t] != null -> current += small.getValue(t)
                    tens[t] != null -> current += tens.getValue(t)
                    t == "hundred" || t == "हंड्रेड" || t == "सौ" -> current = (if (current == 0.0) 1.0 else current) * 100.0
                    t == "thousand" || t == "हजार" || t == "हज़ार" -> { total += (if (current == 0.0) 1.0 else current) * 1000.0; current = 0.0 }
                    t == "लाख" -> { total += (if (current == 0.0) 1.0 else current) * 100000.0; current = 0.0 }
                }
            }
            return (total + current).takeIf { it > 0.0 && it <= 100000000.0 }
        }
        var qEnd = 0
        while (qEnd < tokens.size && numeric(tokens[qEnd])) qEnd++
        val quantity = if (qEnd > 0) parseNumber(tokens.subList(0, qEnd)) else null
        var rateStart = tokens.size
        var i = tokens.lastIndex
        while (i >= qEnd && (numeric(tokens[i]) || tokens[i] in currency || tokens[i] == "and" || tokens[i] == "और")) { rateStart = i; i-- }
        val rateWords = if (rateStart < tokens.size) tokens.subList(rateStart, tokens.size) else emptyList()
        val rate = parseNumber(rateWords)
        val itemEnd = if (rate != null) rateStart else tokens.size
        val item = originalTokens.drop(qEnd).take((itemEnd - qEnd).coerceAtLeast(0)).joinToString(" ").trim()
        return Triple(item.ifBlank { raw.trim() }, quantity?.toString()?.removeSuffix(".0"), rate?.let { String.format(java.util.Locale.US, "%.2f", it).trimEnd('0').trimEnd('.') })
    }

    private fun editField(hintText: String, value: String): EditText = EditText(this).apply { hint = hintText; setText(value); setSingleLine(true) }
    private fun emptyLabel(message: String) = TextView(this).apply { text = message; textSize = 15f; setPadding(dp(12), dp(18), dp(12), dp(18)); setTextColor(0xFF455A64.toInt()) }
    private fun card(message: String) = TextView(this).apply { text = message; textSize = 15f; setTextColor(0xFF34394B.toInt()); setPadding(dp(16), dp(14), dp(16), dp(14)); background = rounded(0xFFFFFFFF.toInt(), 16); elevation = dp(2).toFloat() }
    private fun showNotReady(feature: String) { toast("$feature का पूरा लेन-देन मॉड्यूल अभी एकीकृत नहीं है") }
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()

    private fun runBackup(uri: android.net.Uri) { io.execute { try { DatabaseBackup.export(this, uri); BackupHistory.record(this, "Manual", "सफल"); runOnUiThread { toast("Backup सफलतापूर्वक बन गया"); showBackupMenu() } } catch (e: Exception) { BackupHistory.record(this, "Manual", "असफल", e.message ?: "Unknown error"); runOnUiThread { toast("Backup असफल: ${e.message}"); showBackupMenu() } } } }
    private fun runRestore(uri: android.net.Uri) {
        io.execute {
            try {
                val preview = DatabaseBackup.preview(this, uri)
                val created = preview.createdAtEpochMillis?.let { java.text.SimpleDateFormat("dd-MM-yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date(it)) } ?: "पुराना बैकअप"
                val message = "फॉर्मेट: ${preview.format}\nबैकअप तारीख: $created\nDatabase Schema: ${preview.databaseVersion}\nDatabase Size: ${preview.databaseBytes / 1024} KB\nPurchase Attachments: ${preview.attachmentCount} (${preview.attachmentBytes / 1024} KB)\nजाँच: ${preview.integrity}\n\nRestore करने पर मौजूदा डेटा इस बैकअप से बदल जाएगा। पहले वर्तमान डेटा का अलग Backup बना लें।"
                runOnUiThread { AlertDialog.Builder(this).setTitle("Restore Preview – पुष्टि करें").setMessage(message).setNegativeButton("रद्द", null).setPositiveButton("Restore करें") { _, _ -> io.execute { try { DatabaseBackup.restore(this, uri); BackupHistory.record(this, "Restore", "सफल"); runOnUiThread { toast("Restore सफल। ऐप दोबारा खोलें।"); recreate() } } catch (e: Exception) { BackupHistory.record(this, "Restore", "असफल", e.message ?: "Unknown error"); runOnUiThread { toast("Restore असफल: ${e.message}") } } } }.show() }
            } catch (e: Exception) { runOnUiThread { AlertDialog.Builder(this).setTitle("Backup मान्य नहीं").setMessage(e.message ?: "Preview जाँच असफल").setPositiveButton("ठीक", null).show() } }
        }
    }
    override fun onDestroy() { io.shutdown(); super.onDestroy() }
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
