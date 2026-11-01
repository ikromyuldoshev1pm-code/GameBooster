package com.example.gamebooster

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

class HomeView(ctx: Context, private val onStart: () -> Unit) : View(ctx) {

    private val bmp: Bitmap? = try {
        val id = ctx.resources.getIdentifier("game_home", "drawable", ctx.packageName)
        if (id != 0) BitmapFactory.decodeResource(ctx.resources, id) else null
    } catch (_: Throwable) {
        null
    }

    private val bw = bmp?.width?.toFloat() ?: 768f
    private val bh = bmp?.height?.toFloat() ?: 1365f
    private val m = Matrix()
    private val inv = Matrix()
    private val btn = RectF(bw * 0.117f, bh * 0.571f, bw * 0.885f, bh * 0.766f)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val hi = Paint(Paint.ANTI_ALIAS_FLAG)
    private var pressed = false

    init {
        hi.color = Color.argb(70, 120, 200, 255)
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        val s = maxOf(w / bw, h / bh)
        m.reset()
        m.setScale(s, s)
        m.postTranslate((w - bw * s) / 2f, (h - bh * s) / 2f)
        m.invert(inv)
    }

    override fun onDraw(c: Canvas) {
        c.drawColor(Color.BLACK)
        val b = bmp
        if (b != null) {
            c.drawBitmap(b, m, paint)
        } else {
            c.save()
            c.concat(m)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 4f
            paint.color = Color.rgb(0, 170, 255)
            c.drawRoundRect(btn, 30f, 30f, paint)
            paint.style = Paint.Style.FILL
            paint.textSize = 90f
            paint.textAlign = Paint.Align.CENTER
            c.drawText("START", btn.centerX(), btn.centerY() + 30f, paint)
            c.restore()
        }
        if (pressed) {
            val r = RectF(btn)
            m.mapRect(r)
            c.drawRoundRect(r, 30f, 30f, hi)
        }
    }

    private fun inside(x: Float, y: Float): Boolean {
        val pts = floatArrayOf(x, y)
        inv.mapPoints(pts)
        return btn.contains(pts[0], pts[1])
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (inside(e.x, e.y)) {
                    pressed = true
                    invalidate()
                    return true
                }
                return false
            }
            MotionEvent.ACTION_UP -> {
                val hit = pressed && inside(e.x, e.y)
                pressed = false
                invalidate()
                if (hit) {
                    performClick()
                    onStart()
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                pressed = false
                invalidate()
                return true
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        return super.performClick()
    }
}

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK

        val root = FrameLayout(this)
        root.setBackgroundColor(Color.BLACK)
        root.addView(
            HomeView(this) { startCleaning() },
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        setContentView(root)

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        startMonitor()
    }

    private fun startMonitor() {
        Prefs.setEnabled(this, true)
        try {
            startForegroundService(Intent(this, MonitorService::class.java))
        } catch (_: Exception) {
        }
    }

    private fun startCleaning() {
        val svc = CacheAccessibilityService.instance
        if (svc == null) {
            askAccessibility()
            return
        }
        svc.enqueue(CacheUtil.userApps(this))
    }

    private fun askAccessibility() {
        AlertDialog.Builder(this)
            .setTitle("Ruxsat kerak")
            .setMessage("Keshni tozalash uchun Sozlamalar > Maxsus imkoniyatlar bo'limida GameBooster ni yoqing.")
            .setPositiveButton("Sozlamalarni ochish") { _, _ ->
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            .setNegativeButton("Bekor qilish", null)
            .show()
    }
}
