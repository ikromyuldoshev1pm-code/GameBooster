package com.example.gamebooster

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
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
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

class MonitorService : Service() {

    private var job: Job? = null
    private var lastNetAlert = 0L
    private var lastClean = 0L
    private var warnedA11y = false

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, flags: Int, startId: Int): Int {
        createChannels()
        val n = NotificationCompat.Builder(this, "monitor")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("GameBooster ishlayapti")
            .setOngoing(true)
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
        var tick = 0
        while (currentCoroutineContext().isActive) {
            if (!Prefs.anyActive(this)) {
                stopSelf()
                return
            }
            if (Prefs.net(this)) checkNet()
            if (tick % 12 == 0 && Prefs.limitMb(this) > 0) checkCache()
            tick++
            delay(5000L)
        }
    }

    private fun checkNet() {
        val p = ping()
        if (p in 0L..199L) return
        val now = System.currentTimeMillis()
        if (now - lastNetAlert < 60000L) return
        lastNetAlert = now
        val target = if (connType() == "Wi-Fi") "mobil internetga" else "Wi-Fi'ga"
        alert(2, "Internet sifati past", "Boshqa tarmoqqa ulaning: " + target + " o'ting")
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

    private suspend fun checkCache() {
        val limit = Prefs.limitMb(this) * 1024L * 1024L
        if (CacheUtil.totalCache(this) < limit) return
        val now = System.currentTimeMillis()
        if (now - lastClean < 600000L) return

        val svc = CacheAccessibilityService.instance
        if (svc == null) {
            if (!warnedA11y) {
                warnedA11y = true
                alert(3, "Kesh limitiga yetdi", "Avto-tozalash uchun Maxsus imkoniyatlarni yoqing")
            }
            return
        }
        if (!CacheUtil.screenOn(this) || CacheUtil.gameInForeground(this)) return

        lastClean = now
        alert(3, "Kesh tozalanmoqda", "Limitga yetgani uchun barcha ilovalar keshi tozalanmoqda")
        val pkgs = CacheUtil.userApps(this)
        withContext(Dispatchers.Main) { svc.enqueue(pkgs) }
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("monitor", "Fon xizmati", NotificationManager.IMPORTANCE_MIN))
        nm.createNotificationChannel(NotificationChannel("alerts", "Ogohlantirishlar", NotificationManager.IMPORTANCE_DEFAULT))
    }

    private fun alert(id: Int, title: String, text: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val n = NotificationCompat.Builder(this, "alerts")
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(this).notify(id, n)
    }

    override fun onDestroy() {
        job?.cancel()
        super.onDestroy()
    }
}
