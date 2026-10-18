package app.textexpander

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/** Opens a target: "app:<package>", "action:<id>" or any URL. */
object Launch {
    val actions = linkedMapOf(
        "action:note" to "Brain Dump (Quick Note)",
        "action:study" to "Study Mode starten",
        "action:workout" to "Workout Mode starten",
        "action:focus" to "Fokus-Modi öffnen",
        "action:clipboard" to "Smart Clipboard öffnen",
        "action:cheat" to "C-Spickzettel öffnen",
    )

    fun isEmpty(target: String) = target.isBlank() || target == "https://" || target == "app:"

    fun describe(ctx: Context, target: String): String = when {
        isEmpty(target) -> "⚠️ Noch kein Ziel – lange drücken → Bearbeiten"
        target.startsWith("app:") -> {
            val pkg = target.removePrefix("app:")
            try {
                val pm = ctx.packageManager
                "App: " + pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0))
            } catch (e: Exception) {
                "App: $pkg (nicht installiert)"
            }
        }
        target.startsWith("action:") -> "Aktion: " + (actions[target] ?: target)
        else -> target
    }

    fun open(ctx: Context, target: String, kindForEmpty: String = "launchers"): Boolean {
        val intent: Intent? = when {
            isEmpty(target) -> {
                ctx.toast("Ziel fehlt noch – bitte eintragen")
                ListActivity.intent(ctx, kindForEmpty)
            }
            target.startsWith("app:") -> ctx.packageManager.getLaunchIntentForPackage(target.removePrefix("app:"))
                ?: run { ctx.toast("App ist nicht installiert"); return false }
            target == "action:note" -> Intent(ctx, QuickNoteActivity::class.java)
            target == "action:study" -> Intent(ctx, FocusActivity::class.java).putExtra("start", "study")
            target == "action:workout" -> Intent(ctx, FocusActivity::class.java).putExtra("start", "workout")
            target == "action:focus" -> Intent(ctx, FocusActivity::class.java)
            target == "action:clipboard" -> ListActivity.intent(ctx, "clips")
            target == "action:cheat" -> ListActivity.intent(ctx, "cheat")
            target.startsWith("action:") -> null
            else -> Intent(Intent.ACTION_VIEW, Uri.parse(if (target.contains("://")) target else "https://$target"))
        }
        if (intent == null) return false
        val external = intent.component?.packageName != ctx.packageName
        if (ctx !is Activity || external) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            ctx.startActivity(intent); true
        } catch (e: ActivityNotFoundException) {
            ctx.toast("Kann das nicht öffnen"); false
        }
    }
}
