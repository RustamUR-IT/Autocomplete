package app.textexpander

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

/** One suggestion chip: shows [label], replaces the last [replaceLen] typed chars with [insert]. */
class Suggestion(val label: String, val insert: String, val replaceLen: Int, val strong: Boolean = false)

class ContactInfo(val key: String, val title: String, val pkg: String, val messages: Int, val words: Int, val last: Long)

/**
 * Learns from what you type – entirely on the phone.
 *  - word statistics (which word follows which) globally and per chat/contact
 *  - full messages you sent to a contact, offered again as quick replies / sentence completion
 */
class SuggestEngine private constructor(private val app: Context) {

    private class Msg(val text: String, var n: Int, var last: Long)

    /** "They wrote [inText] → you answered [reply]". */
    private class PairRec(val inNorm: String, val inText: String, val reply: String, var n: Int, var last: Long)

    private class Ctx(var title: String) {
        val uni = HashMap<String, Int>()
        val bi = HashMap<String, HashMap<String, Int>>()
        val msgs = HashMap<String, Msg>()
        val pairs = ArrayList<PairRec>()
        var last = 0L
    }

    companion object {
        private val WORD = Regex("[\\p{L}\\p{N}][\\p{L}\\p{N}'’\\-]*")
        private val PREFIX = Regex("[\\p{L}\\p{N}'’\\-]*$")
        private val SENTENCE = Regex("[.!?\\n]+")
        private val NON_WORD = Regex("[^\\p{L}\\p{N} ]")
        private val SPACES = Regex("\\s+")

        /** Lower-case words only; emoji-only messages keep their emoji so "❤️" still matches "❤️". */
        fun norm(s: String): String {
            val n = s.lowercase().replace(NON_WORD, " ").replace(SPACES, " ").trim()
            return n.ifEmpty { s.trim() }
        }

        private fun similarity(a: String, b: String): Double {
            if (a == b) return 1.0
            val ta = a.split(' ').filter { it.isNotEmpty() }.toSet()
            val tb = b.split(' ').filter { it.isNotEmpty() }.toSet()
            if (ta.isEmpty() || tb.isEmpty()) return 0.0
            return ta.intersect(tb).size.toDouble() / ta.union(tb).size
        }

        @Volatile private var instance: SuggestEngine? = null
        fun get(ctx: Context): SuggestEngine =
            instance ?: synchronized(this) { instance ?: SuggestEngine(ctx.applicationContext).also { instance = it } }
    }

    private val uni = HashMap<String, Int>()
    private val forms = HashMap<String, String>()
    private val bi = HashMap<String, HashMap<String, Int>>()
    private val tri = HashMap<String, HashMap<String, Int>>()
    private val ctxs = HashMap<String, Ctx>()
    private val globalPairs = ArrayList<PairRec>()

    private val file = File(app.filesDir, "suggest.json")
    private val handler = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private val saveTask = Runnable { saveNow() }

    init { load() }

    private fun tokens(text: String) = WORD.findAll(text).map { it.value }.filter { it.length <= 30 }.toList()

    private fun inc(m: HashMap<String, Int>, k: String) { m[k] = (m[k] ?: 0) + 1 }

    // ── Learning ──────────────────────────────────────────────────────────

    @Synchronized
    fun learn(text: String, key: String, title: String, sent: Boolean) {
        val now = System.currentTimeMillis()
        val c = ctxs.getOrPut(key) { Ctx(title) }
        if (title.isNotBlank()) c.title = title
        c.last = now

        for (seg in text.split(SENTENCE)) {
            val toks = tokens(seg)
            if (toks.isEmpty()) continue
            val low = toks.map { it.lowercase() }
            low.forEachIndexed { i, w ->
                if (i > 0 || w !in forms) forms[w] = toks[i]
                inc(uni, w)
                inc(c.uni, w)
                if (i >= 1) {
                    inc(bi.getOrPut(low[i - 1]) { HashMap() }, w)
                    inc(c.bi.getOrPut(low[i - 1]) { HashMap() }, w)
                }
                if (i >= 2) inc(tri.getOrPut(low[i - 2] + " " + low[i - 1]) { HashMap() }, w)
            }
        }

        if (sent) {
            val t = text.trim()
            if (t.length in 2..300) {
                val k = t.lowercase()
                val m = c.msgs[k]
                if (m == null) c.msgs[k] = Msg(t, 1, now) else { m.n++; m.last = now }
                if (c.msgs.size > 80) c.msgs.entries.minByOrNull { msgScore(it.value) }?.let { c.msgs.remove(it.key) }
            }
        }
        prune()
        handler.removeCallbacks(saveTask)
        handler.postDelayed(saveTask, 5000)
    }

