package app.textexpander

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.TextView

/** Study Mode & Workout Mode: settings, start buttons and the running timer screen. */
class FocusActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private var timerText: TextView? = null
    private var restText: TextView? = null
    private var restEnd = 0L
    private var shownMode: String? = "-"

    private val tick = object : Runnable {
        override fun run() {
            if (FocusService.mode != shownMode) render()
            timerText?.text = fmt(FocusService.endAt - System.currentTimeMillis())
            if (restEnd > 0) {
                val left = restEnd - System.currentTimeMillis()
                if (left <= 0) {
                    restEnd = 0
                    restText?.text = "Los geht's! 💪"
                    alarm()
                } else {
                    restText?.text = "Pause  " + fmt(left)
                }
            }
            handler.postDelayed(this, 250)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleStart(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleStart(intent)
    }

    override fun onResume() {
        super.onResume()
        render()
        handler.post(tick)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(tick)
    }

    private fun handleStart(i: Intent?) {
        val m = i?.getStringExtra("start") ?: return
        i.removeExtra("start")
        startMode(m)
    }

    private fun startMode(m: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)

        if (Store.getBool(this, "${m}_dnd", true) &&
            !sys<NotificationManager>().isNotificationPolicyAccessGranted
        ) toast("„Nicht stören“-Zugriff fehlt – auf der Startseite erlauben")

        val minutes = Store.getInt(this, "${m}_min", if (m == "study") 50 else 60)
        FocusService.start(this, m, minutes)

        if (m == "workout") {
            if (Store.getBool(this, "workout_music", true)) mediaKey(KeyEvent.KEYCODE_MEDIA_PLAY)
            val t = Store.getStr(this, "workout_target", "")
            if (!Launch.isEmpty(t)) handler.postDelayed({ Launch.open(this, t) }, 600)
        } else {
            val targets = Store.list(this, "studyapps").map { it["target"].orEmpty() }.filter { !Launch.isEmpty(it) }
            if (targets.isEmpty()) toast("Tipp: Lern-Apps im Study Mode eintragen")
            targets.forEachIndexed { i, t -> handler.postDelayed({ Launch.open(this, t, "studyapps") }, 600L + i * 900L) }
        }
        handler.postDelayed({ render() }, 150)
    }

    private fun render() {
        val m = FocusService.mode
        shownMode = m
        val keepOn = m == "workout" && Store.getBool(this, "workout_screen", true)
        if (keepOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val c = page("Fokus-Modi")
        timerText = null
        restText = null

        if (m != null) {
            c.card {
                label(FocusService.label(m).uppercase(), 13f, muted = true, mono = true, topDp = 0).gravity = Gravity.CENTER
                timerText = label("--:--", 64f, mono = true, topDp = 8).apply { gravity = Gravity.CENTER }
                if (m == "workout") {
                    restText = label("", 26f, mono = true).apply { gravity = Gravity.CENTER }
                    val def = Store.getInt(context, "workout_rest", 90)
                    pill("Satzpause ${def}s", primary = true) { restEnd = System.currentTimeMillis() + def * 1000L }
                    row {
                        for (s in listOf(60, 120, 180)) pill("${s}s") { restEnd = System.currentTimeMillis() + s * 1000L }
                    }
                    pill("⏯  Musik Play/Pause") { mediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) }
                }
                pill("Beenden") {
                    FocusService.stop(this@FocusActivity)
                    restEnd = 0
                    handler.postDelayed({ render() }, 300)
                }
            }
        }

        c.section("📚 Study Mode")
        c.card {
            numberSetting("Dauer (Minuten)", "study_min", 50)
            toggle("„Nicht stören“ einschalten", Store.getBool(context, "study_dnd", true)) {
                Store.putBool(context, "study_dnd", it)
            }
            pill("Lern-Apps beim Start (${Store.list(context, "studyapps").size})") {
                ListActivity.open(this@FocusActivity, "studyapps")
            }
            pill(if (m == "study") "Neu starten" else "Study Mode starten", primary = m != "study") { startMode("study") }
        }

        c.section("🏋️ Workout Mode")
        c.card {
            numberSetting("Dauer (Minuten)", "workout_min", 60)
            numberSetting("Satzpause (Sekunden)", "workout_rest", 90)
            toggle("Musik starten (setzt letzte Wiedergabe fort)", Store.getBool(context, "workout_music", true)) {
                Store.putBool(context, "workout_music", it)
            }
            toggle("„Nicht stören“ einschalten", Store.getBool(context, "workout_dnd", true)) {
                Store.putBool(context, "workout_dnd", it)
            }
            toggle("Display bleibt an, solange dieser Timer offen ist", Store.getBool(context, "workout_screen", true)) {
                Store.putBool(context, "workout_screen", it)
            }
            val t = Store.getStr(context, "workout_target", "")
            pill(if (Launch.isEmpty(t)) "Trainings-App beim Start: keine" else Launch.describe(context, t)) {
                pickApp { _, pkg -> Store.putStr(this@FocusActivity, "workout_target", "app:$pkg"); render() }
            }
            if (!Launch.isEmpty(t)) label("Tipp: Mit Trainings-App siehst du den Timer in der Benachrichtigung statt hier.", 12f, muted = true)
            if (!Launch.isEmpty(t)) pill("Trainings-App entfernen") { Store.putStr(this@FocusActivity, "workout_target", ""); render() }
            pill(if (m == "workout") "Neu starten" else "Workout starten", primary = m != "workout") { startMode("workout") }
        }
    }

    private fun fmt(ms: Long): String {
        val s = ((ms + 999) / 1000).coerceAtLeast(0)
        return "%02d:%02d".format(s / 60, s % 60)
    }

    private fun mediaKey(code: Int) {
        val am = sys<AudioManager>()
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }

    private fun alarm() {
        val vib: Vibrator = if (Build.VERSION.SDK_INT >= 31) sys<VibratorManager>().defaultVibrator
                            else @Suppress("DEPRECATION") sys<Vibrator>()
        vib.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 350, 150, 350), -1))
        try {
            val tone = ToneGenerator(AudioManager.STREAM_ALARM, 90)
            tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 500)
            handler.postDelayed({ tone.release() }, 900)
        } catch (e: RuntimeException) { }
    }
}
