package app.textexpander

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings

class MainActivity : Activity() {

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        val c = page("FlowKit", "Kürzel, Fokus und weniger Ablenkung – für FH, Code und Gym.")

        // ── Status / permissions ──────────────────────────────────────────
        val acc = ExpanderService.isEnabled(this)
        val dnd = sys<NotificationManager>().isNotificationPolicyAccessGranted
        val notif = Build.VERSION.SDK_INT < 33 ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

        c.card {
            if (acc && dnd && notif) {
                label("✅  Alles aktiv", 16f, bold = true, topDp = 0)
                label("Kürzel, Launcher und Habit Friction laufen in jeder App.", 13f, muted = true)
            } else {
                label("Einrichtung", 16f, bold = true, topDp = 0)
            }
            if (!acc) {
                label("⚠️  Bedienungshilfe ist aus – ohne sie funktionieren Kürzel, Launcher und Habit Friction nicht.", 13f)
                pill("1 · Bedienungshilfe einschalten", primary = true) {
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
                label("Schalter ausgegraut? Einstellungen → Apps → FlowKit → ⋮ oben rechts → „Eingeschränkte Einstellungen zulassen“, dann nochmal.", 12f, muted = true)
            }
            if (!dnd) pill("2 · „Nicht stören“-Zugriff erlauben (Fokus-Modi)") {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            }
            if (!notif) pill("3 · Benachrichtigungen erlauben (Timer)") {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            }
        }

        // ── Quick start ───────────────────────────────────────────────────
        c.section("Schnellstart")
        FocusService.mode?.let { m ->
            val left = ((FocusService.endAt - System.currentTimeMillis()) / 60000).coerceAtLeast(0)
            c.pill("⏱  ${FocusService.label(m)} läuft – noch $left min", primary = true) { Launch.open(this, "action:focus") }
        }
        val focusOn = Features.on(this, "focus")
        val notesOn = Features.on(this, "notes")
        if (focusOn || notesOn) c.row {
            if (focusOn) pill("📚 Study") { Launch.open(this@MainActivity, "action:study") }
            if (focusOn) pill("🏋️ Workout") { Launch.open(this@MainActivity, "action:workout") }
            if (notesOn) pill("📝 Notiz") { Launch.open(this@MainActivity, "action:note") }
        }

        // ── Tools ─────────────────────────────────────────────────────────
        c.section("Werkzeuge")
        val snippets = Store.list(this, "snippets")
        if (Features.on(this, "expander")) c.tool("⌨️  Text-Kürzel", "${snippets.size} Kürzel · " + snippets.take(5).joinToString(" ") { it["abbr"].orEmpty() } + " …") {
            ListActivity.open(this, "snippets")
        }
        if (Features.on(this, "suggest")) c.tool("💡  Smarte Vorschläge", "Lernt, wie du schreibst – pro Person. ${SuggestEngine.get(this).contacts().size} Chats gelernt") {
            startActivity(Intent(this, SuggestActivity::class.java))
        }
        if (Features.on(this, "places")) c.tool("📍  Orts-Modi", "Gym-WLAN → Workout Mode · FH-WLAN → Nicht stören · ${Store.list(this, "places").size} Orte") {
            startActivity(Intent(this, PlacesActivity::class.java))
        }
        if (Features.on(this, "clipboard")) c.tool("📋  Smart Clipboard", "${Store.list(this, "clips").size} gespeicherte Texte, Links, Uni-Sachen und Code") {
            ListActivity.open(this, "clips")
        }
        if (Features.on(this, "launcher")) c.tool("🚀  Quick Launcher", Store.list(this, "launchers").joinToString(" ") { it["abbr"].orEmpty() }) {
            ListActivity.open(this, "launchers")
        }
        if (Features.on(this, "focus")) c.tool("🎯  Fokus-Modi", "Study Mode (DND + Timer + Lern-Apps) · Workout Mode (Musik + Timer + Satzpausen)") {
            Launch.open(this, "action:focus")
        }
        if (Features.on(this, "notes")) c.tool("🧠  Brain Dump", "${Store.list(this, "notes").size} Notizen · auch als Schnelleinstellungs-Kachel") {
            ListActivity.open(this, "notes")
        }
        if (Features.on(this, "cheat")) c.tool("💻  C-Spickzettel", "scanf, printf, Arrays, Pointer, Strings, Schleifen, struct … offline") {
            ListActivity.open(this, "cheat")
        }
        c.tool("⚙️  Einstellungen", "Funktionen an/aus · Backup · Habit Friction · Text-Aktionen") {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // ── Tips ──────────────────────────────────────────────────────────
        c.section("Tipps")
        c.label(
            "• Text markieren → ⋮ im Menü: „Erklären“, „Zusammenfassen“, „Übersetzen“, „Lernfrage“, „In Clipboard speichern“.\n" +
                "• Schnelleinstellungen runterziehen → Stift → Kacheln „Brain Dump“, „Study Mode“ und „Text kopieren“ hinzufügen.\n" +
                "• Doppeltippen auf eine Nachricht kopiert den Abschnitt, lange drücken kopiert alles auf dem Bildschirm.\n" +
                "• Kürzel feuern sofort beim Tippen – deshalb beginnen alle mit ;",
            13f, muted = true,
        )
    }
}
