package com.example.gamebooster

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.InetSocketAddress
import java.net.Socket

class MonitorService : Service() {

    private var job: Job? = null
    private var lastAlert = 0L
    private var logoBmp: Bitmap? = null
    private var logoLoaded = false

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, flags: Int, startId: Int): Int {
        if (i?.action == "STOP") {
            Prefs.setEnabled(this, false)
            job?.cancel()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        createChannels()

        val stop = Intent(this, MonitorService::class.java)
        stop.action = "STOP"
        val pi = PendingIntent.getService(
            this, 0, stop,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(this, "monitor")
            .setSmallIcon(R.drawable.ic_notif)
            .setLargeIcon(logo())
            .setContentTitle("GameBooster ishlayapti")
            .setContentText("Internet sifati kuzatilmoqda")
            .setOngoing(true)
            .addAction(0, "To'xtatish", pi)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, n)
        }

        job?.cancel()
        job = CoroutineScope(Dispatchers.Default).launch { loop() }
        return START_STICKY
    }

    private suspend fun loop() {
        while (currentCoroutineContext().isActive) {
            if (!Prefs.enabled(this)) {
                stopSelf()
                return
            }
            checkNet()
            delay(5000L)
        }
    }

    private fun checkNet() {
        val p = ping()
        if (p in 0L..199L) return
        val now = System.currentTimeMillis()
        if (now - lastAlert < 60000L) return
        lastAlert = now
        val target = if (connType() == "Wi-Fi") "mobil internetga" else "Wi-Fi'ga"
        alert("Internet sifati past", "Boshqa tarmoqqa ulaning: " + target + " o'ting")
    }

    private fun ping(): Long {
        return try {
            val s = System.nanoTime()
            Socket().use { it.connect(InetSocketAddress("1.1.1.1", 53), 2000) }
            (System.nanoTime() - s) / 1000000L
        } catch (_: Exception) {
            -1L
        }
    }

    private fun connType(): String {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val c = cm.getNetworkCapabilities(cm.activeNetwork) ?: return "Yo'q"
        return if (c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) "Wi-Fi" else "Mobil"
    }

    private fun logo(): Bitmap? {
        if (logoLoaded) return logoBmp
        logoLoaded = true
        logoBmp = try {
            val id = resources.getIdentifier("game_logo", "drawable", packageName)
            if (id == 0) {
                null
            } else {
                val o = BitmapFactory.Options()
                o.inSampleSize = 4
                val b = BitmapFactory.decodeResource(resources, id, o)
                Bitmap.createScaledBitmap(b, 192, 192, true)
            }
        } catch (_: Throwable) {
            null
        }
        return logoBmp
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("monitor", "Fon xizmati", NotificationManager.IMPORTANCE_MIN))
        nm.createNotificationChannel(NotificationChannel("alerts", "Ogohlantirishlar", NotificationManager.IMPORTANCE_DEFAULT))
    }

    private fun alert(title: String, text: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val n = NotificationCompat.Builder(this, "alerts")
            .setSmallIcon(R.drawable.ic_notif)
            .setLargeIcon(logo())
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(this).notify(2, n)
    }

    override fun onDestroy() {
        job?.cancel()
        super.onDestroy()
    }
}
