package app.textexpander

import android.content.Context
import java.text.DateFormat
import java.util.Date

enum class Tap { EDIT, COPY, OPEN }

data class Field(
    val key: String,
    val label: String,
    val hint: String = "",
    val multiline: Boolean = false,
    val mono: Boolean = false,
    val target: Boolean = false,
    val abbr: Boolean = false,
)

/** Describes one kind of list (snippets, clips, …) so a single screen can edit all of them. */
class Kind(
    val id: String,
    val title: String,
    val help: String,
    val fields: List<Field>,
    val defaults: List<Rec>,
    val tap: Tap = Tap.EDIT,
    val copyField: String = "text",
    val categoryField: String? = null,
    val mono: Boolean = false,
    val fullSub: Boolean = false,
    val stamp: Boolean = false,
    val sort: (List<Rec>) -> List<Rec> = { it },
    val titleOf: (Context, Rec) -> String,
    val subOf: (Context, Rec) -> String,
)

private fun r(vararg p: Pair<String, String>): Rec = mapOf(*p)

object Kinds {
    private val abbr = Field("abbr", "Kürzel", hint = ";hallo", abbr = true)
    private val label = Field("label", "Name")
    private val target = Field("target", "Ziel", hint = "https://… oder App/Aktion wählen", target = true)
    private val targetTitle: (Context, Rec) -> String = { _, r -> r["label"].orEmpty() }
    private val targetSub: (Context, Rec) -> String = { c, r -> Launch.describe(c, r["target"].orEmpty()) }

    val snippets = Kind(
        id = "snippets",
        title = "Text-Kürzel",
        help = "Kürzel in irgendeiner App tippen → wird sofort ersetzt.\nPlatzhalter: ${Dyn.HELP}",
        fields = listOf(abbr, Field("text", "Wird ersetzt durch", multiline = true)),
        defaults = listOf(
            r("abbr" to ";mail", "text" to "deine.mail@beispiel.at"),
            r("abbr" to ";adr", "text" to "Musterstraße 1\n1010 Wien"),
            r("abbr" to ";ty", "text" to "Danke für deine Nachricht!"),
            r("abbr" to ";lg", "text" to "Liebe Grüße"),
            r("abbr" to ";mfg", "text" to "Mit freundlichen Grüßen\n"),
            r("abbr" to ";hallo", "text" to "Hallo {cursor},\n\n\nLiebe Grüße"),
            r("abbr" to ";sorry", "text" to "Sorry für die späte Antwort!"),
            r("abbr" to ";ok", "text" to "Passt, danke!"),
            r("abbr" to ";krank", "text" to "Sehr geehrte Damen und Herren,\n\nleider kann ich heute ({date}) krankheitsbedingt nicht an der Lehrveranstaltung teilnehmen.\n\nMit freundlichen Grüßen"),
            r("abbr" to ";date", "text" to "{date}"),
            r("abbr" to ";today", "text" to "{weekday}, {date}"),
            r("abbr" to ";now", "text" to "{time}"),
            r("abbr" to ";week", "text" to "KW {week}"),
            r("abbr" to ";iso", "text" to "{isodate}"),
            r("abbr" to ";morgen", "text" to "{tomorrow}"),
            r("abbr" to ";main", "text" to "#include <stdio.h>\n\nint main(void) {\n    {cursor}\n    return 0;\n}"),
            r("abbr" to ";pf", "text" to "printf(\"{cursor}\\n\");"),
            r("abbr" to ";sf", "text" to "scanf(\"%d\", &{cursor});"),
            r("abbr" to ";for", "text" to "for (int i = 0; i < {cursor}; i++) {\n    \n}"),
        ),
        sort = { l -> l.sortedBy { it["abbr"].orEmpty().lowercase() } },
        titleOf = { _, r -> r["abbr"].orEmpty() },
        subOf = { _, r -> r["text"].orEmpty() },
    )

    val clips = Kind(
        id = "clips",
        title = "Smart Clipboard",
        help = "Antippen = kopieren · lange drücken = bearbeiten/löschen.\nTipp: Text markieren → „In Clipboard speichern“ oder aus jeder App teilen → FlowKit.",
        fields = listOf(
            Field("cat", "Kategorie", hint = "Texte / Links / Uni / Code"),
            Field("title", "Titel"),
            Field("text", "Inhalt", multiline = true),
        ),
        defaults = listOf(
            r("cat" to "Uni", "title" to "Matrikelnummer", "text" to "00000000"),
            r("cat" to "Links", "title" to "FH-Webseite", "text" to "https://"),
            r("cat" to "Links", "title" to "Stundenplan", "text" to "https://"),
            r("cat" to "Code", "title" to "Kompilieren & starten", "text" to "gcc -Wall -Wextra -o programm main.c && ./programm"),
            r("cat" to "Code", "title" to "git Basics", "text" to "git add .\ngit commit -m \"Nachricht\"\ngit push"),
            r("cat" to "Texte", "title" to "Frage an Lektor:in", "text" to "Sehr geehrte/r …,\n\nich hätte eine kurze Frage zur Lehrveranstaltung:\n\n\nMit freundlichen Grüßen"),
        ),
        tap = Tap.COPY,
        categoryField = "cat",
        sort = { l -> l.sortedWith(compareBy({ it["cat"].orEmpty().lowercase() }, { it["title"].orEmpty().lowercase() })) },
        titleOf = { _, r -> r["title"].orEmpty() },
        subOf = { _, r -> r["text"].orEmpty() },
    )

