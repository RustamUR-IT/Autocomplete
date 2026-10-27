package app.textexpander

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Document vault: files are copied into FlowKit's private storage (nothing leaves the phone),
 * sorted into categories by their name, and can be opened or shared again from the app.
 */
object Docs {
    val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    class Cat(val id: String, val title: String, val icon: String, val important: Boolean, val words: List<String>)

    val cats = listOf(
        Cat("id", "Ausweise", "🪪", true, listOf("ausweis", "reisepass", "passport", "personalausweis", "führerschein", "fuehrerschein", "driving licence", "e-card", "ecard", "meldezettel", "meldebestätigung", "geburtsurkunde", "staatsbürgerschaft", "visa", "visum", "aufenthalt")),
        Cat("contract", "Verträge", "📝", true, listOf("vertrag", "contract", "mietvertrag", "miete", "kündigung", "kuendigung", "agb", "vereinbarung", "agreement", "lease", "handyvertrag", "arbeitsvertrag", "dienstvertrag", "mitgliedschaft", "abo", "subscription")),
        Cat("insurance", "Versicherung", "🛡️", true, listOf("versicherung", "insurance", "polizze", "police", "haftpflicht", "ögk", "oegk", "sozialversicherung", "krankenkasse", "unfall", "schaden")),
        Cat("uni", "FH & Uni", "🎓", true, listOf("fh", "uni", "zeugnis", "transcript", "immatrikulation", "inskription", "studienbestätigung", "studienbestaetigung", "studienblatt", "bachelor", "master", "prüfung", "pruefung", "exam", "semester", "ects", "stipendium", "studienbeihilfe", "notenspiegel", "sammelzeugnis", "matura", "diplom", "zertifikat", "certificate", "lehrveranstaltung", "skript", "vorlesung", "übung", "uebung", "abgabe", "praktikum")),
        Cat("finance", "Finanzen", "🏦", true, listOf("kontoauszug", "statement", "bank", "iban", "kredit", "loan", "steuer", "tax", "finanzamt", "arbeitnehmerveranlagung", "lohnzettel", "gehalt", "lohn", "payslip", "salary", "dienstzettel", "jahreslohnzettel", "l16", "sparen", "depot", "überweisung", "ueberweisung")),
        Cat("health", "Gesundheit", "🩺", true, listOf("befund", "arzt", "doctor", "rezept", "impf", "vaccin", "krank", "spital", "krankenhaus", "labor", "röntgen", "roentgen", "mrt", "therapie", "zahnarzt", "medikament", "attest", "krankmeldung")),
        Cat("invoice", "Rechnungen & Belege", "🧾", false, listOf("rechnung", "invoice", "beleg", "receipt", "quittung", "kassenbon", "bon", "bestellung", "order", "zahlung", "payment", "mahnung", "gutschrift", "lieferschein", "auftragsbestätigung",
            "amazon", "mediamarkt", "saturn", "zalando", "ikea", "shein", "temu", "aliexpress", "ebay", "willhaben", "paypal", "klarna", "hornbach", "obi", "dm", "billa", "spar", "hofer", "lidl", "thalia", "apple", "nothing", "cyberport", "e-tec", "conrad", "hm", "zara", "nike", "adidas", "decathlon", "gymshark", "myprotein")),
        Cat("warranty", "Garantie", "🔧", false, listOf("garantie", "warranty", "gewährleistung", "gewaehrleistung", "kaufbeleg", "kaufvertrag", "seriennummer", "serial")),
        Cat("work", "Arbeit", "💼", false, listOf("lebenslauf", "cv", "resume", "bewerbung", "application", "arbeitszeugnis", "dienstzeugnis", "referenz", "motivationsschreiben", "jobs", "stellen", "gehaltsabrechnung")),
        Cat("travel", "Reisen & Tickets", "✈️", false, listOf("ticket", "flug", "flight", "boarding", "hotel", "booking", "buchung", "reservierung", "reservation", "bahn", "öbb", "oebb", "zug", "train", "klimaticket", "jahreskarte", "wiener linien", "semesterticket", "konzert", "event", "eintritt")),
        Cat("home", "Wohnen & Verträge", "🏠", false, listOf("strom", "gas", "energie", "wien energie", "internet", "magenta", "a1", "drei", "spusu", "betriebskosten", "hausverwaltung", "gis", "ORF", "wohnung", "kaution")),
        Cat("other", "Sonstiges", "📄", false, emptyList()),
    )

    fun cat(id: String?) = cats.firstOrNull { it.id == id } ?: cats.last()

