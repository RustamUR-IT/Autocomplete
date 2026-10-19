package app.textexpander

import android.app.Activity
import android.app.AlertDialog
import java.text.DateFormat
import java.util.Date

/** Settings and overview for smart suggestions. */
class SuggestActivity : Activity() {

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun appName(pkg: String) = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) { pkg }

    private fun render() {
        val engine = SuggestEngine.get(this)
        val contacts = engine.contacts()
        val c = page("Smarte Vorschläge", "Lernt aus dem, was du schreibst – getrennt für jede Person und jeden Chat. Bleibt nur auf dem Handy.")

        c.card {
            toggle("Vorschläge beim Tippen anzeigen", Store.getBool(context, "suggest_on", true)) {
                Store.putBool(context, "suggest_on", it)
            }
            toggle("Aus meinem Schreiben lernen", Store.getBool(context, "learn_on", true)) {
                Store.putBool(context, "learn_on", it)
            }
            label("${engine.wordCount()} Wörter · ${contacts.size} Chats & Kontakte gelernt", 13f, muted = true, topDp = 8)
        }

        c.section("So funktioniert's")
        c.label(
            "• Beim Tippen: Wort fertig schreiben und nächstes Wort vorschlagen – so, wie du schreibst.\n" +
                "• Pro Person: Was du einer Person öfter schreibst, steht bei ihr weiter vorne.\n" +
                "• Leeres Feld im Chat: deine häufigsten Antworten an genau diese Person.\n" +
                "• Rote Chips: ganzer Satz, den du dieser Person schon geschrieben hast.\n" +
                "• Am Anfang ist es leer. Nach ein paar Tagen Schreiben wird es spürbar besser.",
            13f, muted = true,
        )

        c.section("Datenschutz")
        c.card {
            label("Passwortfelder werden nie gelesen. Nichts verlässt das Handy – FlowKit hat keine Internet-Berechtigung.", 13f, muted = true, topDp = 0)
            pill("Apps vom Lernen ausschließen (${Store.list(context, "nolearn").size})") {
                ListActivity.open(this@SuggestActivity, "nolearn")
            }
            pill("Alles Gelernte löschen") {
                AlertDialog.Builder(this@SuggestActivity)
                    .setTitle("Alles löschen?")
                    .setMessage("Alle gelernten Wörter und Nachrichten werden entfernt.")
                    .setPositiveButton("Löschen") { _, _ -> engine.clear(); render() }
                    .setNegativeButton("Abbrechen", null)
                    .show()
            }
        }

        c.section("Chats & Kontakte")
        if (contacts.isEmpty()) c.label("Noch nichts gelernt. Schreib einfach wie immer.", 13f, muted = true)
        for (ct in contacts.take(60)) {
            val name = ct.title.ifBlank { appName(ct.pkg) }
            val when_ = DateFormat.getDateInstance(DateFormat.SHORT).format(Date(ct.last))
            c.card {
                label(name, 16f, bold = true, topDp = 0)
                label("${appName(ct.pkg)} · ${ct.messages} Nachrichten · ${ct.words} Wörter · $when_", 12f, muted = true)
            }.apply {
                isClickable = true
                setOnClickListener {
                    val msgs = engine.topMessages(ct.key)
                    AlertDialog.Builder(this@SuggestActivity)
                        .setTitle(name)
                        .setMessage(if (msgs.isEmpty()) "Noch keine gesendeten Nachrichten gelernt." else "Häufigste Antworten:\n\n• " + msgs.joinToString("\n• "))
                        .setPositiveButton("OK", null)
                        .setNegativeButton("Vergessen") { _, _ -> engine.deleteContact(ct.key); render() }
                        .show()
                }
            }
        }
    }
}
