package app.textexpander

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
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

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> onText(event)
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> onWindow(event)
        }
    }

    override fun onInterrupt() {}

    // ── Text Expander & Quick Launcher ────────────────────────────────────

    private fun onText(event: AccessibilityEvent) {
        if (event.isPassword) return
        val node = event.source ?: return
        if (!node.isEditable || node.isPassword || node.isShowingHintText) return

        val text = node.text?.toString() ?: return
        var cursor = node.textSelectionEnd
        if (cursor < 0 || cursor > text.length) cursor = text.length
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
        consider(Store.list(this, "snippets"), false)
        consider(Store.list(this, "launchers"), true)
        val match = best ?: return

        val abbr = match["abbr"].orEmpty()
        val start = cursor - abbr.length
        // Abbreviations that start with a letter only fire at the start of a word.
        if (start > 0 && abbr.first().isLetterOrDigit() && before[start - 1].isLetterOrDigit()) return

        if (isLauncher) {
            setText(node, text.substring(0, start) + text.substring(cursor), start)
            Launch.open(this, match["target"].orEmpty())
        } else {
            val (expansion, cur) = Dyn.render(match["text"].orEmpty())
            val pos = start + if (cur >= 0) cur else expansion.length
            setText(node, text.substring(0, start) + expansion + text.substring(cursor), pos)
        }
    }

    private fun setText(node: AccessibilityNodeInfo, newText: String, cursor: Int) {
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
        if (!Store.getBool(this, "friction_on", true)) return
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
