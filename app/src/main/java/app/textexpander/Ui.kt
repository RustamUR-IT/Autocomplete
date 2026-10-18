package app.textexpander

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.*

/** Nothing-inspired look: black/white, monospace headings, one red accent. */
object Look {
    const val ACCENT = 0xFFD71921.toInt()
    fun dark(c: Context) =
        (c.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    fun bg(c: Context) = if (dark(c)) 0xFF000000.toInt() else 0xFFF2F2F2.toInt()
    fun card(c: Context) = if (dark(c)) 0xFF181818.toInt() else 0xFFFFFFFF.toInt()
    fun fg(c: Context) = if (dark(c)) 0xFFF2F2F2.toInt() else 0xFF111111.toInt()
    fun muted(c: Context) = if (dark(c)) 0xFF9A9A9A.toInt() else 0xFF666666.toInt()
    fun line(c: Context) = if (dark(c)) 0xFF333333.toInt() else 0xFFDDDDDD.toInt()
}

fun Context.dp(v: Int): Int =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

fun Context.toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

fun Context.copyText(text: String, msg: String = "Kopiert") {
    sys<ClipboardManager>().setPrimaryClip(ClipData.newPlainText("FlowKit", text))
    if (Build.VERSION.SDK_INT < 33) toast(msg)
}

fun Context.rounded(color: Int, radiusDp: Int = 18, stroke: Int? = null) = GradientDrawable().apply {
    setColor(color)
    cornerRadius = dp(radiusDp).toFloat()
    if (stroke != null) setStroke(dp(1), stroke)
}

fun EditText.onText(f: (String) -> Unit) = addTextChangedListener(object : TextWatcher {
    override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
    override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
    override fun afterTextChanged(s: Editable?) = f(s?.toString().orEmpty())
})

/** Builds a scrolling page with a heading and returns the column to fill. */
fun Activity.page(title: String, subtitle: String? = null): LinearLayout {
    val root = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(16), dp(16), dp(40))
    }
    val scroll = ScrollView(this).apply {
        setBackgroundColor(Look.bg(context))
        isFillViewport = true
        addView(root)
        setOnApplyWindowInsetsListener { v, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val b = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
                v.setPadding(b.left, b.top, b.right, b.bottom)
            }
            insets
        }
    }
    setContentView(scroll)
    if (Build.VERSION.SDK_INT >= 30 && !Look.dark(this)) {
        val light = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
        window.insetsController?.setSystemBarsAppearance(light, light)
    }
    root.label(title.uppercase(), 24f, mono = true, topDp = 12).letterSpacing = 0.12f
    if (subtitle != null) root.label(subtitle, 13f, muted = true)
    return root
}

fun LinearLayout.label(
    text: CharSequence, size: Float = 14f, muted: Boolean = false, mono: Boolean = false,
    bold: Boolean = false, topDp: Int = 4,
): TextView {
    val tv = TextView(context).apply {
        this.text = text
        textSize = size
        setTextColor(if (muted) Look.muted(context) else Look.fg(context))
        typeface = when {
            mono && bold -> Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            mono -> Typeface.MONOSPACE
            bold -> Typeface.DEFAULT_BOLD
            else -> Typeface.DEFAULT
        }
        setPadding(0, context.dp(topDp), 0, context.dp(2))
    }
    addView(tv, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
    return tv
}

fun LinearLayout.section(text: String) =
    label(text.uppercase(), 12f, muted = true, mono = true, topDp = 20).apply { letterSpacing = 0.1f }

/** Rounded button. Fills the width in a column, shares the width in a row. */
fun LinearLayout.pill(text: String, primary: Boolean = false, onClick: () -> Unit): TextView {
    val c = context
    val tv = TextView(c).apply {
        this.text = text
        textSize = 15f
        gravity = Gravity.CENTER
        setPadding(c.dp(14), c.dp(12), c.dp(14), c.dp(12))
        setTextColor(if (primary) 0xFFFFFFFF.toInt() else Look.fg(c))
        background = c.rounded(if (primary) Look.ACCENT else Look.card(c), 24, if (primary) null else Look.line(c))
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }
    }
    val lp = if (orientation == LinearLayout.HORIZONTAL) LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
             else LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
    lp.setMargins(c.dp(3), c.dp(4), c.dp(3), c.dp(4))
    addView(tv, lp)
    return tv
}

