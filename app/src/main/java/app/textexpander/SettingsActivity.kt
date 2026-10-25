package app.textexpander

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import java.time.LocalDate

class SettingsActivity : Activity() {

    private val REQ_EXPORT = 41
    private val REQ_IMPORT = 42

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        val c = page("Einstellungen")

        // ── Features on/off ───────────────────────────────────────────────
        c.section("Funktionen")
        c.card {
            label("Schalte aus, was du nicht brauchst – es verschwindet dann von der Startseite, aus dem Textmenü und den Schnelleinstellungen.", 13f, muted = true, topDp = 0)
            for (f in Features.all) {
                toggle(f.title, Features.on(context, f.id)) { Features.set(context, f.id, it); render() }
                label(f.desc, 12f, muted = true, topDp = 0)
            }
            if (Features.on(context, "copy")) pill("Apps ohne Doppeltipp-Kopieren (${Store.list(context, "nocopy").size})") {
                ListActivity.open(this@SettingsActivity, "nocopy")
            }
            label("Diagnose Kopieren: ${Copier.lastTap}", 12f, muted = true, mono = true, topDp = 10)
        }

        // ── Backup ────────────────────────────────────────────────────────
        c.section("💾 Backup")
        c.card {
            label("Speichert alles in eine Datei: Kürzel, Clipboard, Notizen, Lieblingsantworten, Orte, Einstellungen und was die Vorschläge gelernt haben. Am besten auf Google Drive ablegen.\nDie Dateien im Dokumente-Tresor selbst sind nicht enthalten – sie bleiben beim Update auf dem Handy, würden aber bei einer Deinstallation gelöscht (vorher über „Teilen“ sichern).", 13f, muted = true, topDp = 0)
            row {
                pill("Backup speichern", primary = true) {
                    @Suppress("DEPRECATION")
                    startActivityForResult(
                        Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                            .setType("application/json")
                            .putExtra(Intent.EXTRA_TITLE, "flowkit-backup-${LocalDate.now()}.json"),
                        REQ_EXPORT,
                    )
                }
                pill("Wiederherstellen") {
                    @Suppress("DEPRECATION")
                    startActivityForResult(
                        Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"),
                        REQ_IMPORT,
                    )
                }
            }
            val last = Store.getStr(context, "last_backup", "")
            label(if (last.isEmpty()) "Noch kein Backup gemacht." else "Letztes Backup: $last", 12f, muted = true, topDp = 6)
        }

        // ── Habit Friction ────────────────────────────────────────────────
        if (Features.on(this, "friction") || Features.on(this, "doom")) {
            c.section("🛑 Habit Friction")
            c.card {
                if (Features.on(context, "friction")) {
                    label("Beim Öffnen einer gebremsten App", 15f, bold = true, topDp = 0)
                    numberSetting("Wartezeit (Sekunden)", "friction_delay", 10)
                    numberSetting("Danach frei für (Minuten)", "friction_pass", 5)
                }
                if (Features.on(context, "doom")) {
                    label("Doomscroll-Limit", 15f, bold = true, topDp = 10)
                    numberSetting("Hinweis nach (Minuten am Stück)", "doom_min", 10)
                    numberSetting("Danach wieder nach (Minuten)", "doom_snooze", 5)
                }
                pill("Gebremste Apps (${Store.list(context, "blocked").size})") {
                    ListActivity.open(this@SettingsActivity, "blocked")
                }
                pill("Stattdessen anbieten (${Store.list(context, "alts").size})") {
                    ListActivity.open(this@SettingsActivity, "alts")
                }
                val resisted = Store.getInt(context, "friction_resisted", 0)
                if (resisted > 0) label("Schon $resisted× widerstanden 💪", 13f, topDp = 8)
            }
        }

        // ── Text actions ──────────────────────────────────────────────────
        if (Features.on(this, "textactions")) {
            c.section("✨ Text-Aktionen")
            c.card {
                label("Text markieren → Menü → „Erklären“, „Zusammenfassen“, „Übersetzen“ oder „Lernfrage“. Der fertige Prompt geht an deinen Assistenten und wird zusätzlich kopiert.", 13f, muted = true, topDp = 0)
                label("Assistent: ${Assistant.current(context).name}", 15f, topDp = 8)
                pill("Assistent ändern") {
                    val all = Assistant.all
                    AlertDialog.Builder(this@SettingsActivity)
                        .setTitle("Assistent")
                        .setItems(all.map { it.name }.toTypedArray()) { _, i ->
                            Store.putStr(this@SettingsActivity, "assistant", all[i].id)
                            render()
                        }
                        .show()
                }
            }
        }

        c.section("Datenschutz")
        c.label("Alles bleibt auf dem Handy. FlowKit hat keine Internet-Berechtigung, Passwortfelder werden ignoriert.", 13f, muted = true)
    }

    @Deprecated("Uses the platform result API (no AndroidX).")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        val uri: Uri = data?.data ?: return
        if (resultCode != RESULT_OK) return
        val main = Handler(Looper.getMainLooper())
        when (requestCode) {
            REQ_EXPORT -> Thread {
                val msg = try {
                    Backup.export(this, uri)
                    Store.putStr(this, "last_backup", LocalDate.now().toString())
                    "Backup gespeichert ✅"
                } catch (e: Exception) { "Backup fehlgeschlagen: ${e.message}" }
                main.post { toast(msg); render() }
            }.start()

            REQ_IMPORT -> AlertDialog.Builder(this)
                .setTitle("Wiederherstellen?")
                .setMessage("Alles Aktuelle wird durch den Inhalt der Sicherung ersetzt.")
                .setPositiveButton("Wiederherstellen") { _, _ ->
                    Thread {
                        val msg = try { Backup.restore(this, uri) } catch (e: Exception) { "Fehler: ${e.message}" }
                        main.post {
                            AlertDialog.Builder(this).setTitle("Wiederherstellen").setMessage(msg).setPositiveButton("OK", null).show()
                            render()
                        }
                    }.start()
                }
                .setNegativeButton("Abbrechen", null)
                .show()
        }
    }
}
