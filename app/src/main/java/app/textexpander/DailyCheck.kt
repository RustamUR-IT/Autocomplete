package app.textexpander

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.util.Calendar

/** Once a day (around 9:00): warranty / document expiry reminders and new document finds. */
class DailyCheck : BroadcastReceiver() {

    companion object {
        private const val CH = "daily"

        fun schedule(ctx: Context) {
            val am = ctx.sys<AlarmManager>()
            val pi = PendingIntent.getBroadcast(
                ctx, 70, Intent(ctx, DailyCheck::class.java).setAction("check"),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val next = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 9); set(Calendar.MINUTE, 5); set(Calendar.SECOND, 0)
                if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
            }
            am.setInexactRepeating(AlarmManager.RTC_WAKEUP, next.timeInMillis, AlarmManager.INTERVAL_DAY, pi)
        }

        fun run(ctx: Context) {
            val nm = ctx.sys<NotificationManager>()
            nm.createNotificationChannel(NotificationChannel(CH, "Erinnerungen (Garantie, Dokumente)", NotificationManager.IMPORTANCE_DEFAULT))
            var id = 100

            fun notify(title: String, text: String, tab: String, key: String) {
                if (Store.getBool(ctx, "notified_$key", false)) return
                Store.putBool(ctx, "notified_$key", true)
                val pi = PendingIntent.getActivity(
                    ctx, id, Intent(ctx, DocsActivity::class.java).putExtra("tab", tab).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
                nm.notify(id++, Notification.Builder(ctx, CH).setSmallIcon(R.drawable.ic_flow)
                    .setContentTitle(title).setContentText(text).setStyle(Notification.BigTextStyle().bigText(text))
                    .setContentIntent(pi).setAutoCancel(true).build())
            }

            if (Features.on(ctx, "warranty")) {
                for (w in Docs.warranties(ctx)) {
                    val d = Docs.warrantyDaysLeft(w) ?: continue
                    val stage = when {
                        d == 0L -> "0"; d in 1..7 -> "7"; d in 8..30 -> "30"; else -> null
                    } ?: continue
                    val text = if (d == 0L) "Die Garantie für ${w["product"]} läuft heute ab." else "Die Garantie für ${w["product"]} läuft in $d Tagen ab (${Docs.warrantyEnd(w)?.format(Docs.DATE)}). Jetzt noch prüfen, ob alles funktioniert."
                    notify("🔧 Garantie läuft ab", text, "warranty", "w_${w["id"]}_$stage")
                }
            }

            if (Features.on(ctx, "docs")) {
                for (doc in Docs.all(ctx)) {
                    val d = Docs.daysUntil(doc["expiry"]) ?: continue
                    val stage = when { d == 0L -> "0"; d in 1..14 -> "14"; d in 15..60 -> "60"; else -> null } ?: continue
                    notify("📄 ${doc["title"]} läuft ab", if (d == 0L) "Gültig nur noch heute." else "Noch $d Tage gültig (bis ${doc["expiry"]}). Rechtzeitig verlängern!", "docs", "d_${doc["id"]}_$stage")
                }
                // New finds since last check
                if (DocScanner.roots(ctx).isNotEmpty()) {
                    val seen = Store.getStr(ctx, "finds_seen", "").split('\n').toSet()
                    val finds = try { DocScanner.scan(ctx, onlyImportant = true) } catch (e: Exception) { emptyList() }
                    val fresh = finds.filter { it.uri.toString() !in seen }
                    if (fresh.isNotEmpty()) {
                        val names = fresh.take(3).joinToString(", ") { it.name }
                        notify("🔍 ${fresh.size} neue wichtige Dokumente", "$names${if (fresh.size > 3) " …" else ""} – antippen, um sie in den Tresor zu übernehmen.", "finds", "finds_" + System.currentTimeMillis())
                        Store.putStr(ctx, "finds_seen", (seen + finds.map { it.uri.toString() }).joinToString("\n"))
                    }
                }
            }
        }
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) { schedule(ctx); return }
        val pending = goAsync()
        Thread {
            try { run(ctx) } catch (e: Exception) { } finally { pending.finish() }
        }.start()
    }
}
