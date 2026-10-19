package app.textexpander

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView

/** One screen for every list: snippets, clipboard, launcher, notes, cheat sheet, friction lists. */
class ListActivity : Activity() {

    companion object {
        fun intent(ctx: Context, kind: String): Intent =
            Intent(ctx, ListActivity::class.java).putExtra("kind", kind)

        fun open(ctx: Context, kind: String) = ctx.startActivity(
            intent(ctx, kind).apply { if (ctx !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        )
    }

    private lateinit var kind: Kind
    private lateinit var rows: LinearLayout
    private var filterRow: LinearLayout? = null
    private var query = ""
    private var category: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        kind = Kinds.get(intent.getStringExtra("kind")) ?: run { finish(); return }

        val c = page(kind.title, kind.help)
        c.addView(input(hint = "Suchen…").apply { onText { query = it; refresh() } })

        if (kind.categoryField != null) {
            val hs = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
            val fr = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            hs.addView(fr)
            c.addView(hs)
            filterRow = fr
        }

        c.row {
            pill("+  Neu", primary = true) { edit(null) }
            if (kind.id == "clips") pill("📋 Aus Zwischenablage") { fromClipboard() }
        }

        rows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        c.addView(rows)

        // Opened with a name to add (e.g. "favourite replies for Lena") → go straight to the editor.
        if (savedInstanceState == null) intent.getStringExtra("prefill_name")?.let { name ->
            val existing = data().firstOrNull { it["name"].equals(name, ignoreCase = true) }
            if (existing != null) edit(existing) else edit(null, mapOf("name" to name, "replies" to intent.getStringExtra("prefill_replies").orEmpty()))
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun data() = Store.list(this, kind.id)

    private fun refresh() {
        buildFilters()
        rows.removeAllViews()
        val field = kind.categoryField
        val shown = kind.sort(data()).filter { r ->
            (category == null || field == null || r[field] == category) &&
                (query.isBlank() || r.values.any { it.contains(query, ignoreCase = true) })
        }
        if (shown.isEmpty()) rows.label("Noch nichts hier.", muted = true, topDp = 16)

        for (r in shown) {
            val box = rows.card {
                if (field != null && !r[field].isNullOrBlank()) label(r[field]!!.uppercase(), 11f, muted = true, mono = true, topDp = 0)
                label(kind.titleOf(context, r), 16f, bold = true, topDp = 0)
                val sub = kind.subOf(context, r)
                if (sub.isNotBlank()) {
                    label(sub, 13f, muted = !kind.mono, mono = kind.mono || r[field ?: ""] == "Code").apply {
                        if (!kind.fullSub) {
                            maxLines = 3
                            ellipsize = TextUtils.TruncateAt.END
                        }
                    }
                }
            }
            box.isClickable = true
            box.setOnClickListener { tap(r) }
            box.setOnLongClickListener { menu(r); true }
        }
    }

    private fun buildFilters() {
        val fr = filterRow ?: return
        val field = kind.categoryField ?: return
        fr.removeAllViews()
        val cats = data().mapNotNull { it[field] }.filter { it.isNotBlank() }.distinct().sortedBy { it.lowercase() }
        if (category != null && category !in cats) category = null
        fr.chip("Alle", category == null) { category = null; refresh() }
        for (cat in cats) fr.chip(cat, category == cat) { category = cat; refresh() }
    }

    private fun tap(r: Rec) {
        when (kind.tap) {
            Tap.EDIT -> edit(r)
            Tap.COPY -> copyText(r[kind.copyField].orEmpty())
            Tap.OPEN -> {
                val t = r["target"].orEmpty()
                if (Launch.isEmpty(t)) edit(r) else Launch.open(this, t, kind.id)
            }
        }
    }

    private fun menu(r: Rec) {
        val opts = mutableListOf("Bearbeiten")
        val copyValue = r[kind.copyField] ?: r["text"]
        if (copyValue != null) opts += "Kopieren"
        if (r["target"] != null) opts += "Öffnen"
        if (kind.id == "notes") opts += listOf("Teilen", "→ ins Clipboard verschieben")
        opts += "Löschen"
        AlertDialog.Builder(this)
            .setTitle(kind.titleOf(this, r))
            .setItems(opts.toTypedArray()) { _, i ->
                when (opts[i]) {
                    "Bearbeiten" -> edit(r)
                    "Kopieren" -> copyText(copyValue.orEmpty())
                    "Öffnen" -> Launch.open(this, r["target"].orEmpty(), kind.id)
                    "Teilen" -> startActivity(Intent.createChooser(
                        Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, r["text"]), null))
                    "→ ins Clipboard verschieben" -> {
                        val text = r["text"].orEmpty()
                        Store.add(this, "clips", mapOf("cat" to "Texte", "title" to text.lineSequence().first().take(40), "text" to text))
                        remove(r)
                        toast("Ins Smart Clipboard verschoben")
                    }
                    "Löschen" -> AlertDialog.Builder(this)
                        .setTitle("Löschen?")
                        .setPositiveButton("Löschen") { _, _ -> remove(r) }
                        .setNegativeButton("Abbrechen", null)
                        .show()
                }
            }
            .show()
    }

    private fun remove(r: Rec) {
        Store.save(this, kind.id, data().filter { it !== r })
        refresh()
    }

    private fun fromClipboard() {
        val clip = sys<ClipboardManager>().primaryClip
        val text = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString().orEmpty()
        if (text.isBlank()) { toast("Zwischenablage ist leer"); return }
        val isLink = text.trim().startsWith("http")
        edit(null, mapOf(
            "cat" to if (isLink) "Links" else "Texte",
            "title" to text.trim().lineSequence().first().take(40),
            "text" to text,
        ))
    }

    private fun edit(r: Rec?, prefill: Rec? = null) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(4), dp(20), 0)
        }
        val edits = LinkedHashMap<String, EditText>()
        for (f in kind.fields) {
            box.label(f.label, 12f, muted = true, topDp = 10)
            val value = r?.get(f.key) ?: prefill?.get(f.key) ?: if (f.abbr) ";" else ""
            val e = input(value, f.hint, f.multiline, f.mono || kind.mono)
            box.addView(e)
            edits[f.key] = e
            if (f.target) box.row {
                chip("App wählen") {
                    pickApp { label, pkg ->
                        e.setText("app:$pkg")
                        edits["label"]?.let { if (it.text.isBlank()) it.setText(label) }
                    }
                }
                chip("Aktion") {
                    pickAction { id, name ->
                        e.setText(id)
                        edits["label"]?.let { if (it.text.isBlank()) it.setText(name) }
                    }
                }
            }
        }
        if (r == null && edits.size > 0) edits.values.first().requestFocus()

