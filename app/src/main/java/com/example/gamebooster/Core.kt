package com.example.gamebooster

import android.app.AppOpsManager
import android.app.usage.StorageStatsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.PowerManager
import android.os.Process
import android.os.storage.StorageManager

object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("gb", Context.MODE_PRIVATE)
    fun net(c: Context) = sp(c).getBoolean("net", false)
    fun setNet(c: Context, v: Boolean) = sp(c).edit().putBoolean("net", v).apply()
    fun limitMb(c: Context) = sp(c).getInt("limit_mb", 0)
    fun setLimitMb(c: Context, v: Int) = sp(c).edit().putInt("limit_mb", v).apply()
    fun anyActive(c: Context) = net(c) || limitMb(c) > 0
}

object CacheUtil {
    fun hasUsageAccess(c: Context): Boolean {
        val ops = c.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        return ops.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.packageName
        ) == AppOpsManager.MODE_ALLOWED
    }

    fun userApps(c: Context): List<String> =
        c.packageManager.getInstalledApplications(0)
            .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 && it.packageName != c.packageName }
            .map { it.packageName }

    fun totalCache(c: Context): Long {
        if (!hasUsageAccess(c)) return 0
        val ssm = c.getSystemService(Context.STORAGE_STATS_SERVICE) as StorageStatsManager
        var sum = 0L
        for (p in userApps(c)) {
            try {
                sum += ssm.queryStatsForPackage(StorageManager.UUID_DEFAULT, p, Process.myUserHandle()).cacheBytes
            } catch (_: Exception) {}
        }
        return sum
    }

    fun screenOn(c: Context): Boolean =
        (c.getSystemService(Context.POWER_SERVICE) as PowerManager).isInteractive

    fun gameInForeground(c: Context): Boolean {
        if (!hasUsageAccess(c)) return false
        val usm = c.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val ev = usm.queryEvents(now - 60000L, now)
        val e = UsageEvents.Event()
        var last: String? = null
        while (ev.hasNextEvent()) {
            ev.getNextEvent(e)
            if (e.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) last = e.packageName
        }
        val pkg = last ?: return false
        return try {
            c.packageManager.getApplicationInfo(pkg, 0).category == ApplicationInfo.CATEGORY_GAME
        } catch (_: Exception) {
            false
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (i.action == Intent.ACTION_BOOT_COMPLETED && Prefs.anyActive(c)) {
            c.startForegroundService(Intent(c, MonitorService::class.java))
        }
    }
}
