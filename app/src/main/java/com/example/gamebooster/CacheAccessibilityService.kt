package com.example.gamebooster

import android.accessibilityservice.AccessibilityService
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.animation.LinearInterpolator
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.PI
import kotlin.math.sin

class NeonView(ctx: Context) : View(ctx) {

    private val bg: Bitmap? = load("game_bg", 1)
    private val broom: Bitmap? = load("game_logo", 2)

    private fun load(name: String, sample: Int): Bitmap? {
        return try {
            val id = context.resources.getIdentifier(name, "drawable", context.packageName)
            if (id == 0) {
                null
            } else {
                val o = BitmapFactory.Options()
                o.inSampleSize = sample
                BitmapFactory.decodeResource(context.resources, id, o)
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun ring(color: Int): Paint {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.color = color
        return p
    }

    private val glow = ring(Color.argb(60, 0, 255, 70))
    private val mid = ring(Color.argb(150, 40, 255, 100))
    private val core = ring(Color.rgb(190, 255, 205))
    private val arc = ring(Color.rgb(220, 255, 230))
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bgPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val broomPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val oval = RectF()
    private val dst = RectF()
    private var t = 0f

    private val anim = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 2400L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            t = it.animatedValue as Float
            invalidate()
        }
    }

    init {
        broomPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val two = (2.0 * PI).toFloat()

        c.drawColor(Color.BLACK)
        val b = bg
        if (b != null) {
            val sc = maxOf(w / b.width, h / b.height)
            val dw = b.width * sc
            val dh = b.height * sc
            dst.set((w - dw) / 2f, (h - dh) / 2f, (w + dw) / 2f, (h + dh) / 2f)
            c.drawBitmap(b, null, dst, bgPaint)
        }

        val cx = w / 2f
        val cy = h * 0.40f
        val r = w * 0.36f * (1f + 0.018f * sin(two * t * 2f))

        glow.strokeWidth = w * 0.05f
        mid.strokeWidth = w * 0.02f
        core.strokeWidth = w * 0.006f
        c.drawCircle(cx, cy, r, glow)
        c.drawCircle(cx, cy, r, mid)
        c.drawCircle(cx, cy, r, core)

        oval.set(cx - r, cy - r, cx + r, cy + r)
        arc.strokeWidth = w * 0.014f
        c.drawArc(oval, t * 360f, 70f, false, arc)
        c.drawArc(oval, t * 360f + 180f, 70f, false, arc)

        val br = broom
        if (br != null) {
            val s = r * 1.6f
            val left = cx - s / 2f
            val top = cy - s / 2f
            dst.set(left, top, left + s, top + s)
            val angle = 22f * sin(two * t)
            c.save()
            c.rotate(angle, left + s * 0.78f, top + s * 0.16f)
            c.drawBitmap(br, null, dst, broomPaint)
            c.restore()
        }

        for (i in 0 until 14) {
            val ph = (t * 2f + i / 14f) % 1f
            val side = if (i % 2 == 0) -1f else 1f
            val x = cx + side * (r * 0.15f + ph * r * 0.75f)
            val y = cy + r * 0.50f - ph * r * 0.12f + sin(i * 1.7f) * r * 0.05f
            fill.color = Color.argb(((1f - ph) * 220f).toInt(), 120, 255, 150)
            c.drawCircle(x, y, (1f - ph) * w * 0.012f + 2f, fill)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        anim.start()
    }

    override fun onDetachedFromWindow() {
        anim.cancel()
        super.onDetachedFromWindow()
    }
}

class CacheAccessibilityService : AccessibilityService() {

    companion object {
        var instance: CacheAccessibilityService? = null
        fun running(): Boolean = instance != null

        private val STORAGE = setOf(
            "storage", "storage & cache", "storage and cache",
            "хранилище", "память", "xotira"
        )
        private val CLEAR_CACHE = setOf(
            "clear cache", "очистить кеш", "очистить кэш",
            "keshni tozalash", "keshni o'chirish"
        )
    }

    private val queue = ArrayDeque<String>()
    private var busy = false
    private var gen = 0
    private var total = 0
    private var done = 0
    private val h = Handler(Looper.getMainLooper())

    private var overlay: View? = null
    private var statusText: TextView? = null

    override fun onServiceConnected() { instance = this }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        hideOverlay()
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override f
