package app.textexpander

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.io.File

/** Everything FlowKit knows, as one JSON file: lists, settings and what smart suggestions learned. */
object Backup {
    private const val PREFS = "flowkit"

    fun export(ctx: Context, uri: Uri) {
        SuggestEngine.get(ctx).flushToDisk()
        Thread.sleep(300) // let the background save finish
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val settings = JSONObject()
        for ((k, v) in prefs.all) {
            val entry = when (v) {
                is Boolean -> JSONObject().put("t", "b").put("v", v)
                is Int -> JSONObject().put("t", "i").put("v", v)
                is Long -> JSONObject().put("t", "l").put("v", v)
                is Float -> JSONObject().put("t", "f").put("v", v.toDouble())
                is String -> JSONObject().put("t", "s").put("v", v)
                else -> null
            } ?: continue
            settings.put(k, entry)
        }
        val learned = File(ctx.filesDir, "suggest.json").takeIf { it.exists() }?.readText()
        val root = JSONObject()
            .put("app", "FlowKit")
            .put("version", 1)
            .put("created", System.currentTimeMillis())
            .put("settings", settings)
        if (learned != null) root.put("suggest", JSONObject(learned))
        ctx.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(root.toString().toByteArray()) }
    }

    /** Returns a short summary of what was restored. Throws if the file isn't a FlowKit backup. */
    fun restore(ctx: Context, uri: Uri): String {
        val text = ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) }
        val root = JSONObject(text)
        require(root.optString("app") == "FlowKit") { "Das ist keine FlowKit-Sicherung." }
        val settings = root.getJSONObject("settings")

        val edit = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear()
        for (k in settings.keys()) {
            val e = settings.getJSONObject(k)
            when (e.optString("t")) {
                "b" -> edit.putBoolean(k, e.getBoolean("v"))
                "i" -> edit.putInt(k, e.getInt("v"))
                "l" -> edit.putLong(k, e.getLong("v"))
                "f" -> edit.putFloat(k, e.getDouble("v").toFloat())
                "s" -> edit.putString(k, e.getString("v"))
            }
        }
        edit.commit()
        Store.clearCache()

        val learned = root.optJSONObject("suggest")
        SuggestEngine.get(ctx).replaceWith(learned?.toString())
        Features.applyAll(ctx)

        val lists = settings.keys().asSequence().count { it.startsWith("list_") }
        return "$lists Listen und Einstellungen wiederhergestellt" + if (learned != null) ", inkl. Gelerntem." else "."
    }
}
