package `in`.bhajan.bfpbusinesspro.backup

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Lists and safely manages BFP backup ZIPs in the user-selected SAF cloud folder. */
object CloudBackupManager {
    data class BackupFile(
        val uri: Uri,
        val name: String,
        val bytes: Long,
        val modifiedAt: Long
    ) {
        fun displayLine(): String {
            val date = if (modifiedAt > 0L) SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault()).format(Date(modifiedAt)) else "तारीख उपलब्ध नहीं"
            val size = if (bytes >= 1024L * 1024L) String.format(Locale.getDefault(), "%.2f MB", bytes / (1024.0 * 1024.0)) else "${bytes / 1024L} KB"
            return "$name\n$date  •  $size"
        }
    }

    data class StorageSummary(
        val backupCount: Int,
        val totalBytes: Long,
        val oldestBackupAt: Long,
        val newestBackupAt: Long,
        val keepCount: Int,
        val cleanupCandidates: List<BackupFile>
    ) {
        val candidateBytes: Long get() = cleanupCandidates.sumOf { it.bytes.coerceAtLeast(0L) }
    }

    /** Reports only recognized BFP backup usage; SAF providers do not expose reliable whole-drive quota. */
    fun storageSummary(context: Context, keepLatest: Int = DEFAULT_KEEP_LATEST): StorageSummary {
        require(keepLatest >= 1) { "कम-से-कम एक बैकअप रखना जरूरी है" }
        val backups = list(context)
        val candidates = backups.drop(keepLatest)
        return StorageSummary(
            backupCount = backups.size,
            totalBytes = backups.sumOf { it.bytes.coerceAtLeast(0L) },
            oldestBackupAt = backups.minOfOrNull { it.modifiedAt } ?: 0L,
            newestBackupAt = backups.maxOfOrNull { it.modifiedAt } ?: 0L,
            keepCount = keepLatest,
            cleanupCandidates = candidates
        )
    }

    fun formatBytes(bytes: Long): String = when {
        bytes >= 1024L * 1024L * 1024L -> String.format(Locale.getDefault(), "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        bytes >= 1024L * 1024L -> String.format(Locale.getDefault(), "%.2f MB", bytes / (1024.0 * 1024.0))
        bytes >= 1024L -> String.format(Locale.getDefault(), "%.1f KB", bytes / 1024.0)
        else -> "$bytes B"
    }

    private const val DEFAULT_KEEP_LATEST = 10

    private fun isBfpBackup(file: DocumentFile): Boolean {
        val name = file.name ?: return false
        return file.isFile && name.endsWith(".zip", ignoreCase = true) &&
            (name.startsWith("BFP-Auto-Backup-") || name.startsWith("BFP-Backup-"))
    }

    fun list(context: Context): List<BackupFile> {
        val tree = context.getSharedPreferences("bfp_auto_backup", Context.MODE_PRIVATE).getString("tree_uri", null)
            ?: return emptyList()
        val root = DocumentFile.fromTreeUri(context, Uri.parse(tree)) ?: return emptyList()
        return root.listFiles().filter(::isBfpBackup)
            .map { BackupFile(it.uri, it.name ?: "BFP backup.zip", it.length(), it.lastModified()) }
            .sortedWith(compareByDescending<BackupFile> { it.modifiedAt }.thenByDescending { it.name })
    }

    /** Deletion is deliberately opt-in and refuses to remove the final recognized backup. */
    fun delete(context: Context, uri: Uri, userConfirmed: Boolean): String {
        require(userConfirmed) { "बैकअप हटाने के लिए स्पष्ट पुष्टि जरूरी है" }
        val backups = list(context)
        require(backups.size > 1) { "कम-से-कम एक बैकअप सुरक्षित रखना जरूरी है" }
        val selected = backups.firstOrNull { it.uri == uri } ?: error("यह BFP बैकअप चुने हुए फोल्डर में नहीं मिला")
        val file = DocumentFile.fromSingleUri(context, selected.uri) ?: error("बैकअप फाइल उपलब्ध नहीं है")
        require(isBfpBackup(file)) { "केवल पहचानी गई BFP ZIP फाइल हटाई जा सकती है" }
        require(file.delete()) { "Cloud Folder से बैकअप हटाया नहीं जा सका" }
        return selected.name
    }
}
