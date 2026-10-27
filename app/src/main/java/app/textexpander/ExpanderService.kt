package app.textexpander

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * The always-on part of FlowKit:
 *  - Text Expander + dynamic snippets (text changed in any app)
 *  - Quick Launcher abbreviations (same, but opens something instead)
 *  - Habit Friction (a blocked app comes to the front)
 */
class ExpanderService : AccessibilityService() {

    companion object {
        /** The running service, so tiles can trigger things like copy mode. */
        @Volatile var instance: ExpanderService? = null
        /** Package → time until which a blocked app may be used without friction. */
        val passUntil = HashMap<String, Long>()
        private var lastFrictionAt = 0L
        private var lastFrictionPkg = ""

        fun isEnabled(ctx: Context): Boolean {
            val me = ComponentName(ctx, ExpanderService::class.java).flattenToString()
            val enabled = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                ?: return false
            return enabled.split(':').any { it.equals(me, ignoreCase = true) }
        }
    }

    private var suggest: SuggestController? = null
    private var places: PlaceWatcher? = null
    var copier: Copier? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        try { start() } catch (t: Throwable) {
            Store.putStr(this, "last_crash", "Start: ${t.javaClass.simpleName}: ${t.message}".take(160))
        }
    }

    private fun start() {
        suggest = SuggestController(this)
        places = PlaceWatcher(this).also { it.start() }
        copier = Copier(this)
        instance = this
        val filter = IntentFilter(Intent.ACTION_SCREEN_OFF).apply { addAction(Intent.ACTION_USER_PRESENT) }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else registerReceiver(screenReceiver, filter)
    }

    // ── Doomscroll limit ──────────────────────────────────────────────────

    private val doomHandler = Handler(Looper.getMainLooper())
    private var fgPkg = ""
    private var fgSince = 0L
    private var nextDoomAt = 0L

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            if (i.action == Intent.ACTION_SCREEN_OFF) {
                fgPkg = ""
                doomHandler.removeCallbacks(doomCheck)
                suggest?.hide()
            } else {
                rootInActiveWindow?.packageName?.toString()?.let { trackForeground(it) }
            }
        }
    }

    private val doomCheck = object : Runnable {
        override fun run() {
            if (!Features.on(this@ExpanderService, "doom") || fgPkg.isEmpty()) return
            val now = System.currentTimeMillis()
            if (now >= nextDoomAt) {
                val minutes = ((now - fgSince) / 60_000).toInt()
                startActivity(
                    Intent(this@ExpanderService, FrictionActivity::class.java)
                        .putExtra("pkg", fgPkg).putExtra("doom", minutes)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                )
                nextDoomAt = now + Store.getInt(this@ExpanderService, "doom_snooze", 5) * 60_000L
            }
            doomHandler.postDelayed(this, 30_000)
        }
    }

    /** Remembers which app is in front and since when (only blocked apps are timed). */
    private fun trackForeground(pkg: String) {
        if (pkg == packageName || pkg == "com.android.systemui") return
        val ime = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD).orEmpty()
        if (ime.substringBefore('/') == pkg) return
        val timed = Features.on(this, "doom") && Store.list(this, "blocked").any { it["target"] == "app:$pkg" }
        val newPkg = if (timed) pkg else ""
        if (newPkg == fgPkg) return
        fgPkg = newPkg
        doomHandler.removeCallbacks(doomCheck)
        if (newPkg.isNotEmpty()) {
            fgSince = System.currentTimeMillis()
            nextDoomAt = fgSince + Store.getInt(this, "doom_min", 10) * 60_000L
            doomHandler.postDelayed(doomCheck, 30_000)
        }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        suggest?.shutdown()
        places?.stop()
        copier?.shutdown()
        copier = null
        instance = null
        places = null
        doomHandler.removeCallbacks(doomCheck)
        try { unregisterReceiver(screenReceiver) } catch (e: Exception) { }
        suggest = null
        return super.onUnbind(intent)
    }

    /** Errors seen inside event handling – shown in Settings instead of crashing the service. */
    @Volatile var lastCrash = ""

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // A crash in one handler must never kill the service – Android would switch it off
        // and you'd have to re-enable it in the settings.
        try {
            when (event.eventType) {
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> onText(event)
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> onWindow(event)
                AccessibilityEvent.TYPE_VIEW_FOCUSED -> onFocus(event)
                AccessibilityEvent.TYPE_VIEW_CLICKED -> { copier?.onClick(event); onFocus(event) }
            }
        } catch (t: Throwable) {
            lastCrash = "${t.javaClass.simpleName}: ${t.message}".take(160)
            Store.putStr(this, "last_crash", "${java.time.LocalDateTime.now().withNano(0)} · $lastCrash")
        }
    }

    override fun onInterrupt() {}

    private fun fieldText(node: AccessibilityNodeInfo): String =
        if (node.isShowingHintText) "" else node.text?.toString().orEmpty()

    private fun onFocus(event: AccessibilityEvent) {
        val node = event.source ?: return
        if (event.isPassword || node.isPassword) { suggest?.onSensitive(); return }
        if (!node.isEditable) return
        suggest?.onFocus(node, fieldText(node))
    }

    // ── Text Expander & Quick Launcher ────────────────────────────────────

    private fun onText(event: AccessibilityEvent) {
        if (event.isPassword) { suggest?.onSensitive(); return }
        val node = event.source ?: return
        if (node.isPassword) { suggest?.onSensitive(); return }
        if (!node.isEditable) return

        val text = fieldText(node)
        var cursor = node.textSelectionEnd
        if (cursor < 0 || cursor > text.length) cursor = text.length
        if (!expand(node, text, cursor)) suggest?.onText(node, text, cursor) else suggest?.hide()
    }

    /** Returns true if an abbreviation was replaced. */
    private fun expand(node: AccessibilityNodeInfo, text: String, cursor: Int): Boolean {
        if (text.isEmpty()) return false
        val before = text.substring(0, cursor)

        // Longest matching abbreviation wins, across snippets and launchers.
        var best: Rec? = null
        var isLauncher = false
        fun consider(list: List<Rec>, launcher: Boolean) {
            for (r in list) {
                val a = r["abbr"].orEmpty()
                if (a.length >= 2 && before.endsWith(a) && a.length > (best?.get("abbr")?.length ?: 0)) {
                    best = r; isLauncher = launcher
                }
            }
        }
        if (Features.on(this, "expander")) consider(Store.list(this, "snippets"), false)
        if (Features.on(this, "launcher")) consider(Store.list(this, "launchers"), true)
        val match = best ?: return false

        val abbr = match["abbr"].orEmpty()
        val start = cursor - abbr.length
        // Abbreviations that start with a letter only fire at the start of a word.
        if (start > 0 && abbr.first().isLetterOrDigit() && before[start - 1].isLetterOrDigit()) return false

        if (isLauncher) {
            setText(node, text.substring(0, start) + text.substring(cursor), start)
            Launch.open(this, match["target"].orEmpty())
        } else {
            val (expansion, cur) = Dyn.render(match["text"].orEmpty())
            val pos = start + if (cur >= 0) cur else expansion.length
            setText(node, text.substring(0, start) + expansion + text.substring(cursor), pos)
        }
        return true
    }

    fun setText(node: AccessibilityNodeInfo, newText: String, cursor: Int) {
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, newText)
        }
        if (node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
            val sel = Bundle().apply {
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, cursor)
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, cursor)
            }
            node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, sel)
        }
    }

    // ── Habit Friction ────────────────────────────────────────────────────

    private fun onWindow(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return
        suggest?.onWindow(pkg)
        trackForeground(pkg)
        if (!Features.on(this, "friction")) return
        val blocked = Store.list(this, "blocked").any { it["target"] == "app:$pkg" }
        if (!blocked) return

        val now = System.currentTimeMillis()
        if (now < (passUntil[pkg] ?: 0L)) return
        if (pkg == lastFrictionPkg && now - lastFrictionAt < 2000) return
        lastFrictionPkg = pkg
        lastFrictionAt = now

        startActivity(
            Intent(this, FrictionActivity::class.java)
                .putExtra("pkg", pkg)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        )
    }
}
