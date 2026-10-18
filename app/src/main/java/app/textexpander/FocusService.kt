package app.textexpander

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Runs the Study / Workout timer in the background, switches DND on and back off. */
class FocusService : Service() {

    companion object {
        @Volatile var mode: String? = null
        @Volatile var endAt = 0L
        private const val CH_RUN = "focus_run"
        private const val CH_DONE = "focus_done"

        fun label(m: String) = if (m == "study") "Study Mode" else "Workout Mode"

        fun start(ctx: Context, m: String, minutes: Int) {
            ctx.startForegroundService(
                Intent(ctx, FocusService::class.java).putExtra("mode", m).putExtra("min", minutes)
            )
        }

        fun stop(ctx: Context) {
            if (mode != null) ctx.startService(Intent(ctx, FocusService::class.java).setAction("stop"))
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private val finish = Runnable { done(true) }
    private var savedFilter = -1

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "stop") {
            done(false)
            return START_NOT_STICKY
        }
        val m = intent?.getStringExtra("mode") ?: "study"
        val minutes = intent?.getIntExtra("min", 50) ?: 50
        createChannels()
        mode = m
        endAt = System.currentTimeMillis() + minutes * 60_000L

        val n = runningNotification(m)
        if (Build.VERSION.SDK_INT >= 34) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(1, n)

        if (Store.getBool(this, "${m}_dnd", true)) dndOn()
        handler.removeCallbacks(finish)
        handler.postDelayed(finish, minutes * 60_000L)
        return START_NOT_STICKY
    }

    private fun done(completed: Boolean) {
        handler.removeCallbacks(finish)
        dndRestore()
        val m = mode
        mode = null
        endAt = 0L
        if (completed && m != null) {
            val title = if (m == "study") "Study Mode fertig 🎉" else "Workout fertig 💪"
            val text = if (m == "study") "Super gemacht – jetzt 10 Minuten Pause." else "Stark! Trinken und dehnen nicht vergessen."
            val n = Notification.Builder(this, CH_DONE)
                .setSmallIcon(R.drawable.ic_flow)
                .setContentTitle(title)
                .setContentText(text)
                .setAutoCancel(true)
                .setContentIntent(openFocus())
                .build()
            sys<NotificationManager>().notify(2, n)
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacks(finish)
        if (mode != null) dndRestore()
        mode = null
        super.onDestroy()
    }

    private fun dndOn() {
        val nm = sys<NotificationManager>()
        if (!nm.isNotificationPolicyAccessGranted) return
        if (savedFilter == -1) savedFilter = nm.currentInterruptionFilter
        nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
    }

    private fun dndRestore() {
        val nm = sys<NotificationManager>()
        if (savedFilter != -1 && nm.isNotificationPolicyAccessGranted) nm.setInterruptionFilter(savedFilter)
        savedFilter = -1
    }

    private fun openFocus(): PendingIntent = PendingIntent.getActivity(
        this, 0, Intent(this, FocusActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun runningNotification(m: String): Notification {
        val stopPi = PendingIntent.getService(
            this, 1, Intent(this, FocusService::class.java).setAction("stop"),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val until = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(endAt))
        return Notification.Builder(this, CH_RUN)
            .setSmallIcon(R.drawable.ic_flow)
            .setContentTitle(label(m))
            .setContentText("Läuft bis $until")
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setWhen(endAt)
            .setShowWhen(true)
            .setOngoing(true)
            .setContentIntent(openFocus())
            .addAction(
                Notification.Action.Builder(Icon.createWithResource(this, R.drawable.ic_flow), "Beenden", stopPi).build()
            )
            .build()
    }

    private fun createChannels() {
        val nm = sys<NotificationManager>()
        nm.createNotificationChannel(NotificationChannel(CH_RUN, "Laufender Timer", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CH_DONE, "Timer fertig", NotificationManager.IMPORTANCE_HIGH))
    }
}
