package `in`.bhajan.bfpbusinesspro.backup

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/** User-opt-in daily backup to a folder selected through Android's Storage Access Framework. */
object AutoBackupScheduler {
    private const val UNIQUE_WORK = "bfp_daily_cloud_backup"
    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences("bfp_auto_backup", Context.MODE_PRIVATE).getBoolean("enabled", false)

    fun schedule(context: Context) {
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(24, TimeUnit.HOURS)
            .setConstraints(constraints).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(UNIQUE_WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
        context.getSharedPreferences("bfp_auto_backup", Context.MODE_PRIVATE).edit().putBoolean("enabled", true).apply()
    }

    fun runNow(context: Context) {
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val request = OneTimeWorkRequestBuilder<AutoBackupWorker>().setConstraints(constraints).build()
        WorkManager.getInstance(context).enqueue(request)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK)
        context.getSharedPreferences("bfp_auto_backup", Context.MODE_PRIVATE).edit().putBoolean("enabled", false).apply()
    }
}
