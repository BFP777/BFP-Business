package `in`.bhajan.bfpbusinesspro.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import `in`.bhajan.bfpbusinesspro.data.BfpDatabase
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject

/** Local ZIP backup/restore containing SQLite data and purchase attachments; legacy SQLite restore remains supported. */
object DatabaseBackup {
    private const val DB_NAME = "bfp_business_pro_5.db"
    private val SQLITE_HEADER = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)

    @Synchronized
    fun export(context: Context, destination: Uri) {
        val db = BfpDatabase.get(context)
        db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { cursor ->
            if (cursor.moveToFirst()) require(cursor.getInt(0) == 0) { "डेटाबेस बैकअप के लिए व्यस्त है; कृपया फिर कोशिश करें" }
        }
        BfpDatabase.closeForMaintenance()
        try {
            val source = context.getDatabasePath(DB_NAME)
            require(source.isFile && source.length() > 0L) { "डेटाबेस फाइल उपलब्ध नहीं है" }
            context.contentResolver.openOutputStream(destination, "w").use { raw ->
                requireNotNull(raw) { "बैकअप फाइल नहीं खुल सकी" }
                java.util.zip.ZipOutputStream(raw).use { zip ->
                    val checksums = JSONObject()
                    fun addFile(entryName: String, file: File) {
                        require(!entryName.contains("..") && !entryName.startsWith("/")) { "बैकअप में गलत फाइल पथ है" }
                        zip.putNextEntry(java.util.zip.ZipEntry(entryName))
                        FileInputStream(file).use { input ->
                            val digest = MessageDigest.getInstance("SHA-256")
                            val buffer = ByteArray(8192)
                            while (true) {
                                val count = input.read(buffer); if (count < 0) break
                                digest.update(buffer, 0, count)
                                zip.write(buffer, 0, count)
                            }
                            checksums.put(entryName, digest.digest().joinToString("") { "%02x".format(it) })
                        }
                        zip.closeEntry()
                    }
                    addFile("database/$DB_NAME", source)
                    val attachments = File(context.filesDir, "purchase_attachments")
                    if (attachments.exists()) attachments.walkTopDown().filter { it.isFile }.forEach { file ->
                        val relative = file.relativeTo(attachments).invariantSeparatorsPath
                        addFile("attachments/$relative", file)
                    }
                    val manifest = JSONObject().put("format", "BFP_BACKUP").put("formatVersion", 2)
                        .put("database", DB_NAME).put("createdAtEpochMillis", System.currentTimeMillis())
                        .put("files", checksums)
                    zip.putNextEntry(java.util.zip.ZipEntry("manifest.json"))
                    zip.write(manifest.toString().toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                }
            }
        } finally { BfpDatabase.get(context) }
    }


    data class Preview(val format: String, val createdAtEpochMillis: Long?, val databaseVersion: Int, val databaseBytes: Long, val attachmentCount: Int, val attachmentBytes: Long, val integrity: String)

    /** Fully validates a selected backup without changing the installed database or attachments. */
    @Synchronized
    fun preview(context: Context, sourceUri: Uri): Preview {
        val raw = File(context.cacheDir, "bfp_preview_input.tmp")
        val dir = File(context.cacheDir, "bfp_preview_package")
        try {
            context.contentResolver.openInputStream(sourceUri).use { input ->
                requireNotNull(input) { "चुनी गई फाइल पढ़ी नहीं जा सकी" }
                FileOutputStream(raw).use { output ->
                    val buffer = ByteArray(8192); var total = 0L
                    while (true) { val count = input.read(buffer); if (count < 0) break; total += count; require(total <= 260L * 1024L * 1024L) { "बैकअप 260 MB से बड़ा है" }; output.write(buffer, 0, count) }
                    output.fd.sync()
                }
            }
            val zipFile = raw.inputStream().use { it.read() == 0x50 && it.read() == 0x4B }
            if (!zipFile) {
                validateBackup(raw)
                val version = SQLiteDatabase.openDatabase(raw.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db -> db.rawQuery("PRAGMA user_version", null).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 } }
                return Preview("पुराना SQLite बैकअप", null, version, raw.length(), 0, 0L, "सफल")
            }
            if (dir.exists()) dir.deleteRecursively()
            require(dir.mkdirs()) { "Preview अस्थायी फोल्डर नहीं बन सका" }
            val names = mutableSetOf<String>(); var expanded = 0L; var entryCount = 0
            java.util.zip.ZipInputStream(FileInputStream(raw)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val name = entry.name.replace('\\', '/')
                    entryCount++; require(entryCount <= 5000) { "बैकअप में बहुत अधिक फाइलें हैं" }
                    require(names.add(name)) { "बैकअप में डुप्लीकेट फाइल है" }
                    require(!name.startsWith('/') && name.split('/').none { it == ".." }) { "बैकअप में असुरक्षित पथ है" }
                    require(name == "manifest.json" || name.startsWith("database/") || name.startsWith("attachments/")) { "बैकअप में अनजान सामग्री है" }
                    val out = File(dir, name); require(out.canonicalPath.startsWith(dir.canonicalPath + File.separator))
                    if (!entry.isDirectory) {
                        out.parentFile?.mkdirs()
                        FileOutputStream(out).use { dest -> val buffer = ByteArray(8192); while (true) { val count = zip.read(buffer); if (count < 0) break; expanded += count; require(expanded <= 250L * 1024L * 1024L) { "बैकअप 250 MB से बड़ा है" }; dest.write(buffer, 0, count) } }
                    }
                    zip.closeEntry()
                }
            }
            val dbFile = File(dir, "database/$DB_NAME")
            require(dbFile.isFile) { "बैकअप में डेटाबेस नहीं है" }
            val manifestFile = File(dir, "manifest.json")
            var created: Long? = null
            if (manifestFile.isFile) {
                val manifest = JSONObject(manifestFile.readText(Charsets.UTF_8))
                require(manifest.optString("format") == "BFP_BACKUP" && manifest.optInt("formatVersion") == 2) { "Manifest संस्करण मान्य नहीं है" }
                require(manifest.optString("database") == DB_NAME) { "यह दूसरे डेटाबेस का बैकअप है" }
                val files = manifest.optJSONObject("files") ?: error("Manifest में फाइल सूची नहीं है")
                val keys = files.keys(); var count = 0
                while (keys.hasNext()) { val name = keys.next(); require(name.startsWith("database/") || name.startsWith("attachments/")); val f = File(dir, name); require(f.isFile && sha256(f).equals(files.getString(name), true)) { "SHA-256 जाँच असफल: $name" }; count++ }
                val archiveFiles = names.filter { it != "manifest.json" && !it.endsWith("/") }.toSet()
                val manifestNames = buildSet { val manifestKeys = files.keys(); while (manifestKeys.hasNext()) add(manifestKeys.next()) }
                require(count == archiveFiles.size && manifestNames == archiveFiles) { "Manifest और ZIP की फाइलें पूरी तरह मेल नहीं खातीं" }
                require(manifestNames.contains("database/$DB_NAME")) { "Manifest में डेटाबेस का checksum नहीं है" }
                created = manifest.optLong("createdAtEpochMillis").takeIf { it > 0 }
            } else error("Manifest मौजूद नहीं है")
            validateBackup(dbFile)
            val version = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db -> db.rawQuery("PRAGMA user_version", null).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 } }
            val attachments = File(dir, "attachments")
            val files = if (attachments.exists()) attachments.walkTopDown().filter { it.isFile }.toList() else emptyList()
            return Preview("BFP ZIP v2", created, version, dbFile.length(), files.size, files.sumOf { it.length() }, "SHA-256 और SQLite Integrity सफल")
        } finally { raw.delete(); dir.deleteRecursively() }
    }

    @Synchronized
    fun restore(context: Context, sourceUri: Uri) {
        val rawTemp = File(context.cacheDir, "bfp_restore_input.tmp")
        val extractDir = File(context.cacheDir, "bfp_restore_package")
        val legacyDb = File(context.cacheDir, "bfp_restore_legacy.db")
        val target = context.getDatabasePath(DB_NAME)
        val staging = File(target.parentFile, "$DB_NAME.restore-new")
        val previous = File(target.parentFile, "$DB_NAME.restore-previous")
        val targetAttachments = File(context.filesDir, "purchase_attachments")
        val stagedAttachments = File(context.filesDir, "purchase_attachments.restore-new")
        val previousAttachments = File(context.filesDir, "purchase_attachments.restore-previous")
        try {
            context.contentResolver.openInputStream(sourceUri).use { input ->
                requireNotNull(input) { "चुनी गई फाइल पढ़ी नहीं जा सकी" }
                FileOutputStream(rawTemp).use { output ->
                    val buffer = ByteArray(8192); var total = 0L
                    while (true) {
                        val count = input.read(buffer); if (count < 0) break
                        total += count
                        require(total <= 260L * 1024L * 1024L) { "चुनी गई बैकअप फाइल 260 MB से बड़ी है" }
                        output.write(buffer, 0, count)
                    }
                    output.fd.sync()
                }
            }
            val isZip = rawTemp.inputStream().use { it.read() == 0x50 && it.read() == 0x4B }
            val databaseSource: File
            if (isZip) {
                if (extractDir.exists()) extractDir.deleteRecursively()
                require(extractDir.mkdirs()) { "अस्थायी restore फोल्डर नहीं बन सका" }
                var totalBytes = 0L
                var entryCount = 0
                val extractedEntries = mutableSetOf<String>()
                val extractedFiles = mutableSetOf<String>()
                java.util.zip.ZipInputStream(FileInputStream(rawTemp)).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        val normalized = entry.name.replace('\\', '/')
                        entryCount++; require(entryCount <= 5000) { "बैकअप में बहुत अधिक फाइलें हैं" }
                        require(extractedEntries.add(normalized)) { "बैकअप में एक ही फाइल दो बार मौजूद है" }
                        require(!normalized.startsWith('/') && normalized.split('/').none { it == ".." }) { "बैकअप में असुरक्षित फाइल पथ है" }
                        require(normalized == "manifest.json" || normalized.startsWith("database/") || normalized.startsWith("attachments/")) { "बैकअप में अनजान सामग्री मिली" }
                        val out = File(extractDir, normalized)
                        require(out.canonicalPath.startsWith(extractDir.canonicalPath + File.separator)) { "बैकअप फाइल पथ गलत है" }
                        if (entry.isDirectory) out.mkdirs() else {
                            extractedFiles += normalized
                            out.parentFile?.mkdirs()
                            FileOutputStream(out).use { output ->
                                val buffer = ByteArray(8192)
                                while (true) {
                                    val count = zip.read(buffer); if (count < 0) break
                                    totalBytes += count
                                    require(totalBytes <= 250L * 1024L * 1024L) { "बैकअप 250 MB से बड़ा है" }
                                    output.write(buffer, 0, count)
                                }
                            }
                        }
                        zip.closeEntry()
                    }
                }
                databaseSource = File(extractDir, "database/$DB_NAME")
                require(databaseSource.isFile) { "बैकअप में डेटाबेस फाइल मौजूद नहीं है" }
                val manifestFile = File(extractDir, "manifest.json")
                require(manifestFile.isFile) { "बैकअप Manifest मौजूद नहीं है; ZIP बैकअप अस्वीकार किया गया" }
                if (manifestFile.isFile) {
                    val manifest = JSONObject(manifestFile.readText(Charsets.UTF_8))
                    require(manifest.optString("format") == "BFP_BACKUP" && manifest.optInt("formatVersion") == 2) { "बैकअप Manifest संस्करण मान्य नहीं है" }
                    require(manifest.optString("database") == DB_NAME) { "बैकअप किसी दूसरे डेटाबेस का है" }
                    val files = manifest.optJSONObject("files") ?: error("बैकअप Manifest में फाइल सूची नहीं है")
                    require(files.has("database/$DB_NAME")) { "Manifest में डेटाबेस का checksum नहीं है" }
                    val keys = files.keys()
                    while (keys.hasNext()) {
                        val name = keys.next()
                        require(name.startsWith("database/") || name.startsWith("attachments/")) { "Manifest में अनजान फाइल है" }
                        val checkedFile = File(extractDir, name)
                        require(checkedFile.isFile && sha256(checkedFile).equals(files.getString(name), ignoreCase = true)) { "बैकअप फाइल की SHA-256 जाँच असफल: $name" }
                    }
                    val manifestNames = buildSet { val manifestKeys = files.keys(); while (manifestKeys.hasNext()) add(manifestKeys.next()) }
                    require(manifestNames == extractedFiles && manifestNames.contains("database/$DB_NAME")) { "बैकअप की ZIP फाइलें और Manifest पूरी तरह मेल नहीं खाते" }
                }
                val attachmentSource = File(extractDir, "attachments")
                if (stagedAttachments.exists()) stagedAttachments.deleteRecursively()
                if (attachmentSource.exists()) {
                    attachmentSource.copyRecursively(stagedAttachments, overwrite = true)
                    require(stagedAttachments.exists()) { "अटैचमेंट restore नहीं हो सके" }
                }
            } else {
                rawTemp.copyTo(legacyDb, overwrite = true)
                databaseSource = legacyDb
                if (stagedAttachments.exists()) stagedAttachments.deleteRecursively()
                // Legacy SQLite backups did not contain attachment files.
            }
            validateBackup(databaseSource)
            target.parentFile?.mkdirs()
            if (staging.exists()) require(staging.delete()) { "अस्थायी restore फाइल हटाई नहीं जा सकी" }
            databaseSource.copyTo(staging, overwrite = true)
            validateBackup(staging)

            if (previous.exists()) require(previous.delete()) { "पुरानी सुरक्षा कॉपी हटाई नहीं जा सकी" }
            if (previousAttachments.exists()) require(previousAttachments.deleteRecursively()) { "पुरानी अटैचमेंट सुरक्षा कॉपी हटाई नहीं जा सकी" }
            BfpDatabase.closeForMaintenance()
            var oldDbMoved = false
            var oldAttachmentsMoved = false
            try {
                File(context.getDatabasePath("$DB_NAME-wal").path).delete()
                File(context.getDatabasePath("$DB_NAME-shm").path).delete()
                if (target.exists()) { require(target.renameTo(previous)) { "मौजूदा डेटाबेस सुरक्षित नहीं किया जा सका" }; oldDbMoved = true }
                if (targetAttachments.exists()) { require(targetAttachments.renameTo(previousAttachments)) { "मौजूदा अटैचमेंट सुरक्षित नहीं हो सके" }; oldAttachmentsMoved = true }
                require(staging.renameTo(target)) { "नई डेटाबेस फाइल स्थापित नहीं हो सकी" }
                if (stagedAttachments.exists()) require(stagedAttachments.renameTo(targetAttachments)) { "नए अटैचमेंट स्थापित नहीं हो सके" }
                BfpDatabase.get(context).openHelper.writableDatabase
                previous.delete(); previousAttachments.deleteRecursively()
            } catch (failure: Exception) {
                BfpDatabase.closeForMaintenance()
                if (target.exists()) target.delete()
                if (targetAttachments.exists()) targetAttachments.deleteRecursively()
                if (oldDbMoved && previous.exists()) previous.renameTo(target)
                if (oldAttachmentsMoved && previousAttachments.exists()) previousAttachments.renameTo(targetAttachments)
                BfpDatabase.get(context)
                throw failure
            }
        } finally {
            rawTemp.delete(); legacyDb.delete(); staging.delete(); extractDir.deleteRecursively(); stagedAttachments.deleteRecursively()
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(8192)
            while (true) { val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun validateBackup(file: File) {
        require(file.isFile && file.length() > SQLITE_HEADER.size) { "बैकअप फाइल खाली या अधूरी है" }
        val header = ByteArray(SQLITE_HEADER.size)
        FileInputStream(file).use { input ->
            require(input.read(header) == header.size && header.contentEquals(SQLITE_HEADER)) {
                "यह मान्य BFP SQLite बैकअप नहीं है"
            }
        }
        val checkDb = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        try {
            checkDb.rawQuery("PRAGMA integrity_check", null).use { cursor ->
                require(cursor.moveToFirst() && cursor.getString(0) == "ok") { "बैकअप डेटाबेस की जाँच असफल रही" }
            }
            checkDb.rawQuery("PRAGMA user_version", null).use { cursor ->
                require(cursor.moveToFirst() && cursor.getInt(0) in 3..11) {
                    "बैकअप का डेटाबेस संस्करण समर्थित नहीं है (3 से 11)"
                }
            }
            val requiredTables = setOf("parties", "items", "invoices", "invoice_lines", "payments", "cash_entries", "business_profile")
            val foundTables = mutableSetOf<String>()
            checkDb.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null).use { cursor ->
                while (cursor.moveToNext()) foundTables += cursor.getString(0)
            }
            require(foundTables.containsAll(requiredTables)) { "बैकअप में जरूरी BFP डेटा टेबल मौजूद नहीं हैं" }
            checkDb.rawQuery("PRAGMA user_version", null).use { cursor ->
                if (cursor.moveToFirst() && cursor.getInt(0) >= 7) {
                    require(foundTables.containsAll(setOf("employees", "salary_months", "salary_payments"))) { "बैकअप में कर्मचारी/वेतन टेबल अधूरी हैं" }
                }
            }
            checkDb.rawQuery("PRAGMA user_version", null).use { cursor ->
                if (cursor.moveToFirst() && cursor.getInt(0) >= 10) {
                    require(foundTables.contains("stock_movements")) { "बैकअप में स्टॉक हिस्ट्री टेबल मौजूद नहीं है" }
                }
            }
        } finally {
            checkDb.close()
        }
    }

}
