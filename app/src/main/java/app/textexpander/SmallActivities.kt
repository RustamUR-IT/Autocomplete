package app.textexpander

import android.app.Activity
import android.app.AlertDialog
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.view.WindowManager
import android.widget.LinearLayout
import java.net.URLEncoder

/** Brain Dump: a small dialog to jot down a thought. Opened from the tile, ;notiz or the home screen. */
class QuickNoteActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val field = input(hint = "Was geht dir durch den Kopf?", multiline = true)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(field)
        }
        val dlg = AlertDialog.Builder(this)
            .setTitle("🧠 Brain Dump")
            .setView(box)
            .setPositiveButton("Speichern") { _, _ ->
                val text = field.text.toString().trim()
                if (text.isNotEmpty()) {
                    Store.add(this, "notes", mapOf("text" to text, "time" to System.currentTimeMillis().toString()))
                    toast("Gespeichert")
                }
            }
            .setNeutralButton("Alle Notizen") { _, _ -> ListActivity.open(this, "notes") }
            .setNegativeButton("Abbrechen", null)
            .create()
        dlg.setOnDismissListener { finish() }
        dlg.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        dlg.show()
        field.requestFocus()
    }
}

/** "In Clipboard speichern" from the text-selection menu, and "share → FlowKit" from any app. */
class SaveClipActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = (intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)
            ?: intent.getCharSequenceExtra(Intent.EXTRA_TEXT))?.toString()?.trim().orEmpty()
        if (text.isEmpty()) { finish(); return }

        val existing = Store.list(this, "clips").mapNotNull { it["cat"] }.filter { it.isNotBlank() }
        val base = if (text.startsWith("http")) listOf("Links", "Uni", "Texte", "Code") else listOf("Texte", "Uni", "Code", "Links")
        val cats = (base + existing).distinct()

        AlertDialog.Builder(this)
            .setTitle("Im Smart Clipboard speichern unter …")
            .setItems(cats.toTypedArray()) { _, i ->
                val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)
                val title = (subject ?: text.lineSequence().first()).take(40)
                Store.add(this, "clips", mapOf("cat" to cats[i], "title" to title, "text" to text))
                toast("Gespeichert in „${cats[i]}“")
            }
            .setOnDismissListener { finish() }
            .show()
    }
}

/** Sends selected text with a ready-made prompt to the chosen AI assistant. */
class ProcessTextActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()?.trim().orEmpty()
        if (text.isNotEmpty()) {
            val cls = intent.component?.className.orEmpty()
            val prompt = when {
                cls.endsWith("SummarizeAction") -> "Fasse folgenden Text kurz in Stichpunkten auf Deutsch zusammen:\n\n"
                cls.endsWith("TranslateAction") -> "Übersetze folgenden Text ins Deutsche (falls er schon Deutsch ist, ins Englische). Nur die Übersetzung:\n\n"
                cls.endsWith("QuizAction") -> "Formuliere aus folgendem Text 3 Lernfragen mit kurzen Musterantworten für meine Prüfungsvorbereitung an der FH:\n\n"
                else -> "Erkläre mir folgendes einfach und verständlich, mit einem kurzen Beispiel. Ich studiere an einer FH (Informatik, Programmieren in C):\n\n"
            } + text
            Assistant.send(this, prompt)
        }
        finish()
    }
}

object Assistant {
    class A(val id: String, val name: String, val pkg: String, val web: (String) -> String)

    val all = listOf(
        A("copilot", "Microsoft Copilot", "com.microsoft.copilot") { "https://copilot.microsoft.com/?q=$it" },
        A("chatgpt", "ChatGPT", "com.openai.chatgpt") { "https://chatgpt.com/?q=$it" },
        A("claude", "Claude", "com.anthropic.claude") { "https://claude.ai/new?q=$it" },
        A("gemini", "Gemini", "com.google.android.apps.bard") { "https://gemini.google.com/app" },
    )

    fun current(ctx: Context) = all.firstOrNull { it.id == Store.getStr(ctx, "assistant", "copilot") } ?: all[0]

    fun send(ctx: Context, prompt: String) {
        ctx.copyText(prompt, "Prompt kopiert")
        val a = current(ctx)
        val share = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, prompt).setPackage(a.pkg)
        try {
            if (share.resolveActivity(ctx.packageManager) != null) {
                ctx.startActivity(share)
            } else {
                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(a.web(URLEncoder.encode(prompt, "UTF-8")))))
            }
            ctx.toast("Falls das Feld leer ist: einfügen – der Prompt ist kopiert")
        } catch (e: ActivityNotFoundException) {
            ctx.toast("Prompt kopiert – in ${a.name} einfügen")
        }
    }
}

/** Base for tiles that open an activity and collapse the shade. */
abstract class LaunchTile : TileService() {
    abstract fun target(): Intent

    override fun onClick() {
        val i = target().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(i)
        }
    }
}

class NoteTileService : LaunchTile() {
    override fun target() = Intent(this, QuickNoteActivity::class.java)
}

class StudyTileService : LaunchTile() {
    override fun target(): Intent = Intent(this, FocusActivity::class.java).apply {
        if (FocusService.mode != "study") putExtra("start", "study")
    }

    override fun onStartListening() {
        qsTile?.apply {
            state = if (FocusService.mode == "study") Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            updateTile()
        }
    }
}
