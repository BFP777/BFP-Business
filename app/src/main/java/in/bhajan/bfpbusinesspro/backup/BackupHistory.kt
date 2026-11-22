package `in`.bhajan.bfpbusinesspro.backup

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Small bounded history of completed backup operations; stored locally, not inside the business database. */
object BackupHistory {
    private const val PREFS = "bfp_backup_history"
    private const val KEY = "entries"

    @Synchronized fun record(context: Context, kind: String, status: String, detail: String = "") {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val old = runCatching { JSONArray(prefs.getString(KEY, "[]")) }.getOrDefault(JSONArray())
        val next = JSONArray()
        next.put(JSONObject().put("at", System.currentTimeMillis()).put("kind", kind).put("status", status).put("detail", detail.take(240)))
        for (i in 0 until minOf(old.length(), 19)) next.put(old.optJSONObject(i) ?: JSONObject())
        prefs.edit().putString(KEY, next.toString()).apply()
    }

    fun summary(context: Context): String {
        val entries = runCatching { JSONArray(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]")) }.getOrDefault(JSONArray())
        if (entries.length() == 0) return "Backup History: अभी कोई रिकॉर्ड नहीं"
        return buildString {
            append("Backup History (नवीनतम 10)\n")
            for (i in 0 until minOf(entries.length(), 10)) {
                val item = entries.optJSONObject(i) ?: continue
                val date = java.text.SimpleDateFormat("dd-MM-yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date(item.optLong("at")))
                append("• ").append(date).append(" | ").append(item.optString("kind")).append(" | ").append(item.optString("status"))
                if (item.optString("detail").isNotBlank()) append(" — ").append(item.optString("detail"))
                append('\n')
            }
        }
    }
}