    private fun prune() {
        if (uni.size > 40_000) {
            uni.entries.removeAll { it.value <= 1 }
            bi.values.forEach { m -> m.entries.removeAll { it.value <= 1 } }
            tri.values.forEach { m -> m.entries.removeAll { it.value <= 1 } }
            bi.entries.removeAll { it.value.isEmpty() }
            tri.entries.removeAll { it.value.isEmpty() }
            forms.keys.retainAll(uni.keys)
        }
        while (ctxs.size > 300) ctxs.entries.minByOrNull { it.value.last }?.let { ctxs.remove(it.key) } ?: break
    }

    private fun msgScore(m: Msg): Double {
        val days = (System.currentTimeMillis() - m.last) / 86_400_000.0
        return m.n * 2.0 + 5.0 / (1.0 + days)
    }

    // ── Reply pairs ───────────────────────────────────────────────────────

    private fun addPair(list: ArrayList<PairRec>, ni: String, incoming: String, reply: String, cap: Int, now: Long) {
        val existing = list.firstOrNull { it.inNorm == ni && it.reply.equals(reply, ignoreCase = true) }
        if (existing != null) { existing.n++; existing.last = now; return }
        list += PairRec(ni, incoming.take(200), reply, 1, now)
        if (list.size > cap) list.minByOrNull { pairScoreBase(it) }?.let { list.remove(it) }
    }

    private fun pairScoreBase(p: PairRec): Double {
        val days = (System.currentTimeMillis() - p.last) / 86_400_000.0
        return p.n * 0.6 + 2.0 / (1.0 + days)
    }

    /** Remember that you answered [incoming] with [reply] in this chat. */
    @Synchronized
    fun learnPair(key: String, title: String, incoming: String, reply: String) {
        val r = reply.trim()
        if (incoming.isBlank() || r.length !in 1..300) return
        val now = System.currentTimeMillis()
        val ni = norm(incoming)
        val c = ctxs.getOrPut(key) { Ctx(title) }
        addPair(c.pairs, ni, incoming, r, 250, now)
        addPair(globalPairs, ni, incoming, r, 600, now)
        handler.removeCallbacks(saveTask)
        handler.postDelayed(saveTask, 5000)
    }

    /** What you usually answer to a message like [incoming] – this person first, others as fallback. */
    @Synchronized
    fun repliesTo(key: String, incoming: String?): List<String> {
        if (incoming.isNullOrBlank()) return emptyList()
        val ni = norm(incoming)
        val scored = HashMap<String, Pair<String, Double>>()
        fun consider(list: List<PairRec>, weight: Double) {
            for (p in list) {
                val sim = similarity(ni, p.inNorm)
                if (sim < 0.5) continue
                val score = weight * (sim * 3.0 + pairScoreBase(p))
                val k = p.reply.lowercase()
                if ((scored[k]?.second ?: 0.0) < score) scored[k] = p.reply to score
            }
        }
        consider(ctxs[key]?.pairs.orEmpty(), 1.0)
        consider(globalPairs, 0.4)
        return scored.values.sortedByDescending { it.second }.map { it.first }.take(3)
    }

    @Synchronized fun pairCount() = globalPairs.size

    // ── Suggesting ────────────────────────────────────────────────────────

    @Synchronized
    /** Favourite replies you set yourself for a person (matched against the chat name). */
    fun pinnedFor(title: String): List<String> {
        if (title.isBlank()) return emptyList()
        return Store.list(app, "pinned").filter {
            val n = it["name"].orEmpty().trim()
            n.isNotEmpty() && (title.equals(n, ignoreCase = true) || title.contains(n, ignoreCase = true))
        }.flatMap { r -> r["replies"].orEmpty().lines().map { it.trim() }.filter { it.isNotEmpty() } }
    }