/** Small wrap-content chip, for filter rows. */
fun LinearLayout.chip(text: String, selected: Boolean = false, onClick: () -> Unit): TextView {
    val c = context
    val tv = TextView(c).apply {
        this.text = text
        textSize = 13f
        setPadding(c.dp(14), c.dp(7), c.dp(14), c.dp(7))
        setTextColor(if (selected) 0xFFFFFFFF.toInt() else Look.fg(c))
        background = c.rounded(if (selected) Look.ACCENT else Look.card(c), 20, if (selected) null else Look.line(c))
        setOnClickListener { onClick() }
    }
    addView(tv, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { setMargins(0, c.dp(6), c.dp(6), c.dp(6)) })
    return tv
}

fun LinearLayout.card(build: LinearLayout.() -> Unit): LinearLayout {
    val c = context
    val box = LinearLayout(c).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(c.dp(16), c.dp(12), c.dp(16), c.dp(14))
        background = c.rounded(Look.card(c))
    }
    box.build()
    addView(box, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { setMargins(0, c.dp(5), 0, c.dp(5)) })
    return box
}

fun LinearLayout.row(build: LinearLayout.() -> Unit): LinearLayout {
    val r = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    r.build()
    addView(r, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
    return r
}

/** Tappable card with a title and a description, used on the home screen. */
fun LinearLayout.tool(title: String, sub: String, onClick: () -> Unit) = card {
    label(title, 17f, bold = true, topDp = 0)
    label(sub, 13f, muted = true)
}.apply {
    isClickable = true
    setOnClickListener { onClick() }
}

fun Context.input(
    value: String = "", hint: String = "", multiline: Boolean = false, mono: Boolean = false, number: Boolean = false,
) = EditText(this).apply {
    setText(value)
    this.hint = hint
    inputType = when {
        number -> InputType.TYPE_CLASS_NUMBER
        multiline -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        else -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
    }
    if (multiline) {
        minLines = 3
        maxLines = 14
        gravity = Gravity.TOP or Gravity.START
    }
    if (mono) typeface = Typeface.MONOSPACE
    setTextColor(Look.fg(context))
    setHintTextColor(Look.muted(context))
}

fun LinearLayout.toggle(text: String, checked: Boolean, onChange: (Boolean) -> Unit): Switch {
    val s = Switch(context).apply {
        this.text = text
        isChecked = checked
        textSize = 15f
        setTextColor(Look.fg(context))
        setPadding(0, context.dp(8), 0, context.dp(8))
        setOnCheckedChangeListener { _, b -> onChange(b) }
    }
    addView(s, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
    return s
}

/** "Label ...... [ 50 ]" row that saves a positive number setting as you type. */
fun LinearLayout.numberSetting(text: String, key: String, def: Int) {
    row {
        label(text, 15f).layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        val e = context.input(Store.getInt(context, key, def).toString(), number = true).apply {
            gravity = Gravity.CENTER
            minEms = 3
            onText { s -> s.toIntOrNull()?.let { if (it > 0) Store.putInt(context, key, it) } }
        }
        addView(e)
    }
}

/** Searchable list of installed apps. */
fun Activity.pickApp(onPick: (label: String, pkg: String) -> Unit) {
    val pm = packageManager
    val apps = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        .map { it.loadLabel(pm).toString() to it.activityInfo.packageName }
        .distinctBy { it.second }
        .sortedBy { it.first.lowercase() }
    val box = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(8), dp(16), 0)
    }
    val search = input(hint = "Suchen…")
    box.addView(search)
    val list = ListView(this)
    box.addView(list, LinearLayout.LayoutParams(MATCH_PARENT, dp(420)))
    var shown = apps
    val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, shown.map { it.first }.toMutableList())
    list.adapter = adapter
    val dlg = AlertDialog.Builder(this).setTitle("App wählen").setView(box).setNegativeButton("Abbrechen", null).create()
    search.onText { q ->
        shown = apps.filter { it.first.contains(q, ignoreCase = true) }
        adapter.clear()
        adapter.addAll(shown.map { it.first })
    }
    list.setOnItemClickListener { _, _, pos, _ ->
        val (l, p) = shown[pos]
        dlg.dismiss()
        onPick(l, p)
    }
    dlg.show()
}

fun Activity.pickAction(onPick: (id: String, name: String) -> Unit) {
    val entries = Launch.actions.entries.toList()
    AlertDialog.Builder(this)
        .setTitle("Aktion wählen")
        .setItems(entries.map { it.value }.toTypedArray()) { _, i -> onPick(entries[i].key, entries[i].value) }
        .show()
}

inline fun <reified T : Any> Context.sys(): T = getSystemService(T::class.java)!!
