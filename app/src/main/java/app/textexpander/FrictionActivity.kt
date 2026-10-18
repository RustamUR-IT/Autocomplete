package app.textexpander

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.TextView

/** Shown in front of a blocked app: wait a few seconds, or pick something better. */
class FrictionActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private var pkg = ""
    private var left = 0
    private var go: TextView? = null

    private val tick = object : Runnable {
        override fun run() {
            val b = go ?: return
            b.text = if (left > 0) "Trotzdem öffnen ($left)" else "Trotzdem öffnen"
            b.alpha = if (left > 0) 0.4f else 1f
            if (left > 0) {
                left--
                handler.postDelayed(this, 1000)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        build(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        build(intent)
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        super.onDestroy()
    }

    @Deprecated("Back always leads home instead of into the blocked app.")
    override fun onBackPressed() = goHome()

    private fun build(i: Intent) {
        pkg = i.getStringExtra("pkg") ?: run { finish(); return }
        handler.removeCallbacks(tick)
        val appName = try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
        } catch (e: Exception) { pkg }

        val c = page("Kurz durchatmen")
        c.label("Du wolltest $appName öffnen.", 22f, bold = true, topDp = 28)
        c.label("Ist das gerade wirklich, was du willst – oder nur Gewohnheit?", 15f, muted = true)

        c.section("Lieber stattdessen")
        for (r in Store.list(this, "alts")) {
            c.pill(r["label"].orEmpty()) {
                resisted()
                Launch.open(this, r["target"].orEmpty(), "alts")
                finish()
            }
        }

        c.section(" ")
        c.pill("Nein, zurück", primary = true) { resisted(); goHome() }
        go = c.pill("") {
            if (left <= 0) {
                val minutes = Store.getInt(this, "friction_pass", 5)
                ExpanderService.passUntil[pkg] = System.currentTimeMillis() + minutes * 60_000L
                packageManager.getLaunchIntentForPackage(pkg)?.let {
                    startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                finish()
            }
        }
        val count = Store.getInt(this, "friction_resisted", 0)
        if (count > 0) c.label("Schon $count× widerstanden 💪", 13f, muted = true, topDp = 16)

        left = Store.getInt(this, "friction_delay", 10)
        handler.post(tick)
    }

    private fun resisted() =
        Store.putInt(this, "friction_resisted", Store.getInt(this, "friction_resisted", 0) + 1)

    private fun goHome() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }
}
