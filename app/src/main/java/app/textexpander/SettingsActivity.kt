package app.textexpander

import android.app.Activity
import android.app.AlertDialog

class SettingsActivity : Activity() {

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        val c = page("Einstellungen")

        c.section("🛑 Habit Friction")
        c.card {
            label("Gebremste Apps zeigen erst eine Wartezeit und schlagen dir Besseres vor.", 13f, muted = true, topDp = 0)
            toggle("Habit Friction aktiv", Store.getBool(context, "friction_on", true)) {
                Store.putBool(context, "friction_on", it)
            }
            numberSetting("Wartezeit (Sekunden)", "friction_delay", 10)
            numberSetting("Danach frei für (Minuten)", "friction_pass", 5)
            pill("Gebremste Apps (${Store.list(context, "blocked").size})") {
                ListActivity.open(this@SettingsActivity, "blocked")
            }
            pill("Stattdessen anbieten (${Store.list(context, "alts").size})") {
                ListActivity.open(this@SettingsActivity, "alts")
            }
            val resisted = Store.getInt(context, "friction_resisted", 0)
            if (resisted > 0) label("Schon $resisted× widerstanden 💪", 13f, topDp = 8)
            label(
                "Startbildschirm-Tipp: Lern-, Lese- und Gym-Apps in die untere Leiste legen, Social Media in einen Ordner auf der letzten Seite.",
                12f, muted = true, topDp = 8,
            )
        }

        c.section("✨ Text-Aktionen")
        c.card {
            label("Text markieren → Menü → „Erklären“, „Zusammenfassen“, „Übersetzen“ oder „Lernfrage“. Der fertige Prompt wird an deinen Assistenten geschickt und zusätzlich kopiert.", 13f, muted = true, topDp = 0)
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

        c.section("Datenschutz")
        c.label("Alles bleibt auf dem Handy. FlowKit hat keine Internet-Berechtigung, Passwortfelder werden ignoriert.", 13f, muted = true)
    }
}
