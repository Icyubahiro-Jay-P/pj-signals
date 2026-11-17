package com.pj.signals

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

// Holds one streaming connection to https://ntfy.sh/<topic>/json and turns each message into a notification.
class SignalService : Service() {
    @Volatile private var topic = ""
    @Volatile private var conn: HttpURLConnection? = null
    private var nextId = 100

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        channels()
        val n = builder(CH_LIVE).setContentTitle("JP Signals").setContentText("Listening for signals")
            .setSmallIcon(R.drawable.ic_stat).setOngoing(true).setContentIntent(openApp()).build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(1, n)   // must happen before any stopSelf, or Android kills the app
        val t = prefs(this).getString("topic", "") ?: ""
        if (t.isEmpty()) { stopSelf(); return START_NOT_STICKY }
        if (t != topic) {
            topic = t
            conn?.disconnect()        // old thread sees topic changed and exits
            Thread { loop(t) }.start()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        topic = ""
        conn?.disconnect()
        super.onDestroy()
    }

    private fun loop(t: String) {
        var wait = 5_000L
        while (topic == t) {
            var c: HttpURLConnection? = null
            try {
                // ponytail: an unknown/expired lastId makes ntfy replay its 12 h cache once
                val since = prefs(this).getString("lastId", null) ?: "none"
                c = URL("https://ntfy.sh/$t/json?since=$since").openConnection() as HttpURLConnection
                conn = c
                c.connectTimeout = 15_000
                c.readTimeout = 90_000   // ntfy sends a keepalive about every 45 s
                c.inputStream.bufferedReader().useLines { lines ->
                    for (line in lines) {
                        if (topic != t) break
                        wait = 5_000L
                        val j = JSONObject(line)
                        if (j.optString("event") == "message") {
                            show(j)
                            prefs(this).edit().putString("lastId", j.getString("id")).apply()
                        }
                    }
                }
            } catch (e: Exception) {
                // network drop, timeout or bad line: reconnect below
            } finally {
                c?.disconnect()
            }
            if (topic != t) break
            try { Thread.sleep(wait) } catch (e: InterruptedException) { }
            wait = minOf(wait * 2, 60_000L)
        }
    }

    private fun show(j: JSONObject) {
        val title = j.optString("title").ifEmpty { "JP signal" }
        val msg = j.optString("message")
        @Suppress("DEPRECATION")
        val n = builder(CH_SIG).setContentTitle(title).setContentText(msg)
            .setStyle(Notification.BigTextStyle().bigText(msg))
            .setSmallIcon(R.drawable.ic_stat).setAutoCancel(true).setContentIntent(openApp())
            .setDefaults(Notification.DEFAULT_ALL).setPriority(Notification.PRIORITY_HIGH).build()
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(nextId++, n)
    }

    @Suppress("DEPRECATION")
    private fun builder(ch: String): Notification.Builder =
        if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, ch) else Notification.Builder(this)

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE
    )

    private fun channels() {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(CH_LIVE, "Connection", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CH_SIG, "Signals", NotificationManager.IMPORTANCE_HIGH).apply { enableVibration(true) })
    }

    companion object {
        private const val CH_LIVE = "live"
        private const val CH_SIG = "signals"

        fun start(c: Context) {
            if ((prefs(c).getString("topic", "") ?: "").isEmpty()) return
            val i = Intent(c, SignalService::class.java)
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i) else c.startService(i)
        }
    }
}
