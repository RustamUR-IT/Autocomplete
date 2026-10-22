package app.textexpander

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import java.text.DateFormat
import java.time.LocalDate
import java.util.Date

/** Document vault + automatic finds + warranties, in one screen with three tabs. */
class DocsActivity : Activity() {

    companion object {
        const val REQ_PICK = 51
        const val REQ_TREE = 52
        const val REQ_RECEIPT = 53
    }

    private var tab = "docs"
    private var query = ""
    private var catFilter: String? = null
    private lateinit var list: LinearLayout
    private val main = Handler(Looper.getMainLooper())
    private var finds: List<DocScanner.Find>? = null
    private var scanning = false
    private var pendingReceiptFor: Rec? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tab = intent.getStringExtra("tab") ?: "docs"
    }

    override fun onResume() {
        super.onResume()
        render()
        intent.getStringExtra("newFor")?.let { id ->
            intent.removeExtra("newFor")
            Docs.all(this).firstOrNull { it["id"] == id }?.let { editWarranty(null, prefillDoc = it) }
        }
    }

    private fun render() {
        val c = page("Dokumente", "Wichtige Unterlagen sicher auf dem Handy – mit Suche, Kategorien und Garantie-Erinnerung.")

        val tabs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val nFinds = finds?.size
        tabs.chip("📁 Tresor (${Docs.all(this).size})", tab == "docs") { tab = "docs"; render() }
        tabs.chip("🔍 Gefunden" + (if (nFinds != null) " ($nFinds)" else ""), tab == "finds") { tab = "finds"; render() }
        tabs.chip("🔧 Garantien (${Docs.warranties(this).size})", tab == "warranty") { tab = "warranty"; render() }
        c.addView(tabs)

        when (tab) {
            "docs" -> renderDocs(c)
            "finds" -> renderFinds(c)
            else -> renderWarranties(c)
        }
    }

    // ── Vault ─────────────────────────────────────────────────────────────

    private fun renderDocs(c: LinearLayout) {
        c.addView(input(hint = "Suchen …", value = query).apply { onText { query = it; fillDocs() } })
        c.row {
            pill("+ Datei", primary = true) {
                @Suppress("DEPRECATION")
                startActivityForResult(
                    Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
                        .putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/pdf", "image/*", "application/*", "text/plain"))
                        .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true),
                    REQ_PICK,
                )
            }
            pill("📷 Foto") {
                @Suppress("DEPRECATION")
                startActivityForResult(
                    Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*")
                        .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true),
                    REQ_PICK,
                )
            }
        }
        val hs = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val cats = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val used = Docs.all(this).map { it["cat"] }.toSet()
        cats.chip("Alle", catFilter == null) { catFilter = null; fillDocs() }
        for (k in Docs.cats) if (k.id in used) cats.chip("${k.icon} ${k.title}", catFilter == k.id) { catFilter = k.id; fillDocs() }
        hs.addView(cats)
        c.addView(hs)
        c.label("Tipp: In jeder App „Teilen → FlowKit“ speichert PDFs, Fotos und Screenshots direkt hier.", 12f, muted = true)
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        c.addView(list)
        fillDocs()
    }

    private fun fillDocs() {
        list.removeAllViews()
        val docs = Docs.all(this)
            .filter { catFilter == null || it["cat"] == catFilter }
            .filter { query.isBlank() || listOf(it["title"], it["name"], it["note"], Docs.cat(it["cat"]).title).any { v -> v?.contains(query, true) == true } }
            .sortedByDescending { it["id"] }
        if (docs.isEmpty()) list.label(if (Docs.all(this).isEmpty()) "Noch leer. Füge eine Datei hinzu oder lass FlowKit im Tab „Gefunden“ suchen." else "Nichts gefunden.", 13f, muted = true, topDp = 16)
        for (d in docs) {
            val k = Docs.cat(d["cat"])
            val expiry = Docs.daysUntil(d["expiry"])
            list.card {
                label("${k.icon} ${k.title.uppercase()}", 11f, muted = true, mono = true, topDp = 0)
                label(d["title"].orEmpty(), 16f, bold = true, topDp = 0)
                val meta = buildString {
                    append(d["name"]); append(" · "); append(Docs.sizeText(d["size"]?.toLongOrNull() ?: 0)); append(" · "); append(d["added"])
                    if (expiry != null) append(if (expiry < 0) "\n⛔ abgelaufen (${d["expiry"]})" else if (expiry < 60) "\n⚠️ läuft ab in $expiry Tagen (${d["expiry"]})" else "\ngültig bis ${d["expiry"]}")
                    if (!d["note"].isNullOrBlank()) append("\n").append(d["note"])
                }
                label(meta, 12f, muted = true).apply { maxLines = 4; ellipsize = TextUtils.TruncateAt.END }
            }.apply {
                isClickable = true
                setOnClickListener { Docs.open(this@DocsActivity, d) }
                setOnLongClickListener { docMenu(d); true }
            }
        }
    }

    private fun docMenu(d: Rec) {
        val opts = arrayOf("Öffnen", "Teilen", "Bearbeiten", "Garantie dazu anlegen", "Löschen")
        AlertDialog.Builder(this).setTitle(d["title"]).setItems(opts) { _, i ->
            when (i) {
                0 -> Docs.open(this, d)
                1 -> Docs.share(this, d)
                2 -> editDoc(d)
                3 -> editWarranty(null, prefillDoc = d)
                4 -> AlertDialog.Builder(this).setTitle("Löschen?").setMessage("Die Datei wird aus dem Tresor entfernt.")
                    .setPositiveButton("Löschen") { _, _ -> Docs.delete(this, d); render() }
                    .setNegativeButton("Abbrechen", null).show()
            }
        }.show()
    }

    private fun editDoc(d: Rec) {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(4), dp(20), 0) }
        box.label("Titel", 12f, muted = true, topDp = 8)
        val title = input(d["title"].orEmpty()); box.addView(title)
        box.label("Kategorie", 12f, muted = true, topDp = 12)
        var cat = d["cat"] ?: "other"
        val catRow = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        fun drawCats() {
            catRow.removeAllViews()
            var row: LinearLayout? = null
            Docs.cats.forEachIndexed { i, k ->
                if (i % 2 == 0) row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }.also { catRow.addView(it) }
                row!!.chip("${k.icon} ${k.title}", cat == k.id) { cat = k.id; drawCats() }
            }
        }
        drawCats()
        box.addView(catRow)
        box.label("Gültig bis (optional, z. B. Ausweis)", 12f, muted = true, topDp = 12)
        val expiry = input(d["expiry"].orEmpty(), hint = "TT.MM.JJJJ"); box.addView(expiry)
        box.label("Notiz", 12f, muted = true, topDp = 12)
        val note = input(d["note"].orEmpty(), multiline = true); box.addView(note)
        AlertDialog.Builder(this).setTitle("Dokument bearbeiten")
            .setView(ScrollView(this).apply { addView(box) })
            .setPositiveButton("Speichern") { _, _ ->
                val e = expiry.text.toString().trim()
                if (e.isNotEmpty() && Docs.parseDate(e) == null) { toast("Datum bitte als TT.MM.JJJJ"); return@setPositiveButton }
                Docs.update(this, d, d + mapOf("title" to title.text.toString().trim().ifBlank { d["title"].orEmpty() }, "cat" to cat, "expiry" to e, "note" to note.text.toString().trim()))
                render()
            }
            .setNegativeButton("Abbrechen", null).show()
    }

    // ── Finds ─────────────────────────────────────────────────────────────

    private fun renderFinds(c: LinearLayout) {
        val roots = DocScanner.roots(this)
        c.card {
            label("FlowKit durchsucht Ordner, die du freigibst (z. B. Download, WhatsApp Documents, DCIM/Screenshots), nach Rechnungen, Verträgen, Zeugnissen, Ausweisen … Nichts wird verschoben – du entscheidest, was in den Tresor kommt.", 13f, muted = true, topDp = 0)
            for (r in roots) row {
                label("📂 ${r["label"]}", 14f).layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                chip("✕") { DocScanner.removeRoot(this@DocsActivity, r); finds = null; render() }
            }
            row {
                pill("+ Ordner freigeben", primary = roots.isEmpty()) {
                    @Suppress("DEPRECATION")
                    startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION), REQ_TREE)
                }
                if (roots.isNotEmpty()) pill(if (scanning) "Suche läuft …" else "🔍 Jetzt suchen", primary = true) { scan() }
            }
            if (roots.isEmpty()) label("Empfohlen: „Download“ und „WhatsApp/Media/WhatsApp Documents“ (unter Android/media/com.whatsapp).", 12f, muted = true)
        }
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        c.addView(list)
        val f = finds
        if (f == null) {
            if (roots.isNotEmpty() && !scanning) scan()
        } else fillFinds(f)
    }

    private fun scan() {
        if (scanning) return
        scanning = true
        Thread {
            val result = try { DocScanner.scan(this) } catch (e: Exception) { emptyList() }
            main.post { scanning = false; finds = result; if (tab == "finds") render() }
        }.start()
    }

    private fun fillFinds(f: List<DocScanner.Find>) {
        list.removeAllViews()
        val important = f.filter { it.score > 0 }
        val rest = f.filter { it.score == 0 }
        if (f.isEmpty()) { list.label("Nichts Neues gefunden.", 13f, muted = true, topDp = 16); return }
        if (important.isNotEmpty()) {
            list.section("Sieht wichtig aus (${important.size})")
            list.pill("Alle ${important.size} in den Tresor übernehmen", primary = true) {
                Thread {
                    var n = 0
                    for (x in important) try { Docs.import(this, x.uri, catId = x.cat.id); n++ } catch (e: Exception) { }
                    main.post { toast("$n Dokumente übernommen"); finds = null; tab = "docs"; render() }
                }.start()
            }
            important.take(150).forEach { findCard(it) }
        }
        if (rest.isNotEmpty()) {
            list.section("Weitere Dateien (${rest.size})")
            rest.take(60).forEach { findCard(it) }
        }
    }

    private fun findCard(x: DocScanner.Find) {
        val when_ = if (x.modified > 0) DateFormat.getDateInstance(DateFormat.SHORT).format(Date(x.modified)) else ""
        list.card {
            label("${x.cat.icon} ${x.cat.title.uppercase()}" + if (x.score >= 2) "  ★" else "", 11f, muted = true, mono = true, topDp = 0)
            label(x.name, 15f, bold = true, topDp = 0)
            label("${x.folder} · ${Docs.sizeText(x.size)} · $when_", 12f, muted = true)
            row {
                pill("Übernehmen", primary = true) {
                    try { Docs.import(this@DocsActivity, x.uri, catId = x.cat.id); toast("Gespeichert") } catch (e: Exception) { toast("Fehler: ${e.message}") }
                    finds = finds?.filter { it !== x }; render()
                }
                pill("Ansehen") {
                    try { startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(x.uri, x.mime ?: "*/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) } catch (e: Exception) { toast("Kann nicht öffnen") }
                }
                pill("Nein") { DocScanner.dismiss(this@DocsActivity, x); finds = finds?.filter { it !== x }; render() }
            }
        }
    }

    // ── Warranties ────────────────────────────────────────────────────────

    private fun renderWarranties(c: LinearLayout) {
        c.pill("+ Garantie anlegen", primary = true) { editWarranty(null) }
        c.label("FlowKit erinnert dich 30 Tage, 7 Tage und am letzten Tag, bevor eine Garantie abläuft.", 12f, muted = true)
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        c.addView(list)
        val ws = Docs.warranties(this).sortedBy { Docs.warrantyDaysLeft(it) ?: Long.MAX_VALUE }
        if (ws.isEmpty()) list.label("Noch keine Garantien. Tipp: Kaufbeleg fotografieren → lange drücken → „Garantie dazu anlegen“.", 13f, muted = true, topDp = 16)
        for (w in ws) {
            val d = Docs.warrantyDaysLeft(w)
            val receipt = Docs.all(this).firstOrNull { it["id"] == w["doc"] }
            list.card {
                label((if (d != null && d < 0) "⛔ " else if (d != null && d < 30) "⚠️ " else "🔧 ") + w["product"], 16f, bold = true, topDp = 0)
                label(Docs.warrantyStatus(w), 13f, muted = d == null || d >= 30)
                val meta = listOfNotNull(
                    w["store"]?.takeIf { it.isNotBlank() },
                    w["bought"]?.takeIf { it.isNotBlank() }?.let { "gekauft $it" },
                    w["price"]?.takeIf { it.isNotBlank() }?.let { "$it €" },
                    receipt?.let { "📎 ${it["title"]}" },
                    w["note"]?.takeIf { it.isNotBlank() },
                ).joinToString(" · ")
                if (meta.isNotEmpty()) label(meta, 12f, muted = true)
            }.apply {
                isClickable = true
                setOnClickListener { editWarranty(w) }
                setOnLongClickListener {
                    val opts = if (receipt != null) arrayOf("Bearbeiten", "Beleg öffnen", "Löschen") else arrayOf("Bearbeiten", "Löschen")
                    AlertDialog.Builder(this@DocsActivity).setTitle(w["product"]).setItems(opts) { _, i ->
                        when (opts[i]) {
                            "Bearbeiten" -> editWarranty(w)
                            "Beleg öffnen" -> Docs.open(this@DocsActivity, receipt!!)
                            "Löschen" -> { Store.save(this@DocsActivity, "warranties", Docs.warranties(this@DocsActivity).filter { it !== w }); render() }
                        }
                    }.show(); true
                }
            }
        }
    }

    private fun editWarranty(w: Rec?, prefillDoc: Rec? = null) {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(4), dp(20), 0) }
        fun field(label: String, value: String, hint: String = "", number: Boolean = false): EditText {
            box.label(label, 12f, muted = true, topDp = 10)
            return input(value, hint, number = number).also { box.addView(it) }
        }
        val product = field("Produkt", w?.get("product") ?: prefillDoc?.get("title").orEmpty(), "z. B. Nothing Phone 3a")
        val store = field("Gekauft bei", w?.get("store").orEmpty(), "MediaMarkt, Amazon …")
        val bought = field("Kaufdatum", w?.get("bought") ?: prefillDoc?.get("added") ?: LocalDate.now().format(Docs.DATE), "TT.MM.JJJJ")
        val months = field("Garantie (Monate)", w?.get("months") ?: "24", number = true)
        val price = field("Preis (optional)", w?.get("price").orEmpty(), "€", number = true)
        val note = field("Notiz / Seriennummer", w?.get("note").orEmpty())
        var docId = w?.get("doc") ?: prefillDoc?.get("id")
        box.label("Kaufbeleg", 12f, muted = true, topDp = 12)
        val docLabel = box.label(Docs.all(this).firstOrNull { it["id"] == docId }?.get("title")?.let { "📎 $it" } ?: "kein Beleg verknüpft", 13f)
        box.row {
            chip("Aus Tresor wählen") {
                val docs = Docs.all(this@DocsActivity)
                if (docs.isEmpty()) { toast("Tresor ist leer"); return@chip }
                AlertDialog.Builder(this@DocsActivity).setTitle("Beleg wählen")
                    .setItems(docs.map { it["title"].orEmpty() }.toTypedArray()) { _, i -> docId = docs[i]["id"]; docLabel.text = "📎 ${docs[i]["title"]}" }.show()
            }
            chip("Datei/Foto hinzufügen") {
                pendingReceiptFor = mapOf("product" to product.text.toString(), "store" to store.text.toString(), "bought" to bought.text.toString(), "months" to months.text.toString(), "price" to price.text.toString(), "note" to note.text.toString(), "editing" to (w?.get("id") ?: ""))
                @Suppress("DEPRECATION")
                startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"), REQ_RECEIPT)
            }
        }
        AlertDialog.Builder(this).setTitle(if (w == null) "Garantie anlegen" else "Garantie bearbeiten")
            .setView(ScrollView(this).apply { addView(box) })
            .setPositiveButton("Speichern") { _, _ ->
                val p = product.text.toString().trim()
                if (p.isEmpty()) { toast("Produkt fehlt"); return@setPositiveButton }
                if (Docs.parseDate(bought.text.toString()) == null) { toast("Kaufdatum bitte als TT.MM.JJJJ"); return@setPositiveButton }
                saveWarranty(w, mapOf(
                    "id" to (w?.get("id") ?: System.currentTimeMillis().toString(36)),
                    "product" to p, "store" to store.text.toString().trim(), "bought" to bought.text.toString().trim(),
                    "months" to (months.text.toString().trim().ifBlank { "24" }), "price" to price.text.toString().trim(),
                    "note" to note.text.toString().trim(), "doc" to docId.orEmpty(),
                ))
            }
            .setNegativeButton("Abbrechen", null).show()
    }

    private fun saveWarranty(old: Rec?, new: Rec) {
        val all = Docs.warranties(this).toMutableList()
        val i = all.indexOfFirst { it["id"] == new["id"] }
        if (i >= 0) all[i] = new else all += new
        Store.save(this, "warranties", all)
        tab = "warranty"
        render()
    }

    // ── Results from pickers ──────────────────────────────────────────────

    @Deprecated("Platform result API (no AndroidX)")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data == null) return
        when (requestCode) {
            REQ_PICK -> {
                val uris = ArrayList<Uri>()
                data.clipData?.let { cd -> for (i in 0 until cd.itemCount) uris += cd.getItemAt(i).uri }
                data.data?.let { uris += it }
                if (uris.isEmpty()) return
                if (uris.size == 1) ReceiveDocActivity.askAndImport(this, uris[0]) { render() }
                else Thread {
                    var n = 0
                    for (u in uris) try { Docs.import(this, u); n++ } catch (e: Exception) { }
                    main.post { toast("$n Dateien gespeichert"); tab = "docs"; render() }
                }.start()
            }
            REQ_TREE -> data.data?.let { DocScanner.addRoot(this, it); finds = null; tab = "finds"; render() }
            REQ_RECEIPT -> {
                val u = data.data ?: return
                val p = pendingReceiptFor ?: return
                pendingReceiptFor = null
                try {
                    val d = Docs.import(this, u, title = p["product"]?.takeIf { it.isNotBlank() }?.let { "Beleg $it" }, catId = "warranty")
                    val existing = Docs.warranties(this).firstOrNull { it["id"] == p["editing"] }
                    saveWarranty(existing, mapOf(
                        "id" to (existing?.get("id") ?: System.currentTimeMillis().toString(36)),
                        "product" to p["product"].orEmpty().ifBlank { d["title"].orEmpty() }, "store" to p["store"].orEmpty(),
                        "bought" to p["bought"].orEmpty().ifBlank { LocalDate.now().format(Docs.DATE) },
                        "months" to p["months"].orEmpty().ifBlank { "24" }, "price" to p["price"].orEmpty(),
                        "note" to p["note"].orEmpty(), "doc" to d["id"].orEmpty(),
                    ))
                    toast("Beleg gespeichert – Angaben prüfen")
                    editWarranty(Docs.warranties(this).firstOrNull { it["doc"] == d["id"] })
                } catch (e: Exception) { toast("Fehler: ${e.message}") }
            }
        }
    }
}

