package com.example.gamebooster

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

class MainActivity : AppCompatActivity() {

    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        status = TextView(this)
        status.textSize = 15f
        status.setPadding(0, 0, 0, 32)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(48, 96, 48, 48)
        root.addView(status)
        root.addView(makeButton("1. Tarmoqni nazorat qilish") { toggleNet() })
        root.addView(makeButton("2. Keshni tozalash") { askClearAll() })
        root.addView(makeButton("3. Keshni nazorat qilish") { askLimit() })
        root.addView(makeButton("Hammasini to'xtatish") { stopAll() })
        setContentView(root)

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    private fun makeButton(label: String, action: () -> Unit): Button {
        val b = Button(this)
        b.text = label
        b.setOnClickListener { action() }
        return b
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val limit = Prefs.limitMb(this)
        val net = if (Prefs.net(this)) "YOQIQ" else "o'chiq"
        val lim = if (limit > 0) "$limit MB" else "o'chiq"
        val acc = if (CacheAccessibilityService.running()) "yoqilgan" else "yoqilmagan"
        status.text = "Tarmoq nazorati: $net\nKesh limiti: $lim\nMaxsus imkoniyat: $acc"
    }

    private fun startSvc() {
        startForegroundService(Intent(this, MonitorService::class.java))
    }

    private fun toggleNet() {
        val on = !Prefs.net(this)
        Prefs.setNet(this, on)
        if (on) startSvc()
        Toast.makeText(this, if (on) "Tarmoq nazorati yoqildi" else "O'chirildi", Toast.LENGTH_SHORT).show()
        refresh()
    }

    private fun askClearAll() {
        if (!CacheAccessibilityService.running()) {
            askAccessibility()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Keshni tozalash")
            .setMessage("Barcha ilovalar keshi tozalanadi (Sozlamalar ekrani qisqa vaqt ochiladi). Ruxsat berasizmi?")
            .setPositiveButton("Ruxsat beraman") { _, _ ->
                CacheAccessibilityService.instance?.enqueue(CacheUtil.userApps(this))
            }
            .setNegativeButton("Bekor qilish", null)
            .show()
    }

    private fun askLimit() {
        if (!CacheUtil.hasUsageAccess(this)) {
            AlertDialog.Builder(this)
                .setTitle("Ruxsat kerak")
                .setMessage("Kesh hajmini o'lchash uchun Foydalanish ma'lumotlariga kirish ruxsatini yoqing.")
                .setPositiveButton("Sozlamalarni ochish") { _, _ ->
                    startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                }
                .setNegativeButton("Bekor qilish", null)
                .show()
            return
        }
        if (!CacheAccessibilityService.running()) {
            askAccessibility()
            return
        }
        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_NUMBER
        input.hint = "Masalan: 500 (MB)"
        AlertDialog.Builder(this)
            .setTitle("Kesh limiti (MB)")
            .setMessage("Umumiy kesh shu miqdorga yetganda barcha ilovalar keshi avtomatik tozalanadi.")
            .setView(input)
            .setPositiveButton("Yoqish") { _, _ ->
                val mb = input.text.toString().toIntOrNull() ?: 0
                if (mb > 0) {
                    Prefs.setLimitMb(this, mb)
                    startSvc()
                    refresh()
                }
            }
            .setNegativeButton("Bekor qilish", null)
            .show()
    }

    private fun askAccessibility() {
        AlertDialog.Builder(this)
            .setTitle("Ruxsat kerak")
            .setMessage("Boshqa ilovalar keshini tozalash uchun Sozlamalar > Maxsus imkoniyatlar bo'limida GameBooster ni yoqing.")
            .setPositiveButton("Sozlamalarni ochish") { _, _ ->
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            .setNegativeButton("Bekor qilish", null)
            .show()
    }

    private fun stopAll() {
        Prefs.setNet(this, false)
        Prefs.setLimitMb(this, 0)
        stopService(Intent(this, MonitorService::class.java))
        refresh()
    }
}