    @Synchronized
    fun suggest(before: String, key: String, title: String = "", incoming: String? = null): List<Suggestion> {
        val c = ctxs[key]
        val out = ArrayList<Suggestion>()

        // 1) Whole sentence: your favourites for this person first, then what you sent before
        val typed = before.trimStart()
        if (typed.length >= 2) {
            val fromPairs = repliesTo(key, incoming).firstOrNull { it.length > typed.length && it.startsWith(typed, ignoreCase = true) }
            val fromPinned = fromPairs ?: pinnedFor(title).firstOrNull { it.length > typed.length && it.startsWith(typed, ignoreCase = true) }
            val fromHistory = if (typed.length >= 3) c?.msgs?.values
                ?.filter { it.text.length > typed.length && it.text.startsWith(typed, ignoreCase = true) }
                ?.maxByOrNull { msgScore(it) }?.text else null
            (fromPinned ?: fromHistory)?.let { full ->
                val rest = full.substring(typed.length)
                val shown = rest.trim()
                out += Suggestion("…" + shown.take(26) + if (shown.length > 26) "…" else "", rest, 0, strong = true)
            }
        }

        // 2) Word completion / next word
        val prefix = PREFIX.find(before)?.value.orEmpty()
        val head = before.substring(0, before.length - prefix.length)
        val sentenceStart = head.isBlank() || head.trimEnd().lastOrNull()?.let { it in ".!?\n" } == true
        val prev = if (sentenceStart) emptyList() else tokens(head.split(SENTENCE).last()).map { it.lowercase() }.takeLast(2)
        val p1 = prev.lastOrNull()
        val p2 = if (prev.size == 2) prev[0] else null
        val pl = prefix.lowercase()

        val cands = HashSet<String>()
        if (pl.isEmpty()) {
            if (p1 == null) return out
            bi[p1]?.keys?.let { cands += it }
            c?.bi?.get(p1)?.keys?.let { cands += it }
            if (p2 != null) tri["$p2 $p1"]?.keys?.let { cands += it }
        } else {
            val minCount = if (pl.length == 1) 3 else 2
            for ((w, n) in uni) if (w.length > pl.length && w.startsWith(pl) && n >= minCount) cands += w
            c?.uni?.keys?.forEach { w -> if (w.length > pl.length && w.startsWith(pl)) cands += w }
        }

        fun score(w: String): Double {
            var s = 0.3 * (uni[w] ?: 0) + 1.5 * (c?.uni?.get(w) ?: 0)
            if (p1 != null) {
                s += 6.0 * (c?.bi?.get(p1)?.get(w) ?: 0)
                s += 2.0 * (bi[p1]?.get(w) ?: 0)
                if (p2 != null) s += 4.0 * (tri["$p2 $p1"]?.get(w) ?: 0)
            }
            return s
        }

        val already = out.map { it.insert.trim().lowercase() }.toSet()
        val words = cands.map { it to score(it) }
            .filter { it.second >= (if (pl.isEmpty()) 2.0 else 0.6) && it.first !in already }
            .sortedByDescending { it.second }
            .take(3 - out.size)
        for ((w, _) in words) {
            var f = forms[w] ?: w
            if (prefix.firstOrNull()?.isUpperCase() == true) f = f.replaceFirstChar { it.uppercase() }
            out += Suggestion(f, "$f ", prefix.length)
        }
        return out
    }

    /** Your most used messages to this person – shown when the field is empty. */
    @Synchronized
    fun quickReplies(key: String, title: String = "", incoming: String? = null): List<Suggestion> {
        fun short(t: String) = if (t.length > 28) t.take(27) + "…" else t
        val out = LinkedHashMap<String, Suggestion>()
        // 1) answers to the message they just sent  2) your favourites  3) what you often write them
        for (r in repliesTo(key, incoming)) out.putIfAbsent(r.lowercase(), Suggestion("↩ " + short(r), r, 0, strong = true))
        for (r in pinnedFor(title)) out.putIfAbsent(r.lowercase(), Suggestion(short(r), r, 0, strong = true))
        ctxs[key]?.msgs?.values?.sortedByDescending { msgScore(it) }?.forEach {
            out.putIfAbsent(it.text.lowercase(), Suggestion(short(it.text), it.text, 0))
        }
        return out.values.take(4)
    }

    // ── Info & management (settings screen) ───────────────────────────────

    @Synchronized fun wordCount() = uni.size

    @Synchronized
    fun contacts(): List<ContactInfo> = ctxs.map { (k, c) ->
        ContactInfo(k, c.title, k.substringBefore('|'), c.msgs.size, c.uni.size, c.last)
    }.sortedByDescending { it.last }

    @Synchronized
    fun topMessages(key: String, n: Int = 10): List<String> =
        ctxs[key]?.msgs?.values?.sortedByDescending { msgScore(it) }?.take(n)?.map { it.text } ?: emptyList()