    /** Guesses the category from the file name; second value = how sure we are (0..3). */
    fun classify(name: String): Pair<Cat, Int> {
        val n = name.lowercase().replace('_', ' ').replace('-', ' ').replace('.', ' ')
        var best: Cat? = null
        var bestHits = 0
        for (c in cats) {
            val hits = c.words.count { w -> Regex("(^|[^a-zäöüß])" + Regex.escape(w) + "([^a-zäöüß]|$)").containsMatchIn(n) }
            if (hits > bestHits) { best = c; bestHits = hits }
        }
        return (best ?: cats.last()) to bestHits.coerceAtMost(3)
    }

    private val datePatterns = listOf(
        Regex("(20\\d{2})[-_.]?(0[1-9]|1[0-2])[-_.]?(0[1-9]|[12]\\d|3[01])") to "ymd",   // 2026-03-14, 20260314
        Regex("(0[1-9]|[12]\\d|3[01])[-_.](0[1-9]|1[0-2])[-_.](20\\d{2})") to "dmy",         // 14.03.2026
        Regex("(20\\d{2})[-_.](0[1-9]|1[0-2])(?![\\d])") to "ym",                             // 2026-03
    )

    /** Finds a date in a file name like "Rechnung_2026-03-14.pdf" or "IMG_20261019_123456.jpg". */
    fun dateFromName(name: String): LocalDate? {
        for ((re, kind) in datePatterns) {
            val m = re.find(name) ?: continue
            val g = m.groupValues
            return try {
                when (kind) {
                    "ymd" -> LocalDate.of(g[1].toInt(), g[2].toInt(), g[3].toInt())
                    "dmy" -> LocalDate.of(g[3].toInt(), g[2].toInt(), g[1].toInt())
                    else -> LocalDate.of(g[1].toInt(), g[2].toInt(), 1)
                }.takeIf { !it.isAfter(LocalDate.now().plusDays(1)) && it.year >= 2000 }
            } catch (e: Exception) { null }
        }
        return null
    }

    private val shops = linkedMapOf(
        "amazon" to "Amazon", "mediamarkt" to "MediaMarkt", "saturn" to "Saturn", "zalando" to "Zalando", "ikea" to "IKEA",
        "shein" to "Shein", "temu" to "Temu", "aliexpress" to "AliExpress", "ebay" to "eBay", "willhaben" to "willhaben",
        "hornbach" to "Hornbach", "obi" to "OBI", "thalia" to "Thalia", "apple" to "Apple", "nothing" to "Nothing",
        "cyberport" to "Cyberport", "e-tec" to "e-tec", "conrad" to "Conrad", "decathlon" to "Decathlon",
        "gymshark" to "Gymshark", "myprotein" to "Myprotein", "nike" to "Nike", "adidas" to "adidas", "zara" to "Zara",
    )

    /** Shop name mentioned in a file name, e.g. "Rechnung_Amazon_2026.pdf" → "Amazon". */
    fun shopIn(name: String): String? {
        val n = name.lowercase()
        return shops.entries.firstOrNull { (k, _) -> Regex("(^|[^a-z])" + Regex.escape(k) + "([^a-z]|$)").containsMatchIn(n) }?.value
    }

    /** Same file already in the vault? (name + size) */
    fun duplicateOf(ctx: Context, name: String, size: Long): Rec? =
        all(ctx).firstOrNull { it["name"] == name && it["size"]?.toLongOrNull() == size }

