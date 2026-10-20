package app.textexpander

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/** Every FlowKit function can be switched on/off. Off = hidden everywhere and doing nothing. */
object Features {
    class F(
        val id: String,
        val title: String,
        val desc: String,
        val default: Boolean = true,
        /** Manifest components (tiles, text-menu entries) that appear/disappear with the feature. */
        val components: List<String> = emptyList(),
    )

    val all = listOf(
        F("expander", "⌨️ Text-Kürzel", ";mail, ;date … werden beim Tippen ersetzt"),
        F("suggest", "💡 Smarte Vorschläge", "Wortvorschläge & Antworten pro Person über dem Eingabefeld"),
        F("launcher", "🚀 Quick Launcher", ";fh, ;gym … öffnen Seiten, Apps, Aktionen"),
        F("copy", "👆 Tippen zum Kopieren", "Doppeltippen = Abschnitt kopieren · lange drücken = alles auf dem Bildschirm · Kachel „Text kopieren“",
            components = listOf("CopyTileService")),
        F("clipboard", "📋 Smart Clipboard", "Gespeicherte Texte + „In Clipboard speichern“ im Textmenü",
            components = listOf("SaveClipActivity")),
        F("textactions", "✨ Text-Aktionen", "Erklären / Zusammenfassen / Übersetzen / Lernfrage im Textmenü",
            components = listOf("ExplainAction", "SummarizeAction", "TranslateAction", "QuizAction")),
        F("focus", "🎯 Fokus-Modi", "Study Mode & Workout Mode + Kachel „Study Mode“",
            components = listOf("StudyTileService")),
        F("places", "📍 Orts-Modi", "Gym-/FH-WLAN erkennen und Modus starten"),
        F("notes", "🧠 Brain Dump", "Schnelle Notizen + Kachel „Brain Dump“",
            components = listOf("NoteTileService")),
        F("cheat", "💻 C-Spickzettel", "Offline-Nachschlagewerk für C"),
        F("friction", "🛑 Habit Friction", "Wartezeit, bevor sich gebremste Apps öffnen"),
        F("doom", "⏳ Doomscroll-Limit", "Hinweis nach X Minuten am Stück in gebremsten Apps", default = false),
    )

    private fun find(id: String) = all.first { it.id == id }

    fun on(ctx: Context, id: String) = Store.getBool(ctx, "feat_$id", find(id).default)

    fun set(ctx: Context, id: String, value: Boolean) {
        Store.putBool(ctx, "feat_$id", value)
        applyComponents(ctx, find(id), value)
    }

    /** Re-applies all component states (e.g. after restoring a backup). */
    fun applyAll(ctx: Context) = all.forEach { applyComponents(ctx, it, on(ctx, it.id)) }

    private fun applyComponents(ctx: Context, f: F, value: Boolean) {
        val pm = ctx.packageManager
        val state = if (value) PackageManager.COMPONENT_ENABLED_STATE_DEFAULT else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        for (name in f.components) {
            try {
                pm.setComponentEnabledSetting(ComponentName(ctx.packageName, "${ctx.packageName}.$name"), state, PackageManager.DONT_KILL_APP)
            } catch (e: Exception) { }
        }
    }
}