    @Synchronized
    fun deleteContact(key: String) { ctxs.remove(key); saveNow() }

    @Synchronized
    fun clear() {
        uni.clear(); forms.clear(); bi.clear(); tri.clear(); ctxs.clear(); globalPairs.clear()
        saveNow()
    }

    fun flushToDisk() { handler.removeCallbacks(saveTask); saveNow() }

    /** Restore from a backup: replace everything learned with [json] (null = empty). */
    @Synchronized
    fun replaceWith(json: String?) {
        handler.removeCallbacks(saveTask)
        uni.clear(); forms.clear(); bi.clear(); tri.clear(); ctxs.clear(); globalPairs.clear()
        if (json != null) {
            file.writeText(json)
            load()
        } else {
            file.delete()
        }
    }

    // ── Persistence ───────────────────────────────────────────────────────

    private fun counts(m: Map<String, Int>) = JSONObject().apply { m.forEach { (k, v) -> put(k, v) } }
    private fun nested(m: Map<String, Map<String, Int>>) = JSONObject().apply { m.forEach { (k, v) -> put(k, counts(v)) } }

    private fun pairsJson(list: List<PairRec>) = JSONArray().apply {
        list.forEach { p -> put(JSONObject().put("k", p.inNorm).put("i", p.inText).put("r", p.reply).put("n", p.n).put("l", p.last)) }
    }

    private fun readPairs(arr: JSONArray?, into: ArrayList<PairRec>) {
        arr ?: return
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            into += PairRec(o.optString("k"), o.optString("i"), o.optString("r"), o.optInt("n", 1), o.optLong("l"))
        }
    }

    @Synchronized
    private fun saveNow() {
        val root = JSONObject()
        root.put("uni", counts(uni))
        root.put("forms", JSONObject().apply { forms.forEach { (k, v) -> put(k, v) } })
        root.put("bi", nested(bi))
        root.put("tri", nested(tri))
        root.put("pairs", pairsJson(globalPairs))
        root.put("ctx", JSONObject().apply {
            ctxs.forEach { (k, c) ->
                put(k, JSONObject().apply {
                    put("title", c.title)
                    put("last", c.last)
                    put("uni", counts(c.uni))
                    put("bi", nested(c.bi))
                    put("pairs", pairsJson(c.pairs))
                    put("msgs", JSONArray().apply {
                        c.msgs.values.forEach { m -> put(JSONObject().put("t", m.text).put("n", m.n).put("l", m.last)) }
                    })
                })
            }
        })
        val json = root.toString()
        io.execute {
            try {
                val tmp = File(file.path + ".tmp")
                tmp.writeText(json)
                tmp.renameTo(file)
            } catch (e: Exception) { }
        }
    }

    private fun readCounts(o: JSONObject?, into: HashMap<String, Int>) {
        o ?: return
        for (k in o.keys()) into[k] = o.optInt(k)
    }

    private fun readNested(o: JSONObject?, into: HashMap<String, HashMap<String, Int>>) {
        o ?: return
        for (k in o.keys()) into[k] = HashMap<String, Int>().also { readCounts(o.optJSONObject(k), it) }
    }

    private fun load() {
        if (!file.exists()) return
        try {
            val root = JSONObject(file.readText())
            readCounts(root.optJSONObject("uni"), uni)
            root.optJSONObject("forms")?.let { o -> for (k in o.keys()) forms[k] = o.optString(k) }
            readNested(root.optJSONObject("bi"), bi)
            readNested(root.optJSONObject("tri"), tri)
            readPairs(root.optJSONArray("pairs"), globalPairs)
            root.optJSONObject("ctx")?.let { all ->
                for (k in all.keys()) {
                    val o = all.getJSONObject(k)
                    val c = Ctx(o.optString("title"))
                    c.last = o.optLong("last")
                    readCounts(o.optJSONObject("uni"), c.uni)
                    readNested(o.optJSONObject("bi"), c.bi)
                    readPairs(o.optJSONArray("pairs"), c.pairs)
                    o.optJSONArray("msgs")?.let { arr ->
                        for (i in 0 until arr.length()) {
                            val m = arr.getJSONObject(i)
                            val t = m.optString("t")
                            c.msgs[t.lowercase()] = Msg(t, m.optInt("n", 1), m.optLong("l"))
                        }
                    }
                    ctxs[k] = c
                }
            }
        } catch (e: Exception) { }
    }
}
