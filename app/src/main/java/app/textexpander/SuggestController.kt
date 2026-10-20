package app.textexpander

import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Shows suggestion chips just above the text field you're typing in, works out
 * which chat/contact you're in, and notices when a message was sent (field cleared).
 */
class SuggestController(private val svc: ExpanderService) {

    companion object {
        /** True while the test field in FlowKit's own settings screen is open. */
        @Volatile var testMode = false
        /** Last diagnostic line, shown on the settings screen. */
        @Volatile var lastInfo = "Noch kein Textfeld gesehen."
        @Volatile var lastError = ""
        @Volatile var lastEventAt = 0L
        @Volatile var running = false
    }

    init { running = true }

    private val engine = SuggestEngine.get(svc)
    private val wm = svc.sys<WindowManager>()
    private val handler = Handler(Looper.getMainLooper())
    private val hideTask = Runnable { hide() }

    private var root: HorizontalScrollView? = null
    private var bar: LinearLayout? = null
    private var attached = false

    private var node: AccessibilityNodeInfo? = null
    private var pkg = ""
    private var ctxKey = ""
    private var ctxTitle = ""
    private var fieldKey = ""
    private var lastText = ""
    /** The newest message the other person sent, if it's the last one in the chat. */
    private var incoming: String? = null
    private var lastPairedIncoming: String? = null

    private fun showOn() = Features.on(svc, "suggest") && Store.getBool(svc, "suggest_on", true)

    private fun learnAllowed() = Features.on(svc, "suggest") && Store.getBool(svc, "learn_on", true) &&
        pkg.isNotEmpty() && (pkg != svc.packageName || testMode) &&
        Store.list(svc, "nolearn").none { it["target"] == "app:$pkg" }

    private fun isIgnoredPackage(p: String): Boolean {
        if (p == svc.packageName) return !testMode
        if (p == "com.android.systemui") return true
        val ime = Settings.Secure.getString(svc.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD).orEmpty()
        return ime.substringBefore('/') == p
    }

    // ── Events from the accessibility service ─────────────────────────────

    fun onWindow(p: String) {
        if (isIgnoredPackage(p)) return
        // Only a different app ends the current draft – apps fire window events all the time.
        if (p != pkg) { flush(); hide() }
        pkg = p
        handler.removeCallbacks(windowSettled)
        handler.postDelayed(windowSettled, 450)
    }