/** "Share → FlowKit" for files from any app (PDF, photo, screenshot, Office file). */
class ReceiveDocActivity : Activity() {

    companion object {
        /** Small dialog: title + category, then import. */
        fun askAndImport(a: Activity, uri: Uri, then: () -> Unit) {
            val name = Docs.nameOf(a.contentResolver, uri)
            val (guess, _) = Docs.classify(name)
            val box = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; setPadding(a.dp(20), a.dp(4), a.dp(20), 0) }
            box.label("Titel", 12f, muted = true, topDp = 8)
            val title = a.input(name.substringBeforeLast('.')); box.addView(title)
            box.label("Kategorie (automatisch erkannt: ${guess.icon} ${guess.title})", 12f, muted = true, topDp = 12)
            var cat = guess.id
            val catRow = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
            fun drawCats() {
                catRow.removeAllViews()
                var row: LinearLayout? = null
                Docs.cats.forEachIndexed { i, k ->
                    if (i % 2 == 0) row = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL }.also { catRow.addView(it) }
                    row!!.chip("${k.icon} ${k.title}", cat == k.id) { cat = k.id; drawCats() }
                }
            }
            drawCats()
            box.addView(catRow)
            AlertDialog.Builder(a).setTitle("Im Tresor speichern")
                .setView(ScrollView(a).apply { addView(box) })
                .setPositiveButton("Speichern") { _, _ ->
                    try { Docs.import(a, uri, title.text.toString(), cat); a.toast("Gespeichert"); then() } catch (e: Exception) { a.toast("Fehler: ${e.message}") }
                }
                .setNeutralButton("Als Garantie-Beleg") { _, _ ->
                    try {
                        val d = Docs.import(a, uri, title.text.toString(), "warranty")
                        a.startActivity(Intent(a, DocsActivity::class.java).putExtra("tab", "warranty").putExtra("newFor", d["id"]))
                        then()
                    } catch (e: Exception) { a.toast("Fehler: ${e.message}") }
                }
                .setNegativeButton("Abbrechen") { _, _ -> then() }
                .show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uris = ArrayList<Uri>()
        @Suppress("DEPRECATION")
        if (intent.action == Intent.ACTION_SEND_MULTIPLE) intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.let { uris += it }
        else intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.let { uris += it }
        if (uris.isEmpty()) { finish(); return }
        if (uris.size == 1) {
            askAndImport(this, uris[0]) { finish() }
        } else {
            Thread {
                var n = 0
                for (u in uris) try { Docs.import(this, u); n++ } catch (e: Exception) { }
                runOnUiThread { toast("$n Dateien im Tresor gespeichert"); finish() }
            }.start()
        }
    }
}
