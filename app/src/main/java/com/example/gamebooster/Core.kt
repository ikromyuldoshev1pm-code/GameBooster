package com.example.gamebooster

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo

object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("gb", Context.MODE_PRIVATE)
    fun enabled(c: Context): Boolean = sp(c).getBoolean("enabled", true)
    fun setEnabled(c: Context, v: Boolean) = sp(c).edit().putBoolean("enabled", v).apply()
}

object CacheUtil {
    fun userApps(c: Context): List<String> =
        c.packageManager.getInstalledApplications(0)
            .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 && it.packageName != c.packageName }
            .map { it.packageName }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (i.action == Intent.ACTION_BOOT_COMPLETED && Prefs.enabled(c)) {
            try {
                c.startForegroundService(Intent(c, MonitorService::class.java))
            } catch (_: Exception) {
            }
        }
    }
}