    fun isDocumentFile(name: String, mime: String?): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase()
        if (ext in setOf("pdf", "doc", "docx", "odt", "rtf", "txt", "xls", "xlsx", "csv", "ppt", "pptx")) return true
        if (mime != null && (mime.startsWith("image/") || mime == "application/pdf" || mime.startsWith("application/vnd"))) return true
        return ext in setOf("jpg", "jpeg", "png", "heic", "webp")
    }

    // ── Storage ───────────────────────────────────────────────────────────

    fun dir(ctx: Context): File = File(ctx.filesDir, "docs").apply { mkdirs() }

    fun all(ctx: Context): List<Rec> = Store.list(ctx, "docs")

    fun file(ctx: Context, d: Rec): File = File(dir(ctx), d["file"].orEmpty())

    fun nameOf(cr: ContentResolver, uri: Uri): String {
        try {
            cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0)?.let { n -> if (n.isNotBlank()) return n }
            }
        } catch (e: Exception) { }
        return uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':') ?: "Dokument"
    }

    /** Copies the file into the vault and records it. Returns the new record. */
    fun import(ctx: Context, uri: Uri, title: String? = null, catId: String? = null, note: String = "", expiry: String = ""): Rec {
        val cr = ctx.contentResolver
        val name = nameOf(cr, uri)
        val srcSize = try {
            cr.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { if (it.moveToFirst() && !it.isNull(0)) it.getLong(0) else -1L } ?: -1L
        } catch (e: Exception) { -1L }
        if (srcSize > 0) duplicateOf(ctx, name, srcSize)?.let { return it }   // already saved – don't store twice
        val mime = cr.getType(uri) ?: guessMime(name)
        val id = System.currentTimeMillis().toString(36) + (0..999).random()
        val safeName = name.replace(Regex("[^A-Za-z0-9._ÄÖÜäöüß-]"), "_").take(80)
        val target = File(dir(ctx), "${id}_$safeName")
        cr.openInputStream(uri)!!.use { input -> target.outputStream().use { input.copyTo(it) } }
        val (guess, _) = classify(name)
        val rec = mapOf(
            "id" to id,
            "title" to (title?.takeIf { it.isNotBlank() } ?: name.substringBeforeLast('.')),
            "name" to name,
            "cat" to (catId ?: guess.id),
            "mime" to mime,
            "size" to target.length().toString(),
            "file" to target.name,
            "added" to LocalDate.now().format(DATE),
            "date" to (dateFromName(name)?.format(DATE) ?: ""),
            "src" to uri.toString(),
            "note" to note,
            "expiry" to expiry,
        )
        Store.add(ctx, "docs", rec)
        return rec
    }

    fun update(ctx: Context, old: Rec, new: Rec) {
        Store.save(ctx, "docs", all(ctx).map { if (it["id"] == old["id"]) new else it })
    }

    fun delete(ctx: Context, d: Rec) {
        file(ctx, d).delete()
        Store.save(ctx, "docs", all(ctx).filter { it["id"] != d["id"] })
    }

    fun guessMime(name: String) = when (name.substringAfterLast('.', "").lowercase()) {
        "pdf" -> "application/pdf"
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        "heic" -> "image/heic"
        "txt" -> "text/plain"
        "doc" -> "application/msword"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "xls" -> "application/vnd.ms-excel"
        "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        "ppt", "pptx" -> "application/vnd.ms-powerpoint"
        else -> "application/octet-stream"
    }

    fun contentUri(ctx: Context, d: Rec): Uri =
        Uri.parse("content://${ctx.packageName}.docs/" + Uri.encode(d["file"]))

    fun open(ctx: Context, d: Rec) {
        val i = Intent(Intent.ACTION_VIEW)
            .setDataAndType(contentUri(ctx, d), d["mime"])
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        try { ctx.startActivity(i) } catch (e: Exception) { ctx.toast("Keine App zum Öffnen gefunden") }
    }

    fun share(ctx: Context, d: Rec) {
        val i = Intent(Intent.ACTION_SEND)
            .setType(d["mime"])
            .putExtra(Intent.EXTRA_STREAM, contentUri(ctx, d))
            .putExtra(Intent.EXTRA_SUBJECT, d["title"])
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(i, d["title"]).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun sizeText(bytes: Long) = when {
        bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000.0)
        bytes >= 1_000 -> "${bytes / 1000} KB"
        else -> "$bytes B"
    }

    fun parseDate(s: String?): LocalDate? = try { if (s.isNullOrBlank()) null else LocalDate.parse(s.trim(), DATE) } catch (e: Exception) { null }

    fun daysUntil(s: String?): Long? = parseDate(s)?.let { ChronoUnit.DAYS.between(LocalDate.now(), it) }

    // ── Warranties ────────────────────────────────────────────────────────

    fun warranties(ctx: Context): List<Rec> = Store.list(ctx, "warranties")

    fun warrantyEnd(w: Rec): LocalDate? {
        val bought = parseDate(w["bought"]) ?: return null
        val months = w["months"]?.toLongOrNull() ?: 24L
        return bought.plusMonths(months)
    }

    fun warrantyDaysLeft(w: Rec): Long? = warrantyEnd(w)?.let { ChronoUnit.DAYS.between(LocalDate.now(), it) }

    fun warrantyStatus(w: Rec): String {
        val d = warrantyDaysLeft(w) ?: return "Kaufdatum fehlt"
        val end = warrantyEnd(w)!!.format(DATE)
        return when {
            d < 0 -> "abgelaufen seit ${-d} Tagen ($end)"
            d == 0L -> "läuft heute ab!"
            d < 30 -> "noch $d Tage ($end) ⚠️"
            d < 365 -> "noch ${d / 30} Monate ($end)"
            else -> "bis $end"
        }
    }
}

/** Serves vault files to other apps (open in PDF viewer, share) without AndroidX FileProvider. */
class DocProvider : android.content.ContentProvider() {
    override fun onCreate() = true