    val launchers = Kind(
        id = "launchers",
        title = "Quick Launcher",
        help = "Kürzel in einem beliebigen Textfeld tippen → öffnet Seite, App oder Aktion. Antippen = testen.",
        fields = listOf(abbr, label, target),
        defaults = listOf(
            r("abbr" to ";fh", "label" to "FH-Seite", "target" to "https://"),
            r("abbr" to ";moodle", "label" to "Moodle", "target" to "https://"),
            r("abbr" to ";gym", "label" to "Trainings-App", "target" to "app:"),
            r("abbr" to ";notiz", "label" to "Brain Dump", "target" to "action:note"),
            r("abbr" to ";lernen", "label" to "Study Mode", "target" to "action:study"),
            r("abbr" to ";workout", "label" to "Workout Mode", "target" to "action:workout"),
            r("abbr" to ";cheat", "label" to "C-Spickzettel", "target" to "action:cheat"),
            r("abbr" to ";clip", "label" to "Smart Clipboard", "target" to "action:clipboard"),
        ),
        tap = Tap.OPEN,
        sort = { l -> l.sortedBy { it["abbr"].orEmpty().lowercase() } },
        titleOf = { _, r -> "${r["abbr"]}  ·  ${r["label"]}" },
        subOf = targetSub,
    )

    val notes = Kind(
        id = "notes",
        title = "Brain Dump",
        help = "Gedanken schnell rein, später sortieren. Am schnellsten über die Schnelleinstellungs-Kachel „Brain Dump“.",
        fields = listOf(Field("text", "Notiz", multiline = true)),
        defaults = emptyList(),
        stamp = true,
        fullSub = true,
        sort = { l -> l.sortedByDescending { it["time"]?.toLongOrNull() ?: 0L } },
        titleOf = { _, r ->
            val t = r["time"]?.toLongOrNull() ?: 0L
            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(t))
        },
        subOf = { _, r -> r["text"].orEmpty() },
    )

    val cheat = Kind(
        id = "cheat",
        title = "C-Spickzettel",
        help = "Offline. Antippen = Code kopieren · lange drücken = bearbeiten. Eigene Einträge mit „+ Neu“.",
        fields = listOf(Field("title", "Thema"), Field("text", "Code / Erklärung", multiline = true, mono = true)),
        defaults = CheatSheet.entries.map { (t, c) -> r("title" to t, "text" to c) },
        tap = Tap.COPY,
        mono = true,
        fullSub = true,
        titleOf = { _, r -> r["title"].orEmpty() },
        subOf = { _, r -> r["text"].orEmpty() },
    )

    val studyapps = Kind(
        id = "studyapps",
        title = "Lern-Apps",
        help = "Öffnen sich beim Start des Study Mode der Reihe nach – die letzte liegt vorne, alle sind in „Letzte Apps“.",
        fields = listOf(label, target),
        defaults = listOf(r("label" to "Moodle", "target" to "https://")),
        titleOf = targetTitle, subOf = targetSub,
    )

    val alts = Kind(
        id = "alts",
        title = "Stattdessen anbieten",
        help = "Diese Buttons erscheinen, wenn du eine gebremste App öffnest.",
        fields = listOf(label, target),
        defaults = listOf(
            r("label" to "📚 Study Mode starten", "target" to "action:study"),
            r("label" to "📖 Lesen", "target" to "app:"),
            r("label" to "🏋️ Workout Mode", "target" to "action:workout"),
            r("label" to "📝 Gedanken notieren", "target" to "action:note"),
        ),
        titleOf = targetTitle, subOf = targetSub,
    )

    val blocked = Kind(
        id = "blocked",
        title = "Gebremste Apps",
        help = "Diese Apps zeigen erst eine Wartezeit und Alternativen, bevor sie sich öffnen. Mit „App wählen“ hinzufügen.",
        fields = listOf(label, target),
        defaults = listOf(
            r("label" to "Instagram", "target" to "app:com.instagram.android"),
            r("label" to "TikTok", "target" to "app:com.zhiliaoapp.musically"),
            r("label" to "Snapchat", "target" to "app:com.snapchat.android"),
            r("label" to "X / Twitter", "target" to "app:com.twitter.android"),
            r("label" to "Reddit", "target" to "app:com.reddit.frontpage"),
        ),
        titleOf = targetTitle, subOf = targetSub,
    )

    val nolearn = Kind(
        id = "nolearn",
        title = "Nicht lernen in …",
        help = "In diesen Apps lernt FlowKit nichts (z. B. Banking, Arbeit). Mit „App wählen“ hinzufügen.",
        fields = listOf(label, target),
        defaults = emptyList(),
        titleOf = targetTitle, subOf = targetSub,
    )

    private val all = listOf(snippets, clips, launchers, notes, cheat, studyapps, alts, blocked, nolearn).associateBy { it.id }
    fun get(id: String?): Kind? = all[id]
}
