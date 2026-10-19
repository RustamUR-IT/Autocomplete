package app.textexpander

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper

/**
 * Location modes via Wi-Fi: when the phone joins a saved Wi-Fi (gym, FH, home),
 * FlowKit switches "Do not disturb" on directly, or offers Study/Workout Mode with one tap.
 * Runs inside the accessibility service, so it keeps working in the background.
 */
class PlaceWatcher(private val ctx: Context) {

    companion object {
        const val DND = "action:dnd"
        val actions = linkedMapOf(
            "action:workout" to "Workout Mode anbieten (1 Tipp)",
            "action:study" to "Study Mode anbieten (1 Tipp)",
            DND to "Nur „Nicht stören“ (automatisch, bis du gehst)",
        )
        @Volatile var currentSsid: String? = null
        @Volatile var active: PlaceWatcher? = null
        @Volatile var status = "Noch kein WLAN geprüft."

        fun hasLocation(c: Context) =
            c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

        fun hasBackgroundLocation(c: Context) =
            c.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED

        /** Current Wi-Fi name while the app is open (needs location permission). */
        @Suppress("DEPRECATION")
        fun readSsidNow(c: Context): String? {
            val raw = c.applicationContext.sys<WifiManager>().connectionInfo?.ssid ?: return null
            return clean(raw)
        }

        fun clean(raw: String?): String? {
            val s = raw?.trim()?.removeSurrounding("\"") ?: return null
            return if (s.isEmpty() || s == "<unknown ssid>" || s == WifiManager.UNKNOWN_SSID) null else s
        }
    }

    private val cm = ctx.sys<ConnectivityManager>()
    private val nm = ctx.sys<NotificationManager>()
    private val handler = Handler(Looper.getMainLooper())
    private var wifiNet: Network? = null
    private val lastOffer = HashMap<String, Long>()

    private val callback: ConnectivityManager.NetworkCallback =
        if (Build.VERSION.SDK_INT >= 31) object : ConnectivityManager.NetworkCallback(FLAG_INCLUDE_LOCATION_INFO) {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = onCaps(network, caps)
            override fun onLost(network: Network) = onLostNet(network)
        } else object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = onCaps(network, caps)
            override fun onLost(network: Network) = onLostNet(network)
        }

    /** Re-register after the user granted location, so Android delivers the Wi-Fi name. */
    fun restart() { stop(); start() }

    fun start() {
        active = this
        try {
            cm.registerNetworkCallback(
                NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(), callback
            )
        } catch (e: Exception) {
            status = "WLAN-Überwachung konnte nicht starten: ${e.message}"
        }
    }

    fun stop() {
        try { cm.unregisterNetworkCallback(callback) } catch (e: Exception) { }
    }

    private fun onCaps(network: Network, caps: NetworkCapabilities) {
        val ssid = clean((caps.transportInfo as? WifiInfo)?.ssid)
            ?: if (Build.VERSION.SDK_INT < 31) readSsidNow(ctx) else null
        handler.post {
            wifiNet = network
            if (ssid == null) {
                status = if (!hasLocation(ctx) || !hasBackgroundLocation(ctx))
                    "WLAN verbunden, Name nicht lesbar – Standort „Immer erlauben“ fehlt."
                else "WLAN verbunden, Name nicht lesbar – ist der Standort eingeschaltet?"
                return@post
            }
            status = "Verbunden mit „$ssid“"
            if (ssid != currentSsid) {
                currentSsid?.let { leave(it) }
                currentSsid = ssid
                enter(ssid)
            }
        }
    }

    private fun onLostNet(network: Network) {
        handler.post {
            if (network != wifiNet) return@post
            wifiNet = null
            currentSsid?.let { leave(it) }
            currentSsid = null
            status = "Kein WLAN"
        }
    }

    private fun rulesFor(ssid: String) =
        Store.list(ctx, "places").filter { it["ssid"].orEmpty().trim().equals(ssid, ignoreCase = true) }

    private fun enter(ssid: String) {
        if (!Features.on(ctx, "places")) return
        for (r in rulesFor(ssid)) {
            val place = r["label"].orEmpty().ifBlank { ssid }
            when (val action = r["target"].orEmpty()) {
                DND -> dndOn(place)
                "action:workout", "action:study" -> offer(place, action)
            }
        }
    }

    private fun leave(ssid: String) {
        if (rulesFor(ssid).any { it["target"] == DND }) dndRestore()
        nm.cancel(30)
    }

    private fun dndOn(place: String) {
        if (!nm.isNotificationPolicyAccessGranted) return
        if (Store.getInt(ctx, "place_saved_filter", -1) == -1) Store.putInt(ctx, "place_saved_filter", nm.currentInterruptionFilter)
        nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
        status = "„Nicht stören“ an – du bist bei: $place"
    }

    private fun dndRestore() {
        val saved = Store.getInt(ctx, "place_saved_filter", -1)
        if (saved != -1 && nm.isNotificationPolicyAccessGranted) nm.setInterruptionFilter(saved)
        Store.putInt(ctx, "place_saved_filter", -1)
    }

    /** Offers the mode as a notification – one tap starts it. Max once per 2 hours per place. */
    private fun offer(place: String, action: String) {
        val now = System.currentTimeMillis()
        if (now - (lastOffer[place] ?: 0L) < 2 * 3_600_000L) return
        if (FocusService.mode != null) return
        lastOffer[place] = now

        nm.createNotificationChannel(NotificationChannel("places", "Orts-Modi", NotificationManager.IMPORTANCE_HIGH))
        val mode = if (action == "action:workout") "workout" else "study"
        val start = PendingIntent.getActivity(
            ctx, 31, Intent(ctx, FocusActivity::class.java).putExtra("start", mode).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val title = if (mode == "workout") "Im Gym angekommen 💪" else "Bei „$place“ angekommen 📚"
        val text = if (mode == "workout") "Tippen → Workout Mode: Musik, Timer, Nicht stören" else "Tippen → Study Mode: Nicht stören, Timer, Lern-Apps"
        val n = Notification.Builder(ctx, "places")
            .setSmallIcon(R.drawable.ic_flow)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(start)
            .setAutoCancel(true)
            .addAction(Notification.Action.Builder(null, if (mode == "workout") "Workout starten" else "Study starten", start).build())
            .build()
        nm.notify(30, n)
        status = "Angekommen bei: $place – Benachrichtigung gezeigt"
    }
}
