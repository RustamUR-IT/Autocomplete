package app.textexpander

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast

/**
 * Copy a whole text block:
 *  - double-tap on a message / list item → that block (works where the app reports taps, e.g. WhatsApp bubbles)
 *  - long-press → all text visible on screen, top to bottom
 *  - copy mode (tile "Text kopieren"): tap = text under your finger, hold = everything
 */
class Copier(private val svc: ExpanderService) {

    private val handler = Handler(Looper.getMainLooper())
    private val wm = svc.sys<WindowManager>()
    private var lastKey = ""
    private var lastAt = 0L
    private var overlay: FrameLayout? = null

    private val timeOnly = Regex("^(\\d{1,2}:\\d{2}( ?(am|pm))?|bearbeitet|edited|✓+|gesehen|seen)$", RegexOption.IGNORE_CASE)
    private val messageIds = setOf("com.whatsapp:id/message_text", "com.whatsapp.w4b:id/message_text")

    // ── Double tap ────────────────────────────────────────────────────────

    fun onClick(event: AccessibilityEvent) {
        if (!Features.on(svc, "copy")) return
        val n = event.source ?: return
        if (n.isEditable || n.isPassword) return   // double tap in a text field = normal word selection
        val b = Rect().also { n.getBoundsInScreen(it) }
        val key = "${event.packageName}|${n.windowId}|${n.viewIdResourceName}|${b.flattenToString()}"
        val now = System.currentTimeMillis()
        if (key == lastKey && now - lastAt < 450) {
            lastKey = ""
            copy(textOf(n))
        } else {
            lastKey = key
            lastAt = now
        }
    }

    // ── Long press → everything on screen ─────────────────────────────────

    fun onLongClick(event: AccessibilityEvent) {
        if (!Features.on(svc, "copy")) return
        val n = event.source ?: return
        if (n.isEditable || n.isPassword) return   // long press in a text field = normal selection
        handler.postDelayed({ copy(allText(), everything = true) }, 150)
    }

    /** All visible text of the current screen, in reading order. Chat apps: only the messages. */
    private fun allText(): String {
        val root = svc.rootInActiveWindow ?: return ""
        val h = svc.resources.displayMetrics.heightPixels
        val found = ArrayList<Triple<Int, Int, String>>()   // top, left, text
        val hasMsgIds = messageIds.any { root.findAccessibilityNodeInfosByViewId(it).isNotEmpty() }
        val b = Rect()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < 3000) {
            val n = queue.removeFirst()
            visited++
            for (i in 0 until n.childCount) n.getChild(i)?.let { queue.add(it) }
            if (!n.isVisibleToUser || n.isPassword || n.isEditable) continue
            val t = n.text?.toString()?.trim().orEmpty()
            if (t.isEmpty() || timeOnly.matches(t)) continue
            if (hasMsgIds && n.viewIdResourceName !in messageIds) continue
            n.getBoundsInScreen(b)
            if (b.bottom <= 0 || b.top >= h) continue
            found += Triple(b.top, b.left, t)
        }
        return found.sortedWith(compareBy({ it.first }, { it.second }))
            .map { it.third }
            .fold(ArrayList<String>()) { acc, t -> if (acc.lastOrNull() != t) acc += t; acc }
            .joinToString("\n")
    }

    // ── Copy mode ─────────────────────────────────────────────────────────

    fun startCopyMode() {
        if (overlay != null) return
        if (Build.VERSION.SDK_INT >= 31) svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
        handler.postDelayed({ showOverlay() }, 350)
    }

    private fun showOverlay() {
        val hint = TextView(svc).apply {
            text = "Tippen = diesen Text kopieren · Halten = alles"
            setTextColor(Color.WHITE)
            textSize = 15f
            setPadding(svc.dp(18), svc.dp(10), svc.dp(18), svc.dp(10))
            background = svc.rounded(0xE6D71921.toInt(), 22)
        }
        val root = FrameLayout(svc).apply {
            setBackgroundColor(0x33000000)
            addView(hint, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = svc.dp(70) })
            setOnTouchListener { _, e ->
                if (e.action == MotionEvent.ACTION_UP) {
                    val x = e.rawX.toInt()
                    val y = e.rawY.toInt()
                    val held = e.eventTime - e.downTime > 500
                    removeOverlay()
                    // let the overlay disappear, then look at what's underneath
                    handler.postDelayed({
                        if (held) copy(allText(), everything = true) else copy(textAt(x, y))
                    }, 120)
                }
                true
            }
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        )
        try {
            wm.addView(root, lp)
            overlay = root
            handler.postDelayed({ removeOverlay() }, 10_000)   // give up after 10 s
        } catch (e: Exception) { }
    }

    private fun removeOverlay() {
        overlay?.let { try { wm.removeView(it) } catch (e: Exception) { } }
        overlay = null
    }

    /** The smallest element under (x, y) that has text – or its surrounding block. */
    private fun textAt(x: Int, y: Int): String {
        val root = svc.rootInActiveWindow ?: return ""
        var best: AccessibilityNodeInfo? = null
        var bestArea = Long.MAX_VALUE
        val b = Rect()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < 2500) {
            val n = queue.removeFirst()
            visited++
            n.getBoundsInScreen(b)
            if (!b.contains(x, y)) continue
            for (i in 0 until n.childCount) n.getChild(i)?.let { queue.add(it) }
            if (!n.isVisibleToUser || n.isPassword) continue
            val area = b.width().toLong() * b.height()
            if (textOf(n).isNotBlank() && area < bestArea) { best = n; bestArea = area }
        }
        return best?.let { textOf(it) }.orEmpty()
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    /** Text of a node; for containers (chat bubble, card) the text of its children, minus times/ticks. */
    private fun textOf(n: AccessibilityNodeInfo): String {
        for (id in messageIds) n.findAccessibilityNodeInfosByViewId(id)?.firstOrNull()?.text?.let {
            if (it.isNotBlank()) return it.toString()
        }
        val parts = ArrayList<String>()
        collect(n, 0, parts)
        return parts.filter { !timeOnly.matches(it) }.distinct().joinToString("\n").trim()
    }

    private fun collect(n: AccessibilityNodeInfo, depth: Int, out: MutableList<String>) {
        val own = n.text?.toString()?.trim().orEmpty()
        if (own.isNotEmpty()) { out += own; return }
        if (n.childCount == 0 || depth >= 5) {
            n.contentDescription?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { out += it }
            return
        }
        for (i in 0 until n.childCount) n.getChild(i)?.let { collect(it, depth + 1, out) }
    }

    private fun copy(text: String, everything: Boolean = false) {
        if (text.isBlank()) {
            Toast.makeText(svc, "Hier ist kein Text zum Kopieren", Toast.LENGTH_SHORT).show()
            return
        }
        svc.sys<ClipboardManager>().setPrimaryClip(ClipData.newPlainText("FlowKit", text))
        val msg = if (everything) "📋 Alles kopiert (${text.lines().size} Zeilen)"
                  else "📋 Kopiert: " + text.replace('\n', ' ').let { if (it.length > 40) it.take(40) + "…" else it }
        Toast.makeText(svc, msg, Toast.LENGTH_SHORT).show()
    }

    fun shutdown() = removeOverlay()
}
