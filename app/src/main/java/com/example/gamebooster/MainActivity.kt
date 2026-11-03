package com.example.gamebooster

import android.Manifest
import android.animation.ValueAnimator
import android.content.ComponentName
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
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
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

    private val bw = bmp?.width?.toFloat() ?: 941f
    private val bh = bmp?.height?.toFloat() ?: 1672f
    private val m = Matrix()
    private val inv = Matrix()
    private val btn = RectF(bw * 0.117f, bh * 0.718f, bw * 0.882f, bh * 0.822f)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    private var pressed = false
    private var btnScale = 1f
    private var anim: ValueAnimator? = null

    private fun animateTo(target: Float, ms: Long) {
        anim?.cancel()
        val a = ValueAnimator.ofFloat(btnScale, target)
        a.duration = ms
        a.addUpdateListener {
            btnScale = it.animatedValue as Float
            invalidate()
        }
        a.start()
        anim = a
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        val s = maxOf(w / bw, h / bh)
        m.reset()
        m.setScale(s, s)
        m.postTranslate((w - bw * s) / 2f, (h - bh * s) / 2f)
        m.invert(inv)
    }

    private fun drawContent(c: Canvas) {
        val b = bmp
        if (b != null) {
            c.drawBitmap(b, 0f, 0f, paint)
        } else {
            c.drawColor(Color.BLACK)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 4f
            paint.color = Color.rgb(0, 255, 80)
            c.drawRoundRect(btn, 60f, 60f, paint)
            paint.style = Paint.Style.FILL
            paint.textSize = 80f
            paint.textAlign = Paint.Align.CENTER
            c.drawText("Tozalash", btn.centerX(), btn.centerY() + 28f, paint)
        }
    }

    override fun onDraw(c: Canvas) {
        c.drawColor(Color.BLACK)

        c.save()
        c.concat(m)
        drawContent(c)
        c.restore()

        if (btnScale < 0.999f) {
            val mg = bw * 0.06f
            val area = RectF(btn.left - mg, btn.top - mg, btn.right + mg, btn.bottom + mg)
            c.save()
            c.concat(m)
            c.scale(btnScale, btnScale, btn.centerX(), btn.centerY())
            c.clipRect(area)
            drawContent(c)
            c.restore()
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
                    animateTo(0.92f, 90L)
                    return true
                }
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                if (pressed && !inside(e.x, e.y)) {
                    pressed = false
                    animateTo(1f, 140L)
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                val hit = pressed && inside(e.x, e.y)
                pressed = false
                animateTo(1f, 140L)
                if (hit) {
                    performClick()
                    onStart()
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                pressed = false
                animateTo(1f, 140L)
                return true
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        return super.performClick()
    }

    override fun onDetachedFromWindow() {
        anim?.cancel()
        super.onDetachedFromWindow()
    }
}

class MainActivity : AppCompatActivity() {

    private var waiting = false

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

    override fun onResume() {
        super.onResume()
        if (waiting) {
            waiting = false
            if (CacheAccessibilityService.running()) {
                Toast.makeText(this, "Ruxsat berildi. Tozalash ni bosing", Toast.LENGTH_LONG).show()
            }
        }
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
            .setMessage(
                "Keshni tozalash uchun Maxsus imkoniyatlar ruxsati kerak. " +
                    "Ruxsat berish ni bosing, ro'yxatdan CLENOX ni toping va yoqing.\n\n" +
                    "Yoqib bo'lmasa: Ilova sozlamalari › ⋮ › Cheklangan sozlamalarga ruxsat berish."
            )
            .setPositiveButton("Ruxsat berish") { _, _ -> openAccessibility() }
            .setNeutralButton("Ilova sozlamalari") { _, _ -> openAppSettings() }
            .setNegativeButton("Bekor qilish", null)
            .show()
    }

    private fun openAccessibility() {
        waiting = true
        try {
            val i = Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS")
            i.putExtra(
                "android.intent.extra.COMPONENT_NAME",
                ComponentName(this, CacheAccessibilityService::class.java).flattenToString()
            )
            startActivity(i)
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        Toast.makeText(this, "CLENOX ni toping va yoqing", Toast.LENGTH_LONG).show()
    }

    private fun openAppSettings() {
        val i = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + packageName))
        startActivity(i)
    }
}