    private fun fileFor(uri: Uri): File? {
        val name = uri.lastPathSegment ?: return null
        if (name.contains("..") || name.contains('/')) return null
        val f = File(Docs.dir(context!!), name)
        return if (f.exists()) f else null
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val f = fileFor(uri) ?: throw java.io.FileNotFoundException(uri.toString())
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri): String? = fileFor(uri)?.let { Docs.guessMime(it.name) }

    override fun query(uri: Uri, projection: Array<String>?, selection: String?, selectionArgs: Array<String>?, sortOrder: String?): android.database.Cursor {
        val f = fileFor(uri)
        val cols = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        val cursor = android.database.MatrixCursor(cols)
        if (f != null) {
            cursor.addRow(cols.map { c ->
                when (c) {
                    OpenableColumns.DISPLAY_NAME -> f.name.substringAfter('_')
                    OpenableColumns.SIZE -> f.length()
                    else -> null
                }
            })
        }
        return cursor
    }

    override fun insert(uri: Uri, values: android.content.ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?) = 0
    override fun update(uri: Uri, values: android.content.ContentValues?, selection: String?, selectionArgs: Array<String>?) = 0
}

/**
 * Looks through folders you picked (Downloads, WhatsApp Documents, …) for files that look like
 * important documents, so you can add them to the vault with one tap.
 */
object DocScanner {
    class Find(val uri: Uri, val name: String, val mime: String?, val size: Long, val modified: Long, val cat: Docs.Cat, val score: Int, val folder: String)

    fun roots(ctx: Context): List<Rec> = Store.list(ctx, "docroots")

    fun addRoot(ctx: Context, uri: Uri) {
        try {
            ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: Exception) { }
        val label = uri.lastPathSegment?.substringAfterLast(':')?.ifBlank { null } ?: "Ordner"
        if (roots(ctx).none { it["uri"] == uri.toString() })
            Store.add(ctx, "docroots", mapOf("label" to label, "uri" to uri.toString()))
    }

    fun removeRoot(ctx: Context, r: Rec) = Store.save(ctx, "docroots", roots(ctx).filter { it["uri"] != r["uri"] })

    private fun dismissed(ctx: Context) = Store.getStr(ctx, "doc_dismissed", "").split('\n').filter { it.isNotEmpty() }.toSet()

    fun dismiss(ctx: Context, f: Find) {
        Store.putStr(ctx, "doc_dismissed", (dismissed(ctx) + f.uri.toString()).joinToString("\n"))
    }

    /** All candidate files in all roots, best matches first. Slow – run off the main thread. */
    fun scan(ctx: Context, onlyImportant: Boolean = false): List<Find> {
        val cr = ctx.contentResolver
        // Already in the vault: same source file, or same name + size (= duplicate)
        val known = Docs.all(ctx).mapNotNull { it["src"] }.toSet() + Docs.all(ctx).map { it["name"] + "|" + it["size"] }
        val skip = dismissed(ctx)
        val out = ArrayList<Find>()
        for (r in roots(ctx)) {
            val tree = Uri.parse(r["uri"])
            try {
                walk(cr, tree, DocumentsContract.getTreeDocumentId(tree), r["label"].orEmpty(), 0, out, known, skip)
            } catch (e: Exception) { }
            if (out.size > 2000) break
        }
        return out
            .filter { !onlyImportant || it.score > 0 }
            .sortedWith(compareByDescending<Find> { it.score }.thenByDescending { it.modified })
    }

    private fun walk(cr: ContentResolver, tree: Uri, docId: String, folder: String, depth: Int, out: MutableList<Find>, known: Set<String>, skip: Set<String>) {
        if (depth > 3 || out.size > 2000) return
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId)
        val cols = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
        cr.query(children, cols, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0) ?: continue
                val name = c.getString(1) ?: continue
                val mime = c.getString(2)
                val size = if (c.isNull(3)) 0L else c.getLong(3)
                val modified = if (c.isNull(4)) 0L else c.getLong(4)
                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                    if (!name.startsWith(".")) walk(cr, tree, id, "$folder/$name", depth + 1, out, known, skip)
                    continue
                }
                if (!Docs.isDocumentFile(name, mime)) continue
                if (size < 2_000 && mime?.startsWith("image/") == true) continue   // icons, stickers
                val uri = DocumentsContract.buildDocumentUriUsingTree(tree, id)
                val s = uri.toString()
                if (s in known || s in skip || "$name|$size" in known) continue
                val (cat, score) = Docs.classify(name)
                // PDFs in folders like "Documents" are usually worth a look even without a keyword
                val bonus = if (score == 0 && mime == "application/pdf" && folder.contains("ocument", true)) 1 else 0
                out += Find(uri, name, mime, size, modified, cat, score + bonus, folder)
            }
        }
    }
}
