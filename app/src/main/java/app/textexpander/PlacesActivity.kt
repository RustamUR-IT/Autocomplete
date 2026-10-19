package app.textexpander

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.LinearLayout

/** Location modes: save a Wi-Fi as "Gym", "FH", … and choose what happens there. */
class PlacesActivity : Activity() {

    override fun onResume() {
        super.onResume()
        render()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        render()
    }

    private fun render() {
        val c = page("Orts-Modi", "FlowKit erkennt Orte an ihrem WLAN – ohne GPS-Tracking. Im Gym-WLAN kommt Workout Mode, in der FH „Nicht stören“.")

        // ── Permissions ───────────────────────────────────────────────────
        val loc = PlaceWatcher.hasLocation(this)
        val bg = PlaceWatcher.hasBackgroundLocation(this)
        val dnd = sys<NotificationManager>().isNotificationPolicyAccessGranted
        val acc = ExpanderService.isEnabled(this)
        if (loc && bg && !Store.getBool(this, "places_restarted", false)) {
            PlaceWatcher.active?.restart()
            Store.putBool(this, "places_restarted", true)
        }
        c.card {
            if (loc && bg && dnd && acc) {
                label("✅  Bereit", 16f, bold = true, topDp = 0)
            } else {
                label("Einrichtung", 16f, bold = true, topDp = 0)
                label("Android verrät den WLAN-Namen nur Apps mit Standort-Berechtigung. FlowKit liest nur den WLAN-Namen, keinen Standort.", 12f, muted = true)
            }
            if (!acc) pill("Bedienungshilfe einschalten") { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            if (!loc) pill("1 · Standort erlauben", primary = true) {
                requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 7)
            }
            if (loc && !bg) {
                pill("2 · Standort „Immer erlauben“", primary = true) {
                    AlertDialog.Builder(this@PlacesActivity)
                        .setTitle("Immer erlauben")
                        .setMessage("Damit FlowKit das WLAN auch erkennt, wenn die App zu ist: Im nächsten Bildschirm „Standort“ → „Immer erlauben“ wählen.")
                        .setPositiveButton("Weiter") { _, _ ->
                            requestPermissions(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION), 8)
                        }
                        .setNeutralButton("App-Einstellungen") { _, _ ->
                            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
                        }
                        .show()
                }
            }
            if (!dnd) pill("3 · „Nicht stören“-Zugriff erlauben") {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            }
            label("Status: ${PlaceWatcher.status}", 12f, muted = true, mono = true, topDp = 8)
        }

        // ── Current Wi-Fi ─────────────────────────────────────────────────
        c.section("Aktuelles WLAN")
        val ssid = if (loc) PlaceWatcher.readSsidNow(this) else null
        c.card {
            if (ssid == null) {
                label(if (loc) "Kein WLAN erkannt. WLAN und Standort eingeschaltet?" else "Erst Standort erlauben.", 14f, topDp = 0)
            } else {
                label("📶  $ssid", 18f, bold = true, topDp = 0)
                pill("Dieses WLAN als Ort speichern", primary = true) { addPlace(ssid) }
            }
        }

        // ── Saved places ──────────────────────────────────────────────────
        c.section("Gespeicherte Orte")
        val places = Store.list(this, "places")
        if (places.isEmpty()) c.label("Noch keine. Geh ins Gym- oder FH-WLAN und tippe oben auf „Speichern“.", 13f, muted = true)
        for (p in places) {
            c.card {
                label(p["label"].orEmpty(), 16f, bold = true, topDp = 0)
                label("WLAN „${p["ssid"]}“\n→ ${PlaceWatcher.actions[p["target"]] ?: p["target"]}", 13f, muted = true)
            }.apply {
                isClickable = true
                setOnClickListener {
                    AlertDialog.Builder(this@PlacesActivity)
                        .setTitle(p["label"])
                        .setPositiveButton("Löschen") { _, _ ->
                            Store.save(this@PlacesActivity, "places", Store.list(this@PlacesActivity, "places").filter { it !== p })
                            render()
                        }
                        .setNegativeButton("Abbrechen", null)
                        .show()
                }
            }
        }

        c.section("Gut zu wissen")
        c.label(
            "• „Nicht stören“ geht automatisch an und beim Verlassen des WLANs wieder aus.\n" +
                "• Workout/Study Mode kommen als Benachrichtigung – ein Tipp startet sie (Android erlaubt Apps nicht, das ungefragt im Hintergrund zu öffnen).\n" +
                "• Höchstens einmal alle 2 Stunden pro Ort, damit WLAN-Wackler nicht nerven.",
            13f, muted = true,
        )
    }

    private fun addPlace(ssid: String) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(4), dp(20), 0)
        }
        box.label("Name des Orts", 12f, muted = true, topDp = 8)
        val name = input(hint = "Gym, FH, Zuhause …")
        box.addView(name)
        box.label("Was soll passieren?", 12f, muted = true, topDp = 14)
        val keys = PlaceWatcher.actions.keys.toList()
        var chosen = keys[0]
        val options = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        box.addView(options)
        fun drawOptions() {
            options.removeAllViews()
            for (k in keys) options.chip(PlaceWatcher.actions[k]!!, k == chosen) { chosen = k; drawOptions() }
        }
        drawOptions()

        AlertDialog.Builder(this)
            .setTitle("📶 $ssid")
            .setView(box)
            .setPositiveButton("Speichern") { _, _ ->
                val label = name.text.toString().trim().ifBlank { ssid }
                Store.add(this, "places", mapOf("label" to label, "ssid" to ssid, "target" to chosen))
                render()
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }
}