    /** After a screen change: work out the chat, and offer quick replies if the input field is empty. */
    private val windowSettled = Runnable {
        refreshContext()
        val focused = svc.rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focused != null && focused.isEditable && !focused.isPassword) {
            val t = if (focused.isShowingHintText) "" else focused.text?.toString().orEmpty()
            readIncoming(focused)
            if (t.isBlank() && showOn()) { node = focused; showReplies(focused) }
        }
    }

    /** Field focused or tapped. */
    fun onFocus(n: AccessibilityNodeInfo, text: String) {
        if (!n.isEditable || n.isPassword) { hide(); return }
        val p = n.packageName?.toString() ?: return
        if (isIgnoredPackage(p)) return
        node = n
        pkg = p
        refreshContext()
        readIncoming(n)
        if (text.isBlank() && showOn()) showReplies(n) else if (text.isBlank()) hide()
    }

    private fun showReplies(n: AccessibilityNodeInfo) {
        val list = engine.quickReplies(ctxKey, ctxTitle, incoming)
        note(list.size)
        show(list, n)
    }

    private fun note(count: Int) {
        lastEventAt = System.currentTimeMillis()
        lastInfo = "App: $pkg\nChat erkannt: ${ctxTitle.ifBlank { "– (kein Name gefunden)" }}\n" +
            "Letzte erhaltene Nachricht: ${incoming?.take(40) ?: "–"}\nVorschläge gerade: $count"
    }

    fun onText(n: AccessibilityNodeInfo, text: String, cursor: Int) {
        node = n
        val p = n.packageName?.toString() ?: return
        if (isIgnoredPackage(p)) return
        if (p != pkg) { flush(); pkg = p; refreshContext() }

        val key = "$p|${n.viewIdResourceName}|${n.windowId}"
        if (key != fieldKey) {
            flush()
            fieldKey = key
        } else if (text.isBlank() && lastText.trim().isNotEmpty()) {
            sent(lastText)   // field was cleared → message was sent
            lastText = ""
        }
        if (text.isNotBlank() && lastText.isBlank()) readIncoming(n)   // starting a new message
        if (text.isNotBlank()) lastText = text

        val before = text.substring(0, cursor.coerceIn(0, text.length))
        val list = if (!showOn()) emptyList()
                   else if (before.isBlank()) engine.quickReplies(ctxKey, ctxTitle, incoming) else engine.suggest(before, ctxKey, ctxTitle, incoming)
        note(list.size)
        show(list, n)
    }

    fun onSensitive() = hide()

    fun shutdown() {
        flush()
        hide()
        engine.flushToDisk()
        running = false
    }

    private fun learn(text: String, sent: Boolean) {
        if (learnAllowed()) engine.learn(text, ctxKey.ifEmpty { pkg }, ctxTitle, sent)
    }

    private fun pairsOn() = Store.getBool(svc, "pairs_on", true)

    /** A message was sent: learn the words/phrase, and the pair "their message → your answer". */
    private fun sent(text: String) {
        if (text.trim().length >= 2) learn(text, sent = true)
        val inc = incoming
        if (inc != null && inc != lastPairedIncoming && pairsOn() && learnAllowed()) {
            engine.learnPair(ctxKey.ifEmpty { pkg }, ctxTitle, inc, text)
            lastPairedIncoming = inc   // only your first answer to each of their messages
        }
        incoming = null   // you wrote last now – wait for their next message
    }

    // ── Their last message ────────────────────────────────────────────────

    private val messageTextIds = setOf(
        "com.whatsapp:id/message_text",
        "com.whatsapp.w4b:id/message_text",
    )

    /**
     * Finds the lowest chat bubble above the input field. If it's on the left (their side),
     * that's the message you're answering; if it's on the right (you wrote last), there is none.
     */
    private fun readIncoming(field: AccessibilityNodeInfo) {
        incoming = null
        if (!pairsOn() || pkg == svc.packageName) return
        val root = svc.rootInActiveWindow ?: return
        val w = svc.resources.displayMetrics.widthPixels
        val h = svc.resources.displayMetrics.heightPixels
        val fieldBox = Rect().also { field.getBoundsInScreen(it) }
        val hasIds = root.findAccessibilityNodeInfosByViewId(messageTextIds.first()).isNotEmpty() ||
            root.findAccessibilityNodeInfosByViewId(messageTextIds.last()).isNotEmpty()

        var lowestBottom = -1
        var lowestText: String? = null
        var lowestIsTheirs = false
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        val b = Rect()
        while (queue.isNotEmpty() && visited < 1500) {
            val n = queue.removeFirst()
            visited++
            for (i in 0 until n.childCount) n.getChild(i)?.let { queue.add(it) }
            val t = n.text?.toString()?.trim() ?: continue
            if (t.isEmpty() || t.length > 500 || n.isEditable || !n.isVisibleToUser) continue
            if (hasIds && n.viewIdResourceName !in messageTextIds) continue
            if (!hasIds && statusText.matches(t)) continue
            n.getBoundsInScreen(b)
            if (b.bottom > fieldBox.top || b.top < h * 0.1) continue
            if (b.bottom > lowestBottom) {
                lowestBottom = b.bottom
                lowestText = t
                lowestIsTheirs = b.left < w * 0.22 && b.centerX() < w * 0.55
            }
        }
        if (lowestIsTheirs) incoming = lowestText
    }

    private fun flush() {
        if (lastText.trim().length >= 2) learn(lastText, sent = false)
        lastText = ""
        fieldKey = ""
    }

    // ── Who am I talking to? ──────────────────────────────────────────────

    private val knownTitleIds = listOf(
        "com.whatsapp:id/conversation_contact_name",
        "com.whatsapp.w4b:id/conversation_contact_name",
        "org.thoughtcrime.securesms:id/conversation_title_view_name",
        "com.instagram.android:id/thread_title",
        "com.google.android.apps.messaging:id/conversation_title",
    )

    private val statusText = Regex(
        "^(online|offline|tippt.*|schreibt.*|typing.*|zuletzt.*|last seen.*|zul\\..*|\\d{1,2}:\\d{2}.*|heute|today|gestern|yesterday)$",
        RegexOption.IGNORE_CASE,
    )

    private fun refreshContext() {
        val r = svc.rootInActiveWindow ?: return
        val p = r.packageName?.toString() ?: return
        if (isIgnoredPackage(p)) return
        val title = if (p == svc.packageName) "FlowKit-Test" else findTitle(r)
        val newKey = if (title.isBlank()) p else "$p|$title"
        if (newKey != ctxKey && lastText.isNotBlank()) flush()   // draft belongs to the previous chat
        if (newKey != ctxKey) { incoming = null; lastPairedIncoming = null }
        pkg = p
        ctxTitle = title
        ctxKey = newKey
    }

    private fun findTitle(r: AccessibilityNodeInfo): String {
        for (id in knownTitleIds) {
            val t = r.findAccessibilityNodeInfosByViewId(id)?.firstOrNull()?.text?.toString()?.trim()
            if (!t.isNullOrEmpty()) return t
        }
        // Heuristic: the top-most short text in the app bar area is usually the chat name.
        val h = svc.resources.displayMetrics.heightPixels
        val w = svc.resources.displayMetrics.widthPixels
        var best: String? = null
        var bestTop = Int.MAX_VALUE
        var bestLeft = Int.MAX_VALUE
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(r)
        var visited = 0
        val b = Rect()
        while (queue.isNotEmpty() && visited < 400) {
            val n = queue.removeFirst()
            visited++
            for (i in 0 until n.childCount) n.getChild(i)?.let { queue.add(it) }
            val t = n.text?.toString()?.trim() ?: continue
            if (n.isEditable || !n.isVisibleToUser || t.length !in 2..40 || statusText.matches(t)) continue
            n.getBoundsInScreen(b)
            if (b.top <= 0 || b.top > h * 0.16 || b.left > w * 0.8) continue
            if (b.top < bestTop || (b.top == bestTop && b.left < bestLeft)) {
                best = t; bestTop = b.top; bestLeft = b.left
            }
        }
        return best.orEmpty()
    }

    // ── Overlay ───────────────────────────────────────────────────────────

    private fun ensureView() {
        if (root != null) return
        val b = LinearLayout(svc).apply { orientation = LinearLayout.HORIZONTAL }
        root = HorizontalScrollView(svc).apply {
            isHorizontalScrollBarEnabled = false
            addView(b)
        }
        bar = b
    }

    private fun chip(s: Suggestion, phrase: Boolean): View = TextView(svc).apply {
        text = s.label
        textSize = 14f
        setTextColor(0xFFFFFFFF.toInt())
        isSingleLine = true
        ellipsize = TextUtils.TruncateAt.END
        maxWidth = svc.dp(230)
        setPadding(svc.dp(14), svc.dp(8), svc.dp(14), svc.dp(8))
        background = svc.rounded(if (phrase) 0xF0D71921.toInt() else 0xF0222222.toInt(), 18, 0x55FFFFFF)
        elevation = svc.dp(4).toFloat()
        setOnClickListener { apply(s) }
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, svc.dp(2), svc.dp(6), svc.dp(2)) }
    }

    private var lp: WindowManager.LayoutParams? = null
    private var anchored: AccessibilityNodeInfo? = null
    private val lastField = Rect()

    private fun show(list: List<Suggestion>, n: AccessibilityNodeInfo) {
        if (list.isEmpty()) { hide(); return }
        ensureView()
        val r = root!!
        val b = bar!!
        b.removeAllViews()
        list.forEach { s -> b.addView(chip(s, s.strong)) }
        anchored = n
        place(n, force = true)
        handler.removeCallbacks(hideTask)
        handler.postDelayed(hideTask, 20_000)
        handler.removeCallbacks(follow)
        handler.postDelayed(follow, 250)
    }

    /** Puts the chips directly above the text field (below it if there's no room above). */
    private fun place(n: AccessibilityNodeInfo, force: Boolean) {
        val r = root ?: return
        val field = Rect()
        n.getBoundsInScreen(field)
        if (field.isEmpty) { hide(); return }
        if (!force && field == lastField && attached) return
        lastField.set(field)

        val screenW = svc.resources.displayMetrics.widthPixels
        r.measure(
            View.MeasureSpec.makeMeasureSpec(screenW - svc.dp(16), View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        val h = r.measuredHeight
        val above = field.top - h - svc.dp(6)
        val params = lp ?: WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.START }.also { lp = it }
        params.x = maxOf(svc.dp(8), field.left)
        params.y = if (above > svc.dp(56)) above else field.bottom + svc.dp(6)
        try {
            if (attached) wm.updateViewLayout(r, params) else { wm.addView(r, params); attached = true }
            lastError = ""
        } catch (e: Exception) {
            attached = false
            lastError = "Overlay-Fehler: ${e.javaClass.simpleName}: ${e.message}"
        }
    }

    /** While visible: follow the field when the keyboard opens/closes or the layout shifts; vanish with it. */
    private val follow = object : Runnable {
        override fun run() {
            if (!attached) return
            val n = anchored ?: run { hide(); return }
            if (!n.refresh() || !n.isVisibleToUser || !n.isFocused) { hide(); return }
            place(n, force = false)
            handler.postDelayed(this, 250)
        }
    }

    fun hide() {
        handler.removeCallbacks(hideTask)
        handler.removeCallbacks(follow)
        anchored = null
        if (attached) {
            try { wm.removeView(root) } catch (e: Exception) { }
            attached = false
        }
    }

    private fun apply(s: Suggestion) {
        val n = node ?: return
        n.refresh()
        val text = if (n.isShowingHintText) "" else n.text?.toString().orEmpty()
        var cur = n.textSelectionEnd
        if (cur < 0 || cur > text.length) cur = text.length
        val before = text.substring(0, cur)
        val newBefore = before.dropLast(s.replaceLen.coerceAtMost(before.length)) + s.insert
        svc.setText(n, newBefore + text.substring(cur), newBefore.length)
    }
}
