package app.textexpander

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.IsoFields
import java.util.Locale

/** One saved entry (snippet, clip, launcher, note, …) as simple key → value pairs. */
typealias Rec = Map<String, String>

/** All app data lives in one SharedPreferences file, cached in memory for the accessibility service. */
object Store {
    private val cache = HashMap<String, List<Rec>>()

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences("flowkit", Context.MODE_PRIVATE)

    fun list(ctx: Context, id: String): List<Rec> = synchronized(cache) {
        cache.getOrPut(id) {
            val raw = prefs(ctx).getString("list_$id", null)
            if (raw == null) Kinds.get(id)?.defaults ?: emptyList() else parse(raw)
        }
    }

    fun save(ctx: Context, id: String, list: List<Rec>) {
        synchronized(cache) { cache[id] = list }
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject(it)) }
        prefs(ctx).edit().putString("list_$id", arr.toString()).apply()
    }

    fun add(ctx: Context, id: String, rec: Rec) = save(ctx, id, list(ctx, id) + rec)

    fun clearCache() = synchronized(cache) { cache.clear() }

    private fun parse(raw: String): List<Rec> = try {
        val arr = JSONArray(raw)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            o.keys().asSequence().associateWith { o.optString(it) }
        }
    } catch (e: Exception) {
        emptyList()
    }

    fun getInt(ctx: Context, key: String, def: Int) = prefs(ctx).getInt(key, def)
    fun putInt(ctx: Context, key: String, v: Int) = prefs(ctx).edit().putInt(key, v).apply()
    fun getStr(ctx: Context, key: String, def: String) = prefs(ctx).getString(key, def) ?: def
    fun putStr(ctx: Context, key: String, v: String) = prefs(ctx).edit().putString(key, v).apply()
    fun getBool(ctx: Context, key: String, def: Boolean) = prefs(ctx).getBoolean(key, def)
    fun putBool(ctx: Context, key: String, v: Boolean) = prefs(ctx).edit().putBoolean(key, v).apply()
}

/** Dynamic snippet placeholders, filled in at the moment of expansion. */
object Dyn {
    const val HELP = "{date} {isodate} {time} {weekday} {week} {tomorrow} {cursor}"

    /** Returns the expanded text and where the cursor should go (-1 = at the end). */
    fun render(raw: String): Pair<String, Int> {
        val today = LocalDate.now()
        val now = LocalDateTime.now()
        val de = Locale.GERMAN
        var s = raw
            .replace("{date}", today.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")))
            .replace("{isodate}", today.toString())
            .replace("{time}", now.format(DateTimeFormatter.ofPattern("HH:mm")))
            .replace("{weekday}", today.format(DateTimeFormatter.ofPattern("EEEE", de)))
            .replace("{week}", today.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR).toString())
            .replace("{tomorrow}", today.plusDays(1).format(DateTimeFormatter.ofPattern("EEEE, dd.MM.", de)))
        val cursor = s.indexOf("{cursor}")
        s = s.replace("{cursor}", "")
        return s to cursor
    }
}
