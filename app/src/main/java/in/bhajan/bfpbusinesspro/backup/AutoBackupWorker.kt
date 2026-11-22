package `in`.bhajan.bfpbusinesspro.backup

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AutoBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences("bfp_auto_backup", Context.MODE_PRIVATE)
        val tree = prefs.getString("tree_uri", null) ?: run {
            prefs.edit().putString("last_status", "FOLDER_NOT_SELECTED").putLong("last_failure_epoch", System.currentTimeMillis()).apply()
            BackupHistory.record(applicationContext, "Automatic", "असफल", "Backup Folder नहीं चुना गया")
            return Result.failure()
        }
        return try {
            val root = DocumentFile.fromTreeUri(applicationContext, Uri.parse(tree)) ?: throw SecurityException("Backup Folder उपलब्ध नहीं है")
            if (!root.canWrite()) throw SecurityException("Backup Folder में लिखने की अनुमति नहीं है")
            val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
            val target = root.createFile("application/zip", "BFP-Auto-Backup-$stamp.zip") ?: throw IllegalStateException("Backup ZIP फाइल नहीं बन सकी")
            DatabaseBackup.export(applicationContext, target.uri)
            prefs.edit().putLong("last_success_epoch", System.currentTimeMillis()).putString("last_status", "SUCCESS").remove("last_failure_epoch").apply()
            BackupHistory.record(applicationContext, "Automatic", "सफल", target.name ?: "BFP-Auto-Backup-$stamp.zip")
            Result.success()
        } catch (e: SecurityException) {
            prefs.edit().putString("last_status", "PERMISSION_ERROR").putLong("last_failure_epoch", System.currentTimeMillis()).apply()
            BackupHistory.record(applicationContext, "Automatic", "असफल", e.message ?: "Storage permission")
            Result.failure()
        } catch (e: Exception) {
            prefs.edit().putString("last_status", "RETRY").putLong("last_failure_epoch", System.currentTimeMillis()).apply()
            BackupHistory.record(applicationContext, "Automatic", "दोबारा प्रयास", e.message ?: "Unknown error")
            Result.retry()
        }
    }
}