        val dlg = AlertDialog.Builder(this)
            .setTitle(if (r == null) "Neu" else "Bearbeiten")
            .setView(ScrollView(this).apply { addView(box) })
            .setPositiveButton("Speichern", null)
            .setNegativeButton("Abbrechen", null)
            .create()
        dlg.setOnShowListener {
            dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { if (save(r, edits)) dlg.dismiss() }
        }
        dlg.show()
    }

    private fun save(old: Rec?, edits: Map<String, EditText>): Boolean {
        val values = LinkedHashMap<String, String>()
        for (f in kind.fields) {
            val raw = edits[f.key]?.text?.toString().orEmpty()
            values[f.key] = if (f.multiline) raw.trimEnd() else raw.trim()
        }
        val first = kind.fields.first()
        if (values[first.key].isNullOrBlank() || (first.abbr && values[first.key] == ";")) {
            toast("${first.label} fehlt"); return false
        }
        val abbr = values["abbr"]
        if (abbr != null && (abbr.length < 2 || abbr.contains(' '))) {
            toast("Kürzel: mindestens 2 Zeichen, keine Leerzeichen"); return false
        }
        if (kind.stamp) values["time"] = old?.get("time") ?: System.currentTimeMillis().toString()

        val list = data().toMutableList()
        val idx = list.indexOfFirst { it === old }
        if (idx >= 0) list[idx] = values else list += values
        if (abbr != null) list.removeAll { it !== values && it["abbr"] == abbr }
        Store.save(this, kind.id, list)

        if (abbr != null) warnPrefix(abbr)
        refresh()
        return true
    }

    /** An abbreviation fires as soon as it's typed, so ";ty" would block ";typ". */
    private fun warnPrefix(abbr: String) {
        val others = (Store.list(this, "snippets") + Store.list(this, "launchers"))
            .mapNotNull { it["abbr"] }.filter { it != abbr }
        val clash = others.firstOrNull { it.startsWith(abbr) || abbr.startsWith(it) } ?: return
        AlertDialog.Builder(this)
            .setTitle("Achtung: Überschneidung")
            .setMessage("„$abbr“ und „$clash“ beginnen gleich. Das kürzere Kürzel feuert zuerst – das längere wird nie erreicht. Am besten eines umbenennen.")
            .setPositiveButton("OK", null)
            .show()
    }
}
