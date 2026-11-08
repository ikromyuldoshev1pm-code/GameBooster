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
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

class HomeView(
    ctx: Context,
    private val onClean: () -> Unit,
    private val onReport: () -> Unit
) : View(ctx) {

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
    private val btns = arrayOf(
        RectF(bw * 0.115f, bh * 0.607f, bw * 0.887f, bh * 0.712f),
        RectF(bw * 0.115f, bh * 0.740f, bw * 0.884f, bh * 0.841f)
    )
    private val labels = arrayOf("Tozalash", "Hisobot")
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    private var pressedIdx = -1
    private val scales = floatArrayOf(1f, 1f)
    private val anims = arrayOfNulls<ValueAnimator>(2)

    private fun animateTo(i: Int, target: Float, ms: Long) {
        anims[i]?.cancel()
        val a = ValueAnimator.ofFloat(scales[i], target)
        a.duration = ms
        a.addUpdateListener {
            scales[i] = it.animatedValue as Float
            invalidate()
        }
        a.start()
        anims[i] = a
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
            for (i in 0 until 2) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 4f
                paint.color = Color.rgb(0, 255, 80)
                c.drawRoundRect(btns[i], 80f, 80f, paint)
                paint.style = Paint.Style.FILL
                paint.textSize = 70f
                paint.textAlign = Paint.Align.CENTER
                c.drawText(labels[i], btns[i].centerX(), btns[i].centerY() + 24f, paint)
            }
        }
    }

    override fun onDraw(c: Canvas) {
        c.drawColor(Color.BLACK)

        c.save()
        c.concat(m)
        drawContent(c)
        c.restore()

        val mx = bw * 0.06f
        val my = bh * 0.012f
        for (i in 0 until 2) {
            if (scales[i] < 0.999f) {
                val r = btns[i]
                val area = RectF(r.left - mx, r.top - my, r.right + mx, r.bottom + my)
                c.save()
                c.concat(m)
                c.scale(scales[i], scales[i], r.centerX(), r.centerY())
                c.clipRect(area)
                drawContent(c)
                c.restore()
            }
        }
    }

    private fun inside(i: Int, x: Float, y: Float): Boolean {
        val pts = floatArrayOf(x, y)
        inv.mapPoints(pts)
        return btns[i].contains(pts[0], pts[1])
    }

    private fun findButton(x: Float, y: Float): Int {
        for (i in 0 until 2) {
            if (inside(i, x, y)) return i
        }
        return -1
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val i = findButton(e.x, e.y)
                if (i >= 0) {
                    pressedIdx = i
                    animateTo(i, 0.92f, 90L)
                    return true
                }
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                val i = pressedIdx
                if (i >= 0 && !inside(i, e.x, e.y)) {
                    pressedIdx = -1
                    animateTo(i, 1f, 140L)
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                val i = pressedIdx
                pressedIdx = -1
                if (i >= 0) {
                    val hit = inside(i, e.x, e.y)
                    animateTo(i, 1f, 140L)
                    if (hit) {
                        performClick()
                        if (i == 0) onClean() else onReport()
                    }
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                val i = pressedIdx
                pressedIdx = -1
                if (i >= 0) animateTo(i, 1f, 140L)
                return true
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        return super.performClick()
    }

    override fun onDetachedFromWindow() {
        for (a in anims) a?.cancel()
        super.onDetachedFromWindow()
    }
}

class MainActivity : AppCompatActivity() {

    private var waiting = false
    private lateinit var root: FrameLayout
    private var report: ReportView? = null

    private val backCb = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            hideReport()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK

        root = FrameLayout(this)
        root.setBackgroundColor(Color.BLACK)
        root.addView(
            HomeView(this, { startCleaning() }, { showReport() }),
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        setContentView(root)
        onBackPressedDispatcher.addCallback(this, backCb)

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        startMonitor()
    }

    override fun onResume() {
        super.onResume()
        report?.refresh()
        if (waiting) {
            waiting = false
            if (CacheAccessibilityService.running()) {
                Toast.makeText(this, "Ruxsat berildi. Tozalash ni bosing", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showReport() {
        if (report != null) return
        val r = ReportView(this) { hideReport() }
        root.addView(
            r,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        report = r
        backCb.isEnabled = true
        r.refresh()
    }

    private fun hideReport() {
        val r = report ?: return
        root.removeView(r)
        report = null
        backCb.isEnabled = false
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
